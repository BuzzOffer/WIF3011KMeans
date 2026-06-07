package impl;

import model.Cluster;
import model.Dataset;
import model.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentKMeansTest {

    private static final double TOLERANCE = 1e-9;

    // ---------- helpers ----------

    private static Dataset datasetOf(double[]... coordinates) {
        List<Point> points = new ArrayList<>();
        for (double[] coordinate : coordinates) {
            points.add(new Point(coordinate));
        }
        return new Dataset(points);
    }

    private static Dataset randomDataset(int size, int dimension, long seed) {
        Random random = new Random(seed);
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            double[] coordinates = new double[dimension];
            for (int d = 0; d < dimension; d++) {
                coordinates[d] = random.nextDouble();
            }
            points.add(new Point(coordinates));
        }
        return new Dataset(points);
    }

    private static double[][] centroidCoordinates(ConcurrentKMeans kMeans) {
        List<Cluster> clusters = kMeans.getClusters();
        double[][] coordinates = new double[clusters.size()][];
        for (int i = 0; i < clusters.size(); i++) {
            coordinates[i] = clusters.get(i).getCentroid().getCoordinates();
        }
        return coordinates;
    }

    /**
     * Minimal single-threaded reference K-Means used as a correctness oracle.
     * Mirrors ConcurrentKMeans' semantics exactly: first-k initialization,
     * Math.pow-based distance with strict < tie-breaking (ties go to the
     * lower cluster index), empty clusters keep their centroid, EPSILON 1e-6.
     */
    private static double[][] referenceKMeans(Dataset data, int k, int maxIteration) {
        List<Point> points = data.getAllPoints();
        int dimension = points.get(0).getDimension();

        double[][] centroids = new double[k][];
        for (int i = 0; i < k; i++) {
            centroids[i] = points.get(i).getCoordinates().clone();
        }

        int[] assignments = new int[points.size()];

        for (int iteration = 0; iteration < maxIteration; iteration++) {
            // Assignment phase
            for (int i = 0; i < points.size(); i++) {
                double[] p = points.get(i).getCoordinates();
                int best = 0;
                double bestDistance = Double.MAX_VALUE;

                for (int c = 0; c < k; c++) {
                    double sum = 0;
                    for (int d = 0; d < dimension; d++) {
                        sum += Math.pow(p[d] - centroids[c][d], 2);
                    }
                    double distance = Math.sqrt(sum);

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = c;
                    }
                }
                assignments[i] = best;
            }

            // Update phase
            double[][] sums = new double[k][dimension];
            int[] counts = new int[k];

            for (int i = 0; i < points.size(); i++) {
                double[] p = points.get(i).getCoordinates();
                for (int d = 0; d < dimension; d++) {
                    sums[assignments[i]][d] += p[d];
                }
                counts[assignments[i]]++;
            }

            double maxShift = 0;
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) {
                    continue; // empty cluster keeps its centroid
                }

                double shiftSquared = 0;
                for (int d = 0; d < dimension; d++) {
                    double mean = sums[c][d] / counts[c];
                    double diff = mean - centroids[c][d];
                    shiftSquared += diff * diff;
                    centroids[c][d] = mean;
                }
                maxShift = Math.max(maxShift, Math.sqrt(shiftSquared));
            }

            if (maxShift < 1e-6) {
                break;
            }
        }
        return centroids;
    }

    // ---------- tests ----------

    @Test
    void findsTheTwoObviousClusters() {
        // Group A around (0,0), group B around (10,10).
        // Initialization takes the FIRST k points as centroids, so the data
        // is ordered to put one point of each group first.
        Dataset data = datasetOf(
                new double[]{0.0, 0.0},     // group A (initial centroid 0)
                new double[]{10.0, 10.0},   // group B (initial centroid 1)
                new double[]{0.2, 0.0},     // group A
                new double[]{0.0, 0.2},     // group A
                new double[]{10.2, 10.0},   // group B
                new double[]{10.0, 10.2}    // group B
        );

        ConcurrentKMeans kMeans = new ConcurrentKMeans(4);
        kMeans.cluster(data, 2, 100);

        double[][] centroids = centroidCoordinates(kMeans);

        // Expected: mean of each group.
        // Group A mean = ((0 + 0.2 + 0)/3, (0 + 0 + 0.2)/3) = (0.2/3, 0.2/3)
        // Group B mean = (10 + 0.2/3, 10 + 0.2/3)
        // centroids[i] corresponds to cluster ID i (deterministic first-k init,
        // list returned in insertion order) — position-based asserts are safe.
        assertArrayEquals(new double[]{0.2 / 3, 0.2 / 3}, centroids[0], TOLERANCE);
        assertArrayEquals(new double[]{10 + 0.2 / 3, 10 + 0.2 / 3}, centroids[1], TOLERANCE);
    }

    @Test
    void matchesSequentialReferenceImplementation() {
        Dataset data = randomDataset(1000, 3, 7);

        double[][] expected = referenceKMeans(data, 3, 100);

        ConcurrentKMeans kMeans = new ConcurrentKMeans(4);
        kMeans.cluster(data, 3, 100);
        double[][] actual = centroidCoordinates(kMeans);

        for (int c = 0; c < expected.length; c++) {
            assertArrayEquals(expected[c], actual[c], TOLERANCE, "centroid " + c);
        }
    }

    @Test
    void differentThreadCountsProduceTheSameResult() {
        Dataset data = randomDataset(1000, 3, 42);

        double[][] reference = null;

        for (int threadCount : new int[]{1, 2, 4, 8}) {
            ConcurrentKMeans kMeans = new ConcurrentKMeans(threadCount);
            kMeans.cluster(data, 3, 100);
            double[][] centroids = centroidCoordinates(kMeans);

            if (reference == null) {
                reference = centroids;
            } else {
                for (int c = 0; c < reference.length; c++) {
                    assertArrayEquals(reference[c], centroids[c], TOLERANCE,
                            "centroid " + c + " differs for threadCount=" + threadCount);
                }
            }
        }
    }

    @Test
    void stopsEarlyOnceCentroidsStopMoving() {
        // Same well-separated dataset as findsTheTwoObviousClusters:
        // centroids reach the group means in iteration 1 and stop moving,
        // so convergence triggers on iteration 2.
        Dataset data = datasetOf(
                new double[]{0.0, 0.0},
                new double[]{10.0, 10.0},
                new double[]{0.2, 0.0},
                new double[]{0.0, 0.2},
                new double[]{10.2, 10.0},
                new double[]{10.0, 10.2}
        );

        ConcurrentKMeans kMeans = new ConcurrentKMeans(2);
        kMeans.cluster(data, 2, 1000);

        assertTrue(kMeans.getIterationsRun() < 1000,
                "expected early convergence, but ran " + kMeans.getIterationsRun() + " iterations");
        // Lower bound: iteration 1 always moves the centroids off the initial
        // points, so a correct implementation can never report fewer than 2.
        assertTrue(kMeans.getIterationsRun() >= 2,
                "iterationsRun suspiciously low: " + kMeans.getIterationsRun());
    }

    @Test
    void emptyClusterKeepsItsPreviousCentroidWithoutNaN() {
        // Both initial centroids coincide at (1,1) because points[0] == points[1].
        // In iteration 1 every point ties for both clusters; strict < sends them
        // all to cluster 0, leaving cluster 1 EMPTY — exercising the NaN guard.
        // From iteration 2 onward centroid 0 has moved away, the (1,1) points
        // migrate to cluster 1, and the algorithm converges with centroid 1
        // back at (1,1) — so the final assertion still holds.
        Dataset data = datasetOf(
                new double[]{1.0, 1.0},
                new double[]{1.0, 1.0},
                new double[]{2.0, 2.0},
                new double[]{3.0, 3.0}
        );

        ConcurrentKMeans kMeans = new ConcurrentKMeans(2);
        kMeans.cluster(data, 2, 100);

        double[][] centroids = centroidCoordinates(kMeans);

        for (double[] centroid : centroids) {
            for (double value : centroid) {
                assertFalse(Double.isNaN(value), "centroid coordinate is NaN");
            }
        }

        // The empty cluster's centroid must stay at its initial position
        assertArrayEquals(new double[]{1.0, 1.0}, centroids[1], TOLERANCE);
    }

    @Test
    void handlesEmptyDatasetWithoutCrashing() {
        ConcurrentKMeans kMeans = new ConcurrentKMeans(4);

        kMeans.cluster(new Dataset(new ArrayList<>()), 3, 100);

        assertNull(kMeans.getClusters());
    }

    @Test
    void handlesNonPositiveKWithoutCrashing() {
        ConcurrentKMeans kMeans = new ConcurrentKMeans(4);

        kMeans.cluster(randomDataset(10, 2, 1), 0, 100);
        assertNull(kMeans.getClusters());

        kMeans.cluster(randomDataset(10, 2, 1), -1, 100);
        assertNull(kMeans.getClusters());
    }

    @Test
    void worksWhenThreadCountExceedsPointCount() {
        // 4 points, 16 threads: chunkSize is 0, so the last thread does all
        // the work (degenerate but must still be correct).
        Dataset data = datasetOf(
                new double[]{0.0, 0.0},
                new double[]{10.0, 10.0},
                new double[]{0.2, 0.0},
                new double[]{10.2, 10.0}
        );

        ConcurrentKMeans kMeans = new ConcurrentKMeans(16);
        kMeans.cluster(data, 2, 100);

        double[][] centroids = centroidCoordinates(kMeans);

        assertArrayEquals(new double[]{0.1, 0.0}, centroids[0], TOLERANCE);
        assertArrayEquals(new double[]{10.1, 10.0}, centroids[1], TOLERANCE);
    }
}
