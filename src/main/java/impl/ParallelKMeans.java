package impl;

import api.KMeansAlgo;
import model.Centroid;
import model.Cluster;
import model.Dataset;
import model.Point;

import java.util.*;
import java.util.concurrent.*;

public class ParallelKMeans implements KMeansAlgo {
    private final int numOfThreads;
    private final long randomSeed;

    public ParallelKMeans(int numOfThreads, long randomSeed) {
        this.numOfThreads = numOfThreads;
        this.randomSeed = randomSeed;
    }

    @Override
    public void cluster(Dataset data, int k, int maxIteration) {
        List<Point> points = data.getAllPoints();
        if (points == null || points.isEmpty() || k <= 0)
            return;

        Random rand = new Random();

        Collections.shuffle(points, rand);

        //Initialize centroids
        List<Cluster> clusters = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            Point randomPoint = points.get(i);
            Centroid newCentroid = new Centroid(i, randomPoint.getCoordinates().clone());
            clusters.add(new Cluster(newCentroid));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numOfThreads);

        int chunkSize = points.size() / numOfThreads;
        List<List<Point>> partitions = new ArrayList<>();

        for (int i = 0; i < numOfThreads; i++) {
            int start = i * chunkSize; //Starting index
            int end = (i == numOfThreads - 1) ? points.size() : (i + 1) * chunkSize; //get last points in the last chunk
            List<Point> chunk = points.subList(start, end);
            partitions.add(chunk);
        }

        try {
            for (int iter = 0; iter < maxIteration; iter++) {
                for (Cluster cluster : clusters) {
                    cluster.clearPoints();
                }

                List<Future<?>> futures = new ArrayList<>();

                for (int t = 0; t < numOfThreads; t++) {
                    int chunkIndex = t;
                    futures.add(executor.submit(() -> {
                        assignChunk(partitions.get(chunkIndex), clusters);
                    }));
                }

                for (Future<?> future : futures) {
                    future.get();
                }

                for (Cluster cluster : clusters) {
                    executor.submit(() -> {
                        updateCentroids(cluster);
                    });
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
            nearestCluster.addPoint(point);
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
