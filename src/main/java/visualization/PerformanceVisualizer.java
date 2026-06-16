package visualisation;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;

import java.awt.BasicStroke;
import java.io.*;
import java.util.*;

// Visualization based on results
public class PerformanceVisualizer {

    private static final String OUT = "charts";
    private static final int W = 1200, H = 700;
    // Default thread count used when comparing Concurrent/Parallel against Sequential.
    // Sequential always uses 1 thread; this is the thread count for the multi-threaded
    // implementations so they are compared at their intended (best-case) parallelism.
    private static final int MT_THREADS = 8;

    private final List<Row> rows = new ArrayList<>();

    private record Row(String algo, int threads, int size, int dim, int k,
                       double timeMs, double memMb) {}

    public static void main(String[] args) {
        var viz = new PerformanceVisualizer();
        String path = findLatestCsv();
        if (path == null) { System.err.println("No CSV found. Run Main first."); System.exit(1); }
        System.out.println("Loading: " + path);
        viz.load(path);
        System.out.println("  " + viz.rows.size() + " rows loaded.\n");

        new File(OUT).mkdirs();
        viz.chartTimeVsSize();
        viz.chartSpeedupVsThreads();
        viz.chartTimeVsDimension();
        viz.chartMemoryComparison();
        viz.chartTimeVsThreads();
        viz.chartSpeedupHeatmap();
        viz.chartEfficiency();
        System.out.println("\nDone → ./" + OUT + "/");
    }

    private static String findLatestCsv() {
        File[] fs = new File(".").listFiles((d, n) -> n.startsWith("benchmark_results_") && n.endsWith(".csv"));
        return (fs == null || fs.length == 0) ? null
                : Arrays.stream(fs).max(Comparator.comparingLong(File::lastModified)).orElseThrow().getPath();
    }

    private void load(String path) {
        try (var br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 7) continue;
                rows.add(new Row(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                        Double.parseDouble(p[5]), Double.parseDouble(p[6])));
            }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private static java.awt.Color c(String algo) {
        return switch (algo) {
            case "Sequential" -> new java.awt.Color(231, 76, 60);
            case "Concurrent" -> new java.awt.Color(52, 152, 219);
            case "Parallel"   -> new java.awt.Color(46, 204, 113);
            default           -> java.awt.Color.GRAY;
        };
    }

    private double time(String algo, int size, int dim, int k) {
        return rows.stream()
                .filter(r -> r.algo.equals(algo) && r.size == size && r.dim == dim && r.k == k)
                .mapToDouble(r -> r.timeMs).min().orElse(0);
    }

    private double timeT(String algo, int size, int dim, int k, int threads) {
        return rows.stream()
                .filter(r -> r.algo.equals(algo) && r.size == size && r.dim == dim && r.k == k && r.threads == threads)
                .mapToDouble(r -> r.timeMs).min().orElse(0);
    }

    // ─── Chart 01: Execution Time vs Dataset Size (dim=2, k=5) ───
    // Sequential = 1 thread.  Concurrent / Parallel = MT_THREADS threads.
    void chartTimeVsSize() {
        int dim = 2, k = 5;
        int[] sizes = {10000, 100000, 1000000};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Time vs Dataset Size (Dim=" + dim + ", K=" + k + ", " + MT_THREADS + " threads)")
                .xAxisTitle("Dataset Size").yAxisTitle("Time (ms)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        for (String algo : List.of("Sequential", "Concurrent", "Parallel")) {
            List<Double> vals = new ArrayList<>();
            for (int s : sizes) {
                if (algo.equals("Sequential"))
                    vals.add(time(algo, s, dim, k));               // 1 thread
                else
                    vals.add(timeT(algo, s, dim, k, MT_THREADS));  // 8 threads
            }
            ch.addSeries(algo, Arrays.stream(sizes).mapToObj(String::valueOf).toList(), vals)
              .setFillColor(c(algo));
        }
        save(ch, "01_time_vs_size");
    }

