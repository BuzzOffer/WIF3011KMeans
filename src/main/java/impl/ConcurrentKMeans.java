package impl;

import api.KMeansAlgo;
import model.Centroid;
import model.Cluster;
import model.Dataset;
import model.Point;

import java.util.ArrayList;
import java.util.List;

public class ConcurrentKMeans implements KMeansAlgo {

    private final int threadCount;

    public ConcurrentKMeans(int threadCount) {
        this.threadCount = threadCount;
    }

    @Override
    public void cluster(Dataset data, int k, int maxIteration) {
        List<Point> points = data.getAllPoints();

        if (points == null || points.isEmpty() || k <= 0) {
            return;
        }

        // Initialize clusters using the first k points as centroids
        List<Cluster> clusters = new ArrayList<>();

        for (int i = 0; i < k; i++) {
            Point point = points.get(i);
            Centroid centroid = new Centroid(i, point.getCoordinates().clone());
            clusters.add(new Cluster(centroid));
        }

        for (int iteration = 0; iteration < maxIteration; iteration++) {
            for (Cluster cluster : clusters) {
                cluster.clearPoints();
            }
            assignPointsConcurrently(points, clusters);

        }
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

    /*
     * My part:
     * Each thread calls this method to assign its points.
     */
    private void assignChunk(List<Point> points, List<Cluster> clusters, int start, int end) {
        for (int i = start; i < end; i++) {
            Point point = points.get(i);
            Cluster nearestCluster = getNearestCluster(point, clusters);

            // synchronized is needed because many threads may add to the same cluster
            synchronized (nearestCluster) {
                nearestCluster.addPoint(point);
            }
        }
    }

    /*
     * My part:
     * Find the closest cluster for one point.
     */
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
}