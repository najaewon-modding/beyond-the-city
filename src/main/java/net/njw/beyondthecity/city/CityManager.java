package net.njw.beyondthecity.city;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Collection;

public final class CityManager {

    private CityManager() {
    }

    /*
     * =========================================================
     * Saved Data
     * =========================================================
     */

    private static CitySavedData getSavedData(
            MinecraftServer server
    ) {
        return server.getDataStorage()
                .computeIfAbsent(
                        CitySavedData.TYPE
                );
    }

    /*
     * =========================================================
     * City Lookup
     * =========================================================
     */

    public static City getStartingCity(
            MinecraftServer server
    ) {
        City city =
                getSavedData(
                        server
                ).getCity(
                        CityRegistry.STARTING_CITY_ID
                );

        if (city == null) {
            throw new IllegalStateException(
                    "Starting city is missing."
            );
        }

        return city;
    }

    /**
     * 월드에 존재하는 모든 도시.
     *
     * 접근 가능 여부와는 관계없다.
     */
    public static Collection<City> getCities(
            MinecraftServer server
    ) {
        return getSavedData(
                server
        ).getCities();
    }

    /**
     * 현재 접근 가능한 도시만 반환한다.
     */
    public static Collection<City> getAccessibleCities(
            MinecraftServer server
    ) {
        return getSavedData(
                server
        ).getAccessibleCities();
    }

    public static City getCity(
            MinecraftServer server,
            String cityId
    ) {
        return getSavedData(
                server
        ).getCity(
                cityId
        );
    }

    /*
     * =========================================================
     * City Registration
     * =========================================================
     */

    /**
     * 도시를 등록만 한다.
     *
     * 접근 가능한 상태로 만들지는 않는다.
     */
    public static void addCity(
            MinecraftServer server,
            City city
    ) {
        getSavedData(
                server
        ).addCity(
                city
        );
    }

    /**
     * 도시를 생성과 동시에 접근 가능 상태로 등록한다.
     */
    public static void addAccessibleCity(
            MinecraftServer server,
            City city
    ) {
        getSavedData(
                server
        ).addAccessibleCity(
                city
        );
    }

    /*
     * =========================================================
     * Accessibility
     * =========================================================
     */

    public static boolean isCityAccessible(
            MinecraftServer server,
            String cityId
    ) {
        return getSavedData(
                server
        ).isCityAccessible(
                cityId
        );
    }

    public static void unlockCity(
            MinecraftServer server,
            String cityId
    ) {
        getSavedData(
                server
        ).unlockCity(
                cityId
        );
    }

    /*
     * =========================================================
     * Position Lookup
     * =========================================================
     */

    /**
     * 존재하는 모든 도시 중
     * 해당 위치를 포함하는 도시를 반환한다.
     */
    public static City findCityContaining(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ
    ) {
        for (
                City city :
                getCities(server)
        ) {
            if (
                    city.contains(
                            dimension,
                            blockX,
                            blockZ
                    )
            ) {
                return city;
            }
        }

        return null;
    }

    /**
     * 접근 가능한 도시 중
     * 해당 위치를 포함하는 도시를 반환한다.
     *
     * 다음 CityBoundaryHandler 일반화에서
     * 이 메서드를 사용하게 된다.
     */
    public static City findAccessibleCityContaining(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ
    ) {
        for (
                City city :
                getAccessibleCities(server)
        ) {
            if (
                    city.contains(
                            dimension,
                            blockX,
                            blockZ
                    )
            ) {
                return city;
            }
        }

        return null;
    }

    public static boolean isInsideAnyCity(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ
    ) {
        return findCityContaining(
                server,
                dimension,
                blockX,
                blockZ
        ) != null;
    }

    public static boolean isInsideAccessibleCity(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ
    ) {
        return findAccessibleCityContaining(
                server,
                dimension,
                blockX,
                blockZ
        ) != null;
    }
}