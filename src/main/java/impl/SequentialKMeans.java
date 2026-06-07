package impl;

import api.KMeansAlgo;
import model.*;
import java.util.*;

public class SequentialKMeans implements KMeansAlgo {

    // Convergence threshold
    private static final double EPSILON = 1e-6;

    // Results kept after cluster() for verification
    private List<Cluster> clusters;
    private int iterationsRun;

    @Override
    public void cluster(Dataset data, int k, int maxIterations) {
        this.clusters = null;
        this.iterationsRun = 0;

        try {
            List<Point> points = data.getAllPoints();
            Random random = new Random();

            //Shuffle the points in the dataset so that it won't get a repeated point.
            Collections.shuffle(points, random);

            //Initialize the centroids randomly
            List<Cluster> clusterList = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                Point randomPoint = points.get(i);
                Centroid centroid = new Centroid(i, randomPoint.getCoordinates().clone());
                clusterList.add(new Cluster(centroid));
            }

            int dim = points.get(0).getDimension();
            double[][] previousCentroids = null;

            int iterationsDone = 0;

            //Iterate the given input
            for (int iter = 0; iter < maxIterations; iter++) {
                //Clear previous points that assigned to the cluster
                for (Cluster cluster : clusterList) {
                    cluster.clearPoints();
                }

                //Assign points to the nearest centroids
                for (Point point : points) {
                    Cluster nearest_cluster = getNearestCluster(point, clusterList);
                    nearest_cluster.addPoint(point);
                }

                //Snapshot old centroid positions for convergence
                double[][] oldCentroids = new double[k][dim];
                for (int c = 0; c < k; c++) {
                    double[] coords = clusterList.get(c).getCentroid().getCoordinates();
                    System.arraycopy(coords, 0, oldCentroids[c], 0, dim);
                }

                //Next we update the centroids after assigning the points
                for (Cluster cluster : clusterList) {
                    updateCentroids(cluster);
                }

                iterationsDone = iter + 1;

                //Convergence check
                if (previousCentroids != null) {
                    double maxShift = 0;
                    for (int c = 0; c < k; c++) {
                        double[] cur = clusterList.get(c).getCentroid().getCoordinates();
                        double[] prev = previousCentroids[c];
                        double sum = 0;
                        for (int d = 0; d < dim; d++) {
                            double diff = cur[d] - prev[d];
                            sum += diff * diff;
                        }
                        double shift = Math.sqrt(sum);
                        if (shift > maxShift) {
                            maxShift = shift;
                        }
                    }
                    if (maxShift < EPSILON) {
                        break;
                    }
                }
                previousCentroids = oldCentroids;
            }

            this.iterationsRun = iterationsDone;
            this.clusters = clusterList;

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
