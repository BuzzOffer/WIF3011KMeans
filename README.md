# Concurrent & Parallel Implementations of K-Means Clustering in Java
## Project Overview
K-Means clustering algorithm is a cornerstone of unsupervised learning in Artificial Intelligence. As data scales, sequential implementations face significant computational bottlenecks. Our project is to overcome thses challenges by developing three distinct Java implementations, including Sequential, Concurrent, and Parallel, to compare their scalability and resource efficiency.
The application is capable of:
- Generating random datasets ranging from 10,000 to 1,000,000 points with varying dimensions
- Quantifying performance through execution time and memory usage metrics
- Testing performance across different hardware configurations and thread pool sizes

## Architecture and Design
### Core Interface and Data Models
- `KMeansAlgo` interface: The contract for all clustering implementations
- `Point`: A model representing a multidimensional vector
- `Centroid`: A container holding a centroid and its assigned points
- `Dataset`: A wrapper for handling large-scale point collections
### Parallelization Targets
The design focuses on parallelizing the two most computationally intensive phases:
- **Distance Calculations**: Assigning points to the nearest centroid
- **Centroid Updates**: Reculculating the mean of clusters

## Development Workflow
### Commit Message Convention
All commits should follow this format:
`<type>(<scope>): <subject>`
- Types:
    - `fact`: new feature
    - `fix`: bug fix
    - `perf`: performance optimization
    - `docs`: documentation
    - `refactor`: code improvement
    - `test`: testing
- Scopes:
    - `core`
    - `seq`
    - `concur`
    - `parallel`
    - `utils`
    - `tester`