    // ─── Chart 02: Speedup vs Thread Count (n=1M, dim=20, k=5) ───
    // Speedup = T_seq / T_multi_threaded
    void chartSpeedupVsThreads() {
        int size = 1000000, dim = 20, k = 5;
        int[] th = {1, 2, 4, 8};
        double seq = time("Sequential", size, dim, k);
        if (seq <= 0) { System.out.println("  [SKIP] speedup: no baseline"); return; }
        XYChart ch = new XYChartBuilder().width(W).height(H)
                .title("Speedup vs Threads (Size=" + size + ", Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Threads").yAxisTitle("Speedup (×)").build();
        ch.getStyler().setMarkerSize(8);
        for (String algo : List.of("Concurrent", "Parallel")) {
            List<Double> xl = new ArrayList<>(), yl = new ArrayList<>();
            for (int t : th) {
                double best = timeT(algo, size, dim, k, t);
                if (best > 0) { xl.add((double) t); yl.add(seq / best); }
            }
            var s = ch.addSeries(algo, xl, yl);
            s.setLineColor(c(algo)); s.setMarkerColor(c(algo));
        }
        var ref = ch.addSeries("1×", List.of(1.0, 8.0), List.of(1.0, 1.0));
        ref.setLineColor(java.awt.Color.GRAY);
        ref.setLineStyle(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 5}, 0));
        ref.setMarker(new org.knowm.xchart.style.markers.None());
        ch.getStyler().setXAxisMin(0.5); ch.getStyler().setXAxisMax(8.5);
        save(ch, "02_speedup_vs_threads");
    }

    // ─── Chart 03: Execution Time vs Dimension (n=100K, k=5) ───
    // Sequential = 1 thread.  Concurrent / Parallel = MT_THREADS threads.
    void chartTimeVsDimension() {
        int size = 100000, k = 5;
        int[] dims = {2, 10, 20};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Time vs Dimension (Size=" + size + ", K=" + k + ", " + MT_THREADS + " threads)")
                .xAxisTitle("Dimension").yAxisTitle("Time (ms)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        for (String algo : List.of("Sequential", "Concurrent", "Parallel")) {
            List<Double> vals = new ArrayList<>();
            for (int d : dims) {
                if (algo.equals("Sequential"))
                    vals.add(time(algo, size, d, k));
                else
                    vals.add(timeT(algo, size, d, k, MT_THREADS));
            }
            ch.addSeries(algo, Arrays.stream(dims).mapToObj(String::valueOf).toList(), vals)
              .setFillColor(c(algo));
        }
        save(ch, "03_time_vs_dimension");
    }

    // ─── Chart 04: Memory Usage vs Dataset Size (dim=2, k=5) ───
    // Sequential = 1 thread.  Concurrent / Parallel = MT_THREADS threads.
    void chartMemoryComparison() {
        int dim = 2, k = 5;
        int[] sizes = {10000, 100000, 1000000};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Memory Comparison (Dim=" + dim + ", K=" + k + ", " + MT_THREADS + " threads)")
                .xAxisTitle("Dataset Size").yAxisTitle("Memory (MB)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        for (String algo : List.of("Sequential", "Concurrent", "Parallel")) {
            List<Double> mems = new ArrayList<>();
            for (int s : sizes) {
                int th = algo.equals("Sequential") ? 1 : MT_THREADS;
                mems.add(rows.stream()
                        .filter(r -> r.algo.equals(algo) && r.size == s && r.dim == dim && r.k == k && r.threads == th)
                        .mapToDouble(r -> r.memMb).findFirst().orElse(0));
            }
            ch.addSeries(algo, Arrays.stream(sizes).mapToObj(String::valueOf).toList(), mems)
              .setFillColor(c(algo));
        }
        save(ch, "04_memory_comparison");
    }

    // ─── Chart 05: Execution Time per Thread Count (n=1M, dim=20, k=5) ───
    // Sequential = dashed reference line.
    void chartTimeVsThreads() {
        int size = 1000000, dim = 20, k = 5;
        XYChart ch = new XYChartBuilder().width(W).height(H)
                .title("Time per Thread Count (Size=" + size + ", Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Thread Count").yAxisTitle("Time (ms)").build();
        ch.getStyler().setMarkerSize(8);
        for (String algo : List.of("Concurrent", "Parallel")) {
            List<Double> xl = new ArrayList<>(), yl = new ArrayList<>();
            for (int t : new int[]{1, 2, 4, 8}) {
                double best = timeT(algo, size, dim, k, t);
                if (best > 0) { xl.add((double) t); yl.add(best); }
            }
            var s = ch.addSeries(algo, xl, yl);
            s.setLineColor(c(algo)); s.setMarkerColor(c(algo));
        }
        double seqT = time("Sequential", size, dim, k);
        var ref = ch.addSeries("Sequential", List.of(0.5, 8.5), List.of(seqT, seqT));
        ref.setLineColor(c("Sequential"));
        ref.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0));
        ref.setMarker(new org.knowm.xchart.style.markers.None());
        ch.getStyler().setXAxisMin(0.5); ch.getStyler().setXAxisMax(8.5);
        save(ch, "05_time_vs_threads");
    }

    // ─── Chart 06: Parallel Speedup by Thread Count & Dataset Size (dim=2, k=5) ───
    // Grouped bar chart: x = thread count, y = speedup, 3 series = 3 dataset sizes.
    void chartSpeedupHeatmap() {
        int dim = 2, k = 5;
        int[] sizes = {10000, 100000, 1000000};
        int[] th = {1, 2, 4, 8};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Parallel Speedup by Thread Count & Dataset Size (Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Thread Count").yAxisTitle("Speedup (x Sequential)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        for (int si = 0; si < sizes.length; si++) {
            int size = sizes[si];
            double seq = time("Sequential", size, dim, k);
            if (seq <= 0) seq = 1;
            List<Double> vals = new ArrayList<>();
            for (int t : th) {
                double par = timeT("Parallel", size, dim, k, t);
                vals.add((par > 0) ? seq / par : 0);
            }
            String label = (size >= 1000000) ? (size / 1000000) + "M" : (size / 1000) + "K";
            ch.addSeries("n=" + label, Arrays.stream(th).mapToObj(String::valueOf).toList(), vals)
              .setFillColor(new java.awt.Color(255 - si * 60, 200 - si * 40, 80 + si * 60));
        }
        save(ch, "06_speedup_heatmap");
    }

    // ─── Chart 07: Parallel Efficiency (n=1M, dim=20, k=5) ───
    // Efficiency = Speedup / Threads × 100%
    void chartEfficiency() {
        int size = 1000000, dim = 20, k = 5;
        int[] th = {1, 2, 4, 8};
        double seq = time("Sequential", size, dim, k);
        if (seq <= 0) { System.out.println("  [SKIP] efficiency"); return; }
        XYChart ch = new XYChartBuilder().width(W).height(H)
                .title("Parallel Efficiency (Size=" + size + ", Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Threads").yAxisTitle("Efficiency (%)").build();
        ch.getStyler().setMarkerSize(8);
        ch.getStyler().setYAxisMin(0.0); ch.getStyler().setYAxisMax(130.0);
        for (String algo : List.of("Concurrent", "Parallel")) {
            List<Double> xl = new ArrayList<>(), yl = new ArrayList<>();
            for (int t : th) {
                double best = timeT(algo, size, dim, k, t);
                if (best > 0) {
                    xl.add((double) t);
                    yl.add(seq / best / t * 100.0);
                }
            }
            var s = ch.addSeries(algo, xl, yl);
            s.setLineColor(c(algo)); s.setMarkerColor(c(algo));
        }
        var ref = ch.addSeries("Ideal", List.of(1.0, 8.0), List.of(100.0, 100.0));
        ref.setLineColor(java.awt.Color.GRAY);
        ref.setLineStyle(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 5}, 0));
        ref.setMarker(new org.knowm.xchart.style.markers.None());
        ch.getStyler().setXAxisMin(0.5); ch.getStyler().setXAxisMax(8.5);
        save(ch, "07_efficiency");
    }

    private static void save(Object o, String name) {
        try {
            if (o instanceof XYChart xy) BitmapEncoder.saveBitmap(xy, OUT + "/" + name, BitmapEncoder.BitmapFormat.PNG);
            else if (o instanceof CategoryChart cc) BitmapEncoder.saveBitmap(cc, OUT + "/" + name, BitmapEncoder.BitmapFormat.PNG);
            System.out.println("  ✓ " + name + ".png");
        } catch (IOException e) { System.err.println("  ✗ " + name + ": " + e.getMessage()); }
    }
}