package model;

import java.util.ArrayList;
import java.util.List;

public class Cluster {
    // The center point representing this cluster
    private final Centroid centroid;
    // List of points currently assigned to this cluster
    private final List<Point> points;

    public Cluster(Centroid centroid) {
        this.centroid = centroid;
        this.points = new ArrayList<>();
    }

    public void addPoint(Point point) {
        points.add(point);
    }

    // Clears all assigned points before the next iteration
    public void clearPoints() {
        points.clear();
    }

    public List<Point> getPoints() {
        return points;
    }

    public Centroid getCentroid() {
        return centroid;
    }
}