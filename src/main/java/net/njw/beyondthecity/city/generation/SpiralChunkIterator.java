package net.njw.beyondthecity.city.generation;

import net.minecraft.world.level.ChunkPos;
import net.njw.beyondthecity.city.CityRegion;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class SpiralChunkIterator
        implements Iterator<ChunkPos> {

    private final CityRegion region;

    private int x;
    private int z;

    private int dx = 1;
    private int dz = 0;

    private int segmentLength = 1;
    private int segmentProgress = 0;
    private int segmentsCompleted = 0;

    private long returnedChunks = 0;
    private final long totalChunks;

    public SpiralChunkIterator(
            CityRegion region
    ) {
        this.region = region;

        this.x = region.centerChunkX();
        this.z = region.centerChunkZ();

        this.totalChunks =
                (long) region.widthChunks()
                        * region.heightChunks();
    }

    @Override
    public boolean hasNext() {
        return returnedChunks < totalChunks;
    }

    @Override
    public ChunkPos next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        while (true) {
            int currentX = x;
            int currentZ = z;

            advance();

            if (!region.containsChunk(
                    currentX,
                    currentZ
            )) {
                continue;
            }

            returnedChunks++;

            return new ChunkPos(
                    currentX,
                    currentZ
            );
        }
    }

    private void advance() {
        x += dx;
        z += dz;

        segmentProgress++;

        if (segmentProgress < segmentLength) {
            return;
        }

        segmentProgress = 0;

        rotateClockwise();

        segmentsCompleted++;

        /*
         * Spiral에서는 같은 길이의 segment를
         * 두 번 이동한 뒤 길이가 1 증가한다.
         *
         * 1, 1, 2, 2, 3, 3, ...
         */
        if (segmentsCompleted % 2 == 0) {
            segmentLength++;
        }
    }

    private void rotateClockwise() {
        int oldDx = dx;

        dx = -dz;
        dz = oldDx;
    }

    public void skip(long count) {
        for (long i = 0; i < count && hasNext(); i++) {
            next();
        }
    }
}