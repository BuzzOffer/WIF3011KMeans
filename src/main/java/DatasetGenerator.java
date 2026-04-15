import model.Dataset;
import model.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DatasetGenerator {

    public static Dataset generateDataset(int size, int dimension) {
        List<Point> points = new ArrayList<Point>();

        Random rand = new Random();

        for (int i = 0; i < size; i++) {
            double[] currentCoordinates = new double[dimension];

            for (int j = 0; j < dimension; j++) {
                currentCoordinates[j] = rand.nextDouble();
            }

            points.add(new Point(currentCoordinates));
        }
        return new Dataset(points);
    }
}
