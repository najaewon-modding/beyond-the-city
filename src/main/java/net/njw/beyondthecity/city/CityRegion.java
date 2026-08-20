package net.njw.beyondthecity.city;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 하나의 차원 안에서 도시가 차지하는 직사각형 영역.
 * <p>
 * 좌표와 크기는 청크 단위로 관리한다.
 */
public record CityRegion(
        ResourceKey<Level> dimension,
        int centerChunkX,
        int centerChunkZ,
        int widthChunks,
        int heightChunks
) {

    public CityRegion {
        if (widthChunks <= 0) {
            throw new IllegalArgumentException("widthChunks must be greater than 0.");
        }

        if (heightChunks <= 0) {
            throw new IllegalArgumentException("heightChunks must be greater than 0.");
        }
    }

    public int minChunkX() {
        return centerChunkX - widthChunks / 2;
    }

    public int maxChunkX() {
        return minChunkX() + widthChunks - 1;
    }

    public int minChunkZ() {
        return centerChunkZ - heightChunks / 2;
    }

    public int maxChunkZ() {
        return minChunkZ() + heightChunks - 1;
    }

    /**
     * 특정 청크가 이 도시 영역 안에 있는지 확인한다.
     */
    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= minChunkX()
                && chunkX <= maxChunkX()
                && chunkZ >= minChunkZ()
                && chunkZ <= maxChunkZ();
    }

    public boolean containsChunk(ChunkPos chunkPos) {
        return containsChunk(chunkPos.x(), chunkPos.z());
    }

    /**
     * 블록 좌표를 받아 도시 영역 안에 있는지 확인한다.
     */
    public boolean containsBlock(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        return containsChunk(chunkX, chunkZ);
    }

    public int minBlockX() {
        return minChunkX() * 16;
    }

    public int maxBlockX() {
        return (maxChunkX() + 1) * 16 - 1;
    }

    public int minBlockZ() {
        return minChunkZ() * 16;
    }

    public int maxBlockZ() {
        return (maxChunkZ() + 1) * 16 - 1;
    }
}