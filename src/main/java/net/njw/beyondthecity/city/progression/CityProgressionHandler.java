package net.njw.beyondthecity.city.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.city.City;
import net.njw.beyondthecity.city.CityManager;
import net.njw.beyondthecity.city.generation.CityPregenerationHandler;
import net.njw.beyondthecity.city.placement.CityPlacementService;
import net.njw.beyondthecity.network.CitySyncService;

public final class CityProgressionHandler {

    /*
     * =========================================================
     * Temporary Progression Configuration
     * =========================================================
     */

    /*
     * Starting City를 포함한 전체 도시의 최대 개수.
     */
    private static final int MAX_CITY_COUNT =
            5;

    private CityProgressionHandler() {
    }

    /*
     * =========================================================
     * Ender Dragon Death
     * =========================================================
     *
     * 임시 progression rule:
     *
     * Ender Dragon이 죽을 때마다
     * accessible city를 하나 추가한다.
     *
     * Starting City를 포함하여
     * 최대 5개의 도시까지만 생성한다.
     */

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event
    ) {
        /*
         * Ender Dragon이 아니면 무시.
         */
        if (
                !(event.getEntity()
                        instanceof EnderDragon dragon)
        ) {
            return;
        }

        /*
         * 클라이언트 측 event는 무시하고
         * 실제 서버 world에서만 처리.
         */
        if (
                !(dragon.level()
                        instanceof ServerLevel serverLevel)
        ) {
            return;
        }

        MinecraftServer server =
                serverLevel.getServer();

        /*
         * =====================================================
         * Maximum City Count
         * =====================================================
         */

        int currentCityCount =
                CityManager.getCities(
                        server
                ).size();

        /*
         * Starting City 포함 총 5개가 이미 존재하면
         * 더 이상 생성하지 않는다.
         */
        if (
                currentCityCount
                        >= MAX_CITY_COUNT
        ) {

            BeyondtheCity.LOGGER.info(
                    "Ender Dragon defeated, but maximum city count "
                            + "has already been reached: {}/{}",
                    currentCityCount,
                    MAX_CITY_COUNT
            );

            return;
        }

        /*
         * =====================================================
         * New City ID / Name
         * =====================================================
         */

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

        /*
         * =====================================================
         * Create City
         * =====================================================
         */

        try {
            /*
             * 새 도시는 즉시 accessible 상태로 생성.
             *
             * 위치 선정은 기존 CityPlacementService의
             * 8000 ~ 12000 block placement 규칙을 그대로 사용.
             */
            City city =
                    CityPlacementService.placeAccessibleCity(
                            server,
                            cityId,
                            cityName
                    );

            /*
             * =================================================
             * Pregeneration
             * =================================================
             *
             * 새로 접근 가능한 도시이므로
             * Overworld / Nether pregeneration을 queue에 추가.
             */
            CityPregenerationHandler.enqueueCity(
                    server,
                    city
            );

            /*
             * =================================================
             * Client Synchronization
             * =================================================
             *
             * GUI와 boundary renderer에서
             * 새 도시가 즉시 보이게 한다.
             */
            CitySyncService.syncToAll(
                    server
            );

            BeyondtheCity.LOGGER.info(
                    "Unlocked new city after Ender Dragon defeat: "
                            + "city={}, name={}, cityCount={}/{}",
                    city.id(),
                    city.name(),
                    currentCityCount + 1,
                    MAX_CITY_COUNT
            );

        } catch (RuntimeException exception) {

            /*
             * placement 실패 등으로 서버 자체가 죽지 않도록
             * progression 실패만 로그로 남긴다.
             */
            BeyondtheCity.LOGGER.error(
                    "Failed to create a city after Ender Dragon defeat.",
                    exception
            );
        }
    }

    /*
     * =========================================================
     * Automatic City Number
     * =========================================================
     *
     * city_1
     * city_2
     * city_3
     * ...
     *
     * 중에서 아직 사용되지 않은 가장 작은 번호를 사용한다.
     *
     * 예:
     *
     * city_1
     * city_3
     *
     * 가 존재한다면 다음은 city_2.
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