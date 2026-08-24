package net.njw.beyondthecity.city.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.njw.beyondthecity.city.City;
import net.njw.beyondthecity.city.CityManager;
import net.njw.beyondthecity.city.CityRegion;
import net.njw.beyondthecity.city.CityRegistry;
import net.njw.beyondthecity.city.generation.CityPregenerationHandler;
import net.njw.beyondthecity.city.placement.CityPlacementService;
import net.njw.beyondthecity.network.CitySyncService;

import java.util.Collection;

public final class CityTestCommand {

    private static final int BLOCKS_PER_CHUNK =
            16;

    private CityTestCommand() {
    }

    /*
     * =========================================================
     * Command Registration
     * =========================================================
     */

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {
        event.getDispatcher().register(
                Commands.literal("btc")

                        /*
                         * 테스트용 관리 명령어이므로
                         * 일반 플레이어가 사용할 수 없도록 제한.
                         */
                        .requires(
                                Commands.hasPermission(
                                        Commands.LEVEL_GAMEMASTERS
                                )
                        )

                        .then(
                                Commands.literal("city")

                                        /*
                                         * /btc city create
                                         *
                                         * 새 도시를 만들고
                                         * 즉시 accessible 상태로 등록.
                                         */
                                        .then(
                                                Commands.literal("create")

                                                        .executes(
                                                                context ->
                                                                        createAccessibleCity(
                                                                                context.getSource()
                                                                        )
                                                        )

                                                        /*
                                                         * /btc city create locked
                                                         *
                                                         * 도시만 생성하고
                                                         * 아직 accessible 상태로 만들지 않는다.
                                                         */
                                                        .then(
                                                                Commands.literal("locked")
                                                                        .executes(
                                                                                context ->
                                                                                        createLockedCity(
                                                                                                context.getSource()
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        /*
                                         * /btc city unlock <cityId>
                                         */
                                        .then(
                                                Commands.literal("unlock")
                                                        .then(
                                                                Commands.argument(
                                                                                "cityId",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .executes(
                                                                                context ->
                                                                                        unlockCity(
                                                                                                context.getSource(),
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "cityId"
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        /*
                                         * /btc city list
                                         */
                                        .then(
                                                Commands.literal("list")
                                                        .executes(
                                                                context ->
                                                                        listCities(
                                                                                context.getSource()
                                                                        )
                                                        )
                                        )

                                        /*
                                         * /btc city info <cityId>
                                         */
                                        .then(
                                                Commands.literal("info")
                                                        .then(
                                                                Commands.argument(
                                                                                "cityId",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .executes(
                                                                                context ->
                                                                                        showCityInfo(
                                                                                                context.getSource(),
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "cityId"
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )

                                        /*
                                         * /btc city delete <cityId>
                                         */
                                        .then(
                                                Commands.literal(
                                                                "delete"
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "cityId",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .executes(
                                                                                context ->
                                                                                        deleteCity(
                                                                                                context.getSource(),
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "cityId"
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )
                        )
        );
    }

    /*
     * =========================================================
     * Create Accessible City
     * =========================================================
     */

    private static int createAccessibleCity(
            CommandSourceStack source
    ) {
        MinecraftServer server =
                source.getServer();

        int cityNumber =
                findNextCityNumber(
                        server
                );

        String cityId =
                "city_"
                        + cityNumber;

        String cityName =
                "City "
                        + cityNumber;

        try {
            City city =
                    CityPlacementService.placeAccessibleCity(
                            server,
                            cityId,
                            cityName
                    );

            /*
             * 서버 실행 중 생성된 accessible city이므로
             * pregeneration 작업도 즉시 queue에 추가한다.
             */
            CityPregenerationHandler.enqueueCity(
                    server,
                    city
            );

            CitySyncService.syncToAll(
                    server
            );

            source.sendSuccess(
                    () ->
                            Component.translatable(
                                    "command.njw_beyond_the_city.city.created_accessible",
                                    city.id()
                            ),
                    false
            );

            sendCityCoordinates(
                    source,
                    city
            );

            return 1;

        } catch (RuntimeException exception) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.failed_create",
                            exception.getMessage()
                    )
            );

            return 0;
        }
    }

    /*
     * =========================================================
     * Create Locked City
     * =========================================================
     */

    private static int createLockedCity(
            CommandSourceStack source
    ) {
        MinecraftServer server =
                source.getServer();

        int cityNumber =
                findNextCityNumber(
                        server
                );

        String cityId =
                "city_"
                        + cityNumber;

        String cityName =
                "City "
                        + cityNumber;

        try {
            City city =
                    CityPlacementService.placeLockedCity(
                            server,
                            cityId,
                            cityName
                    );

            /*
             * locked city는 accessible하지 않으므로
             * pregeneration은 시작하지 않는다.
             *
             * 하지만 도시 목록 UI에는 즉시 나타나야 하므로
             * 모든 클라이언트에 city snapshot을 다시 보낸다.
             */
            CitySyncService.syncToAll(
                    server
            );

            source.sendSuccess(
                    () ->
                            Component.translatable(
                                    "command.njw_beyond_the_city.city.created_locked",
                                    city.id()
                            ),
                    false
            );

            sendCityCoordinates(
                    source,
                    city
            );

            return 1;

        } catch (RuntimeException exception) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.failed_create",
                            exception.getMessage()
                    )
            );

            return 0;
        }
    }

    /*
     * =========================================================
     * Unlock
     * =========================================================
     */

    private static int unlockCity(
            CommandSourceStack source,
            String cityId
    ) {
        MinecraftServer server =
                source.getServer();

        City city =
                CityManager.getCity(
                        server,
                        cityId
                );

        if (city == null) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.unknown",
                            cityId
                    )
            );

            return 0;
        }

        if (
                CityManager.isCityAccessible(
                        server,
                        cityId
                )
        ) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.already_accessible",
                            cityId
                    )
            );

            return 0;
        }

        CityManager.unlockCity(
                server,
                cityId
        );

        /*
         * 새로 accessible 상태가 되었으므로
         * pregeneration을 시작한다.
         */
        CityPregenerationHandler.enqueueCity(
                server,
                city
        );

        CitySyncService.syncToAll(
                server
        );

        source.sendSuccess(
                () ->
                        Component.translatable(
                                "command.njw_beyond_the_city.city.unlocked",
                                cityId
                        ),
                false
        );

        return 1;
    }

    /*
     * =========================================================
     * List
     * =========================================================
     */

    private static int listCities(
            CommandSourceStack source
    ) {
        MinecraftServer server =
                source.getServer();

        Collection<City> cities =
                CityManager.getCities(
                        server
                );

        int cityCount =
                cities.size();

        source.sendSuccess(
                () ->
                        Component.translatable(
                                "command.njw_beyond_the_city.city.count",
                                cityCount
                        ),
                false
        );

        for (City city : cities) {
            boolean accessible =
                    CityManager.isCityAccessible(
                            server,
                            city.id()
                    );

            source.sendSuccess(
                    () ->
                            Component.translatable(
                                    accessible
                                            ? "command.njw_beyond_the_city.city.list_entry.accessible"
                                            : "command.njw_beyond_the_city.city.list_entry.locked",
                                    city.id()
                            ),
                    false
            );
        }

        return cityCount;
    }

    /*
     * =========================================================
     * Info
     * =========================================================
     */

    private static int showCityInfo(
            CommandSourceStack source,
            String cityId
    ) {
        MinecraftServer server =
                source.getServer();

        City city =
                CityManager.getCity(
                        server,
                        cityId
                );

        if (city == null) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.unknown",
                            cityId
                    )
            );

            return 0;
        }

        boolean accessible =
                CityManager.isCityAccessible(
                        server,
                        city.id()
                );

        Component state =
                Component.translatable(
                        accessible
                                ? "command.njw_beyond_the_city.city.state.accessible"
                                : "command.njw_beyond_the_city.city.state.locked"
                );

        /*
         * city.name() 자체는 번역하지 않는다.
         *
         * 나중에 도시 이름을 사용자가 직접 변경할 수 있도록
         * 저장된 이름을 그대로 출력한다.
         */
        source.sendSuccess(
                () ->
                        Component.translatable(
                                "command.njw_beyond_the_city.city.info",
                                city.id(),
                                Component.literal(
                                        city.name()
                                ),
                                state
                        ),
                false
        );

        sendCityCoordinates(
                source,
                city
        );

        return 1;
    }

    /*
     * =========================================================
     * City Coordinates
     * =========================================================
     */

    private static void sendCityCoordinates(
            CommandSourceStack source,
            City city
    ) {
        sendRegionCoordinates(
                source,
                Component.translatable(
                        "gui.njw_beyond_the_city.city_list.dimension.overworld"
                ),
                city.getRegion(
                        Level.OVERWORLD
                ).orElse(null)
        );

        sendRegionCoordinates(
                source,
                Component.translatable(
                        "gui.njw_beyond_the_city.city_list.dimension.nether"
                ),
                city.getRegion(
                        Level.NETHER
                ).orElse(null)
        );
    }

    private static void sendRegionCoordinates(
            CommandSourceStack source,
            Component dimensionName,
            CityRegion region
    ) {
        if (region == null) {
            return;
        }

        long centerBlockX =
                (long) region.centerChunkX()
                        * BLOCKS_PER_CHUNK;

        long centerBlockZ =
                (long) region.centerChunkZ()
                        * BLOCKS_PER_CHUNK;

        source.sendSuccess(
                () ->
                        Component.translatable(
                                "command.njw_beyond_the_city.city.coordinates",
                                dimensionName,
                                region.centerChunkX(),
                                region.centerChunkZ(),
                                centerBlockX,
                                centerBlockZ
                        ),
                false
        );
    }

    /*
     * =========================================================
     * Automatic City ID
     * =========================================================
     */

    private static int findNextCityNumber(
            MinecraftServer server
    ) {
        int cityNumber =
                1;

        while (
                CityManager.getCity(
                        server,
                        "city_"
                                + cityNumber
                ) != null
        ) {
            cityNumber++;
        }

        return cityNumber;
    }

    /*
     * =========================================================
     * Delete City
     * =========================================================
     */

    private static int deleteCity(
            CommandSourceStack source,
            String cityId
    ) {
        MinecraftServer server =
                source.getServer();

        /*
         * Starting City는 삭제 금지.
         */
        if (
                CityRegistry.STARTING_CITY_ID.equals(
                        cityId
                )
        ) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.starting_delete_forbidden"
                    )
            );

