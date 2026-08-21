package net.njw.beyondthecity.city;

import net.minecraft.world.level.Level;

import java.util.Map;

public final class CityRegistry {

    public static final String STARTING_CITY_ID =
            "starting_city";

    public static final City STARTING_CITY_TEMPLATE =
            new City(
                    STARTING_CITY_ID,
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

    private CityRegistry() {
    }
}