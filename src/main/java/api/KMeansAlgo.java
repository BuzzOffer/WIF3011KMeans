package api;

import model.Dataset;

/**
 * Unified interface for K-Means implementations
 */

public interface KMeansAlgo {
    /**
     * Executes the K-Means clustering algorithm
     * @param data The input dataset containing points
     * @param k The number of clusters to be formed
     * @param maxIteration The maximum number of allowed iterations
     */
    void cluster(Dataset data, int k, int maxIteration);
}