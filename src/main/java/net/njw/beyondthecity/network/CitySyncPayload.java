package net.njw.beyondthecity.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.city.City;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버가 현재 접근 가능한 도시들의 snapshot을
 * 클라이언트로 전송하기 위한 payload.
 *
 * SavedData용 City.CODEC과 네트워크 형식을 분리한다.
 */
public record CitySyncPayload(
        List<CityData> cities
) implements CustomPacketPayload {

    public static final Type<CitySyncPayload> TYPE =
            new Type<>(
                    Identifier.fromNamespaceAndPath(
                            BeyondtheCity.MODID,
                            "city_sync"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            CitySyncPayload
            > STREAM_CODEC =
            StreamCodec.ofMember(
                    CitySyncPayload::write,
                    CitySyncPayload::decode
            );

    public CitySyncPayload {
        cities =
                List.copyOf(
                        cities
                );
    }

    /*
     * =========================================================
     * Encoding
     * =========================================================
     */

    private void write(
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(
                cities.size()
        );

        for (CityData city : cities) {
            buffer.writeUtf(
                    city.id()
            );

            buffer.writeUtf(
                    city.name()
            );

            buffer.writeVarInt(
                    city.regions().size()
            );

            for (RegionData region :
                    city.regions()) {

                buffer.writeIdentifier(
                        region.dimension()
                );

                /*
                 * chunk 좌표는 음수가 될 수 있으므로
                 * 일반 int로 기록한다.
                 */
                buffer.writeInt(
                        region.centerChunkX()
                );

                buffer.writeInt(
                        region.centerChunkZ()
                );

                buffer.writeVarInt(
                        region.widthChunks()
                );

                buffer.writeVarInt(
                        region.heightChunks()
                );
            }
        }
    }

    /*
     * =========================================================
     * Decoding
     * =========================================================
     */

    private static CitySyncPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        int cityCount =
                buffer.readVarInt();

        List<CityData> cities =
                new ArrayList<>(
                        cityCount
                );

        for (
                int cityIndex = 0;
                cityIndex < cityCount;
                cityIndex++
        ) {
            String cityId =
                    buffer.readUtf();

            String cityName =
                    buffer.readUtf();

            int regionCount =
                    buffer.readVarInt();

            List<RegionData> regions =
                    new ArrayList<>(
                            regionCount
                    );

            for (
                    int regionIndex = 0;
                    regionIndex < regionCount;
                    regionIndex++
            ) {
                Identifier dimension =
                        buffer.readIdentifier();

                int centerChunkX =
                        buffer.readInt();

                int centerChunkZ =
                        buffer.readInt();

                int widthChunks =
                        buffer.readVarInt();

                int heightChunks =
                        buffer.readVarInt();

                regions.add(
                        new RegionData(
                                dimension,
                                centerChunkX,
                                centerChunkZ,
                                widthChunks,
                                heightChunks
                        )
                );
            }

            cities.add(
                    new CityData(
                            cityId,
                            cityName,
                            regions
                    )
            );
        }

        return new CitySyncPayload(
                cities
        );
    }

    @Override
    public Type<CitySyncPayload> type() {
        return TYPE;
    }

    /*
     * =========================================================
     * Network DTO
     * =========================================================
     */

    public record CityData(
            String id,
            String name,
            List<RegionData> regions
    ) {

        public CityData {
            regions =
                    List.copyOf(
                            regions
                    );
        }

        public static CityData fromCity(
                City city
        ) {
            List<RegionData> regions =
                    new ArrayList<>();

            city.regions().forEach(
                    (dimension, region) ->
                            regions.add(
                                    new RegionData(
                                            dimension.identifier(),
                                            region.centerChunkX(),
                                            region.centerChunkZ(),
                                            region.widthChunks(),
                                            region.heightChunks()
                                    )
                            )
            );

            return new CityData(
                    city.id(),
                    city.name(),
                    regions
            );
        }
    }

    public record RegionData(
            Identifier dimension,
            int centerChunkX,
            int centerChunkZ,
            int widthChunks,
            int heightChunks
    ) {
    }
}