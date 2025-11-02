import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class SystemStats implements Serializable {

    private final ConcurrentMap<String, Integer> searchCounts;
    private final ConcurrentMap<String, BarrelMetrics> barrelMetrics;

    public SystemStats() {
        this.searchCounts = new ConcurrentHashMap<>();
        this.barrelMetrics = new ConcurrentHashMap<>();
    }

    // ===== PESQUISAS =====

    public void incrementSearchCount(String keyword) {
        searchCounts.compute(keyword.toLowerCase(), (k, v) -> v == null ? 1 : v + 1);
    }

    public List<Map.Entry<String, Integer>> getTop10Searches() {
        return searchCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    // ===== BARRELS =====

    public void updateBarrelMetrics(String barrelName, int indexSize, long responseTime) {
        if (indexSize < 0 || responseTime < 0) {
            throw new IllegalArgumentException("Valores não podem ser negativos");
        }

        barrelMetrics.compute(barrelName, (k, metrics) -> {
            if (metrics == null) {
                return new BarrelMetrics(indexSize, responseTime);
            } else {
                metrics.updateIndexSize(indexSize);
                metrics.addResponseTime(responseTime);
                return metrics;
            }
        });
    }

    public Map<String, BarrelMetrics> getBarrelMetrics() {
        return new HashMap<>(barrelMetrics);
    }

    // Classe interna
    public static class BarrelMetrics implements Serializable {
        private int indexSize;
        private final List<Long> responseTimes;

        public BarrelMetrics(int indexSize, long responseTime) {
            this.indexSize = indexSize;
            this.responseTimes = Collections.synchronizedList(new ArrayList<>());
            this.responseTimes.add(responseTime);
        }

        public void updateIndexSize(int newSize) { this.indexSize = newSize; }

        public void addResponseTime(long time) {
            responseTimes.add(time);
        }

        public int getIndexSize() { return indexSize; }

        public long getAvgResponseTimeMs() {
            if (responseTimes.isEmpty()) return 0;
            return (long) responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
        }

        @Override
        public String toString() {
            return String.format("Tamanho: %d | Tempo médio: %.1f ms",
                    indexSize, (double) getAvgResponseTimeMs());
        }
    }
}
