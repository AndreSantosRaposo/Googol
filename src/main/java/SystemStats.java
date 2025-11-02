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

    public void updateBarrelMetrics(String barrelName, int indexSize, long avgResponseTimeMs) {
        if (indexSize < 0 || avgResponseTimeMs < 0) {
            throw new IllegalArgumentException("Valores não podem ser negativos");
        }
        barrelMetrics.put(barrelName, new BarrelMetrics(indexSize, avgResponseTimeMs));
    }

    public Map<String, BarrelMetrics> getBarrelMetrics() {
        return new HashMap<>(barrelMetrics);
    }

    // Classe interna
    public static class BarrelMetrics implements Serializable {

        private final int indexSize;
        private final long avgResponseTimeMs;

        public BarrelMetrics(int indexSize, long avgResponseTimeMs) {
            this.indexSize = indexSize;
            this.avgResponseTimeMs = avgResponseTimeMs;
        }

        public int getIndexSize() { return indexSize; }
        public long getAvgResponseTimeMs() { return avgResponseTimeMs; }

        @Override
        public String toString() {
            return String.format("Tamanho: %d | Tempo médio: %.2f ms",
                    indexSize, avgResponseTimeMs);
        }
    }
}