            return 0;
        }

        City city =
                CityManager.getCity(
                        server,
                        cityId
                );

        if (city == null) {
            source.sendFailure(
                    Component.translatable(
                            "command.njw_beyond_the_city.city.unknown",
                            cityId
                    )
            );

            return 0;
        }

        /*
         * 현재 해당 도시 안에 플레이어가 있다면
         * 삭제를 막는다.
         *
         * 도시 삭제 직후 그 플레이어의 safe position이나
         * boundary 상태가 애매해지는 것을 방지한다.
         */
        for (
                var player :
                server.getPlayerList()
                        .getPlayers()
        ) {
            if (
                    city.contains(
                            player.level()
                                    .dimension(),
                            player.getBlockX(),
                            player.getBlockZ()
                    )
            ) {
                source.sendFailure(
                        Component.translatable(
                                "command.njw_beyond_the_city.city.delete_player_inside",
                                player.getName()
                        )
                );

                return 0;
            }
        }

        /*
         * 런타임 pregeneration queue에서 먼저 제거.
         */
        CityPregenerationHandler.removeCity(
                cityId
        );

        /*
         * SavedData에서 도시 제거.
         */
        CityManager.removeCity(
                server,
                cityId
        );

        /*
         * UI / boundary client cache 즉시 갱신.
         */
        CitySyncService.syncToAll(
                server
        );

        source.sendSuccess(
                () ->
                        Component.translatable(
                                "command.njw_beyond_the_city.city.deleted",
                                cityId
                        ),
                false
        );

        return 1;
    }
}