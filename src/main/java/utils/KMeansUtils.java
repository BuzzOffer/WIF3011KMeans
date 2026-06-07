package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import model.Centroid;
import model.Dataset;
import model.Point;

public class KMeansUtils {
    public static List<Centroid> initializeCentroids(Dataset data, int k) {
        List<Point> allPoints = new ArrayList<>(data.getAllPoints());
        // Can change the seed here
        Collections.shuffle(allPoints, new Random(42)); 
        
        List<Centroid> centroids = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            // Create a new centroid using the coordinates of a randomly selected point
            centroids.add(new Centroid(i, allPoints.get(i).getCoordinates().clone()));
        }
        return centroids;
    }

    // Calculate Euclidean distance between a point and a centroid
    public static double calculateDistance(Point p, Centroid c) {
        double sum = 0;
        double[] pCoords = p.getCoordinates();
        double[] cCoords = c.getCoordinates();
        for (int i = 0; i < pCoords.length; i++) {
            sum += Math.pow(pCoords[i] - cCoords[i], 2);
        }
        return Math.sqrt(sum);
    }
}