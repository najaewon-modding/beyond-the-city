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
import net.njw.beyondthecity.city.generation.CityPregenerationHandler;
import net.njw.beyondthecity.city.placement.CityPlacementService;

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

            source.sendSuccess(
                    () ->
                            Component.literal(
                                    "Created accessible city: "
                                            + city.id()
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
                    Component.literal(
                            "Failed to create city: "
                                    + exception.getMessage()
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
             * locked city는 아직 접근 가능하지 않으므로
             * pregeneration queue에는 넣지 않는다.
             */
            source.sendSuccess(
                    () ->
                            Component.literal(
                                    "Created locked city: "
                                            + city.id()
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
                    Component.literal(
                            "Failed to create city: "
                                    + exception.getMessage()
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
                    Component.literal(
                            "Unknown city: "
                                    + cityId
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
                    Component.literal(
                            "City is already accessible: "
                                    + cityId
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

        source.sendSuccess(
                () ->
                        Component.literal(
                                "Unlocked city: "
                                        + cityId
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
                        Component.literal(
                                "Cities: "
                                        + cityCount
                        ),
                false
        );

        for (City city : cities) {
            boolean accessible =
                    CityManager.isCityAccessible(
                            server,
                            city.id()
                    );

            String state =
                    accessible
                            ? "accessible"
                            : "locked";

            String message =
                    "- "
                            + city.id()
                            + " ("
                            + state
                            + ")";

            source.sendSuccess(
                    () ->
                            Component.literal(
                                    message
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
                    Component.literal(
                            "Unknown city: "
                                    + cityId
                    )
            );

            return 0;
        }

        boolean accessible =
                CityManager.isCityAccessible(
                        server,
                        city.id()
                );

        String state =
                accessible
                        ? "accessible"
                        : "locked";

        source.sendSuccess(
                () ->
                        Component.literal(
                                city.id()
                                        + " / "
                                        + city.name()
                                        + " / "
                                        + state
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
                "Overworld",
                city.getRegion(
                        Level.OVERWORLD
                ).orElse(null)
        );

        sendRegionCoordinates(
                source,
                "Nether",
                city.getRegion(
                        Level.NETHER
                ).orElse(null)
        );
    }

    private static void sendRegionCoordinates(
            CommandSourceStack source,
            String dimensionName,
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

        String message =
                "  "
                        + dimensionName
                        + ": center chunk=("
                        + region.centerChunkX()
                        + ", "
                        + region.centerChunkZ()
                        + "), block=("
                        + centerBlockX
                        + ", "
                        + centerBlockZ
                        + ")";

        source.sendSuccess(
                () ->
                        Component.literal(
                                message
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
}