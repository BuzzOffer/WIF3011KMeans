package impl;

import model.*;
import org.junit.jupiter.api.Test;
import utils.DatasetGenerator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that ConcurrentKMeans produces identical centroids regardless of
 * thread count — i.e., the concurrency design is free of race conditions.
 */
public class ConcurrentKMeansTest {

    private static final double DELTA = 1e-9;

    @Test
    void centroidsIdenticalAcrossThreadCounts() {
        int size = 5000;
        int dimension = 5;
        int k = 5;
        int maxIter = 40;
        int[] threadCounts = {1, 2, 4, 8};

        Dataset dataset = DatasetGenerator.generateDataset(size, dimension);

        // Collect centroid results for each thread count
        Map<Integer, List<Centroid>> results = new LinkedHashMap<>();
        for (int threads : threadCounts) {
            ConcurrentKMeans algo = new ConcurrentKMeans(threads);
            algo.cluster(dataset, k, maxIter);

            List<Centroid> centroids = new ArrayList<>();
            for (Cluster c : algo.getClusters()) {
                centroids.add(c.getCentroid());
            }
            centroids.sort(Comparator.comparingInt(Centroid::getId));
            results.put(threads, centroids);
        }

        // Compare all thread counts against threads=1 baseline
        List<Centroid> baseline = results.get(1);
        assertNotNull(baseline);

        for (int threads : new int[]{2, 4, 8}) {
            List<Centroid> other = results.get(threads);
            assertEquals(baseline.size(), other.size(),
                    "Thread count " + threads + " produced a different number of clusters");

            for (int i = 0; i < baseline.size(); i++) {
                double[] base = baseline.get(i).getCoordinates();
                double[] comp = other.get(i).getCoordinates();
                assertArrayEquals(base, comp, DELTA,
                        "Centroid " + i + " differs between threads=1 and threads=" + threads);
            }

        }
    }
}