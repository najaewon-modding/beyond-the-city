package net.njw.beyondthecity.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.network.CityTeleportRequestPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CityTeleportService {

    /*
     * =========================================================
     * Timing
     * =========================================================
     */

    private static final long CAST_DURATION_NANOS =
            3_000_000_000L;

    private static final long COOLDOWN_DURATION_NANOS =
            3_000_000_000L;

    /*
     * 한 server tick에서 목적지 검색에 사용하는 목표 시간.
     */
    private static final long SEARCH_BUDGET_NANOS_PER_TICK =
            5_000_000L;

    /*
     * =========================================================
     * Movement Interrupt
     * =========================================================
     */

    /*
     * 시전 시작점으로부터 이 거리보다 많이 움직이면
     * City Move가 취소된다.
     *
     * 너무 엄격하게 0으로 두면
     * 작은 위치 보정에도 취소될 수 있으므로
     * 0.1 block의 tolerance를 둔다.
     *
     * 시선 회전은 허용한다.
     */
    private static final double MOVEMENT_CANCEL_DISTANCE =
            0.1D;

    private static final double MOVEMENT_CANCEL_DISTANCE_SQUARED =
            MOVEMENT_CANCEL_DISTANCE
                    * MOVEMENT_CANCEL_DISTANCE;

    /*
     * =========================================================
     * Search
     * =========================================================
     */

    private static final int BLOCKS_PER_CHUNK =
            16;

    /*
     * 도시 중심으로부터 최대 검색 거리.
     */
    private static final int SAFE_SEARCH_RADIUS =
            512;

    /*
     * radial coarse search.
     *
     * 32 blocks씩 바깥으로 이동.
     */
    private static final int RADIAL_STEP =
            32;

    /*
     * 360 / 16 = 22.5 degrees.
     */
    private static final int RADIAL_DIRECTION_COUNT =
            16;

    /*
     * 실제 육지 후보가 발견된 뒤
     * 주변에서 안전한 자리를 찾는 반경.
     */
    private static final int LOCAL_SEARCH_RADIUS =
            12;

    /*
     * Worldgen 기반 coarse probe.
     */
    private static final List<RadialProbe>
            RADIAL_PROBES =
            createRadialProbes();

    /*
     * 실제 안전지점 local search.
     */
    private static final List<LocalOffset>
            LOCAL_OFFSETS =
            createLocalOffsets();

    /*
     * =========================================================
     * Monster Check
     * =========================================================
     */

    /*
     * Vanilla bed의 monster proximity와 같은 범위.
     */
    private static final double MONSTER_HORIZONTAL_RANGE =
            8.0D;

    private static final double MONSTER_VERTICAL_RANGE =
            5.0D;

    /*
     * =========================================================
     * Runtime State
     * =========================================================
     */

    private static final Map<UUID, CastSession>
            ACTIVE_CASTS =
            new HashMap<>();

    /*
     * System.nanoTime() 기준.
     */
    private static final Map<UUID, Long>
            COOLDOWN_DEADLINES =
            new HashMap<>();

    private CityTeleportService() {
    }

    /*
     * =========================================================
     * Network
     * =========================================================
     */

    public static void handleRequest(
            CityTeleportRequestPayload payload,
            IPayloadContext context
    ) {
        if (
                !(context.player()
                        instanceof ServerPlayer player)
        ) {
            return;
        }

        startCast(
                player,
                payload.cityId()
        );
    }

    /*
     * =========================================================
     * Cast Start
     * =========================================================
     */

    private static void startCast(
            ServerPlayer player,
            String cityId
    ) {
        UUID playerId =
                player.getUUID();

        /*
         * =====================================================
         * Already Casting
         * =====================================================
         */

        if (
                ACTIVE_CASTS.containsKey(
                        playerId
                )
        ) {
            player.sendOverlayMessage(
                    Component.literal(
                            "City Move is already being cast."
                    )
            );

            return;
        }

        /*
         * =====================================================
         * Cooldown
         * =====================================================
         */

        long now =
                System.nanoTime();

        Long cooldownDeadline =
                COOLDOWN_DEADLINES.get(
                        playerId
                );

        if (cooldownDeadline != null) {

            long remainingNanos =
                    cooldownDeadline
                            - now;

            if (remainingNanos > 0L) {

                player.sendOverlayMessage(
                        Component.literal(
                                "City Move is on cooldown. "
                                        + formatSeconds(
                                        remainingNanos
                                )
                                        + "s remaining."
                        )
                );

                return;
            }

            COOLDOWN_DEADLINES.remove(
                    playerId
            );
        }

        /*
         * =====================================================
         * City Validation
         * =====================================================
         */

        ServerLevel level =
                player.level();

        MinecraftServer server =
                level.getServer();

        City city =
                CityManager.getCity(
                        server,
                        cityId
                );

        if (city == null) {

            player.sendOverlayMessage(
                    Component.literal(
                            "Unknown city."
                    )
            );

            return;
        }

        if (
                !CityManager.isCityAccessible(
                        server,
                        cityId
                )
        ) {

            player.sendOverlayMessage(
                    Component.literal(
                            "This city is locked."
                    )
            );

            return;
        }

        CityRegion region =
                city.getRegion(
                        level.dimension()
                ).orElse(null);

        if (region == null) {

            player.sendOverlayMessage(
                    Component.literal(
                            "This city does not exist in the current dimension."
                    )
            );

            return;
        }

        /*
         * =====================================================
         * Monster Check
         * =====================================================
         */

        if (
                hasRestPreventingMonsterNearby(
                        player
                )
        ) {

            player.sendOverlayMessage(
                    Component.literal(
                            "You may not move while monsters are nearby."
                    )
            );

            return;
        }

        /*
         * =====================================================
         * City Center
         * =====================================================
         */

        int centerBlockX =
                region.centerChunkX()
                        * BLOCKS_PER_CHUNK;

        int centerBlockZ =
                region.centerChunkZ()
                        * BLOCKS_PER_CHUNK;

        /*
         * =====================================================
         * Shared Arrival Position
         * =====================================================
         *
         * 이미 이 도시/차원의 공용 arrival point가
         * 저장되어 있다면 search task가 먼저 그것을 검사한다.
         */

        CitySavedData.CityArrivalPosition storedArrival =
                CityManager.getCityArrivalPosition(
                        server,
                        cityId,
                        level.dimension()
                );

        SafeDestination cachedDestination =
                null;

        if (storedArrival != null) {

            cachedDestination =
                    new SafeDestination(
                            storedArrival.blockX(),
                            storedArrival.y(),
                            storedArrival.blockZ()
                    );
        }

        /*
         * =====================================================
         * Search
         * =====================================================
         */

        SafeSearchTask searchTask =
                new SafeSearchTask(
                        level,
                        region,
                        cityId,
                        centerBlockX,
                        centerBlockZ,
                        cachedDestination
                );

        /*
         * =====================================================
         * Boss Bar
         * =====================================================
         */

        ServerBossEvent bossBar =
                createCastBossBar(
                        level,
                        player,
                        city
                );

        /*
         * =====================================================
         * Session
         * =====================================================
         *
         * startPosition은 movement interrupt용.
         */

        CastSession session =
                new CastSession(
                        cityId,
                        level.dimension(),
                        player.position(),
                        now,
                        now + CAST_DURATION_NANOS,
                        searchTask,
                        bossBar
                );

        ACTIVE_CASTS.put(
                playerId,
                session
        );

        player.sendOverlayMessage(
                Component.literal(
                        "Preparing move to "
                                + city.name()
                                + "..."
                )
        );
    }

    /*
     * =========================================================
     * Server Tick
     * =========================================================
     */

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event
    ) {
        if (ACTIVE_CASTS.isEmpty()) {
            return;
        }

        MinecraftServer server =
                event.getServer();

        /*
         * 처리 중 ACTIVE_CASTS가 변경될 수 있으므로
         * snapshot을 사용한다.
         */
        List<UUID> playerIds =
                List.copyOf(
                        ACTIVE_CASTS.keySet()
                );

        for (UUID playerId : playerIds) {

            CastSession session =
                    ACTIVE_CASTS.get(
                            playerId
                    );

            if (session == null) {
                continue;
            }

            ServerPlayer player =
                    server.getPlayerList()
                            .getPlayer(
                                    playerId
                            );

            /*
             * =================================================
             * Logout
             * =================================================
             */

            if (player == null) {

                removeCastSession(
                        playerId
                );

                continue;
            }

            /*
             * =================================================
             * Death
             * =================================================
             */

            if (!player.isAlive()) {

                cancelCast(
                        player,
                        "City Move was interrupted."
                );

                continue;
            }

            /*
             * =================================================
             * Dimension Change
             * =================================================
             */

            if (
                    !player.level()
                            .dimension()
                            .equals(
                                    session.dimension()
                            )
            ) {

                cancelCast(
                        player,
                        "City Move was interrupted because the dimension changed."
                );

                continue;
            }

            /*
             * =================================================
             * Movement Interrupt
             * =================================================
             *
             * 시전 시작 좌표로부터 실제 위치가
             * 0.1 block 이상 달라지면 취소.
             *
             * 걷기
             * 점프
             * 낙하
             * knockback
             * 물 등에 의한 이동
             *
             * 모두 포함된다.
             *
             * yaw/pitch는 검사하지 않으므로
             * 시선 회전은 자유롭게 가능하다.
             */

            if (
                    player.position()
                            .distanceToSqr(
                                    session.startPosition()
                            )
                            > MOVEMENT_CANCEL_DISTANCE_SQUARED
            ) {

                cancelCast(
                        player,
                        "City Move was interrupted because you moved."
                );

                continue;
            }

            /*
             * =================================================
             * Monster Interrupt
             * =================================================
             */

            if (
                    hasRestPreventingMonsterNearby(
                            player
                    )
            ) {

                cancelCast(
                        player,
                        "City Move was interrupted because monsters are nearby."
                );

                continue;
            }

            /*
             * =================================================
             * Destination Search
             * =================================================
             */

            session.searchTask()
                    .advance(
                            SEARCH_BUDGET_NANOS_PER_TICK
                    );

            long now =
                    System.nanoTime();

            /*
             * =================================================
             * Cast Progress
             * =================================================
             */

            double elapsedNanos =
                    now
                            - session.startedAtNanos();

            float progress =
                    (float) Math.max(
                            0.0D,
                            Math.min(
                                    1.0D,
                                    elapsedNanos
                                            / CAST_DURATION_NANOS
                            )
                    );

            session.bossBar()
                    .setProgress(
                            progress
                    );

            /*
             * 목적지 계산이 먼저 끝났어도
             * 3초 cast는 유지.
             */
            if (
                    now
                            < session.completesAtNanos()
            ) {
                continue;
            }

            /*
             * =================================================
             * Cast Time Complete
             * =================================================
             */

            SafeSearchTask searchTask =
                    session.searchTask();

            /*
             * 3초가 끝났지만 아직 search 진행 중.
             */
            if (
                    !searchTask.isComplete()
            ) {

                BeyondtheCity.LOGGER.warn(
                        "City Move destination search timed out. "
                                + "player={}, city={}, {}",
                        player.getUUID(),
                        session.cityId(),
                        searchTask.getDebugSummary()
                );

                cancelCast(
                        player,
                        "City Move failed because the destination could not be calculated in time."
                );

                continue;
            }

            /*
             * search 자체는 완료됐지만
             * 안전한 목적지가 존재하지 않음.
             */
            if (
                    !searchTask.hasDestination()
            ) {

                BeyondtheCity.LOGGER.warn(
                        "City Move destination search completed without a destination. "
                                + "player={}, city={}, {}",
                        player.getUUID(),
                        session.cityId(),
                        searchTask.getDebugSummary()
                );

                cancelCast(
                        player,
                        "City Move failed because no safe destination was found."
                );

                continue;
            }

            SafeDestination destination =
                    searchTask.getDestination();

            /*
             * =================================================
             * Final City Validation
             * =================================================
             */

            City city =
                    CityManager.getCity(
                            server,
                            session.cityId()
                    );

            if (
                    city == null
                            || !CityManager.isCityAccessible(
                            server,
                            session.cityId()
                    )
            ) {

                cancelCast(
                        player,
                        "City Move failed because the city is no longer available."
                );

                continue;
            }

            CityRegion region =
                    city.getRegion(
                            session.dimension()
                    ).orElse(null);

            if (
                    region == null
                            || !region.containsBlock(
                            destination.blockX(),
                            destination.blockZ()
                    )
            ) {

                /*
                 * 혹시 기존 cached arrival이었는데
                 * City 영역 자체가 바뀐 경우를 위해 제거.
                 */
                CityManager.clearCityArrivalPosition(
                        server,
                        session.cityId(),
                        session.dimension()
                );

                cancelCast(
                        player,
                        "City Move failed because the destination is no longer valid."
                );

                continue;
            }

            /*
             * =================================================
             * Final Safety Validation
             * =================================================
             *
             * 3초 동안 블록이 바뀌었을 수도 있으므로
             * 실제 목적지를 마지막으로 재검사.
             */

            if (
                    !isSafeStandingPosition(
                            player.level(),
                            destination.blockX(),
                            destination.y(),
                            destination.blockZ()
                    )
            ) {

                /*
                 * 저장된 공용 지점이었다면
                 * 다음 Move에서 다시 사용하면 안 되므로 제거.
                 *
                 * 새 계산 결과라 아직 저장되지 않은 경우에는
                 * 단순 no-op.
                 */
                CityManager.clearCityArrivalPosition(
                        server,
                        session.cityId(),
                        session.dimension()
                );

                cancelCast(
                        player,
                        "City Move failed because the destination is no longer safe."
                );

                continue;
            }

            /*
             * =================================================
             * Final Monster Check
             * =================================================
             */

            if (
                    hasRestPreventingMonsterNearby(
                            player
                    )
            ) {

                cancelCast(
                        player,
                        "City Move was interrupted because monsters are nearby."
                );

                continue;
            }

            /*
             * =================================================
             * Resolve Shared Arrival Point
             * =================================================
             *
             * 동시에 여러 플레이어가 search를 시작했을 수 있다.
             *
             * 이 시점에 누군가 이미 공용 arrival을 저장했다면
             * 그것을 우선한다.
             *
             * 없다면 이번 계산 결과를 월드 공용 위치로 저장.
             */

            destination =
                    resolveSharedArrival(
                            player.level(),
                            city,
                            destination
                    );

            /*
             * resolveSharedArrival()이 반환한 위치도
             * 실제 안전 위치임이 보장된다.
             */

            completeTeleport(
                    player,
                    city,
                    destination,
                    now
            );
        }
    }

    /*
     * =========================================================
     * Damage Interrupt
     * =========================================================
     */

    @SubscribeEvent
    public static void onLivingDamage(
            LivingDamageEvent.Post event
    ) {
        if (
                !(event.getEntity()
                        instanceof ServerPlayer player)
        ) {
            return;
        }

        if (
                !ACTIVE_CASTS.containsKey(
                        player.getUUID()
                )
        ) {
            return;
        }

        /*
         * 실제 damage가 없으면 유지.
         *
         * 따라서 shield 등으로 피해를 완전히 막았다면
         * 현재 구현에서는 cast가 취소되지 않는다.
         */
        if (
                event.getInflictedDamage()
                        <= 0.0F
        ) {
            return;
        }

        cancelCast(
                player,
                "City Move was interrupted by damage."
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
        if (
                !(event.getEntity()
                        instanceof ServerPlayer player)
        ) {
            return;
        }

        removeCastSession(
                player.getUUID()
        );
    }

    /*
     * =========================================================
     * Server Stop
     * =========================================================
     */

    @SubscribeEvent
    public static void onServerStopped(
            ServerStoppedEvent event
    ) {
        for (
                CastSession session :
                ACTIVE_CASTS.values()
        ) {

            closeBossBar(
                    session.bossBar()
            );
        }

        ACTIVE_CASTS.clear();
        COOLDOWN_DEADLINES.clear();
    }

    /*
     * =========================================================
     * Shared Arrival Position
     * =========================================================
     */

    private static SafeDestination resolveSharedArrival(
            ServerLevel level,
            City city,
            SafeDestination calculatedDestination
    ) {
        MinecraftServer server =
                level.getServer();

        ResourceKey<Level> dimension =
                level.dimension();

        /*
         * =====================================================
         * Existing Shared Arrival
         * =====================================================
         */

        CitySavedData.CityArrivalPosition stored =
                CityManager.getCityArrivalPosition(
                        server,
                        city.id(),
                        dimension
                );

        if (stored != null) {

            int blockX =
                    stored.blockX();

            int y =
                    stored.y();

            int blockZ =
                    stored.blockZ();

            /*
             * 저장된 좌표가 여전히 해당 City 안에 있고,
             * 실제로도 안전한 경우 기존 공용 위치를 유지.
             */
            if (
                    city.contains(
                            dimension,
                            blockX,
                            blockZ
                    )
            ) {

                /*
                 * block state를 읽기 위해
                 * 실제 chunk를 준비한다.
                 */
                level.getChunk(
                        blockX >> 4,
                        blockZ >> 4
                );

                if (
                        isSafeStandingPosition(
                                level,
                                blockX,
                                y,
                                blockZ
                        )
                ) {

                    return new SafeDestination(
                            blockX,
                            y,
                            blockZ
                    );
                }
            }

            /*
             * 저장된 위치가 더 이상 유효하지 않음.
             */
            CityManager.clearCityArrivalPosition(
                    server,
                    city.id(),
                    dimension
            );
        }

        /*
         * =====================================================
         * New Shared Arrival
         * =====================================================
         *
         * 아직 공용 위치가 없으므로
         * 이번 계산 결과를 이 월드의 공용 arrival로 저장한다.
         */

        CityManager.setCityArrivalPosition(
                server,
                city.id(),
                dimension,
                calculatedDestination.blockX(),
                calculatedDestination.y(),
                calculatedDestination.blockZ()
        );

        BeyondtheCity.LOGGER.info(
                "Saved city arrival position: "
                        + "city={}, dimension={}, x={}, y={}, z={}",
                city.id(),
                dimension.identifier(),
                calculatedDestination.blockX(),
                calculatedDestination.y(),
                calculatedDestination.blockZ()
        );

        return calculatedDestination;
    }

    /*
     * =========================================================
     * Complete Teleport
     * =========================================================
     */

    private static void completeTeleport(
            ServerPlayer player,
            City city,
            SafeDestination destination,
            long now
    ) {
        UUID playerId =
                player.getUUID();

        ServerLevel level =
                player.level();

        MinecraftServer server =
                level.getServer();

        double targetX =
                destination.blockX()
                        + 0.5D;

        double targetY =
                destination.y();

        double targetZ =
                destination.blockZ()
                        + 0.5D;

        /*
         * 기존 velocity 제거.
         */
        player.setDeltaMovement(
                0.0D,
                0.0D,
                0.0D
        );

        /*
         * 같은 차원 내부 이동.
         *
         * yaw / pitch는 그대로 유지.
         */
        player.teleportTo(
                targetX,
                targetY,
                targetZ
        );

        player.setDeltaMovement(
                0.0D,
                0.0D,
                0.0D
        );

        player.hurtMarked =
                true;

        /*
         * =====================================================
         * Boundary State
         * =====================================================
         */

        PlayerPositionTracker.resetReturnDeadline(
                playerId
        );

        PlayerPositionTracker.resetPositionSaveTicks(
                playerId
        );

        /*
         * =====================================================
         * Player-specific Last Safe Position
         * =====================================================
         *
         * 이 값은 City 공용 arrival point와 별개다.
         *
         * 기존 CityBoundaryHandler가 경계 밖에서
         * 플레이어를 마지막 정상 위치로 되돌릴 때 사용한다.
         */

        CitySavedData savedData =
                server.getDataStorage()
                        .computeIfAbsent(
                                CitySavedData.TYPE
                        );

        savedData.setLastValidPosition(
                playerId,
                level.dimension(),
                targetX,
                targetY,
                targetZ,
                player.getYRot(),
                player.getXRot()
        );

        if (
                savedData.hasPendingReturn(
                        playerId
                )
        ) {

            savedData.clearPendingReturn(
                    playerId
            );
        }

        /*
         * =====================================================
         * Cast End
         * =====================================================
         */

        removeCastSession(
                playerId
        );

        /*
         * =====================================================
         * Cooldown
         * =====================================================
         *
         * 실제 teleport 성공 시점부터 3초.
         */

        COOLDOWN_DEADLINES.put(
                playerId,
                now
                        + COOLDOWN_DURATION_NANOS
        );

        player.sendOverlayMessage(
                Component.literal(
                        "Moved to "
                                + city.name()
                                + "."
                )
        );
    }

    /*
     * =========================================================
     * Cancel Cast
     * =========================================================
     */

    private static void cancelCast(
            ServerPlayer player,
            String message
    ) {
        UUID playerId =
                player.getUUID();

        if (
                !ACTIVE_CASTS.containsKey(
                        playerId
                )
        ) {
            return;
        }

        removeCastSession(
                playerId
        );

        /*
         * cast 실패/취소에는 cooldown이 없다.
         */
        player.sendOverlayMessage(
                Component.literal(
                        message
                )
        );
    }

    /*
     * =========================================================
     * Remove Cast
     * =========================================================
     */

    private static void removeCastSession(
            UUID playerId
    ) {
        CastSession session =
                ACTIVE_CASTS.remove(
                        playerId
                );

        if (session == null) {
            return;
        }

        closeBossBar(
                session.bossBar()
        );
    }

    /*
     * =========================================================
     * Boss Bar
     * =========================================================
     */

    private static ServerBossEvent createCastBossBar(
            ServerLevel level,
            ServerPlayer player,
            City city
    ) {
        ServerBossEvent bossBar =
                new ServerBossEvent(
                        Mth.createInsecureUUID(
                                level.getRandom()
                        ),
                        Component.literal(
                                "Moving to "
                                        + city.name()
                        ),
                        BossEvent.BossBarColor.GREEN,
                        BossEvent.BossBarOverlay.PROGRESS
                );

        bossBar.setProgress(
                0.0F
        );

        bossBar.setPlayBossMusic(
                false
        );

        bossBar.setCreateWorldFog(
                false
        );

        bossBar.setDarkenScreen(
                false
        );

        bossBar.addPlayer(
                player
        );

        return bossBar;
    }

    private static void closeBossBar(
            ServerBossEvent bossBar
    ) {
        bossBar.setVisible(
                false
        );

        bossBar.removeAllPlayers();
    }

    /*
     * =========================================================
     * Monster Check
     * =========================================================
     */

    private static boolean hasRestPreventingMonsterNearby(
            ServerPlayer player
    ) {
        ServerLevel level =
                player.level();

        Vec3 center =
                Vec3.atBottomCenterOf(
                        player.blockPosition()
                );

        AABB searchBox =
                new AABB(
                        center.x()
                                - MONSTER_HORIZONTAL_RANGE,

                        center.y()
                                - MONSTER_VERTICAL_RANGE,

                        center.z()
                                - MONSTER_HORIZONTAL_RANGE,

                        center.x()
                                + MONSTER_HORIZONTAL_RANGE,

                        center.y()
                                + MONSTER_VERTICAL_RANGE,

                        center.z()
                                + MONSTER_HORIZONTAL_RANGE
                );

        return !level.getEntitiesOfClass(
                        Monster.class,
                        searchBox,
                        monster ->
                                monster.isPreventingPlayerRest(
                                        level,
                                        player
                                )
                )
                .isEmpty();
    }

    /*
     * =========================================================
     * Cooldown Text
     * =========================================================
     */

    private static String formatSeconds(
            long nanos
    ) {
        double seconds =
                Math.ceil(
                        nanos
                                / 100_000_000.0D
                )
                        / 10.0D;

        return String.format(
                Locale.ROOT,
                "%.1f",
                seconds
        );
    }

    /*
     * =========================================================
     * Safe Search Task
     * =========================================================
     *
     * CACHED
     *      ↓ invalid
     *
     * CENTER
     *      ↓
     *
     * RADIAL
     *      ↓
     *
     * BINARY
     *      ↓
     *
     * LOCAL
     *      ↓
     *
     * COMPLETE
     */

    private static final class SafeSearchTask {

        private final ServerLevel level;

        private final CityRegion region;

        private final String cityId;

        private final int centerBlockX;

        private final int centerBlockZ;

        /*
         * 이미 저장되어 있던 공용 도착점.
         */
        private final SafeDestination
                cachedDestination;

        /*
         * 실제 safety 검사를 이미 한 X/Z.
         */
        private final Set<Long>
                checkedSafePositions =
                new HashSet<>();

        /*
         * 이 Move search에서 실제로 load한 chunk.
         */
        private final Set<Long>
                loadedChunks =
                new HashSet<>();

        /*
         * 각 ray의 이전 검색 상태.
         */
        private final int[]
                lastProbeRadius =
                new int[
                        RADIAL_DIRECTION_COUNT
                        ];

        private final boolean[]
                lastProbeWasLand =
                new boolean[
                        RADIAL_DIRECTION_COUNT
                        ];

        /*
         * =====================================================
         * Stage
         * =====================================================
         */

        private SearchStage stage;

        /*
         * =====================================================
         * Radial
         * =====================================================
         */

        private int radialIndex =
                0;

        private int activeDirection =
                -1;

        private int activeRadius =
                0;

        /*
         * =====================================================
         * Binary
         * =====================================================
         */

        private int binaryLowRadius;

        private int binaryHighRadius;

        /*
         * =====================================================
         * Local
         * =====================================================
         */

        private int localCenterX;

        private int localCenterZ;

        private int localIndex =
                0;

        /*
         * =====================================================
         * Result
         * =====================================================
         */

        private SafeDestination destination;

        /*
         * =====================================================
         * Debug
         * =====================================================
         */

        private int fastProbeCount;

        private int fastLandCount;

        /*
         * 정확히는 실제 fluid block을 읽는 것이 아니라
         * worldgen base height가 sea level 이하였던 횟수.
         */
        private int fastFluidCount;

        private int fastInvalidHeightCount;

        private int safeProbeCount;

        /*
         * =====================================================
         * Constructor
         * =====================================================
         */

        private SafeSearchTask(
                ServerLevel level,
                CityRegion region,
                String cityId,
                int centerBlockX,
                int centerBlockZ,
                SafeDestination cachedDestination
        ) {
            this.level =
                    level;

            this.region =
                    region;

            this.cityId =
                    cityId;

            this.centerBlockX =
                    centerBlockX;

            this.centerBlockZ =
                    centerBlockZ;

            this.cachedDestination =
                    cachedDestination;

            this.stage =
                    cachedDestination == null
                            ? SearchStage.CENTER
                            : SearchStage.CACHED;
        }

        /*
         * =====================================================
         * Advance
         * =====================================================
         */

        private void advance(
                long budgetNanos
        ) {
            if (
                    stage
                            == SearchStage.COMPLETE
            ) {
                return;
            }

            long deadline =
                    System.nanoTime()
                            + budgetNanos;

            while (
                    stage
                            != SearchStage.COMPLETE
                            && System.nanoTime()
                            < deadline
            ) {

                switch (stage) {

                    case CACHED ->
                            advanceCached();

                    case CENTER ->
                            advanceCenter();

                    case RADIAL ->
                            advanceRadial();

                    case BINARY ->
                            advanceBinary();

                    case LOCAL ->
                            advanceLocal();

                    case COMPLETE -> {
                        return;
                    }
                }
            }
        }

        /*
         * =====================================================
         * Cached Arrival
         * =====================================================
         */

        private void advanceCached() {

            if (cachedDestination == null) {

                stage =
                        SearchStage.CENTER;

                return;
            }

            int blockX =
                    cachedDestination.blockX();

            int y =
                    cachedDestination.y();

            int blockZ =
                    cachedDestination.blockZ();

            /*
             * City 영역 자체에서 벗어난 저장 좌표.
             */
            if (
                    !region.containsBlock(
                            blockX,
                            blockZ
                    )
            ) {

                clearCachedArrival();

                return;
            }

            /*
             * 실제 현재 world state 확인.
             */
            ensureChunkLoadedForBlock(
                    blockX,
                    blockZ
            );

            if (
                    isSafeStandingPosition(
                            level,
                            blockX,
                            y,
                            blockZ
                    )
            ) {

                destination =
                        cachedDestination;

                stage =
                        SearchStage.COMPLETE;

                return;
            }

            /*
             * 블록 설치/파괴 등으로
             * 공용 위치가 더 이상 안전하지 않음.
             */
            clearCachedArrival();
        }

        private void clearCachedArrival() {

            CityManager.clearCityArrivalPosition(
                    level.getServer(),
                    cityId,
                    level.dimension()
            );

            BeyondtheCity.LOGGER.info(
                    "Cleared invalid city arrival position: "
                            + "city={}, dimension={}",
                    cityId,
                    level.dimension().identifier()
            );

            /*
             * 기존 search algorithm으로 fallback.
             */
            stage =
                    SearchStage.CENTER;
        }

        /*
         * =====================================================
         * Center
         * =====================================================
         */

        private void advanceCenter() {

            /*
             * 정확한 City center가 안전한지 먼저 확인.
             */
            SafeDestination centerDestination =
                    inspectSafePosition(
                            centerBlockX,
                            centerBlockZ
                    );

            if (centerDestination != null) {

                destination =
                        centerDestination;

                stage =
                        SearchStage.COMPLETE;

                return;
            }

            /*
             * Overworld에서는 City center 자체가
             * 육지인데 정확한 한 칸만 불가능할 수도 있다.
             *
             * 예:
             *
             * 나무
             * 작은 장애물
             * 경사
             */
            if (
                    !level.dimension()
                            .equals(
                                    Level.NETHER
                            )
            ) {

                Integer centerLand =
                        probeFastLand(
                                centerBlockX,
                                centerBlockZ
                        );

                if (centerLand != null) {

                    activeDirection =
                            -1;

                    activeRadius =
                            0;

                    beginLocalSearch(
                            centerBlockX,
                            centerBlockZ
                    );

                    return;
                }
            }

            stage =
                    SearchStage.RADIAL;
        }

        /*
         * =====================================================
         * Radial Search
         * =====================================================
         */

        private void advanceRadial() {

            /*
             * 모든 ray를 반경 512까지 확인.
             */
            if (
                    radialIndex
                            >= RADIAL_PROBES.size()
            ) {

                stage =
                        SearchStage.COMPLETE;

                return;
            }

            RadialProbe probe =
                    RADIAL_PROBES.get(
                            radialIndex
                    );

            radialIndex++;

            int blockX =
                    centerBlockX
                            + probe.dx();

            int blockZ =
                    centerBlockZ
                            + probe.dz();

            /*
             * 도시 region 밖.
             */
            if (
                    !region.containsBlock(
                            blockX,
                            blockZ
                    )
            ) {

                lastProbeRadius[
                        probe.direction()
                        ] =
                        probe.radius();

                lastProbeWasLand[
                        probe.direction()
                        ] =
                        false;

                return;
            }

            /*
             * =================================================
             * Nether
             * =================================================
             *
             * Nether는 Overworld의 sea-level surface 개념을
             * 적용하기 어렵기 때문에 실제 vertical safe
             * search를 수행한다.
             */

            if (
                    level.dimension()
                            .equals(
                                    Level.NETHER
                            )
            ) {

                SafeDestination candidate =
                        inspectSafePosition(
                                blockX,
                                blockZ
                        );

                lastProbeRadius[
                        probe.direction()
                        ] =
                        probe.radius();

                lastProbeWasLand[
                        probe.direction()
                        ] =
                        candidate != null;

                if (candidate != null) {

                    destination =
                            candidate;

                    stage =
                            SearchStage.COMPLETE;
                }

                return;
            }

            /*
             * =================================================
             * Overworld Fast Worldgen Probe
             * =================================================
             */

            Integer landY =
                    probeFastLand(
                            blockX,
                            blockZ
                    );

            if (landY == null) {

                lastProbeRadius[
                        probe.direction()
                        ] =
                        probe.radius();

                lastProbeWasLand[
                        probe.direction()
                        ] =
                        false;

                return;
            }

            /*
             * 육지 후보 발견.
             */
            activeDirection =
                    probe.direction();

            activeRadius =
                    probe.radius();

            int previousRadius =
                    lastProbeRadius[
                            probe.direction()
                            ];

            boolean previousWasLand =
                    lastProbeWasLand[
                            probe.direction()
                            ];

            /*
             * 이전 point = water
             * 현재 point = land
             *
             * 해당 구간만 binary refinement.
             */
            if (
                    previousRadius
                            < probe.radius()
                            && !previousWasLand
            ) {

                binaryLowRadius =
                        previousRadius;

                binaryHighRadius =
                        probe.radius();

                stage =
                        SearchStage.BINARY;

                return;
            }

            /*
             * 이미 이전 point도 land였다면
             * 바로 local search.
             */
            beginLocalSearch(
                    blockX,
                    blockZ
            );
        }

        /*
         * =====================================================
         * Binary Shoreline Search
         * =====================================================
         */

        private void advanceBinary() {

            /*
             * shoreline interval을
             * 약 1 block까지 좁힘.
             */
            if (
                    binaryHighRadius
                            - binaryLowRadius
                            <= 1
            ) {

                int blockX =
                        centerBlockX
                                + radialDx(
                                activeDirection,
                                binaryHighRadius
                        );

                int blockZ =
                        centerBlockZ
                                + radialDz(
                                activeDirection,
                                binaryHighRadius
                        );

                activeRadius =
                        binaryHighRadius;

                beginLocalSearch(
                        blockX,
                        blockZ
                );

                return;
            }

            int middleRadius =
                    (
                            binaryLowRadius
                                    + binaryHighRadius
                    )
                            / 2;

            int blockX =
                    centerBlockX
                            + radialDx(
                            activeDirection,
                            middleRadius
                    );

            int blockZ =
                    centerBlockZ
                            + radialDz(
                            activeDirection,
                            middleRadius
                    );

            if (
                    !region.containsBlock(
                            blockX,
                            blockZ
                    )
            ) {

                binaryLowRadius =
                        middleRadius;

                return;
            }

            Integer landY =
                    probeFastLand(
                            blockX,
                            blockZ
                    );

            if (landY != null) {

                /*
                 * middle도 land.
                 *
                 * 더 City center 방향으로 좁힌다.
                 */
                binaryHighRadius =
                        middleRadius;

            } else {

                /*
                 * middle은 water-like.
                 */
                binaryLowRadius =
                        middleRadius;
            }
        }

        /*
         * =====================================================
         * Local Search Start
         * =====================================================
         */

        private void beginLocalSearch(
                int blockX,
                int blockZ
        ) {
            localCenterX =
                    blockX;

            localCenterZ =
                    blockZ;

            localIndex =
                    0;

            stage =
                    SearchStage.LOCAL;
        }

        /*
         * =====================================================
         * Local Safe Search
         * =====================================================
         */

        private void advanceLocal() {

            /*
             * 현재 육지 후보 주변에서
             * 실제 safe position을 못 찾음.
             *
             * 다음 radial probe로 계속.
             */
            if (
                    localIndex
                            >= LOCAL_OFFSETS.size()
            ) {

                if (activeDirection >= 0) {

                    lastProbeRadius[
                            activeDirection
                            ] =
                            activeRadius;

                    lastProbeWasLand[
                            activeDirection
                            ] =
                            true;
                }

                stage =
                        SearchStage.RADIAL;

                return;
            }

            LocalOffset offset =
                    LOCAL_OFFSETS.get(
                            localIndex
                    );

            localIndex++;

            int blockX =
                    localCenterX
                            + offset.dx();

            int blockZ =
                    localCenterZ
                            + offset.dz();

            /*
             * City center 기준 최대 반경.
             */
            if (
                    distanceSquaredFromCityCenter(
                            blockX,
                            blockZ
                    )
                            >
                            SAFE_SEARCH_RADIUS
                                    * SAFE_SEARCH_RADIUS
            ) {
                return;
            }

            SafeDestination candidate =
                    inspectSafePosition(
                            blockX,
                            blockZ
                    );

            /*
             * shoreline 근처까지 이미 좁혔으므로
             * 첫 실제 safe position에서 종료.
             */
            if (candidate != null) {

                destination =
                        candidate;

                stage =
                        SearchStage.COMPLETE;
            }
        }

        /*
         * =====================================================
         * Fast Worldgen Land Probe
         * =====================================================
         *
         * 중요:
         *
         * 여기서는 level.getChunk()를 하지 않는다.
         *
         * 실제 chunk/heightmap을 load하는 대신
         * ChunkGenerator의 getBaseHeight()로
         * worldgen 기준 지표 높이만 빠르게 계산한다.
         *
         * 그래서 Starting City가 넓은 바다더라도
         * coarse search 과정에서 수십 개 chunk를
         * 동기 load하는 병목이 발생하지 않는다.
         */

        private Integer probeFastLand(
                int blockX,
                int blockZ
        ) {
            fastProbeCount++;

            int baseHeight =
                    level.getChunkSource()
                            .getGenerator()
                            .getBaseHeight(
                                    blockX,
                                    blockZ,
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    level,
                                    level.getChunkSource()
                                            .randomState()
                            );

            /*
             * 비정상 높이.
             */
            if (
                    baseHeight
                            <= level.getMinY()
                            || baseHeight
                            >= level.getMaxY()
            ) {

                fastInvalidHeightCount++;

                return null;
            }

            /*
             * coarse stage에서는
             *
             * base terrain <= sea level
             *
             * 이면 water-like terrain으로 취급한다.
             *
             * 이것은 최종 safety 판정이 아니다.
             */
            if (
                    baseHeight
                            <= level.getSeaLevel()
            ) {

                fastFluidCount++;

                return null;
            }

            fastLandCount++;

            return baseHeight;
        }

        /*
         * =====================================================
         * Actual Safe Position
         * =====================================================
         */

        private SafeDestination inspectSafePosition(
                int blockX,
                int blockZ
        ) {
            if (
                    !region.containsBlock(
                            blockX,
                            blockZ
                    )
            ) {
                return null;
            }

            if (
                    distanceSquaredFromCityCenter(
                            blockX,
                            blockZ
                    )
                            >
                            SAFE_SEARCH_RADIUS
                                    * SAFE_SEARCH_RADIUS
            ) {
                return null;
            }

            long positionKey =
                    createXZKey(
                            blockX,
                            blockZ
                    );

            /*
             * 같은 X/Z에서 actual safety check는
             * 한 번만 수행.
             */
            if (
                    !checkedSafePositions.add(
                            positionKey
                    )
            ) {
                return null;
            }

            safeProbeCount++;

            /*
             * actual world state가 필요하므로
             * 여기서는 실제 chunk를 load한다.
             */
            ensureChunkLoadedForBlock(
                    blockX,
                    blockZ
            );

            Integer safeY =
                    findSafeSurfaceY(
                            level,
                            blockX,
                            blockZ
                    );

            if (safeY == null) {
                return null;
            }

            return new SafeDestination(
                    blockX,
                    safeY,
                    blockZ
            );
        }

        /*
         * =====================================================
         * Chunk Loading
         * =====================================================
         */

        private void ensureChunkLoadedForBlock(
                int blockX,
                int blockZ
        ) {
            int chunkX =
                    blockX >> 4;

            int chunkZ =
                    blockZ >> 4;

            long chunkKey =
                    createXZKey(
                            chunkX,
                            chunkZ
                    );

            /*
             * 한 search task에서 같은 chunk는
             * 한 번만 명시적으로 load.
             */
            if (
                    !loadedChunks.add(
                            chunkKey
                    )
            ) {
                return;
            }

            level.getChunk(
                    chunkX,
                    chunkZ
            );
        }

        /*
         * =====================================================
         * Distance
         * =====================================================
         */

        private int distanceSquaredFromCityCenter(
                int blockX,
                int blockZ
        ) {
            int dx =
                    blockX
                            - centerBlockX;

            int dz =
                    blockZ
                            - centerBlockZ;

            return dx * dx
                    + dz * dz;
        }

        /*
         * =====================================================
         * Result
         * =====================================================
         */

        private boolean isComplete() {
            return stage
                    == SearchStage.COMPLETE;
        }

        private boolean hasDestination() {
            return destination != null;
        }

        private SafeDestination getDestination() {
            return destination;
        }

        /*
         * =====================================================
         * Debug
         * =====================================================
         */

        private String getDebugSummary() {
            return "stage="
                    + stage
                    + ", radialIndex="
                    + radialIndex
                    + "/"
                    + RADIAL_PROBES.size()
                    + ", fastProbes="
                    + fastProbeCount
                    + ", fastLand="
                    + fastLandCount
                    + ", fastFluid="
                    + fastFluidCount
                    + ", fastInvalidHeight="
                    + fastInvalidHeightCount
                    + ", safeProbes="
                    + safeProbeCount
                    + ", loadedChunks="
                    + loadedChunks.size();
        }
    }

    /*
     * =========================================================
     * Radial Probe Generation
     * =========================================================
     */

    private static List<RadialProbe>
    createRadialProbes() {
        List<RadialProbe> probes =
                new ArrayList<>();

        Set<Long> usedOffsets =
                new HashSet<>();

        for (
                int radius = RADIAL_STEP;
                radius <= SAFE_SEARCH_RADIUS;
                radius += RADIAL_STEP
        ) {

            for (
                    int direction = 0;
                    direction < RADIAL_DIRECTION_COUNT;
                    direction++
            ) {

                int dx =
                        radialDx(
                                direction,
                                radius
                        );

                int dz =
                        radialDz(
                                direction,
                                radius
                        );

                long key =
                        createXZKey(
                                dx,
                                dz
                        );

                /*
                 * Math.round() 때문에 동일한 offset이
                 * 생길 가능성에 대비.
                 */
                if (
                        !usedOffsets.add(
                                key
                        )
                ) {
                    continue;
                }

                probes.add(
                        new RadialProbe(
                                direction,
                                radius,
                                dx,
                                dz
                        )
                );
            }
        }

        /*
         * 중심에 가까운 radius부터.
         */
        probes.sort(
                Comparator
                        .comparingInt(
                                RadialProbe::radius
                        )
                        .thenComparingInt(
                                RadialProbe::direction
                        )
        );

        return List.copyOf(
                probes
        );
    }

    /*
     * =========================================================
     * Radial Coordinate
     * =========================================================
     */

    private static int radialDx(
            int direction,
            int radius
    ) {
        double angle =
                2.0D
                        * Math.PI
                        * direction
                        / RADIAL_DIRECTION_COUNT;

        return (int) Math.round(
                Math.cos(
                        angle
                )
                        * radius
        );
    }

    private static int radialDz(
            int direction,
            int radius
    ) {
        double angle =
                2.0D
                        * Math.PI
                        * direction
                        / RADIAL_DIRECTION_COUNT;

        return (int) Math.round(
                Math.sin(
                        angle
                )
                        * radius
        );
    }

    /*
     * =========================================================
     * Local Search Offsets
     * =========================================================
     */

    private static List<LocalOffset>
    createLocalOffsets() {
        List<LocalOffset> offsets =
                new ArrayList<>();

        int radiusSquared =
                LOCAL_SEARCH_RADIUS
                        * LOCAL_SEARCH_RADIUS;

        for (
                int dx = -LOCAL_SEARCH_RADIUS;
                dx <= LOCAL_SEARCH_RADIUS;
                dx++
        ) {

            for (
                    int dz = -LOCAL_SEARCH_RADIUS;
                    dz <= LOCAL_SEARCH_RADIUS;
                    dz++
            ) {

                int distanceSquared =
                        dx * dx
                                + dz * dz;

                /*
                 * square가 아니라 원 내부.
                 */
                if (
                        distanceSquared
                                > radiusSquared
                ) {
                    continue;
                }

                offsets.add(
                        new LocalOffset(
                                dx,
                                dz,
                                distanceSquared
                        )
                );
            }
        }

        /*
         * seed point와 가까운 순서.
         */
        offsets.sort(
                Comparator
                        .comparingInt(
                                LocalOffset::distanceSquared
                        )
                        .thenComparingInt(
                                offset ->
                                        Math.abs(
                                                offset.dx()
                                        )
                                                + Math.abs(
                                                offset.dz()
                                        )
                        )
                        .thenComparingInt(
                                LocalOffset::dx
                        )
                        .thenComparingInt(
                                LocalOffset::dz
                        )
        );

        return List.copyOf(
                offsets
        );
    }

    /*
     * =========================================================
     * Safe Surface
     * =========================================================
     */

    private static Integer findSafeSurfaceY(
            ServerLevel level,
            int blockX,
            int blockZ
    ) {
        if (
                level.dimension()
                        .equals(
                                Level.NETHER
                        )
        ) {

            return findNetherSurfaceY(
                    level,
                    blockX,
                    blockZ
            );
        }

        return findOverworldSurfaceY(
                level,
                blockX,
                blockZ
        );
    }

    /*
     * =========================================================
     * Overworld Surface
     * =========================================================
     */

    private static Integer findOverworldSurfaceY(
            ServerLevel level,
            int blockX,
            int blockZ
    ) {
        int surfaceY =
                level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        blockX,
                        blockZ
                );

        if (
                isSafeStandingPosition(
                        level,
                        blockX,
                        surfaceY,
                        blockZ
                )
        ) {
            return surfaceY;
        }

        /*
         * 눈 layer 등의 작은 특수 지형만 고려.
         *
         * cave까지 내려가지는 않도록
         * 최대 3 blocks만 내려간다.
         */
        for (
                int offset = 1;
                offset <= 3;
                offset++
        ) {

            int y =
                    surfaceY
                            - offset;

            if (
                    y <= level.getMinY()
            ) {
                break;
            }

            if (
                    isSafeStandingPosition(
                            level,
                            blockX,
                            y,
                            blockZ
                    )
            ) {
                return y;
            }
        }

        return null;
    }

    /*
     * =========================================================
     * Nether Surface
     * =========================================================
     */

    private static Integer findNetherSurfaceY(
            ServerLevel level,
            int blockX,
            int blockZ
    ) {
        int maxY =
                level.getMaxY()
                        - 2;

        int minY =
                level.getMinY()
                        + 1;

        /*
         * 위에서 아래로 실제 안전 공간 탐색.
         *
         * floor가 BEDROCK이면 아래 safety 검사에서
         * 제외되므로 Nether roof 위에는 도착하지 않는다.
         */
        for (
                int y = maxY;
                y >= minY;
                y--
        ) {

            if (
                    isSafeStandingPosition(
                            level,
                            blockX,
                            y,
                            blockZ
                    )
            ) {
                return y;
            }
        }

        return null;
    }

    /*
     * =========================================================
     * Safe Standing Position
     * =========================================================
     */

    private static boolean isSafeStandingPosition(
            ServerLevel level,
            int blockX,
            int y,
            int blockZ
    ) {
        if (
                y <= level.getMinY()
                        || y + 1
                        >= level.getMaxY()
        ) {
            return false;
        }

        BlockPos floorPos =
                new BlockPos(
                        blockX,
                        y - 1,
                        blockZ
                );

        BlockPos feetPos =
                new BlockPos(
                        blockX,
                        y,
                        blockZ
                );

        BlockPos headPos =
                new BlockPos(
                        blockX,
                        y + 1,
                        blockZ
                );

        BlockState floorState =
                level.getBlockState(
                        floorPos
                );

        if (
                !isSafeFloor(
                        level,
                        floorPos,
                        floorState
                )
        ) {
            return false;
        }

        if (
                !isSafePlayerSpace(
                        level,
                        feetPos
                )
        ) {
            return false;
        }

        return isSafePlayerSpace(
                level,
                headPos
        );
    }

    /*
     * =========================================================
     * Safe Floor
     * =========================================================
     */

    private static boolean isSafeFloor(
            ServerLevel level,
            BlockPos floorPos,
            BlockState state
    ) {
        /*
         * Water / lava.
         */
        if (
                !state.getFluidState()
                        .isEmpty()
        ) {
            return false;
        }

        /*
         * Nether roof 등.
         */
        if (
                state.is(
                        Blocks.BEDROCK
                )
        ) {
            return false;
        }

        /*
         * 위험한 바닥.
         */
        if (
                state.is(
                        Blocks.MAGMA_BLOCK
                )
                        || state.is(
                        Blocks.CAMPFIRE
                )
                        || state.is(
                        Blocks.SOUL_CAMPFIRE
                )
                        || state.is(
                        Blocks.CACTUS
                )
        ) {
            return false;
        }

        /*
         * 플레이어가 실제로 설 수 있는
         * sturdy top surface.
         */
        return state.isFaceSturdy(
                level,
                floorPos,
                Direction.UP
        );
    }

    /*
     * =========================================================
     * Safe Player Space
     * =========================================================
     */

    private static boolean isSafePlayerSpace(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state =
                level.getBlockState(
                        pos
                );

        /*
         * 물 / 용암 안.
         */
        if (
                !state.getFluidState()
                        .isEmpty()
        ) {
            return false;
        }

        /*
         * 몸과 충돌하는 block.
         */
        if (
                !state.getCollisionShape(
                                level,
                                pos
                        )
                        .isEmpty()
        ) {
            return false;
        }

        /*
         * collision은 없지만 위험한 block.
         */
        return !state.is(
                Blocks.FIRE
        )
                && !state.is(
                Blocks.SOUL_FIRE
        )
                && !state.is(
                Blocks.POWDER_SNOW
        )
                && !state.is(
                Blocks.SWEET_BERRY_BUSH
        )
                && !state.is(
                Blocks.WITHER_ROSE
        )
                && !state.is(
                Blocks.NETHER_PORTAL
        )
                && !state.is(
                Blocks.END_PORTAL
        )
                && !state.is(
                Blocks.END_GATEWAY
        );
    }

    /*
     * =========================================================
     * Coordinate Key
     * =========================================================
     */

    private static long createXZKey(
            int x,
            int z
    ) {
        return (
                (
                        (long) x
                                & 0xFFFFFFFFL
                )
                        << 32
        )
                |
                (
                        (long) z
                                & 0xFFFFFFFFL
                );
    }

    /*
     * =========================================================
     * Internal Types
     * =========================================================
     */

    private enum SearchStage {
        CACHED,
        CENTER,
        RADIAL,
        BINARY,
        LOCAL,
        COMPLETE
    }

    private record RadialProbe(
            int direction,
            int radius,
            int dx,
            int dz
    ) {
    }

    private record LocalOffset(
            int dx,
            int dz,
            int distanceSquared
    ) {
    }

    private record SafeDestination(
            int blockX,
            int y,
            int blockZ
    ) {
    }

    private record CastSession(
            String cityId,
            ResourceKey<Level> dimension,
            Vec3 startPosition,
            long startedAtNanos,
            long completesAtNanos,
            SafeSearchTask searchTask,
            ServerBossEvent bossBar
    ) {
    }
}