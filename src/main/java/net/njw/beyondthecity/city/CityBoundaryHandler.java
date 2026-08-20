package net.njw.beyondthecity.city;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

public final class CityBoundaryHandler {

    private static final int TELEPORT_DELAY_TICKS = 20 * 5;
    private static final double BOUNDARY_MARGIN = 10.0;

    private static final int POSITION_SAVE_INTERVAL_TICKS = 20;

    private CityBoundaryHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.level();
        City city = CityRegistry.STARTING_CITY;

        CityRegion region = city.getRegion(level.dimension()).orElse(null);

        if (region == null) {
            return;
        }

        CitySavedData savedData =
                level.getServer()
                        .getDataStorage()
                        .computeIfAbsent(CitySavedData.TYPE);

        if (region.containsBlock(
                player.getBlockX(),
                player.getBlockZ()
        )) {
            handleInsideCity(
                    player,
                    level,
                    savedData
            );

            return;
        }

        handleOutsideCity(
                player,
                region,
                savedData
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();

        if (PlayerPositionTracker.getOutsideTicks(playerId) > 0) {
            ServerLevel level = player.level();

            CitySavedData savedData =
                    level.getServer()
                            .getDataStorage()
                            .computeIfAbsent(CitySavedData.TYPE);

            savedData.markPendingReturn(playerId);
        }

        PlayerPositionTracker.resetSession(playerId);
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.level();

        CitySavedData savedData =
                level.getServer()
                        .getDataStorage()
                        .computeIfAbsent(CitySavedData.TYPE);

        if (!savedData.hasPendingReturn(player.getUUID())) {
            return;
        }

        City city = CityRegistry.STARTING_CITY;

        CityRegion region =
                city.getRegion(level.dimension()).orElse(null);

        if (region == null) {
            return;
        }

        boolean returned = returnPlayerToCity(
                player,
                region,
                savedData
        );

        if (returned) {
            savedData.clearPendingReturn(player.getUUID());
            PlayerPositionTracker.resetSession(player.getUUID());
        }
    }

    private static void handleInsideCity(
            ServerPlayer player,
            ServerLevel level,
            CitySavedData savedData
    ) {
        UUID playerId = player.getUUID();

        PlayerPositionTracker.resetOutsideTicks(playerId);

        CitySavedData.SafePosition lastPosition =
                savedData.getLastValidPosition(
                        playerId,
                        level.dimension()
                );

        if (lastPosition == null) {
            saveCurrentPosition(player, level, savedData);
            PlayerPositionTracker.resetPositionSaveTicks(playerId);
            return;
        }

        int saveTicks =
                PlayerPositionTracker.incrementPositionSaveTicks(playerId);

        if (saveTicks < POSITION_SAVE_INTERVAL_TICKS) {
            return;
        }

        saveCurrentPosition(player, level, savedData);
        PlayerPositionTracker.resetPositionSaveTicks(playerId);
    }

    private static void handleOutsideCity(
            ServerPlayer player,
            CityRegion region,
            CitySavedData savedData
    ) {
        UUID playerId = player.getUUID();

        int outsideTicks =
                PlayerPositionTracker.incrementOutsideTicks(
                        playerId
                );

        int remainingTicks =
                TELEPORT_DELAY_TICKS - outsideTicks;

        int remainingSeconds =
                Math.max(
                        0,
                        (remainingTicks + 19) / 20
                );

        player.sendOverlayMessage(
                Component.literal(
                        "Return to the city area within "
                                + remainingSeconds
                                + " seconds."
                )
        );

        if (outsideTicks < TELEPORT_DELAY_TICKS) {
            return;
        }

        boolean returned = returnPlayerToCity(
                player,
                region,
                savedData
        );

        if (returned) {
            PlayerPositionTracker.resetOutsideTicks(playerId);
            PlayerPositionTracker.resetPositionSaveTicks(playerId);
        }
    }

    private static boolean returnPlayerToCity(
            ServerPlayer player,
            CityRegion region,
            CitySavedData savedData
    ) {
        CitySavedData.SafePosition lastPosition =
                savedData.getLastValidPosition(
                        player.getUUID(),
                        player.level().dimension()
                );

        if (lastPosition == null) {
            return false;
        }

        double safeX = Mth.clamp(
                lastPosition.x(),
                region.minBlockX()
                        + BOUNDARY_MARGIN
                        + 0.5,
                region.maxBlockX()
                        - BOUNDARY_MARGIN
                        + 0.5
        );

        double safeZ = Mth.clamp(
                lastPosition.z(),
                region.minBlockZ()
                        + BOUNDARY_MARGIN
                        + 0.5,
                region.maxBlockZ()
                        - BOUNDARY_MARGIN
                        + 0.5
        );

        player.setDeltaMovement(
                0.0,
                0.0,
                0.0
        );

        player.teleportTo(
                safeX,
                lastPosition.y(),
                safeZ
        );

        player.setDeltaMovement(
                0.0,
                0.0,
                0.0
        );

        player.hurtMarked = true;

        player.setYRot(
                lastPosition.yRot()
        );

        player.setXRot(
                lastPosition.xRot()
        );

        player.sendOverlayMessage(
                Component.literal(
                        "You have been returned to the unlocked city area."
                )
        );

        return true;
    }

    private static void saveCurrentPosition(
            ServerPlayer player,
            ServerLevel level,
            CitySavedData savedData
    ) {
        savedData.setLastValidPosition(
                player.getUUID(),
                level.dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }
}