package net.njw.beyondthecity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.njw.beyondthecity.city.CityBoundaryHandler;

@Mod(BeyondtheCity.MODID)
public class BeyondtheCity {

    public static final String MODID = "njw_beyond_the_city";

    public BeyondtheCity(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(CityBoundaryHandler.class);
    }
}