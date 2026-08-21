package net.njw.beyondthecity.city.generation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.city.City;
import net.njw.beyondthecity.city.CityManager;
import net.njw.beyondthecity.city.CityRegion;
import net.njw.beyondthecity.city.CitySavedData;

public final class CityPregenerationHandler {

    /*
     * 실제 도시 경계보다 추가로 pregenerate할 청크 수.
     */
    private static final int PREGENERATION_MARGIN_CHUNKS = 24;

    /*
     * 몇 청크마다 진행 상황을 로그에 출력할지.
     */
    private static final int LOG_INTERVAL_CHUNKS = 100;

    /*
     * 몇 청크마다 진행 상태를 SavedData에 기록할지.
     */
    private static final int SAVE_INTERVAL_CHUNKS = 100;

    /*
     * 한 server tick에서 pregeneration에 사용할
     * 목표 최대 시간.
     *
     * 5 ms = 5,000,000 ns
     */
    private static final long TIME_BUDGET_NANOS =
            5_000_000L;

    /*
     * 매우 빠르게 청크가 처리되는 경우에도
     * 한 tick에 지나치게 많은 청크를 처리하지 않도록
     * 추가로 거는 안전장치.
     */
    private static final int MAX_CHUNKS_PER_TICK = 8;

    private static CityPregenerator overworldPregenerator;
    private static CityPregenerator netherPregenerator;

    private static CitySavedData savedData;

    /*
     * Pregeneration 작업이 현재 활성 상태인지.
     *
     * 완료된 후 매 tick마다 완료 로그가 반복되는 것을 방지한다.
     */
    private static boolean active = false;

