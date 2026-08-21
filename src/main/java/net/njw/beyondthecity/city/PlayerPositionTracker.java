package net.njw.beyondthecity.city;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPositionTracker {

    /*
     * 마지막 유효 위치를 저장하기 위한 tick 카운터.
     *
     * 이 값은 "5초 경계 타이머"와는 관계없으므로
     * 기존처럼 tick 기반으로 유지한다.
     */
    private static final Map<UUID, Integer> POSITION_SAVE_TICKS =
            new HashMap<>();

    /*
     * 도시 밖으로 나간 플레이어의 복귀 deadline.
     *
     * key:
     *     플레이어 UUID
     *
     * value:
     *     System.nanoTime() 기준 복귀 시각
     *
     * 예:
     *     현재 시간이 10초이고
     *     복귀 제한 시간이 5초라면
     *     deadline = 15초
     */
    private static final Map<UUID, Long> RETURN_DEADLINES =
            new HashMap<>();

    private PlayerPositionTracker() {
    }

    /*
     * ---------------------------------------------------------
     * Position save timer
     * ---------------------------------------------------------
     */

    public static int incrementPositionSaveTicks(
            UUID playerId
    ) {
        int ticks =
                POSITION_SAVE_TICKS.getOrDefault(
                        playerId,
                        0
                ) + 1;

        POSITION_SAVE_TICKS.put(
                playerId,
                ticks
        );

        return ticks;
    }

    public static void resetPositionSaveTicks(
            UUID playerId
    ) {
        POSITION_SAVE_TICKS.remove(
                playerId
        );
    }

    /*
     * ---------------------------------------------------------
     * City return deadline
     * ---------------------------------------------------------
     */

    /**
     * 플레이어에게 이미 복귀 deadline이 존재하면
     * 기존 deadline을 그대로 반환한다.
     *
     * 존재하지 않으면 현재 시간을 기준으로
     * 새로운 deadline을 생성한다.
     *
     * 따라서 이 메서드를 매 tick 호출하더라도
     * deadline이 계속 뒤로 밀리지 않는다.
     */
    public static long getOrCreateReturnDeadline(
            UUID playerId,
            long delayNanos
    ) {
        return RETURN_DEADLINES.computeIfAbsent(
                playerId,
                ignored ->
                        System.nanoTime()
                                + delayNanos
        );
    }

    /**
     * 현재 플레이어가 도시 밖 복귀 대기 상태인지 확인한다.
     */
    public static boolean hasReturnDeadline(
            UUID playerId
    ) {
        return RETURN_DEADLINES.containsKey(
                playerId
        );
    }

    /**
     * 현재 설정되어 있는 복귀 deadline을 반환한다.
     *
     * deadline이 없으면 null을 반환한다.
     */
    public static Long getReturnDeadline(
            UUID playerId
    ) {
        return RETURN_DEADLINES.get(
                playerId
        );
    }

    /**
     * 도시 안으로 돌아왔거나
     * 강제 복귀가 완료된 경우 deadline을 제거한다.
     */
    public static void resetReturnDeadline(
            UUID playerId
    ) {
        RETURN_DEADLINES.remove(
                playerId
        );
    }

    /*
     * ---------------------------------------------------------
     * Session cleanup
     * ---------------------------------------------------------
     */

    /**
     * 플레이어가 로그아웃하는 등
     * 현재 서버 세션의 임시 상태를 모두 제거한다.
     */
    public static void resetSession(
            UUID playerId
    ) {
        POSITION_SAVE_TICKS.remove(
                playerId
        );

        RETURN_DEADLINES.remove(
                playerId
        );
    }
}