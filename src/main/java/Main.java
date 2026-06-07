import api.KMeansAlgo;
import impl.ConcurrentKMeans;
import impl.ParallelKMeans;
import impl.SequentialKMeans;
import model.Dataset;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {

    private static final int[] DATASET_SIZES = {10000, 100000, 1000000};
    private static final int[] DIMENSIONS = {2, 10, 20};
    private static final int[] K_VALUES = {3, 5, 10};
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8};
    private static final int MAX_ITERATIONS = 40;

    // Output
    private static PrintWriter csvWriter = null;

    public static void main(String[] args) {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU cores: " + cores + " | JVM: " + System.getProperty("java.version"));
        System.out.println("Each config runs ONCE (no warmup, no averaging).");
        System.out.println();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String csvPath = "benchmark_results_" + timestamp + ".csv";
        try {
            csvWriter = new PrintWriter(new FileWriter(csvPath));
            csvWriter.println("Algorithm,Threads,DataSetSize,Dimension,K,TimeMs,MemoryMB");
        } catch (IOException e) {
            System.err.println("Cannot write CSV: " + e.getMessage());
            return;
        }

        for (int size : DATASET_SIZES) {
            for (int dimension : DIMENSIONS) {
                for (int k_value : K_VALUES) {
                    System.out.println("========================================");
                    System.out.println("Dataset with size of " + size + "  Dimensions of " + dimension + "  K value of " + k_value);
                    System.out.println("========================================");

                    Dataset dataset = DatasetGenerator.generateDataset(size, dimension);

                    runOnce("Sequential", 1, new SequentialKMeans(), dataset, k_value);

                    for (int threads : THREAD_COUNTS) {
                        runOnce("Concurrent", threads, new ConcurrentKMeans(threads), dataset, k_value);
                        runOnce("Parallel", threads, new ParallelKMeans(threads), dataset, k_value);
                    }

                    System.out.println();
                }
            }
        }

        csvWriter.close();
        System.out.println("Results written to: " + csvPath);
    }

    private static void runOnce(String algoName, int threads, KMeansAlgo algorithm,
                                Dataset dataset, int k) {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();

        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        long startNs = System.nanoTime();

        algorithm.cluster(dataset, k, MAX_ITERATIONS);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memMb = Math.max(0, memAfter - memBefore) / (1024 * 1024);

        System.out.printf("  %-12s Number of Threads:%-2d  Total Time Taken:%5d ms  Total Memory Usage:%d MB%n",
                algoName, threads, elapsedMs, memMb);

        csvWriter.printf("%s,%d,%d,%d,%d,%d,%d%n",
                algoName, threads,
                dataset.getSize(), dataset.getAllPoints().get(0).getDimension(),
                k, elapsedMs, memMb);
    }
}