package net.njw.beyondthecity.city;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /*
     * 마지막 안전 위치 저장 주기.
     *
     * 실제 시간 기반 경계 카운트다운과는 별개이므로
     * tick 기반으로 유지한다.
     */
    private static final int POSITION_SAVE_INTERVAL_TICKS =
            20;

    private CityBoundaryHandler() {
    }

    /*
     * =========================================================
     * Player Tick
     * =========================================================
     */

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level =
                player.level();

        MinecraftServer server =
                level.getServer();

        CitySavedData savedData =
                server.getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        /*
         * Starting City가 아니라,
         *
         * "현재 접근 가능한 어느 도시 안에 있는가?"
         *
         * 를 판정한다.
         */
        City currentCity =
                CityManager.findAccessibleCityContaining(
                        server,
                        level.dimension(),
                        player.getBlockX(),
                        player.getBlockZ()
                );

        if (currentCity != null) {
            handleInsideAccessibleCity(
                    player,
                    level,
                    server,
                    currentCity,
                    savedData
            );

            return;
        }

        handleOutsideAccessibleArea(
                player,
                savedData
        );
    }

    /*
     * =========================================================
     * Logout
     * =========================================================
     */

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
         * deadline이 존재한다는 것은
         * 플레이어가 접근 가능한 도시 밖에 있다는 뜻이다.
         *
         * 이 상태에서 로그아웃하면
         * 다음 로그인 시 즉시 마지막 안전 위치로 복귀시킨다.
         */
        if (
                PlayerPositionTracker.hasReturnDeadline(
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
         * deadline과 position-save tick 등
         * 세션에만 필요한 상태 제거.
         */
        PlayerPositionTracker.resetSession(
                playerId
        );
    }

    /*
     * =========================================================
     * Login
     * =========================================================
     */

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level =
                player.level();

        MinecraftServer server =
                level.getServer();

        CitySavedData savedData =
                server.getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        UUID playerId =
                player.getUUID();

        if (
                !savedData.hasPendingReturn(
                        playerId
                )
        ) {
            return;
        }

        /*
         * 다른 시스템 등에 의해 이미 접근 가능한 도시 안에서
         * 로그인한 경우에는 굳이 이전 위치로 되돌리지 않는다.
         */
        if (
                CityManager.isInsideAccessibleCity(
                        server,
                        level.dimension(),
                        player.getBlockX(),
                        player.getBlockZ()
                )
        ) {
            savedData.clearPendingReturn(
                    playerId
            );

            saveCurrentPosition(
                    player,
                    level,
                    savedData
            );

            PlayerPositionTracker.resetSession(
                    playerId
            );

            return;
        }

        /*
         * 도시 밖에서 로그아웃했던 경우
         * 저장된 마지막 안전 위치로 즉시 복귀.
         */
        boolean returned =
                returnPlayerToSafePosition(
                        player,
                        savedData
                );

        if (returned) {
            savedData.clearPendingReturn(
                    playerId
            );

            PlayerPositionTracker.resetSession(
                    playerId
            );
        }
    }

    /*
     * =========================================================
     * Inside Accessible City
     * =========================================================
     */

    private static void handleInsideAccessibleCity(
            ServerPlayer player,
            ServerLevel level,
            MinecraftServer server,
            City currentCity,
            CitySavedData savedData
    ) {
        UUID playerId =
                player.getUUID();

        /*
         * 방금 도시 밖에서 다시 들어온 것인지 확인한다.
         *
         * deadline을 제거하기 전에 확인해야 한다.
         */
        boolean returnedFromOutside =
                PlayerPositionTracker.hasReturnDeadline(
                        playerId
                );

        /*
         * 접근 가능한 도시 안이므로
         * 경계 복귀 countdown을 취소한다.
         */
        PlayerPositionTracker.resetReturnDeadline(
                playerId
        );

        /*
         * pending return이 남아 있는 예외적인 상황에서도
         * 현재 위치가 안전 영역이면 해제한다.
         */
        if (
                savedData.hasPendingReturn(
                        playerId
                )
        ) {
            savedData.clearPendingReturn(
                    playerId
            );
        }

        CitySavedData.SafePosition lastPosition =
                savedData.getLastValidPosition(
                        playerId,
                        level.dimension()
                );

        /*
         * 아직 이 차원에 저장된 안전 위치가 없다면
         * 현재 위치를 즉시 저장한다.
         */
        if (lastPosition == null) {
            saveCurrentPosition(
                    player,
                    level,
                    savedData
            );

            PlayerPositionTracker.resetPositionSaveTicks(
                    playerId
            );

            return;
        }

        /*
         * 도시 밖으로 나갔다가 다시 들어온 경우에도
         * 현재 위치를 즉시 새로운 안전 위치로 저장한다.
         *
         * 그렇지 않으면 예전에 저장했던
         * 경계 근처 위치로 되돌아갈 수 있다.
         */
        if (returnedFromOutside) {
            saveCurrentPosition(
                    player,
                    level,
                    savedData
            );

            PlayerPositionTracker.resetPositionSaveTicks(
                    playerId
            );

            return;
        }

        /*
         * 도시 A → 도시 B처럼
         * 서로 다른 접근 가능 도시로 직접 이동한 경우.
         *
         * cityId를 별도로 저장하는 것은 아니며,
         * 기존 SafePosition이 현재 어느 도시에 속하는지만
         * 그 순간 계산해서 비교한다.
         */
        if (
                isSafePositionInDifferentCity(
                        server,
                        currentCity,
                        lastPosition
                )
        ) {
            saveCurrentPosition(
                    player,
                    level,
                    savedData
            );

            PlayerPositionTracker.resetPositionSaveTicks(
                    playerId
            );

            return;
        }

        /*
         * 같은 도시 안에서 평범하게 움직이는 동안에는
         * 매 tick 저장하지 않고 일정 주기로 갱신한다.
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

        PlayerPositionTracker.resetPositionSaveTicks(
                playerId
        );
    }

    /*
     * =========================================================
     * Outside Accessible Area
     * =========================================================
     */

    private static void handleOutsideAccessibleArea(
            ServerPlayer player,
            CitySavedData savedData
    ) {
        UUID playerId =
                player.getUUID();

        /*
         * 처음 안전 영역 밖으로 나간 순간에만
         * deadline을 생성한다.
         *
         * 이후에는 기존 deadline을 계속 사용한다.
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
         * 실제 시간 기준 5초가 지났다면
         * 마지막 안전 위치로 복귀.
         */
        if (remainingNanos <= 0L) {
            boolean returned =
                    returnPlayerToSafePosition(
                            player,
                            savedData
                    );

            if (returned) {
                PlayerPositionTracker.resetReturnDeadline(
                        playerId
                );

                PlayerPositionTracker.resetPositionSaveTicks(
                        playerId
                );
            }

            return;
        }

        /*
         * 나노초 → 초.
         *
         * 올림:
         *
         * 4.8초 → 5
         * 3.2초 → 4
         */
        long remainingSeconds =
                (
                        remainingNanos
                                + 999_999_999L
                )
                        / 1_000_000_000L;

        player.sendOverlayMessage(
                Component.literal(
                        "Return to the unlocked city area within "
                                + remainingSeconds
                                + " seconds."
                )
        );
    }

    /*
     * =========================================================
     * Return to Safe Position
     * =========================================================
     */

    private static boolean returnPlayerToSafePosition(
            ServerPlayer player,
            CitySavedData savedData
    ) {
        ServerLevel level =
                player.level();

        MinecraftServer server =
                level.getServer();

        CitySavedData.SafePosition lastPosition =
                savedData.getLastValidPosition(
                        player.getUUID(),
                        level.dimension()
                );

        /*
         * 이 차원에서 아직 안전 위치를 저장한 적이 없다면
         * 복귀할 수 없다.
         */
        if (lastPosition == null) {
            return false;
        }

        /*
         * 저장된 SafePosition이 실제로 현재도
         * 접근 가능한 도시 안인지 한 번 더 검증한다.
         *
         * 정상적인 상황이라면 항상 true여야 하지만,
         * 도시 데이터가 변경되거나 잘못된 데이터가 남은 경우
         * 잠긴 영역으로 teleport하는 것을 방지한다.
         */
        City safeCity =
                CityManager.findAccessibleCityContaining(
                        server,
                        lastPosition.dimension(),
                        blockCoordinate(
                                lastPosition.x()
                        ),
                        blockCoordinate(
                                lastPosition.z()
                        )
                );

        if (safeCity == null) {
            return false;
        }

        /*
         * 기존 이동 속도 제거.
         */
        player.setDeltaMovement(
                0.0,
                0.0,
                0.0
        );

        /*
         * 별도의 clamp를 사용하지 않는다.
         *
         * SafePosition 자체가 접근 가능한 도시 안에서만
         * 저장된 좌표이기 때문이다.
         */
        player.teleportTo(
                lastPosition.x(),
                lastPosition.y(),
                lastPosition.z()
        );

        /*
         * teleport 과정에서 velocity가 생기는 경우를
         * 방지하기 위해 한 번 더 제거.
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

    /*
     * =========================================================
     * Safe Position
     * =========================================================
     */

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

    /*
     * 현재 저장된 안전 위치가
     * 플레이어가 지금 들어와 있는 도시와 다른 도시인지 확인.
     */
    private static boolean isSafePositionInDifferentCity(
            MinecraftServer server,
            City currentCity,
            CitySavedData.SafePosition safePosition
    ) {
        City previousCity =
                CityManager.findAccessibleCityContaining(
                        server,
                        safePosition.dimension(),
                        blockCoordinate(
                                safePosition.x()
                        ),
                        blockCoordinate(
                                safePosition.z()
                        )
                );

        /*
         * 기존 위치가 더 이상 접근 가능한 도시에 속하지 않는다면
         * 새 위치를 즉시 저장하는 것이 맞다.
         */
        if (previousCity == null) {
            return true;
        }

        return !previousCity.id()
                .equals(
                        currentCity.id()
                );
    }

    /*
     * double 좌표를 Minecraft block 좌표 방식으로 변환.
     *
     * 음수에서는 단순 cast가 아니라 floor가 필요하다.
     *
     * 예:
     *
     * -0.2 → -1
     */
    private static int blockCoordinate(
            double coordinate
    ) {
        return (int) Math.floor(
                coordinate
        );
    }
}