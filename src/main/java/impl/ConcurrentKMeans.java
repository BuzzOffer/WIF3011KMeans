package impl;

import api.KMeansAlgo;
import model.Centroid;
import model.Cluster;
import model.Dataset;
import model.Point;

import java.util.ArrayList;
import java.util.List;

public class ConcurrentKMeans implements KMeansAlgo {

    // Convergence threshold: stop when no centroid moves further than this
    private static final double EPSILON = 1e-6;

    private final int threadCount;

    // Results kept after cluster() so correctness can be verified (tests/demo)
    private List<Cluster> clusters;
    private int iterationsRun;

    // assignments[i] = index (0..k-1) of the cluster point i belongs to.
    // Written by the assignment phase, read by the update phase.
    private int[] assignments;

    public ConcurrentKMeans(int threadCount) {
        this.threadCount = threadCount;
    }

    @Override
    public void cluster(Dataset data, int k, int maxIteration) {
        // Reset results first so a rejected call is observably different
        // from a previous successful run (getters return null/0, not stale data)
        this.clusters = null;
        this.iterationsRun = 0;

        List<Point> points = data.getAllPoints();

        if (points == null || points.isEmpty() || k <= 0) {
            return;
        }

        assignments = new int[points.size()];

        // Initialize clusters using the first k points as centroids.
        // No shuffle on purpose: deterministic initialization (unlike the
        // sequential/parallel versions) makes runs reproducible and testable.
        List<Cluster> clusterList = new ArrayList<>();

        for (int i = 0; i < k; i++) {
            Point point = points.get(i);
            Centroid centroid = new Centroid(i, point.getCoordinates().clone());
            clusterList.add(new Cluster(centroid));
        }

        int iterationsDone = 0;

        for (int iteration = 0; iteration < maxIteration; iteration++) {
            for (Cluster cluster : clusterList) {
                cluster.clearPoints();
            }
            assignPointsConcurrently(points, clusterList);

            double maxShift = updateCentroidsConcurrently(points, clusterList);
            iterationsDone = iteration + 1;

            // Converged: no centroid moved further than EPSILON
            if (maxShift < EPSILON) {
                break;
            }
        }

        this.iterationsRun = iterationsDone;
        this.clusters = clusterList;
    }

