package net.njw.beyondthecity.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CitySavedData extends SavedData {

    private static final Codec<SafePosition> SAFE_POSITION_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC
                            .fieldOf("dimension")
                            .forGetter(SafePosition::dimension),

                    Codec.DOUBLE.fieldOf("x")
                            .forGetter(SafePosition::x),

                    Codec.DOUBLE.fieldOf("y")
                            .forGetter(SafePosition::y),

                    Codec.DOUBLE.fieldOf("z")
                            .forGetter(SafePosition::z),

                    Codec.FLOAT.fieldOf("yRot")
                            .forGetter(SafePosition::yRot),

                    Codec.FLOAT.fieldOf("xRot")
                            .forGetter(SafePosition::xRot)

            ).apply(instance, SafePosition::new));

    private static final Codec<Map<String, SafePosition>> PLAYER_POSITIONS_CODEC =
            Codec.unboundedMap(
                    Codec.STRING,
                    SAFE_POSITION_CODEC
            );

    private static final Codec<Set<UUID>> PENDING_RETURNS_CODEC =
            Codec.STRING.listOf().xmap(
                    list -> {
                        Set<UUID> result = new HashSet<>();

                        for (String value : list) {
                            result.add(UUID.fromString(value));
                        }

                        return result;
                    },

                    set -> set.stream()
                            .map(UUID::toString)
                            .toList()
            );

    public static final SavedDataType<CitySavedData> TYPE =
            new SavedDataType<>(
                    Identifier.fromNamespaceAndPath(
                            "njw_beyond_the_city",
                            "city_data"
                    ),

                    CitySavedData::new,

                    RecordCodecBuilder.create(instance -> instance.group(
                            PLAYER_POSITIONS_CODEC
                                    .fieldOf("playerPositions")
                                    .forGetter(data -> data.serializedPositions),

                            PENDING_RETURNS_CODEC
                                    .optionalFieldOf(
                                            "pendingReturns",
                                            Set.of()
                                    )
                                    .forGetter(data -> data.pendingReturns)

                    ).apply(instance, CitySavedData::new)),

                    null
            );

    /*
     * 저장 형식:
     *
     * UUID + "|" + dimension
     *
     * 예:
     * xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx|minecraft:overworld
     */
    private final Map<String, SafePosition> serializedPositions;

    /*
     * 경계 밖에서 로그아웃하여
     * 다음 접속 시 강제 복귀가 필요한 플레이어.
     */
    private final Set<UUID> pendingReturns;

    public CitySavedData() {
        this.serializedPositions = new HashMap<>();
        this.pendingReturns = new HashSet<>();
    }

    private CitySavedData(
            Map<String, SafePosition> positions,
            Set<UUID> pendingReturns
    ) {
        this.serializedPositions =
                new HashMap<>(positions);

        this.pendingReturns =
                new HashSet<>(pendingReturns);
    }

    public void setLastValidPosition(
            UUID playerId,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yRot,
            float xRot
    ) {
        String key = createKey(
                playerId,
                dimension
        );

        serializedPositions.put(
                key,
                new SafePosition(
                        dimension,
                        x,
                        y,
                        z,
                        yRot,
                        xRot
                )
        );

        setDirty();
    }

    public SafePosition getLastValidPosition(
            UUID playerId,
            ResourceKey<Level> dimension
    ) {
        return serializedPositions.get(
                createKey(
                        playerId,
                        dimension
                )
        );
    }

    public void markPendingReturn(UUID playerId) {
        if (pendingReturns.add(playerId)) {
            setDirty();
        }
    }

    public boolean hasPendingReturn(UUID playerId) {
        return pendingReturns.contains(playerId);
    }

    public void clearPendingReturn(UUID playerId) {
        if (pendingReturns.remove(playerId)) {
            setDirty();
        }
    }

    private static String createKey(
            UUID playerId,
            ResourceKey<Level> dimension
    ) {
        return playerId
                + "|"
                + dimension.identifier();
    }

    public record SafePosition(
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yRot,
            float xRot
    ) {
    }
}