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

    /*
     * 도시 밖에 머무를 수 있는 실제 시간.
     *
     * 5초 = 5,000,000,000 ns
     */
    private static final long TELEPORT_DELAY_NANOS =
            5_000_000_000L;

    private static final double BOUNDARY_MARGIN = 10.0;

    /*
     * 마지막 안전 위치 저장 주기.
     *
     * 이건 실제 시간 카운트다운과는 별개이므로
     * 기존처럼 tick 기반으로 유지한다.
     */
    private static final int POSITION_SAVE_INTERVAL_TICKS = 20;

    private CityBoundaryHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level =
                player.level();

        City city =
                CityManager.getStartingCity(
                        level.getServer()
                );

        CityRegion region =
                city.getRegion(
                        level.dimension()
                ).orElse(null);

        if (region == null) {
            return;
        }

        CitySavedData savedData =
                level.getServer()
                        .getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        if (
                region.containsBlock(
                        player.getBlockX(),
                        player.getBlockZ()
                )
        ) {
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

        UUID playerId =
                player.getUUID();

        /*
         * 복귀 deadline이 있다는 것은
         * 현재 도시 밖에 있다는 뜻.
         *
         * 로그아웃하면 다음 접속 시 즉시 복귀시키기 위해
         * pending return을 저장한다.
         */
        if (
                PlayerPositionTracker
                        .hasReturnDeadline(
                                playerId
                        )
        ) {
            ServerLevel level =
                    player.level();

            CitySavedData savedData =
                    level.getServer()
                            .getDataStorage()
                            .computeIfAbsent(
                                    CitySavedData.TYPE
                            );

            savedData.markPendingReturn(
                    playerId
            );
        }

        /*
         * 메모리에만 존재하는 세션 상태 제거.
         */
        PlayerPositionTracker.resetSession(
                playerId
        );
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level =
                player.level();

        CitySavedData savedData =
                level.getServer()
                        .getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        if (
                !savedData.hasPendingReturn(
                        player.getUUID()
                )
        ) {
            return;
        }

        City city =
                CityManager.getStartingCity(
                        level.getServer()
                );

        CityRegion region =
                city.getRegion(
                        level.dimension()
                ).orElse(null);

        if (region == null) {
            return;
        }

        boolean returned =
                returnPlayerToCity(
                        player,
                        region,
                        savedData
                );

        if (returned) {
            savedData.clearPendingReturn(
                    player.getUUID()
            );

            PlayerPositionTracker.resetSession(
                    player.getUUID()
            );
        }
    }

    private static void handleInsideCity(
            ServerPlayer player,
            ServerLevel level,
            CitySavedData savedData
    ) {
        UUID playerId =
                player.getUUID();

        /*
         * 도시 안으로 돌아왔으므로
         * 기존 복귀 deadline 제거.
         */
        PlayerPositionTracker.resetReturnDeadline(
                playerId
        );

        CitySavedData.SafePosition lastPosition =
                savedData.getLastValidPosition(
                        playerId,
                        level.dimension()
                );

        /*
         * 아직 저장된 안전 위치가 없다면
         * 현재 위치를 즉시 저장.
         */
        if (lastPosition == null) {
            saveCurrentPosition(
                    player,
                    level,
                    savedData
            );

            PlayerPositionTracker
                    .resetPositionSaveTicks(
                            playerId
                    );

            return;
        }

        /*
         * 안전 위치는 매 tick 저장하지 않고
         * POSITION_SAVE_INTERVAL_TICKS마다 갱신.
         */
        int saveTicks =
                PlayerPositionTracker
                        .incrementPositionSaveTicks(
                                playerId
                        );

        if (
                saveTicks
                        < POSITION_SAVE_INTERVAL_TICKS
        ) {
            return;
        }

        saveCurrentPosition(
                player,
                level,
                savedData
        );

        PlayerPositionTracker
                .resetPositionSaveTicks(
                        playerId
                );
    }

    private static void handleOutsideCity(
            ServerPlayer player,
            CityRegion region,
            CitySavedData savedData
    ) {
        UUID playerId =
                player.getUUID();

        /*
         * 처음 도시 밖으로 나간 순간에만
         * deadline을 생성한다.
         *
         * 이후 매 tick 호출돼도 기존 deadline은 유지된다.
         */
        long deadline =
                PlayerPositionTracker
                        .getOrCreateReturnDeadline(
                                playerId,
                                TELEPORT_DELAY_NANOS
                        );

        long remainingNanos =
                deadline
                        - System.nanoTime();

        /*
         * 실제 시간이 5초 이상 지났으면 즉시 복귀.
         */
        if (remainingNanos <= 0L) {
            boolean returned =
                    returnPlayerToCity(
                            player,
                            region,
                            savedData
                    );

            if (returned) {
                PlayerPositionTracker
                        .resetReturnDeadline(
                                playerId
                        );

                PlayerPositionTracker
                        .resetPositionSaveTicks(
                                playerId
                        );
            }

            return;
        }

        /*
         * 나노초 → 초.
         *
         * 올림 처리해서:
         *
         * 4.8초 남음 → 5
         * 3.2초 남음 → 4
         * ...
         */
        long remainingSeconds =
                (
                        remainingNanos
                                + 999_999_999L
                )
                        / 1_000_000_000L;

        player.sendOverlayMessage(
                Component.literal(
                        "Return to the city area within "
                                + remainingSeconds
                                + " seconds."
                )
        );
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

        double safeX =
                Mth.clamp(
                        lastPosition.x(),
                        region.minBlockX()
                                + BOUNDARY_MARGIN
                                + 0.5,
                        region.maxBlockX()
                                - BOUNDARY_MARGIN
                                + 0.5
                );

        double safeZ =
                Mth.clamp(
                        lastPosition.z(),
                        region.minBlockZ()
                                + BOUNDARY_MARGIN
                                + 0.5,
                        region.maxBlockZ()
                                - BOUNDARY_MARGIN
                                + 0.5
                );

        /*
         * 기존 이동 속도 제거.
         */
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

        /*
         * teleport 과정에서 다시 velocity가 생기는 경우를
         * 방지하기 위해 한 번 더 초기화.
         */
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