package Main;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance profiler to find the real bottleneck causing FPS drops
 *
 * Usage in your main render loop:
 *
 * PerformanceProfiler.startFrame();
 *
 * PerformanceProfiler.start("Shadow Rendering");
 * // ... shadow rendering code ...
 * PerformanceProfiler.end("Shadow Rendering");
 *
 * PerformanceProfiler.start("Billboard Rendering");
 * // ... billboard rendering code ...
 * PerformanceProfiler.end("Billboard Rendering");
 *
 * PerformanceProfiler.start("Mesh Rendering");
 * // ... mesh rendering code ...
 * PerformanceProfiler.end("Mesh Rendering");
 *
 * PerformanceProfiler.endFrame();
 *
 * // Print report every 60 frames
 * if (frameCount % 60 == 0) {
 *     PerformanceProfiler.printReport();
 * }
 */
public class PerformanceProfiler {

    private static class TimingData {
        long totalTime = 0;
        long maxTime = 0;
        long minTime = Long.MAX_VALUE;
        int count = 0;

        void add(long time) {
            totalTime += time;
            maxTime = Math.max(maxTime, time);
            minTime = Math.min(minTime, time);
            count++;
        }

        double getAverageMs() {
            return count > 0 ? (totalTime / (double) count) / 1_000_000.0 : 0;
        }

        double getMaxMs() {
            return maxTime / 1_000_000.0;
        }

        double getMinMs() {
            return minTime == Long.MAX_VALUE ? 0 : minTime / 1_000_000.0;
        }

        void reset() {
            totalTime = 0;
            maxTime = 0;
            minTime = Long.MAX_VALUE;
            count = 0;
        }
    }

    private static final Map<String, TimingData> timings = new ConcurrentHashMap<>();
    private static final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private static long frameStartTime = 0;
    private static long lastFrameTime = 0;
    private static final List<Long> recentFrameTimes = new ArrayList<>();
    private static final int MAX_FRAME_HISTORY = 120;

    private static boolean enabled = true;

    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    public static void startFrame() {
        if (!enabled) return;
        frameStartTime = System.nanoTime();
    }

    public static void endFrame() {
        if (!enabled) return;
        long frameTime = System.nanoTime() - frameStartTime;
        lastFrameTime = frameTime;

        // Track recent frame times for statistics
        recentFrameTimes.add(frameTime);
        if (recentFrameTimes.size() > MAX_FRAME_HISTORY) {
            recentFrameTimes.remove(0);
        }
    }

    public static void start(String section) {
        if (!enabled) return;
        startTimes.put(section, System.nanoTime());
    }

    public static void end(String section) {
        if (!enabled) return;
        Long startTime = startTimes.remove(section);
        if (startTime != null) {
            long elapsed = System.nanoTime() - startTime;
            timings.computeIfAbsent(section, k -> new TimingData()).add(elapsed);
        }
    }
    public static void printReport(PrintStream out) {
        if (!enabled || timings.isEmpty()) return;

        out.println("\n═════════════════════════════════════════════════════════");
        out.println("           PERFORMANCE PROFILING REPORT");
        out.println("═════════════════════════════════════════════════════════");

        // Frame statistics
        if (!recentFrameTimes.isEmpty()) {
            double avgFrameTime = recentFrameTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0) / 1_000_000.0;

            double avgFPS = 1000.0 / avgFrameTime;

            long maxFrameTime = recentFrameTimes.stream()
                    .mapToLong(Long::longValue)
                    .max().orElse(0);

            long minFrameTime = recentFrameTimes.stream()
                    .mapToLong(Long::longValue)
                    .min().orElse(0);

            double maxFPS = 1000.0 / (minFrameTime / 1_000_000.0);
            double minFPS = 1000.0 / (maxFrameTime / 1_000_000.0);

            out.printf("Average FPS: %.1f (%.2f ms/frame)%n", avgFPS, avgFrameTime);
            out.printf("FPS Range: %.1f - %.1f%n", minFPS, maxFPS);
            out.printf("Last Frame: %.2f ms%n", lastFrameTime / 1_000_000.0);
        }

        out.println("\n─────────────────────────────────────────────────────────");
        out.println("Section Performance Breakdown:");
        out.println("─────────────────────────────────────────────────────────");

