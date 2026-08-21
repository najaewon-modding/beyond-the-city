package net.njw.beyondthecity.city;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;

public final class CityManager {

    private CityManager() {
    }

    public static City getStartingCity(
            MinecraftServer server
    ) {
        /*
         * 임시 구현.
         *
         * 다음 단계에서 CitySavedData에서
         * 실제 월드의 starting_city를 가져오도록 변경한다.
         */
        return CityRegistry.STARTING_CITY_TEMPLATE;
    }

    public static Collection<City> getCities(
            MinecraftServer server
    ) {
        /*
         * 임시 구현.
         *
         * 다음 단계에서 CitySavedData의
         * 모든 도시를 반환하도록 변경한다.
         */
        return List.of(
                getStartingCity(server)
        );
    }

    public static City getCity(
            MinecraftServer server,
            String cityId
    ) {
        for (City city : getCities(server)) {
            if (city.id().equals(cityId)) {
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
        for (City city : getCities(server)) {
            if (city.contains(
                    dimension,
                    blockX,
                    blockZ
            )) {
                return true;
            }
        }

        return false;
    }
}