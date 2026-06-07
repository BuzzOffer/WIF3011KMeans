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
        viz.chartRunVariance();
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

    // color
    private static java.awt.Color c(String algo) {
        return switch (algo) {
            case "Sequential" -> new java.awt.Color(231, 76, 60);
            case "Concurrent" -> new java.awt.Color(52, 152, 219);
            case "Parallel" -> new java.awt.Color(46, 204, 113);
            default -> java.awt.Color.GRAY;
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
                .mapToDouble(r -> r.timeMs).min().orElse(Double.POSITIVE_INFINITY);
    }

    // 1: Time vs Size
    void chartTimeVsSize() {
        int dim = 2, k = 5;
        int[] sizes = {10000, 100000, 1000000};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Time vs Dataset Size (Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Dataset Size").yAxisTitle("Time (ms)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        ch.getStyler().setOverlapped(true);
        for (String algo : List.of("Sequential", "Concurrent", "Parallel")) {
            List<Double> vals = new ArrayList<>();
            for (int s : sizes) vals.add(time(algo, s, dim, k));
            ch.addSeries(algo, Arrays.stream(sizes).mapToObj(String::valueOf).toList(), vals).setFillColor(c(algo));
        }
        save(ch, "01_time_vs_size");
    }

    // 2: Speedup vs Threads
    void chartSpeedupVsThreads() {
        int size = 100000, dim = 2, k = 5;
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
                if (best > 0 && best < Double.POSITIVE_INFINITY) { xl.add((double) t); yl.add(seq / best); }
            }
            var s = ch.addSeries(algo, xl, yl); s.setLineColor(c(algo)); s.setMarkerColor(c(algo));
        }
        var ref = ch.addSeries("1×", List.of(1.0, 8.0), List.of(1.0, 1.0));
        ref.setLineColor(java.awt.Color.GRAY);
        ref.setLineStyle(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 5}, 0));
        ref.setMarker(new org.knowm.xchart.style.markers.None());
        ch.getStyler().setXAxisMin(0.5); ch.getStyler().setXAxisMax(8.5);
        save(ch, "02_speedup_vs_threads");
    }

    // 3: Time vs Dimension
    void chartTimeVsDimension() {
        int size = 100000, k = 5;
        int[] dims = {2, 10, 50};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Time vs Dimension (Size=" + size + ", K=" + k + ")")
                .xAxisTitle("Dimension").yAxisTitle("Time (ms)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        ch.getStyler().setOverlapped(true);
        for (String algo : List.of("Sequential", "Concurrent", "Parallel")) {
            List<Double> vals = new ArrayList<>();
            for (int d : dims) vals.add(time(algo, size, d, k));
            ch.addSeries(algo, Arrays.stream(dims).mapToObj(String::valueOf).toList(), vals).setFillColor(c(algo));
        }
        save(ch, "03_time_vs_dimension");
    }

    // 4: Memory Comparison
    void chartMemoryComparison() {
        int dim = 2, k = 5;
        int[] sizes = {10000, 100000, 1000000};
        CategoryChart ch = new CategoryChartBuilder().width(W).height(H)
                .title("Memory Comparison (Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Dataset Size").yAxisTitle("Memory (MB)").build();
        ch.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        ch.getStyler().setOverlapped(true);
        for (String algo : List.of("Sequential", "Concurrent", "Parallel")) {
            List<Double> mems = new ArrayList<>();
            for (int s : sizes) {
                mems.add(rows.stream()
                        .filter(r -> r.algo.equals(algo) && r.size == s && r.dim == dim && r.k == k)
                        .mapToDouble(r -> r.memMb).findFirst().orElse(0));
            }
            ch.addSeries(algo, Arrays.stream(sizes).mapToObj(String::valueOf).toList(), mems).setFillColor(c(algo));
        }
        save(ch, "04_memory_comparison");
    }

    // 5: Run Variance (box plot)
    void chartRunVariance() {
        // Since we only have 1 run per config now, group by (algo, size) for 100K dim=2 k=5
        int size = 100000, dim = 2, k = 5;
        String[] algos = {"Sequential", "Concurrent", "Parallel"};

        XYChart ch = new XYChartBuilder().width(W).height(H)
                .title("Time per Thread Count (Size=" + size + ", Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Thread Count").yAxisTitle("Time (ms)").build();
        ch.getStyler().setMarkerSize(8);

        for (String algo : List.of("Concurrent", "Parallel")) {
            List<Double> xl = new ArrayList<>(), yl = new ArrayList<>();
            for (int t : new int[]{1, 2, 4, 8}) {
                double best = timeT(algo, size, dim, k, t);
                if (best > 0 && best < Double.POSITIVE_INFINITY) { xl.add((double) t); yl.add(best); }
            }
            var s = ch.addSeries(algo, xl, yl); s.setLineColor(c(algo)); s.setMarkerColor(c(algo));
        }

        // Sequential as horizontal dashed line
        double seqT = time("Sequential", size, dim, k);
        var ref = ch.addSeries("Sequential", List.of(0.5, 8.5), List.of(seqT, seqT));
        ref.setLineColor(c("Sequential"));
        ref.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0));
        ref.setMarker(new org.knowm.xchart.style.markers.None());

        ch.getStyler().setXAxisMin(0.5); ch.getStyler().setXAxisMax(8.5);
        save(ch, "05_time_vs_threads");
    }

    // 6: Speedup Heatmap
    void chartSpeedupHeatmap() {
        int dim = 2, k = 5;
        int[] sizes = {10000, 100000, 1000000};
        int[] th = {1, 2, 4, 8};
        CategoryChart ch = new CategoryChartBuilder().width(900).height(550)
                .title("Parallel Speedup Heatmap (Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Thread Count").yAxisTitle("Dataset Size").build();
        ch.getStyler().setLegendVisible(false);
        ch.getStyler().setPlotGridHorizontalLinesVisible(false);
        ch.getStyler().setPlotGridVerticalLinesVisible(false);

        for (int si = 0; si < sizes.length; si++) {
            int size = sizes[si];
            double seq = time("Sequential", size, dim, k);
            if (seq <= 0) seq = 1;
            List<Double> vals = new ArrayList<>();
            for (int t : th) {
                double best = timeT("Parallel", size, dim, k, t);
                vals.add((best > 0 && best < Double.POSITIVE_INFINITY) ? seq / best : 0);
            }
            ch.addSeries(String.valueOf(size), Arrays.stream(th).mapToObj(String::valueOf).toList(), vals)
                    .setFillColor(new java.awt.Color(255 - si * 50, 220 - si * 30, 100 + si * 50));
        }
        save(ch, "06_speedup_heatmap");
    }

    // 7: Efficiency
    void chartEfficiency() {
        int size = 100000, dim = 2, k = 5;
        int[] th = {1, 2, 4, 8};
        double seq = time("Sequential", size, dim, k);
        if (seq <= 0) { System.out.println("  [SKIP] efficiency"); return; }
        XYChart ch = new XYChartBuilder().width(W).height(H)
                .title("Parallel Efficiency (Size=" + size + ", Dim=" + dim + ", K=" + k + ")")
                .xAxisTitle("Threads").yAxisTitle("Efficiency (%)").build();
        ch.getStyler().setMarkerSize(8);
        ch.getStyler().setYAxisMin(0.0); ch.getStyler().setYAxisMax(110.0);
        for (String algo : List.of("Concurrent", "Parallel")) {
            List<Double> xl = new ArrayList<>(), yl = new ArrayList<>();
            for (int t : th) {
                double best = timeT(algo, size, dim, k, t);
                if (best > 0 && best < Double.POSITIVE_INFINITY) {
                    xl.add((double) t); yl.add(seq / best / t * 100.0);
                }
            }
            var s = ch.addSeries(algo, xl, yl); s.setLineColor(c(algo)); s.setMarkerColor(c(algo));
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