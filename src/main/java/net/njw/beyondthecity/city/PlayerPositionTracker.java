package net.njw.beyondthecity.city;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPositionTracker {

    private static final Map<UUID, Integer> OUTSIDE_TICKS =
            new HashMap<>();

    private static final Map<UUID, Integer> POSITION_SAVE_TICKS =
            new HashMap<>();

    private PlayerPositionTracker() {
    }

    public static int incrementOutsideTicks(UUID playerId) {
        int ticks = OUTSIDE_TICKS.getOrDefault(playerId, 0) + 1;
        OUTSIDE_TICKS.put(playerId, ticks);
        return ticks;
    }

    public static int getOutsideTicks(UUID playerId) {
        return OUTSIDE_TICKS.getOrDefault(playerId, 0);
    }

    public static void resetOutsideTicks(UUID playerId) {
        OUTSIDE_TICKS.remove(playerId);
    }

    public static int incrementPositionSaveTicks(UUID playerId) {
        int ticks = POSITION_SAVE_TICKS.getOrDefault(playerId, 0) + 1;
        POSITION_SAVE_TICKS.put(playerId, ticks);
        return ticks;
    }

    public static void resetPositionSaveTicks(UUID playerId) {
        POSITION_SAVE_TICKS.remove(playerId);
    }

    public static void resetSession(UUID playerId) {
        OUTSIDE_TICKS.remove(playerId);
        POSITION_SAVE_TICKS.remove(playerId);
    }
}