    private void assignPointsConcurrently(List<Point> points, List<Cluster> clusters) {
        Thread[] threads = new Thread[threadCount];

        int chunkSize = points.size() / threadCount;

        for (int i = 0; i < threadCount; i++) {
            int start = i * chunkSize;

            int end;
            if (i == threadCount - 1) {
                end = points.size();
            } else {
                end = start + chunkSize;
            }

            threads[i] = new Thread(() -> {
                assignChunk(points, clusters, start, end);
            });

            threads[i].start();
        }

        for (int i = 0; i < threadCount; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                System.out.println("Thread interrupted");
            }
        }
    }

    private void assignChunk(List<Point> points, List<Cluster> clusters, int start, int end) {
        for (int i = start; i < end; i++) {
            Point point = points.get(i);
            Cluster nearestCluster = getNearestCluster(point, clusters);

            // Record the assignment for the update phase. No lock needed:
            // each thread writes only its own disjoint range of indices.
            // Invariant: centroid id == cluster's index in the list (both are
            // assigned 0..k-1 at initialization) — the merge step relies on it.
            assignments[i] = nearestCluster.getCentroid().getId();

            // synchronized is needed because many threads may add to the same cluster
            synchronized (nearestCluster) {
                nearestCluster.addPoint(point);
            }
        }
    }

    /**
     * Updates each centroid to the mean of its assigned points using a
     * partial-sum reduction across manually created threads.
     *
     * Each thread accumulates per-cluster coordinate sums and counts for its
     * own chunk of points into THREAD-LOCAL arrays, so there are no shared
     * writes and therefore no locks in the hot loop. After join(), the main
     * thread merges the partial results and writes the new centroid
     * coordinates (only k * dimension work, done single-threaded).
     *
     * Memory visibility without volatile/locks (JMM happens-before chain):
     * assignments[] is written by the assignment-phase workers, whose join()
     * in the main thread happens-before this method's Thread.start() calls,
     * so update workers see all assignments. Likewise, each update worker's
     * partial sums are visible to the main thread once its join() returns
     * normally — which joinAll() guarantees by re-joining until every worker
     * has actually terminated.
     *
     * @return the maximum Euclidean distance any centroid moved, used by the
     *         convergence check
     */
    private double updateCentroidsConcurrently(List<Point> points, List<Cluster> clusters) {
        int k = clusters.size();
        int dimension = points.get(0).getDimension();

        // partialSums[t][c] = sum of coordinates of cluster c's points seen by thread t
        double[][][] partialSums = new double[threadCount][k][dimension];
        int[][] partialCounts = new int[threadCount][k];

        Thread[] threads = new Thread[threadCount];
        int chunkSize = points.size() / threadCount;

        for (int t = 0; t < threadCount; t++) {
            int start = t * chunkSize;

            int end;
            if (t == threadCount - 1) {
                end = points.size();
            } else {
                end = start + chunkSize;
            }

            double[][] localSums = partialSums[t];
            int[] localCounts = partialCounts[t];

            threads[t] = new Thread(() -> {
                for (int i = start; i < end; i++) {
                    int clusterIndex = assignments[i];
                    double[] coordinates = points.get(i).getCoordinates();

                    for (int d = 0; d < dimension; d++) {
                        localSums[clusterIndex][d] += coordinates[d];
                    }
                    localCounts[clusterIndex]++;
                }
            });

            threads[t].start();
        }

        joinAll(threads);

        return mergePartialsAndMoveCentroids(clusters, partialSums, partialCounts, k, dimension);
    }

    /**
     * Merges the thread-local partial sums/counts and moves each centroid to
     * its new mean (single-threaded; only k * dimension work).
     *
     * @return the maximum Euclidean distance any centroid moved
     */
    private double mergePartialsAndMoveCentroids(List<Cluster> clusters, double[][][] partialSums,
                                                 int[][] partialCounts, int k, int dimension) {
        double maxShift = 0;

        for (int c = 0; c < k; c++) {
            double[] newCoordinates = new double[dimension];
            int count = 0;

            for (int t = 0; t < threadCount; t++) {
                count += partialCounts[t][c];

                for (int d = 0; d < dimension; d++) {
                    newCoordinates[d] += partialSums[t][c][d];
                }
            }

            if (count == 0) {
                // Empty cluster: keep the previous centroid (avoids 0/0 = NaN;
                // same behaviour as the sequential implementation)
                continue;
            }

            for (int d = 0; d < dimension; d++) {
                newCoordinates[d] /= count;
            }

            Centroid centroid = clusters.get(c).getCentroid();
            double shift = getShiftDistance(centroid.getCoordinates(), newCoordinates);

            if (shift > maxShift) {
                maxShift = shift;
            }

            centroid.updateCoordinates(newCoordinates);
        }

        return maxShift;
    }

    /**
     * Joins every thread, retrying if interrupted. Waiting for ALL workers to
     * terminate is required for correctness: returning early would let the
     * main thread merge partial sums while workers are still writing them
     * (no happens-before edge -> silent data corruption). The interrupt flag
     * is restored once at the end so callers can still observe cancellation.
     */
    private void joinAll(Thread[] threads) {
        boolean interrupted = false;

        for (Thread thread : threads) {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true; // restore after ALL threads have terminated
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // Euclidean distance between a centroid's old and new coordinates
    private double getShiftDistance(double[] oldCoordinates, double[] newCoordinates) {
        double sum = 0;

        for (int i = 0; i < oldCoordinates.length; i++) {
            double diff = oldCoordinates[i] - newCoordinates[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }

    private Cluster getNearestCluster(Point point, List<Cluster> clusters) {
        Cluster nearestCluster = null;
        double minDistance = Double.MAX_VALUE;

        for (Cluster cluster : clusters) {
            double distance = getEuclideanDistance(point, cluster.getCentroid());

            if (distance < minDistance) {
                minDistance = distance;
                nearestCluster = cluster;
            }
        }

        return nearestCluster;
    }

    private double getEuclideanDistance(Point point, Centroid centroid) {
        double[] pointCoordinates = point.getCoordinates();
        double[] centroidCoordinates = centroid.getCoordinates();

        double sum = 0;

        for (int i = 0; i < pointCoordinates.length; i++) {
            sum += Math.pow(pointCoordinates[i] - centroidCoordinates[i], 2);
        }

        return Math.sqrt(sum);
    }

    public List<Cluster> getClusters() {
        return clusters;
    }

    public int getIterationsRun() {
        return iterationsRun;
    }
}