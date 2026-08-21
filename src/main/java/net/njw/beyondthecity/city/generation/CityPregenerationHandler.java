package net.njw.beyondthecity.city.generation;

import net.minecraft.resources.ResourceKey;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CityPregenerationHandler {

    /*
     * 실제 도시 경계보다 추가로 pregenerate할 청크 수.
     *
     * 8 chunks = 128 blocks
     */
    private static final int PREGENERATION_MARGIN_CHUNKS =
            8;

    /*
     * 몇 청크마다 진행 상황을 로그에 출력할지.
     */
    private static final int LOG_INTERVAL_CHUNKS =
            100;

    /*
     * 몇 청크마다 진행 상태를 SavedData에 기록할지.
     */
    private static final int SAVE_INTERVAL_CHUNKS =
            100;

    /*
     * 한 server tick에서 pregeneration에 사용할
     * 목표 최대 시간.
     *
     * 5 ms = 5,000,000 ns
     *
     * 청크 하나를 생성하는 도중에는 중단할 수 없으므로
     * 실제 사용 시간이 5ms를 조금 넘을 수 있다.
     */
    private static final long TIME_BUDGET_NANOS =
            5_000_000L;

    /*
     * 청크 생성이 매우 빠른 경우에도
     * 한 tick에서 지나치게 많은 청크를 처리하지 않도록
     * 두는 추가 안전장치.
     */
    private static final int MAX_CHUNKS_PER_TICK =
            8;

    /*
     * 현재 처리해야 할 모든 pregeneration 작업.
     *
     * 예:
     *
     * starting_city / minecraft:overworld
     * starting_city / minecraft:the_nether
     * city_1        / minecraft:overworld
     * city_1        / minecraft:the_nether
     *
     * 현재는 CityManager가 Starting City 하나만 반환하므로
     * 실제로는 두 작업만 등록된다.
     *
     * 이후 CityManager가 여러 도시를 반환하게 되면
     * 이 클래스는 수정하지 않아도 자동으로 작업이 늘어난다.
     */
    private static final List<PregenerationTask> tasks =
            new ArrayList<>();

    /*
     * 현재 처리하고 있는 task index.
     */
    private static int currentTaskIndex = 0;

    private static CitySavedData savedData;

    /*
     * Pregeneration 작업이 현재 활성 상태인지.
     */
    private static boolean active = false;

    private CityPregenerationHandler() {
    }

    /*
     * =========================================================
     * Server Start
     * =========================================================
     */

    @SubscribeEvent
    public static void onServerStarted(
            ServerStartedEvent event
    ) {
        MinecraftServer server =
                event.getServer();

        /*
         * 같은 JVM에서 이전 서버의 상태가 남아 있을 가능성을
         * 방지하기 위해 먼저 초기화한다.
         */
        tasks.clear();
        currentTaskIndex = 0;
        active = false;

        savedData =
                server.getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        /*
         * 현재 월드에 존재하는 모든 도시를 대상으로
         * pregeneration task를 생성한다.
         *
         * 현재 CityManager.getCities(server)는
         * Starting City 하나만 반환한다.
         *
         * 나중에 여러 도시를 반환하도록 바뀌면
         * 자동으로 모든 도시가 대상이 된다.
         */
        for (City city :
                CityManager.getAccessibleCities(server)) {

            registerCityTasks(
                    server,
                    city
            );
        }

        active =
                !tasks.isEmpty();

        if (!active) {
            BeyondtheCity.LOGGER.info(
                    "All city pregeneration tasks are already completed."
            );

            return;
        }

        BeyondtheCity.LOGGER.info(
                "City pregeneration started with {} pending task(s).",
                tasks.size()
        );

        for (PregenerationTask task : tasks) {
            BeyondtheCity.LOGGER.info(
                    "Pregeneration task: city={}, dimension={}, progress={}/{}",
                    task.cityId(),
                    task.dimension().identifier(),
                    task.pregenerator().getGeneratedChunks(),
                    task.pregenerator().getTotalChunks()
            );
        }
    }

    /*
     * =========================================================
     * Runtime Task Enqueue
     * =========================================================
     */

    /**
     * 서버가 실행 중인 상태에서 새로 accessible 상태가 된 도시를
     * pregeneration queue에 추가한다.
     *
     * 새 도시 생성이나 도시 unlock 시 호출한다.
     */
    public static void enqueueCity(
            MinecraftServer server,
            City city
    ) {
        if (savedData == null) {
            savedData =
                    server.getDataStorage()
                            .computeIfAbsent(
                                    CitySavedData.TYPE
                            );
        }

        int previousTaskCount =
                tasks.size();

        registerCityTasks(
                server,
                city
        );

        int addedTaskCount =
                tasks.size()
                        - previousTaskCount;

        if (addedTaskCount <= 0) {
            BeyondtheCity.LOGGER.info(
                    "No pregeneration task was added for city {}.",
                    city.id()
            );

            return;
        }

        /*
         * 이전 모든 task가 끝나 active=false였더라도
         * 새 task가 추가되었으므로 다시 활성화한다.
         *
         * currentTaskIndex는 기존 tasks.size() 위치에 있었으므로
         * 새로 append된 첫 task를 그대로 가리키게 된다.
         */
        active = true;

        BeyondtheCity.LOGGER.info(
                "Added {} pregeneration task(s) for city {}.",
                addedTaskCount,
                city.id()
        );
    }

    /*
     * =========================================================
     * Task Registration
     * =========================================================
     */

    private static void registerCityTasks(
            MinecraftServer server,
            City city
    ) {
        /*
         * 기본 Minecraft 차원은
         * Overworld → Nether 순으로 먼저 등록한다.
         *
         * 기존 동작처럼 Overworld를 우선 pregenerate하기 위해서다.
         */
        registerTaskIfNeeded(
                server,
                city,
                Level.OVERWORLD
        );

        registerTaskIfNeeded(
                server,
                city,
                Level.NETHER
        );

        /*
         * 이후 City에 End나 custom dimension이 추가되어도
         * 자동으로 pregeneration할 수 있도록 나머지 차원도 등록.
         */
        for (
                Map.Entry<ResourceKey<Level>, CityRegion> entry :
                city.regions().entrySet()
        ) {
            ResourceKey<Level> dimension =
                    entry.getKey();

            if (
                    dimension == Level.OVERWORLD
                            || dimension == Level.NETHER
            ) {
                continue;
            }

            registerTaskIfNeeded(
                    server,
                    city,
                    dimension
            );
        }
    }

    private static void registerTaskIfNeeded(
            MinecraftServer server,
            City city,
            ResourceKey<Level> dimension
    ) {
        CityRegion region =
                city.getRegion(
                        dimension
                ).orElse(null);

        if (region == null) {
            return;
        }

        /*
         * 이미 완료된 도시 / 차원은
         * task 자체를 만들지 않는다.
         */
        if (
                savedData.isPregenerationCompleted(
                        city.id(),
                        dimension
                )
        ) {
            return;
        }

        ServerLevel level =
                server.getLevel(
                        dimension
                );

        /*
         * 해당 차원이 서버에 존재하지 않으면
         * pregeneration할 수 없다.
         */
        if (level == null) {
            BeyondtheCity.LOGGER.warn(
                    "Cannot pregenerate city {} in dimension {} because the level is unavailable.",
                    city.id(),
                    dimension.identifier()
            );

            return;
        }

        long alreadyGeneratedChunks =
                savedData.getPregeneratedChunks(
                        city.id(),
                        dimension
                );

        CityPregenerator pregenerator =
                new CityPregenerator(
                        level,
                        region,
                        PREGENERATION_MARGIN_CHUNKS,
                        alreadyGeneratedChunks
                );

        tasks.add(
                new PregenerationTask(
                        city.id(),
                        dimension,
                        pregenerator
                )
        );
    }

    /*
     * =========================================================
     * Server Tick
     * =========================================================
     */

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event
    ) {
        if (
                !active
                        || savedData == null
        ) {
            return;
        }

        long startTime =
                System.nanoTime();

        int generatedThisTick =
                0;

        /*
         * 현재 task부터 순차적으로 처리한다.
         *
         * 하나의 task가 끝나면
         * 같은 tick에서 시간이 남아 있을 경우
         * 다음 task로 넘어갈 수도 있다.
         */
        while (
                currentTaskIndex < tasks.size()
                        && generatedThisTick
                        < MAX_CHUNKS_PER_TICK
        ) {
            /*
             * 이번 tick의 time budget을 이미 사용했다면
             * 다음 tick으로 넘긴다.
             *
             * 단, 아직 청크를 하나도 생성하지 않은 경우에는
             * 최소 한 청크는 처리하도록 한다.
             */
            if (
                    generatedThisTick > 0
                            && System.nanoTime()
                            - startTime
                            >= TIME_BUDGET_NANOS
            ) {
                break;
            }

            PregenerationTask task =
                    tasks.get(
                            currentTaskIndex
                    );

            CityPregenerator pregenerator =
                    task.pregenerator();

            /*
             * 이미 완료 상태라면
             * 다음 task로 이동.
             */
            if (pregenerator.isFinished()) {
                completeTask(
                        task
                );

                currentTaskIndex++;

                continue;
            }

            /*
             * 다음 spiral chunk 생성.
             */
            pregenerator.generateNextChunk();

            generatedThisTick++;

            long generated =
                    pregenerator
                            .getGeneratedChunks();

            /*
             * 일정 청크마다 진행 상태를 SavedData에 저장.
             */
            if (
                    generated
                            % SAVE_INTERVAL_CHUNKS == 0
            ) {
                saveTaskProgress(
                        task
                );
            }

            /*
             * 일정 청크마다 로그 출력.
             */
            if (
                    generated > 0
                            && generated
                            % LOG_INTERVAL_CHUNKS == 0
            ) {
                logTaskProgress(
                        task
                );
            }

            /*
             * 방금 생성한 청크가 마지막 청크였다면
             * 즉시 완료 처리.
             */
            if (pregenerator.isFinished()) {
                completeTask(
                        task
                );

                currentTaskIndex++;
            }
        }

        /*
         * 모든 task 완료.
         */
        if (
                currentTaskIndex
                        >= tasks.size()
        ) {
            active = false;

            BeyondtheCity.LOGGER.info(
                    "All city pregeneration tasks completed."
            );
        }
    }

    /*
     * =========================================================
     * Progress
     * =========================================================
     */

    private static void saveTaskProgress(
            PregenerationTask task
    ) {
        savedData.setPregeneratedChunks(
                task.cityId(),
                task.dimension(),
                task.pregenerator()
                        .getGeneratedChunks()
        );
    }

    private static void logTaskProgress(
            PregenerationTask task
    ) {
        BeyondtheCity.LOGGER.info(
                "City pregeneration: city={}, dimension={}, progress={}/{}",
                task.cityId(),
                task.dimension().identifier(),
                task.pregenerator()
                        .getGeneratedChunks(),
                task.pregenerator()
                        .getTotalChunks()
        );
    }

    /*
     * =========================================================
     * Completion
     * =========================================================
     */

    private static void completeTask(
            PregenerationTask task
    ) {
        CityPregenerator pregenerator =
                task.pregenerator();

        /*
         * SAVE_INTERVAL_CHUNKS의 배수가 아닌 지점에서
         * 완료될 수 있으므로 마지막 진행량을 반드시 저장한다.
         */
        savedData.setPregeneratedChunks(
                task.cityId(),
                task.dimension(),
                pregenerator.getGeneratedChunks()
        );

        savedData.markPregenerationCompleted(
                task.cityId(),
                task.dimension()
        );

        BeyondtheCity.LOGGER.info(
                "City pregeneration completed: city={}, dimension={} ({}/{})",
                task.cityId(),
                task.dimension().identifier(),
                pregenerator.getGeneratedChunks(),
                pregenerator.getTotalChunks()
        );
    }

    /*
     * =========================================================
     * Server Stop
     * =========================================================
     */

    @SubscribeEvent
    public static void onServerStopped(
            ServerStoppedEvent event
    ) {
        /*
         * 완료되지 않은 task의 마지막 진행량도 저장한다.
         *
         * 평소에는 100청크 단위로 저장하지만
         * 정상적인 서버 종료 시에는 마지막 지점을
         * 최대한 정확하게 보존한다.
         */
        if (savedData != null) {
            for (
                    int i = currentTaskIndex;
                    i < tasks.size();
                    i++
            ) {
                PregenerationTask task =
                        tasks.get(i);

                if (
                        !task.pregenerator()
                                .isFinished()
                ) {
                    saveTaskProgress(
                            task
                    );
                }
            }
        }

        tasks.clear();

        currentTaskIndex = 0;

        savedData = null;

        active = false;
    }

    /*
     * =========================================================
     * Task
     * =========================================================
     */

    private record PregenerationTask(
            String cityId,
            ResourceKey<Level> dimension,
            CityPregenerator pregenerator
    ) {
    }
}