        List<Map.Entry<String, TimingData>> sorted = new ArrayList<>(timings.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().getAverageMs(), a.getValue().getAverageMs()));

        double totalMs = sorted.stream()
                .mapToDouble(e -> e.getValue().getAverageMs())
                .sum();

        out.printf("%-30s %10s %10s %10s %10s %8s%n",
                "Section", "Avg (ms)", "Min (ms)", "Max (ms)", "Count", "% Time");
        out.println("─────────────────────────────────────────────────────────");

        for (Map.Entry<String, TimingData> entry : sorted) {
            String name = entry.getKey();
            TimingData data = entry.getValue();
            double percentage = totalMs > 0 ? (data.getAverageMs() / totalMs * 100) : 0;

            out.printf("%-30s %10.2f %10.2f %10.2f %10d %7.1f%%%n",
                    name,
                    data.getAverageMs(),
                    data.getMinMs(),
                    data.getMaxMs(),
                    data.count,
                    percentage
            );
        }

        out.println("═════════════════════════════════════════════════════════");

        out.println("\nPotential Issues:");
        boolean foundIssues = false;

        for (Map.Entry<String, TimingData> entry : sorted) {
            String name = entry.getKey();
            TimingData data = entry.getValue();

            if (data.getAverageMs() > 5.0) {
                out.printf("⚠ %s: Taking %.2f ms average (%.1f FPS cost)%n",
                        name, data.getAverageMs(), 1000.0 / data.getAverageMs());
                foundIssues = true;
            }

            if (data.getMaxMs() > data.getAverageMs() * 3 && data.getMaxMs() > 10.0) {
                out.printf("⚠ %s: High variance (max %.2f ms vs avg %.2f ms)%n",
                        name, data.getMaxMs(), data.getAverageMs());
                foundIssues = true;
            }
        }

        if (!foundIssues) {
            out.println("✓ No major performance issues detected");
        }

        out.println("═════════════════════════════════════════════════════════\n");
    }

    public static void printReportFile(){
        try {
            FileOutputStream fout = new FileOutputStream("profile.log",true);
            PrintStream out = new PrintStream(fout,true);
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date());
            out.println("\n===================== " + time + " =====================");
            printReport(out);
        } catch (Exception e) {
            File file = new File("profile.log");
            try {
                file.createNewFile();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                printReportFile();
            }
        }
    }
    public static void printReport() {

        if (!enabled || timings.isEmpty()) return;

        System.out.println("\n═════════════════════════════════════════════════════════");
        System.out.println("           PERFORMANCE PROFILING REPORT");
        System.out.println("═════════════════════════════════════════════════════════");

        // Calculate frame stats
        if (!recentFrameTimes.isEmpty()) {
            double avgFrameTime = recentFrameTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0) / 1_000_000.0;

            double avgFPS = 1000.0 / avgFrameTime;

            long maxFrameTime = recentFrameTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);

            long minFrameTime = recentFrameTimes.stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0);

            double maxFPS = 1000.0 / (minFrameTime / 1_000_000.0);
            double minFPS = 1000.0 / (maxFrameTime / 1_000_000.0);

            System.out.printf("Average FPS: %.1f (%.2f ms/frame)%n", avgFPS, avgFrameTime);
            System.out.printf("FPS Range: %.1f - %.1f%n", minFPS, maxFPS);
            System.out.printf("Last Frame: %.2f ms%n", lastFrameTime / 1_000_000.0);
        }

        System.out.println("\n─────────────────────────────────────────────────────────");
        System.out.println("Section Performance Breakdown:");
        System.out.println("─────────────────────────────────────────────────────────");

        // Sort by average time (descending)
        List<Map.Entry<String, TimingData>> sorted = new ArrayList<>(timings.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().getAverageMs(), a.getValue().getAverageMs()));

        // Calculate total time for percentage
        double totalMs = sorted.stream()
                .mapToDouble(e -> e.getValue().getAverageMs())
                .sum();

        System.out.printf("%-30s %10s %10s %10s %10s %8s%n",
                "Section", "Avg (ms)", "Min (ms)", "Max (ms)", "Count", "% Time");
        System.out.println("─────────────────────────────────────────────────────────");

        for (Map.Entry<String, TimingData> entry : sorted) {
            String name = entry.getKey();
            TimingData data = entry.getValue();
            double percentage = totalMs > 0 ? (data.getAverageMs() / totalMs * 100) : 0;

            System.out.printf("%-30s %10.2f %10.2f %10.2f %10d %7.1f%%%n",
                    name,
                    data.getAverageMs(),
                    data.getMinMs(),
                    data.getMaxMs(),
                    data.count,
                    percentage
            );
        }

        System.out.println("═════════════════════════════════════════════════════════");

        // Identify potential problems
        System.out.println("\nPotential Issues:");
        boolean foundIssues = false;

        for (Map.Entry<String, TimingData> entry : sorted) {
            String name = entry.getKey();
            TimingData data = entry.getValue();

            // Flag sections taking > 5ms average
            if (data.getAverageMs() > 5.0) {
                System.out.printf("⚠ %s: Taking %.2f ms average (%.1f FPS cost)%n",
                        name, data.getAverageMs(), 1000.0 / data.getAverageMs());
                foundIssues = true;
            }

            // Flag sections with high variance (max > 3x average)
            if (data.getMaxMs() > data.getAverageMs() * 3 && data.getMaxMs() > 10.0) {
                System.out.printf("⚠ %s: High variance (max %.2f ms vs avg %.2f ms) - possible stutter source%n",
                        name, data.getMaxMs(), data.getAverageMs());
                foundIssues = true;
            }
        }

        if (!foundIssues) {
            System.out.println("✓ No major performance issues detected");
        }

        System.out.println("═════════════════════════════════════════════════════════\n");
    }

    public static void reset() {
        timings.clear();
        startTimes.clear();
        recentFrameTimes.clear();
    }

    public static double getLastFrameTimeMs() {
        return lastFrameTime / 1_000_000.0;
    }

    public static double getAverageFPS() {
        if (recentFrameTimes.isEmpty()) return 0;
        double avgFrameTime = recentFrameTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0) / 1_000_000.0;
        return 1000.0 / avgFrameTime;
    }
}