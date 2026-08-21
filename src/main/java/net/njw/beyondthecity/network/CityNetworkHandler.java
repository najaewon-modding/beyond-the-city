package net.njw.beyondthecity.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CityNetworkHandler {

    /*
     * 이 payload의 protocol version.
     *
     * 저장 데이터 버전과는 관계없고
     * 네트워크 protocol 버전이다.
     */
    private static final String NETWORK_VERSION =
            "1";

    private CityNetworkHandler() {
    }

    public static void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        event.registrar(
                        NETWORK_VERSION
                )
                .playToClient(
                        CitySyncPayload.TYPE,
                        CitySyncPayload.STREAM_CODEC
                );
    }
}