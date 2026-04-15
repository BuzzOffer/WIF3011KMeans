package impl;

import api.KMeansAlgo;
import model.*;
import java.util.*;

public class SequentialKMeans implements KMeansAlgo {
    @Override
    public void cluster(Dataset data, int k, int maxIterations) {
        try {
            List<Point> points = data.getAllPoints();
            Random random = new Random();

            //Shuffle the points in the dataset so that it won't get a repeated point.
            Collections.shuffle(points, random);

            //Initializr the centroids randomly
            List<Cluster> clusters = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                Point randomPoint = points.get(i);
                Centroid centroid = new Centroid(i, randomPoint.getCoordinates().clone());
                clusters.add(new Cluster(centroid));
            }

            //Iteraten the given input
            for (int iter = 0; iter < maxIterations; iter++) {
                //Clear prevuious points that assigned to the cluster
                for (Cluster cluster : clusters) {
                    cluster.clearPoints();
                }

                //Assigned points to the nearest centroids
                for (Point point : points) {
                    Cluster nearest_cluster = getNearestCluster(point, clusters);
                    nearest_cluster.addPoint(point);
                }

                //Next we update the centroids after assigning the points
                for (Cluster cluster : clusters) {
                    updateCentroids(cluster);
                }
            }

        } catch (Exception error) {
            System.out.println("Fail to cluster the dataset due to: " + error.getMessage());
            error.printStackTrace();
        }
    }

    private Cluster getNearestCluster(Point point, List<Cluster> clusters) {
        Cluster nearestCluster = null;
        double minDistance = Double.MAX_VALUE;

        for (Cluster cluster : clusters) {
            double euclideanDistance = getEuclideanDistance(point, cluster.getCentroid());

            if (euclideanDistance < minDistance) {
                minDistance = euclideanDistance;
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

    public void updateCentroids(Cluster currentCluster) {
        List<Point> points = currentCluster.getPoints();

        if (points.isEmpty()) {
            return;
        }

        int currentDimension = points.getFirst().getDimension();
        double[] newCoordinates = new double[currentDimension];

        for (Point point : points) {
            double[] coordinates = point.getCoordinates();

            for (int i = 0; i < currentDimension; i++) {
                newCoordinates[i] += coordinates[i];
            }
        }

        for (int j = 0; j < currentDimension; j++) {
            newCoordinates[j] /= points.size();
        }

        currentCluster.getCentroid().updateCoordinates(newCoordinates);

    }
    
}
