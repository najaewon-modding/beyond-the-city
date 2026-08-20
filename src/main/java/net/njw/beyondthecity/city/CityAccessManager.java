package net.njw.beyondthecity.city;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class CityAccessManager {

    private CityAccessManager() {
    }

    public static boolean isInsideAccessibleArea(
            ServerPlayer player
    ) {
        City city = CityRegistry.STARTING_CITY;

        CityRegion region =
                city.getRegion(
                        player.level().dimension()
                ).orElse(null);

        if (region == null) {
            return false;
        }

        return region.containsBlock(
                player.getBlockX(),
                player.getBlockZ()
        );
    }

    public static boolean isBlockInsideAccessibleArea(
            ServerPlayer player,
            int blockX,
            int blockZ
    ) {
        City city = CityRegistry.STARTING_CITY;

        CityRegion region =
                city.getRegion(
                        player.level().dimension()
                ).orElse(null);

        if (region == null) {
            return false;
        }

        return region.containsBlock(
                blockX,
                blockZ
        );
    }

    public static boolean isEntityInsideAccessibleArea(
            ServerPlayer player,
            Entity entity
    ) {
        City city = CityRegistry.STARTING_CITY;

        CityRegion region =
                city.getRegion(
                        player.level().dimension()
                ).orElse(null);

        if (region == null) {
            return false;
        }

        return region.containsBlock(
                entity.getBlockX(),
                entity.getBlockZ()
        );
    }
}