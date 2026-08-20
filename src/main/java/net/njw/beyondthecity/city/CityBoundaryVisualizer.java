package net.njw.beyondthecity.city;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class CityBoundaryVisualizer {

    private static final double VISIBILITY_DISTANCE = 24.0;

    // 파티클 벽 높이
    private static final int WALL_HEIGHT = 12;

    // 플레이어를 기준으로 경계선 좌우 몇 블록까지 보여줄지
    private static final int HORIZONTAL_RADIUS = 16;

    // 파티클 간격
    private static final int HORIZONTAL_STEP = 2;
    private static final int VERTICAL_STEP = 2;

    // 매 tick마다 그리지 않고 5 tick마다 갱신
    private static final int UPDATE_INTERVAL_TICKS = 5;

    private CityBoundaryVisualizer() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        /*
         * 플레이어마다 5 tick마다 한 번만 시각화한다.
         */
        if (player.tickCount % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        ServerLevel level = player.level();

        City city = CityRegistry.STARTING_CITY;

        CityRegion region =
                city.getRegion(level.dimension()).orElse(null);

        if (region == null) {
            return;
        }

        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();

        /*
         * 블록 경계의 실제 벽 위치.
         *
         * min 쪽은 minBlock,
         * max 쪽은 마지막 블록 바깥쪽 면인 maxBlock + 1.
         */
        double minX = region.minBlockX();
        double maxX = region.maxBlockX() + 1.0;

        double minZ = region.minBlockZ();
        double maxZ = region.maxBlockZ() + 1.0;

        if (Math.abs(playerX - minX) <= VISIBILITY_DISTANCE) {
            drawXWall(
                    player,
                    level,
                    minX,
                    playerY,
                    playerZ,
                    minZ,
                    maxZ
            );
        }

        if (Math.abs(playerX - maxX) <= VISIBILITY_DISTANCE) {
            drawXWall(
                    player,
                    level,
                    maxX,
                    playerY,
                    playerZ,
                    minZ,
                    maxZ
            );
        }

        if (Math.abs(playerZ - minZ) <= VISIBILITY_DISTANCE) {
            drawZWall(
                    player,
                    level,
                    minZ,
                    playerY,
                    playerX,
                    minX,
                    maxX
            );
        }

        if (Math.abs(playerZ - maxZ) <= VISIBILITY_DISTANCE) {
            drawZWall(
                    player,
                    level,
                    maxZ,
                    playerY,
                    playerX,
                    minX,
                    maxX
            );
        }
    }

    /*
     * X가 고정된 경계.
     *
     * 즉 동쪽 / 서쪽 경계에 해당한다.
     */
    private static void drawXWall(
            ServerPlayer player,
            ServerLevel level,
            double wallX,
            double playerY,
            double playerZ,
            double minZ,
            double maxZ
    ) {
        double startZ = Mth.clamp(
                playerZ - HORIZONTAL_RADIUS,
                minZ,
                maxZ
        );

        double endZ = Mth.clamp(
                playerZ + HORIZONTAL_RADIUS,
                minZ,
                maxZ
        );

        for (
                double z = startZ;
                z <= endZ;
                z += HORIZONTAL_STEP
        ) {
            for (
                    int yOffset = 0;
                    yOffset <= WALL_HEIGHT;
                    yOffset += VERTICAL_STEP
            ) {
                sendParticle(
                        player,
                        level,
                        wallX,
                        playerY + yOffset,
                        z
                );
            }
        }
    }

    /*
     * Z가 고정된 경계.
     *
     * 즉 북쪽 / 남쪽 경계에 해당한다.
     */
    private static void drawZWall(
            ServerPlayer player,
            ServerLevel level,
            double wallZ,
            double playerY,
            double playerX,
            double minX,
            double maxX
    ) {
        double startX = Mth.clamp(
                playerX - HORIZONTAL_RADIUS,
                minX,
                maxX
        );

        double endX = Mth.clamp(
                playerX + HORIZONTAL_RADIUS,
                minX,
                maxX
        );

        for (
                double x = startX;
                x <= endX;
                x += HORIZONTAL_STEP
        ) {
            for (
                    int yOffset = 0;
                    yOffset <= WALL_HEIGHT;
                    yOffset += VERTICAL_STEP
            ) {
                sendParticle(
                        player,
                        level,
                        x,
                        playerY + yOffset,
                        wallZ
                );
            }
        }
    }

    private static void sendParticle(
            ServerPlayer player,
            ServerLevel level,
            double x,
            double y,
            double z
    ) {
        level.sendParticles(
                player,
                ParticleTypes.END_ROD,
                true,
                true,
                x,
                y,
                z,
                1,
                0.0,
                0.0,
                0.0,
                0.0
        );
    }
}