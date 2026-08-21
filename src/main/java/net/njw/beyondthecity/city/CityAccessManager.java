package net.njw.beyondthecity.city;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class CityAccessManager {

    private CityAccessManager() {
    }

    public static boolean isInsideAccessibleArea(
            ServerPlayer player
    ) {
        MinecraftServer server =
                player.level().getServer();

        if (server == null) {
            return false;
        }

        return CityManager.isInsideAccessibleCity(
                server,
                player.level().dimension(),
                player.getBlockX(),
                player.getBlockZ()
        );
    }

    public static boolean isBlockInsideAccessibleArea(
            ServerPlayer player,
            int blockX,
            int blockZ
    ) {
        MinecraftServer server =
                player.level().getServer();

        if (server == null) {
            return false;
        }

        return CityManager.isInsideAccessibleCity(
                server,
                player.level().dimension(),
                blockX,
                blockZ
        );
    }

    public static boolean isEntityInsideAccessibleArea(
            ServerPlayer player,
            Entity entity
    ) {
        MinecraftServer server =
                player.level().getServer();

        if (server == null) {
            return false;
        }

        return CityManager.isInsideAccessibleCity(
                server,
                entity.level().dimension(),
                entity.getBlockX(),
                entity.getBlockZ()
        );
    }
}