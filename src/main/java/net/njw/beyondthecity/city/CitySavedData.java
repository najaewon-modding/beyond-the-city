package net.njw.beyondthecity.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CitySavedData extends SavedData {



    /*
     * =========================================================
     * Safe Position Codec
     * =========================================================
     */

    private static final Codec<SafePosition>
            SAFE_POSITION_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Level.RESOURCE_KEY_CODEC
                            .fieldOf("dimension")
                            .forGetter(
                                    SafePosition::dimension
                            ),

                    Codec.DOUBLE
                            .fieldOf("x")
                            .forGetter(
                                    SafePosition::x
                            ),

                    Codec.DOUBLE
                            .fieldOf("y")
                            .forGetter(
                                    SafePosition::y
                            ),

                    Codec.DOUBLE
                            .fieldOf("z")
                            .forGetter(
                                    SafePosition::z
                            ),

                    Codec.FLOAT
                            .fieldOf("yRot")
                            .forGetter(
                                    SafePosition::yRot
                            ),

                    Codec.FLOAT
                            .fieldOf("xRot")
                            .forGetter(
                                    SafePosition::xRot
                            )

            ).apply(
                    instance,
                    SafePosition::new
            ));

    private static final Codec<Map<String, SafePosition>>
            PLAYER_POSITIONS_CODEC =
            Codec.unboundedMap(
                    Codec.STRING,
                    SAFE_POSITION_CODEC
            );

    /*
     * =========================================================
     * Pending Return Codec
     * =========================================================
     */

    private static final Codec<Set<UUID>>
            PENDING_RETURNS_CODEC =
            Codec.STRING
                    .listOf()
                    .xmap(
                            list -> {
                                Set<UUID> result =
                                        new LinkedHashSet<>();

                                for (String value : list) {
                                    result.add(
                                            UUID.fromString(
                                                    value
                                            )
                                    );
                                }

                                return result;
                            },

                            set ->
                                    set.stream()
                                            .map(UUID::toString)
                                            .sorted()
                                            .toList()
                    );

    /*
     * =========================================================
     * City Codec
     * =========================================================
     */

    /*
     * 메모리에서는:
     *
     * Map<String, City>
     *
     * 형태로 사용한다.
     *
     * 저장할 때는 City 자체가 id를 가지고 있으므로
     * List<City> 형태로 기록해서
     * city id를 중복 저장하지 않는다.
     */
    private static final Codec<Map<String, City>>
            CITIES_CODEC =
            City.CODEC
                    .listOf()
                    .xmap(
                            list -> {
                                Map<String, City> result =
                                        new LinkedHashMap<>();

                                for (City city : list) {
                                    City previous =
                                            result.putIfAbsent(
                                                    city.id(),
                                                    city
                                            );

                                    if (previous != null) {
                                        throw new IllegalArgumentException(
                                                "Duplicate city id: "
                                                        + city.id()
                                        );
                                    }
                                }

                                return result;
                            },

                            map ->
                                    new ArrayList<>(
                                            map.values()
                                    )
                    );

    /*
     * =========================================================
     * Accessible City Codec
     * =========================================================
     */

    private static final Codec<Set<String>>
            ACCESSIBLE_CITY_IDS_CODEC =
            Codec.STRING
                    .listOf()
                    .xmap(
                            LinkedHashSet::new,

                            set ->
                                    new ArrayList<>(
                                            set
                                    )
                    );

    /*
     * =========================================================
     * City Arrival Position Codec
     * =========================================================
     *
     * 플레이어별 위치가 아니라
     * 해당 월드의 도시별 / 차원별 공용 도착점.
     */

    private static final Codec<CityArrivalPosition>
            CITY_ARRIVAL_POSITION_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT
                            .fieldOf("blockX")
                            .forGetter(
                                    CityArrivalPosition::blockX
                            ),

                    Codec.INT
                            .fieldOf("y")
                            .forGetter(
                                    CityArrivalPosition::y
                            ),

                    Codec.INT
                            .fieldOf("blockZ")
                            .forGetter(
                                    CityArrivalPosition::blockZ
                            )

            ).apply(
                    instance,
                    CityArrivalPosition::new
            ));

    private static final Codec<Map<String, CityArrivalPosition>>
            CITY_ARRIVAL_POSITIONS_CODEC =
            Codec.unboundedMap(
                    Codec.STRING,
                    CITY_ARRIVAL_POSITION_CODEC
            );

    /*
     * =========================================================
     * Pregeneration Codec
     * =========================================================
     */

    private static final Codec<PregenerationState>
            PREGENERATION_STATE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG
                            .optionalFieldOf(
                                    "generatedChunks",
                                    0L
                            )
                            .forGetter(
                                    PregenerationState::generatedChunks
                            ),

                    Codec.BOOL
                            .optionalFieldOf(
                                    "completed",
                                    false
                            )
                            .forGetter(
                                    PregenerationState::completed
                            )

            ).apply(
                    instance,
                    PregenerationState::new
            ));

    private static final Codec<Map<String, PregenerationState>>
            PREGENERATION_STATES_CODEC =
            Codec.unboundedMap(
                    Codec.STRING,
                    PREGENERATION_STATE_CODEC
            );

    /*
     * =========================================================
     * SavedData Type
     * =========================================================
     */

    public static final SavedDataType<CitySavedData> TYPE =
            new SavedDataType<>(
                    Identifier.fromNamespaceAndPath(
                            "njw_beyond_the_city",
                            "city_data"
                    ),

                    CitySavedData::new,

                    RecordCodecBuilder.create(instance -> instance.group(
                            PLAYER_POSITIONS_CODEC
                                    .fieldOf(
                                            "playerPositions"
                                    )
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

                            PREGENERATION_STATES_CODEC
                                    .optionalFieldOf(
                                            "pregenerationStates",
                                            Map.of()
                                    )
                                    .forGetter(
                                            data ->
                                                    data.pregenerationStates
                                    ),

                            CITIES_CODEC
                                    .optionalFieldOf(
                                            "cities",
                                            Map.of()
                                    )
                                    .forGetter(
                                            data ->
                                                    data.cities
                                    ),

                            ACCESSIBLE_CITY_IDS_CODEC
                                    .optionalFieldOf(
                                            "accessibleCityIds",
                                            Set.of()
                                    )
                                    .forGetter(
                                            data ->
                                                    data.accessibleCityIds
                                    ),

                            CITY_ARRIVAL_POSITIONS_CODEC
                                    .optionalFieldOf(
                                            "cityArrivalPositions",
                                            Map.of()
                                    )
                                    .forGetter(
                                            data ->
                                                    data.cityArrivalPositions
                                    )

                    ).apply(
                            instance,
                            CitySavedData::new
                    )),

                    null
            );

    /*
     * =========================================================
     * Fields
     * =========================================================
     */

    /*
     * 도시별 / 차원별 공용 Move destination.
     *
     * Key:
     *
     * cityId + "|" + dimension
     */
    private final Map<String, CityArrivalPosition>
            cityArrivalPositions;

    /*
     * 플레이어별, 차원별 마지막 정상 위치.
     *
     * Key:
     *
     * UUID + "|" + dimension
     */
    private final Map<String, SafePosition>
            serializedPositions;

    /*
     * 경계 밖에서 로그아웃하여
     * 다음 로그인 시 강제 복귀해야 하는 플레이어.
     */
    private final Set<UUID>
            pendingReturns;

    /*
     * 현재는 Starting City에 대한
     * Stronghold / Fortress 검사 완료 여부.
     */
    private boolean
            structureRequirementsInitialized;

    /*
     * 도시별 / 차원별 pregeneration 상태.
     *
     * Key:
     *
     * cityId + "|" + dimension
     */
    private final Map<String, PregenerationState>
            pregenerationStates;

    /*
     * 현재 이 월드에 존재하는 모든 도시.
     *
     * Key:
     *
     * city id
     */
    private final Map<String, City>
            cities;

    /*
     * 현재 플레이어가 접근 가능한 도시들의 id.
     *
     * 도시의 존재 여부와 접근 가능 여부를
     * 분리해서 관리한다.
     */
    private final Set<String>
            accessibleCityIds;

    /*
     * =========================================================
     * Constructors
     * =========================================================
     */

    public CitySavedData() {
        this.serializedPositions =
                new HashMap<>();

        this.pendingReturns =
                new LinkedHashSet<>();

        this.structureRequirementsInitialized =
                false;

        this.pregenerationStates =
                new HashMap<>();

        this.cities =
                new LinkedHashMap<>();

        this.accessibleCityIds =
                new LinkedHashSet<>();

        this.cityArrivalPositions =
                new HashMap<>();

        initializeStartingCity();
    }

    private CitySavedData(
            Map<String, SafePosition> positions,
            Set<UUID> pendingReturns,
            boolean structureRequirementsInitialized,
            Map<String, PregenerationState> pregenerationStates,
            Map<String, City> cities,
            Set<String> accessibleCityIds,
            Map<String, CityArrivalPosition> cityArrivalPositions
    ) {
        this.serializedPositions =
                new HashMap<>(
                        positions
                );

        this.pendingReturns =
                new LinkedHashSet<>(
                        pendingReturns
                );

        this.structureRequirementsInitialized =
                structureRequirementsInitialized;

        this.pregenerationStates =
                new HashMap<>(
                        pregenerationStates
                );

        this.cities =
                new LinkedHashMap<>(
                        cities
                );

        this.accessibleCityIds =
                new LinkedHashSet<>(
                        accessibleCityIds
                );

        this.cityArrivalPositions =
                new HashMap<>(
                        cityArrivalPositions
                );

        normalizeCityData();
    }

    /*
     * =========================================================
     * City Initialization
     * =========================================================
     */

    private void initializeStartingCity() {
        cities.put(
                CityRegistry.STARTING_CITY_ID,
                CityRegistry.STARTING_CITY_TEMPLATE
        );

        accessibleCityIds.add(
                CityRegistry.STARTING_CITY_ID
        );
    }

    /*
     * 저장 데이터가 잘못된 상태가 되더라도
     * 최소한 Starting City는 항상 존재하고
     * 접근 가능한 상태가 되도록 보정한다.
     */
    private void normalizeCityData() {
        /*
         * 존재하지 않는 도시 id가
         * accessibleCityIds에 들어 있는 경우 제거.
         */
        accessibleCityIds.retainAll(
                cities.keySet()
        );

        /*
         * Starting City가 없다면 다시 생성.
         */
        cities.putIfAbsent(
                CityRegistry.STARTING_CITY_ID,
                CityRegistry.STARTING_CITY_TEMPLATE
        );

        /*
         * Starting City는 항상 접근 가능.
         */
        accessibleCityIds.add(
                CityRegistry.STARTING_CITY_ID
        );
    }

    /*
     * =========================================================
     * Cities
     * =========================================================
     */

    /**
     * 현재 월드에 존재하는 모든 도시를 반환한다.
     */
    public Collection<City> getCities() {
        return List.copyOf(
                cities.values()
        );
    }

    /**
     * 특정 id의 도시를 반환한다.
     *
     * 존재하지 않으면 null.
     */
    public City getCity(
            String cityId
    ) {
        return cities.get(
                cityId
        );
    }

    public boolean hasCity(
            String cityId
    ) {
        return cities.containsKey(
                cityId
        );
    }

    /**
     * 새로운 도시를 월드에 등록한다.
     *
     * 등록만 하고 접근 가능 상태로 만들지는 않는다.
     */
    public void addCity(
            City city
    ) {
        Objects.requireNonNull(
                city,
                "city"
        );

        if (
                cities.containsKey(
                        city.id()
                )
        ) {
            throw new IllegalArgumentException(
                    "City already exists: "
                            + city.id()
            );
        }

        cities.put(
                city.id(),
                city
        );

        setDirty();
    }

    /**
     * 새 도시를 등록하는 동시에
     * 접근 가능 상태로 만든다.
     */
    public void addAccessibleCity(
            City city
    ) {
        Objects.requireNonNull(
                city,
                "city"
        );

        if (
                cities.containsKey(
                        city.id()
                )
        ) {
            throw new IllegalArgumentException(
                    "City already exists: "
                            + city.id()
            );
        }

        cities.put(
                city.id(),
                city
        );

        accessibleCityIds.add(
                city.id()
        );

        setDirty();
    }

    /*
     * =========================================================
     * Accessible Cities
     * =========================================================
     */

    public Collection<City> getAccessibleCities() {
        List<City> result =
                new ArrayList<>();

        for (String cityId :
                accessibleCityIds) {

            City city =
                    cities.get(
                            cityId
                    );

            if (city != null) {
                result.add(
                        city
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    public boolean isCityAccessible(
            String cityId
    ) {
        return accessibleCityIds.contains(
                cityId
        );
    }

    /**
     * 이미 존재하는 도시를 접근 가능 상태로 변경한다.
     */
    public void unlockCity(
            String cityId
    ) {
        if (
                !cities.containsKey(
                        cityId
                )
        ) {
            throw new IllegalArgumentException(
                    "Unknown city: "
                            + cityId
            );
        }

        if (
                accessibleCityIds.add(
                        cityId
                )
        ) {
            setDirty();
        }
    }

    /*
     * =========================================================
     * Safe Position
     * =========================================================
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
        String key =
                createPlayerPositionKey(
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
                createPlayerPositionKey(
                        playerId,
                        dimension
                )
        );
    }

    /*
     * =========================================================
     * Pending Return
     * =========================================================
     */

    public void markPendingReturn(
            UUID playerId
    ) {
        if (
                pendingReturns.add(
                        playerId
                )
        ) {
            setDirty();
        }
    }

    public boolean hasPendingReturn(
            UUID playerId
    ) {
        return pendingReturns.contains(
                playerId
        );
    }

    public void clearPendingReturn(
            UUID playerId
    ) {
        if (
                pendingReturns.remove(
                        playerId
                )
        ) {
            setDirty();
        }
    }

    /*
     * =========================================================
     * Structure Requirements
     * =========================================================
     */

    public boolean areStructureRequirementsInitialized() {
        return structureRequirementsInitialized;
    }

    public void markStructureRequirementsInitialized() {
        if (
                structureRequirementsInitialized
        ) {
            return;
        }

        structureRequirementsInitialized =
                true;

        setDirty();
    }

    /*
     * =========================================================
     * City Arrival Position
     * =========================================================
     */

    public CityArrivalPosition getCityArrivalPosition(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        return cityArrivalPositions.get(
                createCityArrivalPositionKey(
                        cityId,
                        dimension
                )
        );
    }

    public void setCityArrivalPosition(
            String cityId,
            ResourceKey<Level> dimension,
            int blockX,
            int y,
            int blockZ
    ) {
        City targetCity =
                cities.get(
                        cityId
                );

        if (targetCity == null) {
            throw new IllegalArgumentException(
                    "Unknown city: "
                            + cityId
            );
        }

        CityRegion region =
                targetCity.getRegion(
                        dimension
                ).orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "City does not exist in dimension: "
                                                + cityId
                                                + " / "
                                                + dimension.identifier()
                                )
                );

        if (
                !region.containsBlock(
                        blockX,
                        blockZ
                )
        ) {
            throw new IllegalArgumentException(
                    "Arrival position is outside city region: "
                            + cityId
                            + " / "
                            + dimension.identifier()
                            + " / "
                            + blockX
                            + ", "
                            + blockZ
            );
        }

        String key =
                createCityArrivalPositionKey(
                        cityId,
                        dimension
                );

        CityArrivalPosition newPosition =
                new CityArrivalPosition(
                        blockX,
                        y,
                        blockZ
                );

        CityArrivalPosition previous =
                cityArrivalPositions.put(
                        key,
                        newPosition
                );

        if (
                !newPosition.equals(
                        previous
                )
        ) {
            setDirty();
        }
    }

    public void clearCityArrivalPosition(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        String key =
                createCityArrivalPositionKey(
                        cityId,
                        dimension
                );

        if (
                cityArrivalPositions.remove(
                        key
                ) != null
        ) {
            setDirty();
        }
    }

    /*
     * 나중에 도시 삭제 기능에서 사용.
     */
    public void clearCityArrivalPositions(
            String cityId
    ) {
        String prefix =
                cityId + "|";

        boolean removed =
                cityArrivalPositions
                        .keySet()
                        .removeIf(
                                key ->
                                        key.startsWith(
                                                prefix
                                        )
                        );

        if (removed) {
            setDirty();
        }
    }

    /*
     * =========================================================
     * Pregeneration
     * =========================================================
     */

    public PregenerationState getPregenerationState(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        return pregenerationStates.getOrDefault(
                createPregenerationKey(
                        cityId,
                        dimension
                ),
                PregenerationState.EMPTY
        );
    }

    public long getPregeneratedChunks(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        return getPregenerationState(
                cityId,
                dimension
        ).generatedChunks();
    }

    public boolean isPregenerationCompleted(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        return getPregenerationState(
                cityId,
                dimension
        ).completed();
    }

    public void setPregeneratedChunks(
            String cityId,
            ResourceKey<Level> dimension,
            long generatedChunks
    ) {
        if (generatedChunks < 0L) {
            throw new IllegalArgumentException(
                    "generatedChunks must be greater than or equal to 0."
            );
        }

        String key =
                createPregenerationKey(
                        cityId,
                        dimension
                );

        PregenerationState current =
                pregenerationStates.getOrDefault(
                        key,
                        PregenerationState.EMPTY
                );

        if (
                current.generatedChunks()
                        == generatedChunks
        ) {
            return;
        }

        pregenerationStates.put(
                key,
                new PregenerationState(
                        generatedChunks,
                        current.completed()
                )
        );

        setDirty();
    }

    public void markPregenerationCompleted(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        String key =
                createPregenerationKey(
                        cityId,
                        dimension
                );

        PregenerationState current =
                pregenerationStates.getOrDefault(
                        key,
                        PregenerationState.EMPTY
                );

        if (current.completed()) {
            return;
        }

        pregenerationStates.put(
                key,
                new PregenerationState(
                        current.generatedChunks(),
                        true
                )
        );

        setDirty();
    }

    public void resetPregenerationState(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        String key =
                createPregenerationKey(
                        cityId,
                        dimension
                );

        if (
                pregenerationStates.remove(
                        key
                ) != null
        ) {
            setDirty();
        }
    }

    /*
     * =========================================================
     * Keys
     * =========================================================
     */

    private static String createPlayerPositionKey(
            UUID playerId,
            ResourceKey<Level> dimension
    ) {
        return playerId
                + "|"
                + dimension.identifier();
    }

    private static String createPregenerationKey(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        return cityId
                + "|"
                + dimension.identifier();
    }

    private static String createCityArrivalPositionKey(
            String cityId,
            ResourceKey<Level> dimension
    ) {
        return cityId
                + "|"
                + dimension.identifier();
    }

    /*
     * =========================================================
     * Records
     * =========================================================
     */

    public record SafePosition(
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yRot,
            float xRot
    ) {
    }

    public record PregenerationState(
            long generatedChunks,
            boolean completed
    ) {

        public static final PregenerationState EMPTY =
                new PregenerationState(
                        0L,
                        false
                );

        public PregenerationState {
            if (generatedChunks < 0L) {
                throw new IllegalArgumentException(
                        "generatedChunks must be greater than or equal to 0."
                );
            }
        }
    }

    public record CityArrivalPosition(
            int blockX,
            int y,
            int blockZ
    ) {
    }

    public void removeCity(
            String cityId
    ) {
        Objects.requireNonNull(
                cityId,
                "cityId"
        );

        /*
         * Starting City는 월드의 기본 도시이므로
         * 삭제할 수 없다.
         */
        if (
                CityRegistry.STARTING_CITY_ID.equals(
                        cityId
                )
        ) {
            throw new IllegalArgumentException(
                    "Starting city cannot be deleted."
            );
        }

        City removedCity =
                cities.remove(
                        cityId
                );

        if (removedCity == null) {
            throw new IllegalArgumentException(
                    "Unknown city: "
                            + cityId
            );
        }

        /*
         * 접근 가능 목록에서도 제거.
         */
        accessibleCityIds.remove(
                cityId
        );

        /*
         * 해당 도시의 pregeneration 저장 상태 제거.
         *
         * key 형식:
         *
         * cityId + "|" + dimension
         */
        String prefix =
                cityId
                        + "|";

        pregenerationStates
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        /*
         * 해당 도시의 공용 arrival position 제거.
         */
        clearCityArrivalPositions(
                cityId
        );

        setDirty();
    }
}