    private CityPregenerationHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(
            ServerStartedEvent event
    ) {
        MinecraftServer server =
                event.getServer();

        ServerLevel overworld =
                server.getLevel(
                        Level.OVERWORLD
                );

        ServerLevel nether =
                server.getLevel(
                        Level.NETHER
                );

        if (overworld == null || nether == null) {
            return;
        }

        savedData =
                server.getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        City city =
                CityManager.getStartingCity(
                        server
                );

        CityRegion overworldRegion =
                city.getRegion(
                        Level.OVERWORLD
                ).orElse(null);

        CityRegion netherRegion =
                city.getRegion(
                        Level.NETHER
                ).orElse(null);

        /*
         * Overworld pregeneration 복원.
         */
        if (
                overworldRegion != null
                        && !savedData
                        .isOverworldPregenerationCompleted()
        ) {
            overworldPregenerator =
                    new CityPregenerator(
                            overworld,
                            overworldRegion,
                            PREGENERATION_MARGIN_CHUNKS,
                            savedData
                                    .getOverworldPregeneratedChunks()
                    );
        } else {
            overworldPregenerator = null;
        }

        /*
         * Nether pregeneration 복원.
         */
        if (
                netherRegion != null
                        && !savedData
                        .isNetherPregenerationCompleted()
        ) {
            netherPregenerator =
                    new CityPregenerator(
                            nether,
                            netherRegion,
                            PREGENERATION_MARGIN_CHUNKS,
                            savedData
                                    .getNetherPregeneratedChunks()
                    );
        } else {
            netherPregenerator = null;
        }

        active =
                overworldPregenerator != null
                        || netherPregenerator != null;

        if (!active) {
            BeyondtheCity.LOGGER.info(
                    "Starting city pregeneration is already completed."
            );

            return;
        }

        BeyondtheCity.LOGGER.info(
                "Starting city pregeneration resumed. Overworld: {}, Nether: {}",
                savedData.getOverworldPregeneratedChunks(),
                savedData.getNetherPregeneratedChunks()
        );
    }

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event
    ) {
        if (!active || savedData == null) {
            return;
        }

        long startTime =
                System.nanoTime();

        int generatedThisTick = 0;

        /*
         * ---------------------------------------------------------
         * Overworld 우선 생성
         * ---------------------------------------------------------
         */
        while (
                overworldPregenerator != null
                        && !overworldPregenerator.isFinished()
                        && generatedThisTick
                        < MAX_CHUNKS_PER_TICK
        ) {
            overworldPregenerator
                    .generateNextChunk();

            generatedThisTick++;

            long generated =
                    overworldPregenerator
                            .getGeneratedChunks();

            /*
             * 일정 청크마다 진행 상태 저장.
             */
            if (
                    generated
                            % SAVE_INTERVAL_CHUNKS == 0
            ) {
                savedData
                        .setOverworldPregeneratedChunks(
                                generated
                        );
            }

            /*
             * 이번 tick에서 허용된 시간을
             * 이미 사용했으면 여기서 중단.
             */
            if (
                    System.nanoTime()
                            - startTime
                            >= TIME_BUDGET_NANOS
            ) {
                break;
            }
        }

        /*
         * ---------------------------------------------------------
         * Overworld가 끝났다면
         * 남은 시간으로 Nether 생성
         * ---------------------------------------------------------
         */
        if (
                overworldPregenerator == null
                        || overworldPregenerator.isFinished()
        ) {
            while (
                    netherPregenerator != null
                            && !netherPregenerator.isFinished()
                            && generatedThisTick
                            < MAX_CHUNKS_PER_TICK
                            && System.nanoTime()
                            - startTime
                            < TIME_BUDGET_NANOS
            ) {
                netherPregenerator
                        .generateNextChunk();

                generatedThisTick++;

                long generated =
                        netherPregenerator
                                .getGeneratedChunks();

                if (
                        generated
                                % SAVE_INTERVAL_CHUNKS == 0
                ) {
                    savedData
                            .setNetherPregeneratedChunks(
                                    generated
                            );
                }
            }
        }

        /*
         * 진행 상황 출력.
         */
        logProgress();

        /*
         * ---------------------------------------------------------
         * Overworld 완료 처리
         * ---------------------------------------------------------
         */
        if (
                overworldPregenerator != null
                        && overworldPregenerator.isFinished()
        ) {
            savedData
                    .setOverworldPregeneratedChunks(
                            overworldPregenerator
                                    .getGeneratedChunks()
                    );

            savedData
                    .markOverworldPregenerationCompleted();

            BeyondtheCity.LOGGER.info(
                    "Overworld city pregeneration completed. ({}/{})",
                    overworldPregenerator
                            .getGeneratedChunks(),
                    overworldPregenerator
                            .getTotalChunks()
            );

            overworldPregenerator = null;
        }

        /*
         * ---------------------------------------------------------
         * Nether 완료 처리
         * ---------------------------------------------------------
         */
        if (
                netherPregenerator != null
                        && netherPregenerator.isFinished()
        ) {
            savedData
                    .setNetherPregeneratedChunks(
                            netherPregenerator
                                    .getGeneratedChunks()
                    );

            savedData
                    .markNetherPregenerationCompleted();

            BeyondtheCity.LOGGER.info(
                    "Nether city pregeneration completed. ({}/{})",
                    netherPregenerator
                            .getGeneratedChunks(),
                    netherPregenerator
                            .getTotalChunks()
            );

            netherPregenerator = null;
        }

        /*
         * ---------------------------------------------------------
         * 모든 차원 완료
         * ---------------------------------------------------------
         */
        if (
                overworldPregenerator == null
                        && netherPregenerator == null
        ) {
            active = false;

            BeyondtheCity.LOGGER.info(
                    "Starting city pregeneration completed."
            );
        }
    }

    private static void logProgress() {
        if (
                overworldPregenerator != null
                        && !overworldPregenerator.isFinished()
        ) {
            long generated =
                    overworldPregenerator
                            .getGeneratedChunks();

            if (
                    generated > 0
                            && generated
                            % LOG_INTERVAL_CHUNKS == 0
            ) {
                BeyondtheCity.LOGGER.info(
                        "Overworld city pregeneration: {}/{}",
                        generated,
                        overworldPregenerator
                                .getTotalChunks()
                );
            }
        }

        if (
                netherPregenerator != null
                        && !netherPregenerator.isFinished()
        ) {
            long generated =
                    netherPregenerator
                            .getGeneratedChunks();

            if (
                    generated > 0
                            && generated
                            % LOG_INTERVAL_CHUNKS == 0
            ) {
                BeyondtheCity.LOGGER.info(
                        "Nether city pregeneration: {}/{}",
                        generated,
                        netherPregenerator
                                .getTotalChunks()
                );
            }
        }
    }

    /*
     * 같은 JVM에서 다른 월드를 열었을 때
     * 이전 IntegratedServer의 static 상태가 남지 않도록 정리.
     */
    @SubscribeEvent
    public static void onServerStopped(
            ServerStoppedEvent event
    ) {
        overworldPregenerator = null;
        netherPregenerator = null;
        savedData = null;

        active = false;
    }
}