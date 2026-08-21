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

                    Codec.DOUBLE
                            .fieldOf("x")
                            .forGetter(SafePosition::x),

                    Codec.DOUBLE
                            .fieldOf("y")
                            .forGetter(SafePosition::y),

                    Codec.DOUBLE
                            .fieldOf("z")
                            .forGetter(SafePosition::z),

                    Codec.FLOAT
                            .fieldOf("yRot")
                            .forGetter(SafePosition::yRot),

                    Codec.FLOAT
                            .fieldOf("xRot")
                            .forGetter(SafePosition::xRot)

            ).apply(
                    instance,
                    SafePosition::new
            ));

    private static final Codec<Map<String, SafePosition>> PLAYER_POSITIONS_CODEC =
            Codec.unboundedMap(
                    Codec.STRING,
                    SAFE_POSITION_CODEC
            );

    private static final Codec<Set<UUID>> PENDING_RETURNS_CODEC =
            Codec.STRING
                    .listOf()
                    .xmap(
                            list -> {
                                Set<UUID> result = new HashSet<>();

                                for (String value : list) {
                                    result.add(
                                            UUID.fromString(value)
                                    );
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
                                    .forGetter(
                                            data ->
                                                    data.serializedPositions
                                    ),

                            PENDING_RETURNS_CODEC
                                    .optionalFieldOf(
                                            "pendingReturns",
                                            Set.of()
                                    )
                                    .forGetter(
                                            data ->
                                                    data.pendingReturns
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "structureRequirementsInitialized",
                                            false
                                    )
                                    .forGetter(
                                            data ->
                                                    data.structureRequirementsInitialized
                                    ),

                            Codec.LONG
                                    .optionalFieldOf(
                                            "overworldPregeneratedChunks",
                                            0L
                                    )
                                    .forGetter(
                                            data ->
                                                    data.overworldPregeneratedChunks
                                    ),

                            Codec.LONG
                                    .optionalFieldOf(
                                            "netherPregeneratedChunks",
                                            0L
                                    )
                                    .forGetter(
                                            data ->
                                                    data.netherPregeneratedChunks
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "overworldPregenerationCompleted",
                                            false
                                    )
                                    .forGetter(
                                            data ->
                                                    data.overworldPregenerationCompleted
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "netherPregenerationCompleted",
                                            false
                                    )
                                    .forGetter(
                                            data ->
                                                    data.netherPregenerationCompleted
                                    )

                    ).apply(
                            instance,
                            CitySavedData::new
                    )),

                    null
            );

    /*
     * 플레이어별, 차원별 마지막 정상 위치.
     *
     * Key 형식:
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

    /*
     * 시작 도시의 필수 구조물
     * Stronghold / Fortress 검사를 이미 완료했는지 여부.
     */
    private boolean structureRequirementsInitialized;

    public CitySavedData() {
        this.serializedPositions = new HashMap<>();
        this.pendingReturns = new HashSet<>();
        this.structureRequirementsInitialized = false;

        this.overworldPregeneratedChunks = 0L;
        this.netherPregeneratedChunks = 0L;

        this.overworldPregenerationCompleted = false;
        this.netherPregenerationCompleted = false;
    }

    private CitySavedData(
            Map<String, SafePosition> positions,
            Set<UUID> pendingReturns,
            boolean structureRequirementsInitialized,
            long overworldPregeneratedChunks,
            long netherPregeneratedChunks,
            boolean overworldPregenerationCompleted,
            boolean netherPregenerationCompleted
    ) {
        this.serializedPositions =
                new HashMap<>(positions);

        this.pendingReturns =
                new HashSet<>(pendingReturns);

        this.structureRequirementsInitialized =
                structureRequirementsInitialized;

        this.overworldPregeneratedChunks =
                overworldPregeneratedChunks;

        this.netherPregeneratedChunks =
                netherPregeneratedChunks;

        this.overworldPregenerationCompleted =
                overworldPregenerationCompleted;

        this.netherPregenerationCompleted =
                netherPregenerationCompleted;
    }

    /*
     * 마지막 정상 위치 저장.
     */
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

    /*
     * 특정 플레이어의 특정 차원 마지막 정상 위치 반환.
     */
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

    /*
     * 다음 로그인 시 즉시 도시 내부로 복귀하도록 표시.
     */
    public void markPendingReturn(
            UUID playerId
    ) {
        if (pendingReturns.add(playerId)) {
            setDirty();
        }
    }

    /*
     * 로그인 시 강제 복귀가 필요한 플레이어인지 확인.
     */
    public boolean hasPendingReturn(
            UUID playerId
    ) {
        return pendingReturns.contains(
                playerId
        );
    }

    /*
     * 강제 복귀 처리 완료 후 상태 제거.
     */
    public void clearPendingReturn(
            UUID playerId
    ) {
        if (pendingReturns.remove(playerId)) {
            setDirty();
        }
    }

    /*
     * 시작 도시 필수 구조물 검사가 이미 끝났는지 확인.
     */
    public boolean areStructureRequirementsInitialized() {
        return structureRequirementsInitialized;
    }

    /*
     * 필수 구조물 검사가 끝났음을 영구 저장.
     */
    public void markStructureRequirementsInitialized() {
        if (!structureRequirementsInitialized) {
            structureRequirementsInitialized = true;
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

    private long overworldPregeneratedChunks;
    private long netherPregeneratedChunks;

    private boolean overworldPregenerationCompleted;
    private boolean netherPregenerationCompleted;

    public long getOverworldPregeneratedChunks() {
        return overworldPregeneratedChunks;
    }

    public long getNetherPregeneratedChunks() {
        return netherPregeneratedChunks;
    }

    public boolean isOverworldPregenerationCompleted() {
        return overworldPregenerationCompleted;
    }

    public boolean isNetherPregenerationCompleted() {
        return netherPregenerationCompleted;
    }

    public void setOverworldPregeneratedChunks(
            long value
    ) {
        if (overworldPregeneratedChunks == value) {
            return;
        }

        overworldPregeneratedChunks = value;
        setDirty();
    }

    public void setNetherPregeneratedChunks(
            long value
    ) {
        if (netherPregeneratedChunks == value) {
            return;
        }

        netherPregeneratedChunks = value;
        setDirty();
    }

    public void markOverworldPregenerationCompleted() {
        if (!overworldPregenerationCompleted) {
            overworldPregenerationCompleted = true;
            setDirty();
        }
    }

    public void markNetherPregenerationCompleted() {
        if (!netherPregenerationCompleted) {
            netherPregenerationCompleted = true;
            setDirty();
        }
    }
}