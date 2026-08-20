package net.njw.beyondthecity.city;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 하나의 도시.
 *
 * 하나의 도시는 Overworld, Nether 등
 * 여러 차원에 걸친 영역으로 구성될 수 있다.
 */
public final class City {

    private final String id;
    private final String name;

    private final Map<ResourceKey<Level>, CityRegion> regions;

    public City(
            String id,
            String name,
            Map<ResourceKey<Level>, CityRegion> regions
    ) {
        this.id = id;
        this.name = name;
        this.regions = Collections.unmodifiableMap(
                new HashMap<>(regions)
        );
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Map<ResourceKey<Level>, CityRegion> regions() {
        return regions;
    }

    /**
     * 해당 차원에서 이 도시가 차지하는 영역을 반환한다.
     */
    public Optional<CityRegion> getRegion(ResourceKey<Level> dimension) {
        return Optional.ofNullable(regions.get(dimension));
    }

    /**
     * 특정 위치가 이 도시 안에 존재하는지 확인한다.
     */
    public boolean contains(
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ
    ) {
        CityRegion region = regions.get(dimension);

        if (region == null) {
            return false;
        }

        return region.containsBlock(blockX, blockZ);
    }

    /**
     * 이 도시가 특정 차원에 영역을 가지고 있는지 확인한다.
     */
    public boolean hasDimension(ResourceKey<Level> dimension) {
        return regions.containsKey(dimension);
    }
}