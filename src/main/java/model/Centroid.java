package model;

public class Centroid {
    // Array to store the current coordinates of the centroid
    private double[] coordinates;
    private final int id;

    public Centroid(int id, double[] coordinates) {
        this.id = id;
        this.coordinates = coordinates;
    }

    public void updateCoordinates(double[] newCoordinates) {
        this.coordinates = newCoordinates;
    }

    public double[] getCoordinates() {
        return coordinates;
    }

    public int getId() {
        return id;
    }
}
