package webServer;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * System-wide statistics holder.
 *
 * <p>This class keeps:
 * <ul>
 *     <li>search counts per keyword (used to build top searches)</li>
 *     <li>per-barrel metrics (index size and response time history)</li>
 * </ul>
 *
 * <p>Designed to be thread-safe using concurrent collections and synchronized lists.
 */
public class SystemStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<String, Integer> searchCounts;

    private final ConcurrentMap<String, BarrelMetrics> barrelMetrics;

    public SystemStats() {
        this.searchCounts = new ConcurrentHashMap<>();
        this.barrelMetrics = new ConcurrentHashMap<>();
    }

    // ===== SEARCHES =====
    /**
     * Increment the search count for the provided keyword.
     *
     * @param keyword search keyword
     * @throws NullPointerException if keyword is null
     */
    public void incrementSearchCount(String keyword) {
        Objects.requireNonNull(keyword, "keyword cannot be null");
        searchCounts.compute(keyword.toLowerCase(), (k, v) -> v == null ? 1 : v + 1);
    }

    /**
     * Returns the top 10 searches.
     * @return a list of map entries (keyword -> count) limited to top 10 results
     */
    public List<Map.Entry<String, Integer>> getTop10Searches() {
        return searchCounts.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = e2.getValue().compareTo(e1.getValue());
                    return cmp != 0 ? cmp : e1.getKey().compareTo(e2.getKey());
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Update or create metrics for a barrel.
     *
     * <p>Index size is replaced/updated and the response time is appended to the barrel's
     * response-times history.
     *
     * @param barrelName   barrel identifier (non-null)
     * @param indexSize    number of indexed pages (must be &gt;= 0)
     * @param responseTime response time in milliseconds (must be &gt;= 0)
     * @throws IllegalArgumentException if indexSize or responseTime is negative
     * @throws NullPointerException     if barrelName is null
     */
    public void updateBarrelMetrics(String barrelName, int indexSize, long responseTime) {
        Objects.requireNonNull(barrelName, "barrelName cannot be null");
        if (indexSize < 0 || responseTime < 0) {
            throw new IllegalArgumentException("Values cannot be negative");
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

    /**
     * Returns a shallow copy of the current barrel metrics map
     * @return copy of barrel metrics map
     */
    public Map<String, BarrelMetrics> getBarrelMetrics() {
        return new HashMap<>(barrelMetrics);
    }

    /**
     * Inner class that stores per-barrel metrics (index size and response-time history)
     */
    public static class BarrelMetrics implements Serializable {
        private static final long serialVersionUID = 1L;

        private int indexSize;
        private final List<Long> responseTimes;

        /**
         * Constructor mehtod to create a new BarrelMetrics instance with an initial index size and response time.
         *
         * @param indexSize    initial index size (pages)
         * @param responseTime initial response time in milliseconds
         */
        public BarrelMetrics(int indexSize, long responseTime) {
            if (indexSize < 0 || responseTime < 0) {
                throw new IllegalArgumentException("Values cannot be negative");
            }
            this.indexSize = indexSize;
            this.responseTimes = Collections.synchronizedList(new ArrayList<>());
            this.responseTimes.add(responseTime);
        }

        /**
         * Replace the stored index size with a new value.
         * @param newSize new index size (must be &gt;= 0)
         */
        public void updateIndexSize(int newSize) {
            if (newSize < 0) throw new IllegalArgumentException("Index size cannot be negative");
            this.indexSize = newSize;
        }

        /**
         * Append a new response time sample.
         * @param time response time in milliseconds (must be &gt;= 0)
         */
        public void addResponseTime(long time) {
            if (time < 0) throw new IllegalArgumentException("Response time cannot be negative");
            responseTimes.add(time);
        }

        /**
         * Returns the last known index size.
         * @return index size (pages)
         */
        public int getIndexSize() {
            return indexSize;
        }

        /**
         * Returns the average response time in milliseconds
         * @return average response time in ms
         */
        public long getAvgResponseTimeMs() {
            synchronized (responseTimes) {
                if (responseTimes.isEmpty()) return 0L;
                return (long) responseTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);
            }
        }

        /**
         * To string method.
         * @return formatted string like "Size: 170 | Avg response time: 120.3 ms"
         */
        @Override
        public String toString() {
            double avg = (double) getAvgResponseTimeMs();
            return String.format("Size: %d | Avg response time: %.1f ms", indexSize, avg);
        }
    }
}
