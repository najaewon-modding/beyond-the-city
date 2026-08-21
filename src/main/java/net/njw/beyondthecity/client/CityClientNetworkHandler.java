package net.njw.beyondthecity.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.njw.beyondthecity.BeyondtheCity;
import net.njw.beyondthecity.network.CitySyncPayload;

@EventBusSubscriber(
        modid = BeyondtheCity.MODID,
        value = Dist.CLIENT
)
public final class CityClientNetworkHandler {

    private CityClientNetworkHandler() {
    }

    @SubscribeEvent
    public static void onRegisterClientPayloadHandlers(
            RegisterClientPayloadHandlersEvent event
    ) {
        event.register(
                CitySyncPayload.TYPE,
                (payload, context) ->
                        ClientCityManager.replaceCities(
                                payload.cities()
                        )
        );
    }
}