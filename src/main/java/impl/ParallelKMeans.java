package impl;

import api.KMeansAlgo;
import model.Centroid;
import model.Cluster;
import model.Dataset;
import model.Point;

import java.util.*;
import java.util.concurrent.*;

public class ParallelKMeans implements KMeansAlgo {

    // Convergence threshold
    private static final double EPSILON = 1e-6;

    private final int numOfThreads;

    // Results kept after cluster() for verification
    private List<Cluster> clusters;
    private int iterationsRun;

    public ParallelKMeans(int numOfThreads) {
        this.numOfThreads = numOfThreads;
    }

    @Override
    public void cluster(Dataset data, int k, int maxIteration) {
        this.clusters = null;
        this.iterationsRun = 0;

        List<Point> points = data.getAllPoints();
        if (points == null || points.isEmpty() || k <= 0)
            return;

        Random rand = new Random();
        Collections.shuffle(points, rand);

        //Initialize centroids
        List<Cluster> clusterList = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            Point randomPoint = points.get(i);
            Centroid newCentroid = new Centroid(i, randomPoint.getCoordinates().clone());
            clusterList.add(new Cluster(newCentroid));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numOfThreads);

        int chunkSize = points.size() / numOfThreads;
        List<List<Point>> partitions = new ArrayList<>();

        for (int i = 0; i < numOfThreads; i++) {
            int start = i * chunkSize;
            int end = (i == numOfThreads - 1) ? points.size() : (i + 1) * chunkSize;
            List<Point> chunk = points.subList(start, end);
            partitions.add(chunk);
        }

        int iterationsDone = 0;

        try {
            for (int iter = 0; iter < maxIteration; iter++) {
                for (Cluster cluster : clusterList) {
                    cluster.clearPoints();
                }

                // --- Assignment phase (parallel) ---
                List<Future<?>> assignFutures = new ArrayList<>();
                for (int t = 0; t < numOfThreads; t++) {
                    int chunkIndex = t;
                    assignFutures.add(executor.submit(() -> assignChunk(partitions.get(chunkIndex), clusterList)));
                }
                for (Future<?> future : assignFutures) {
                    future.get();
                }

                // Snapshot centroids BEFORE update for convergence check
                int dim = points.getFirst().getDimension();
                double[][] oldCentroids = new double[k][dim];
                for (int c = 0; c < k; c++) {
                    double[] coords = clusterList.get(c).getCentroid().getCoordinates();
                    System.arraycopy(coords, 0, oldCentroids[c], 0, dim);
                }

                // --- Update phase (parallel, MUST await all futures) ---
                List<Future<?>> updateFutures = new ArrayList<>();
                for (Cluster cluster : clusterList) {
                    updateFutures.add(executor.submit(() -> updateCentroids(cluster)));
                }
                for (Future<?> future : updateFutures) {
                    future.get();
                }

                iterationsDone = iter + 1;

                //convergence check
                double maxShift = 0;
                for (int c = 0; c < k; c++) {
                    //get current and previous centroid coords
                    double[] current = clusterList.get(c).getCentroid().getCoordinates();
                    double[] prev = oldCentroids[c];
                    //calculate Euclidean distance between current and previous
                    double sum = 0;
                    for (int d = 0; d < dim; d++) {
                        double diff = current[d] - prev[d];
                        sum += diff * diff;
                    }
                    double shift = Math.sqrt(sum);
                    //get max shift
                    if (shift > maxShift) {
                        maxShift = shift;
                    }
                }
                //if the max shift is less than 1e-6, then the algorithm has converged
                if (maxShift < EPSILON) {
                    break;
                }
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Parallel KMeans failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel KMeans interrupted", e);
        } finally {
            executor.shutdown();
        }

        this.iterationsRun = iterationsDone;
        this.clusters = clusterList;
    }

    public Cluster getNearestCluster(Point point, List<Cluster> clusters) {
        Cluster nearestCluster = null;
        double minDistance = Double.MAX_VALUE;

        for (Cluster cluster : clusters) {
            double euclidDist = getEuclideanDistance(point, cluster.getCentroid());
            if (euclidDist < minDistance) {
                minDistance = euclidDist;
                nearestCluster = cluster;
            }
        }
        return nearestCluster;
    }

    public double getEuclideanDistance(Point currentPoint, Centroid currentCentroid) {
        double[] currentPointCoordinates = currentPoint.getCoordinates();
        double[] currentCentroidCoordinates = currentCentroid.getCoordinates();

        double sum = 0;
        for (int i = 0; i < currentPointCoordinates.length; i++) {
            sum += Math.pow(currentPointCoordinates[i] - currentCentroidCoordinates[i], 2);
        }

        return Math.sqrt(sum);
    }

    public void assignChunk(List<Point> chunk, List<Cluster> clusters) {
        for (Point point : chunk) {
            Cluster nearestCluster = getNearestCluster(point, clusters);
            synchronized (nearestCluster) {
                nearestCluster.addPoint(point);
            }
        }
    }

    public void updateCentroids(Cluster cluster) {
        List<Point> points = cluster.getPoints();
        if (points.isEmpty()) return;

        int dimensions = points.getFirst().getDimension();
        double[] newCoords = new double[dimensions];
        //Calculate mean of all the points
        for (Point point : points) {
            double[] pointCoords = point.getCoordinates();
            for (int i = 0; i < dimensions; i++) {
                newCoords[i] += pointCoords[i];
            }
        }
        for (int i = 0; i < dimensions; i++) {
            newCoords[i] /= points.size();
        }

        cluster.getCentroid().updateCoordinates(newCoords);
    }

}