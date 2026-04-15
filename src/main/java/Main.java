import api.KMeansAlgo;
import impl.SequentialKMeans;
import model.Dataset;
import model.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class Main {
    public static void main(String[] args) {
        //Generate dataset with 10000 points
        Dataset dataset1 = DatasetGenerator.generateDataset(10000, 2);
        //generate dataset with 100000 poitns
        Dataset dataset2 = DatasetGenerator.generateDataset(100000, 2);
        //Generate dataset with 1000000 points
        Dataset dataset3 = DatasetGenerator.generateDataset(1000000, 2);

        //Initialise the number of clusters (can change if you want)
        int initial_cluster = 3;
        //Initialise the number of iterations (pls change if you want)
        int maxIterations = 100;


        //For sequential K-Mean
        SequentialKMeans kmeans = new SequentialKMeans();

        //Run k-mean with 10000 dataset
        runKMeanTest("Sequential", kmeans, dataset1, initial_cluster, maxIterations);
        //Run k-mean with 100000 dataset
        runKMeanTest("Sequential", kmeans, dataset2, initial_cluster, maxIterations);
        //Run k-mean with 100000 dataset
        runKMeanTest("Sequential", kmeans, dataset3, initial_cluster, maxIterations);

    }

    private static void runKMeanTest(String name, KMeansAlgo kMeanType, Dataset dataset, int k, int maxIterations) {
        long startTimeCount = System.nanoTime();

        kMeanType.cluster(dataset, k, maxIterations);
        long endTimeCount = System.nanoTime();
        long totalTimeUsage = (endTimeCount - startTimeCount) / 1000000;

        System.out.println("The total time used for " + name + " K-Mean with " + dataset.getSize() + " of dataset is: " + totalTimeUsage + " ms.");
    }
}

