package net.njw.beyondthecity.city;

import net.minecraft.world.level.Level;

import java.util.Map;

public final class CityRegistry {

    private CityRegistry() {
    }

    public static final City STARTING_CITY = new City(
            "starting_city",
            "Starting City",
            Map.of(
                    Level.OVERWORLD,
                    new CityRegion(
                            Level.OVERWORLD,
                            0,
                            0,
                            256,
                            256
                    ),

                    Level.NETHER,
                    new CityRegion(
                            Level.NETHER,
                            0,
                            0,
                            32,
                            32
                    )
            )
    );
}