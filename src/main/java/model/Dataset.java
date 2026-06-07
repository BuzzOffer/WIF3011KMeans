package model;

import java.util.List;

public class Dataset {
    private final List<Point> allPoints;

    public Dataset(List<Point> allPoints) {
        this.allPoints = allPoints;
    }

    public List<Point> getAllPoints() {
        return allPoints;
    }

    public int getSize() {
        return allPoints.size();
    }
}
