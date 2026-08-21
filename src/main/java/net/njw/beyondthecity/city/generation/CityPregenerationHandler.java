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
import net.njw.beyondthecity.city.CityRegion;
import net.njw.beyondthecity.city.CityRegistry;
import net.njw.beyondthecity.city.CitySavedData;

public final class CityPregenerationHandler {

    /*
     * 한 tick마다 생성할 청크 수.
     */
    private static final int CHUNKS_PER_TICK = 2;

    /*
     * 진행 상태를 몇 청크마다 SavedData에 기록할지.
     */
    private static final int SAVE_INTERVAL_CHUNKS = 100;

    private static CityPregenerator overworldPregenerator;
    private static CityPregenerator netherPregenerator;

    private static CitySavedData savedData;

    private static int lastLoggedOverworldProgress = -1;
    private static int lastLoggedNetherProgress = -1;

    /*
     * Pregeneration 작업이 현재 활성 상태인지.
     *
     * 완료 후 매 tick마다 완료 로그가 반복되는 것을 방지한다.
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
                server.getLevel(Level.OVERWORLD);

        ServerLevel nether =
                server.getLevel(Level.NETHER);

        if (overworld == null || nether == null) {
            return;
        }

        savedData =
                server.getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        City city =
                CityRegistry.STARTING_CITY;

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
                            savedData
                                    .getNetherPregeneratedChunks()
                    );
        } else {
            netherPregenerator = null;
        }

        lastLoggedOverworldProgress = -1;
        lastLoggedNetherProgress = -1;

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

        int remainingChunks =
                CHUNKS_PER_TICK;

        /*
         * Overworld 우선 생성.
         */
        while (
                remainingChunks > 0
                        && overworldPregenerator != null
                        && !overworldPregenerator.isFinished()
        ) {
            overworldPregenerator
                    .generateNextChunk();

            long generated =
                    overworldPregenerator
                            .getGeneratedChunks();

            if (
                    generated
                            % SAVE_INTERVAL_CHUNKS == 0
            ) {
                savedData
                        .setOverworldPregeneratedChunks(
                                generated
                        );
            }

            remainingChunks--;
        }

        /*
         * Overworld 완료 후
         * 남은 budget으로 Nether 생성.
         */
        while (
                remainingChunks > 0
                        && netherPregenerator != null
                        && !netherPregenerator.isFinished()
        ) {
            netherPregenerator
                    .generateNextChunk();

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

            remainingChunks--;
        }

        logProgress();

        /*
         * Overworld 완료 처리.
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
         * Nether 완료 처리.
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
         * 모든 차원 완료.
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

            if (generated % 100 == 0 && generated > 0) {
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

            if (generated % 100 == 0 && generated > 0) {
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

        lastLoggedOverworldProgress = -1;
        lastLoggedNetherProgress = -1;

        active = false;
    }
}