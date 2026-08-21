package net.njw.beyondthecity.client;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.city.CityRegion;
import net.njw.beyondthecity.network.CitySyncPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 현재 접속 중인 서버에서 전달받은
 * accessible city snapshot을 보관한다.
 *
 * 서버의 SavedData와 달리
 * 영구 저장하지 않는 client-side cache이다.
 */
@EventBusSubscriber(
        modid = BeyondtheCity.MODID,
        value = Dist.CLIENT
)
public final class ClientCityManager {

    private static volatile List<ClientCity>
            accessibleCities =
            List.of();

    private ClientCityManager() {
    }

    /*
     * =========================================================
     * Access
     * =========================================================
     */

    public static List<ClientCity>
    getAccessibleCities() {
        return accessibleCities;
    }

    /*
     * =========================================================
     * Snapshot Replacement
     * =========================================================
     */

    public static void replaceCities(
            List<CitySyncPayload.CityData> networkCities
    ) {
        List<ClientCity> cities =
                new ArrayList<>(
                        networkCities.size()
                );

        for (
                CitySyncPayload.CityData networkCity :
                networkCities
        ) {
            cities.add(
                    ClientCity.fromNetwork(
                            networkCity
                    )
            );
        }

        accessibleCities =
                List.copyOf(
                        cities
                );
    }

    /*
     * =========================================================
     * Clear
     * =========================================================
     */

    public static void clear() {
        accessibleCities =
                List.of();
    }

    /**
     * 다른 서버나 다른 singleplayer world로 이동할 때
     * 이전 서버의 도시 정보가 잠깐이라도 남지 않게 한다.
     */
    @SubscribeEvent
    public static void onClientLogout(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        clear();
    }

    /*
     * =========================================================
     * Client City
     * =========================================================
     */

    public record ClientCity(
            String id,
            String name,
            Map<Identifier, CityRegion> regions
    ) {

        public ClientCity {
            regions =
                    Map.copyOf(
                            regions
                    );
        }

        public CityRegion getRegion(
                Identifier dimension
        ) {
            return regions.get(
                    dimension
            );
        }

        private static ClientCity fromNetwork(
                CitySyncPayload.CityData city
        ) {
            Map<Identifier, CityRegion> regions =
                    new LinkedHashMap<>();

            for (
                    CitySyncPayload.RegionData region :
                    city.regions()
            ) {
                CityRegion cityRegion =
                        new CityRegion(
                                region.centerChunkX(),
                                region.centerChunkZ(),
                                region.widthChunks(),
                                region.heightChunks()
                        );

                regions.put(
                        region.dimension(),
                        cityRegion
                );
            }

            return new ClientCity(
                    city.id(),
                    city.name(),
                    regions
            );
        }
    }
}