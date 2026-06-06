import api.KMeansAlgo;
import impl.ConcurrentKMeans;
import impl.ParallelKMeans;
import impl.SequentialKMeans;
import model.Dataset;

public class Main {
    //Generate the dataset with different size
    private static final int[] DATASET_SIZES = {10000, 100000, 1000000};
    //Vary the K-mean with different dimension (pls change if you want)
    private static final int[] DIMENSIONS = {2, 10, 50};
    //Use multiple number of clusters (can change if you want)
    private static final int[] K_VALUES = {3, 5, 10};
    //Thread pool sizes to simulate
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8};
    //Fixef the num of interations
    private static final int MAX_ITERATIONS = 100;

    public static void main(String[] args) {

        for (int size : DATASET_SIZES) {
            for (int dimension : DIMENSIONS) {
                for (int k_value : K_VALUES) {
                    System.out.println("##############################################################################################");
                    System.out.println("Dataset with size of " + size + " | Dimensions of: " + dimension + " | K value of: " + k_value);
                    System.err.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    Dataset dataset = DatasetGenerator.generateDataset(size, dimension);

                    //Run the sequrntial K-Mean test
                    runDiffKMeanVariantTest("( Sequential )", new SequentialKMeans(), dataset, k_value);

                    //Added thread to Parallel and Concurrent K-Mean test
                    for (int threads : THREAD_COUNTS) {
                        runDiffKMeanVariantTest("( Parallel with number of threads " + threads + " )", new ParallelKMeans(threads), dataset, k_value);
                        runDiffKMeanVariantTest("( Concurrent with number of threads " + threads + " )", new ConcurrentKMeans(threads), dataset, k_value);
                    }

                    System.out.println("##############################################################################################");
                    System.out.println();
                }
            }
        }
    }

    //Run on different types of K-mean to get the memory usage and time taken
    private static void runDiffKMeanVariantTest(String name, KMeansAlgo algorithm, Dataset dataset, int k_value) {
        Runtime runtime = Runtime.getRuntime();

        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long startTime = System.nanoTime();
        algorithm.cluster(dataset, k_value, MAX_ITERATIONS);
        long elapsedTimeInMs = (System.nanoTime() - startTime) / 10000000;

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsedInMB = (memoryAfter - memoryBefore) / (1024 * 1024);

        System.out.println(name + " | Total Time Taken: " + elapsedTimeInMs + " Ms | Total Memory Usage: " + memoryUsedInMB + " MB");
    }
}
