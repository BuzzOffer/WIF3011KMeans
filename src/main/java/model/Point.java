package model;

public class Point {
    // Array to store coordinates in multiple dimensions
    private final double[] coordinates;

    public Point(double[] coordinates) {
        this.coordinates = coordinates;
    }

    public double[] getCoordinates() {
        return coordinates;
    }

    public int getDimension() {
        return coordinates.length;
    }
}
