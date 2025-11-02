import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Gateway that coordinates search requests and URL indexing across multiple Barrel instances.
 *
 * The Gateway:
 * <ul>
 *     <li>Load-balances search requests between two Barrels (round-robin)</li>
 *     <li>Handles Barrel reconnection if a connection is lost</li>
 *     <li>Aggregates system statistics from all Barrels</li>
 *     <li>Maintains a history of indexed URLs for re-transmission if needed</li>
 * </ul>
 *
 * Thread-safety: Methods use blocking RMI calls. Concurrent client requests are handled
 * by the RMI runtime but internal state (like {@code nextBarrel}) is not synchronized.
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {
    private BarrelIndex barrel1;
    private BarrelIndex barrel2;
    private int nextBarrel = 1;

    private final String barrel1Name;
    private final String barrel1Ip;
    private final int barrel1Port;

    private final String barrel2Name;
    private final String barrel2Ip;
    private final int barrel2Port;

    private final HashMap<Integer, String> urlHistory;
    private int currentSeqNumber;

    private final String name;
    private final String gatewayIp;
    private final int gatewayPort;

    private final SystemStats globalStats;

    /**
     * Constructs a new Gateway and connects to the specified Barrels.
     *
     * @param b1Name      name of Barrel 1 in RMI registry
     * @param b1Ip        IP address of Barrel 1
     * @param b1Port      RMI registry port of Barrel 1
     * @param b2Name      name of Barrel 2 in RMI registry
     * @param b2Ip        IP address of Barrel 2
     * @param b2Port      RMI registry port of Barrel 2
     * @param gatewayIp   IP address where this Gateway is bound
     * @param gatewayPort RMI registry port for this Gateway
     * @throws RemoteException if RMI export fails
     */
    public Gateway(String b1Name, String b1Ip, int b1Port,
                   String b2Name, String b2Ip, int b2Port,
                   String gatewayIp, int gatewayPort) throws RemoteException {
        super();
        this.barrel1Name = b1Name;
        this.barrel1Ip = b1Ip;
        this.barrel1Port = b1Port;
        this.barrel2Name = b2Name;
        this.barrel2Ip = b2Ip;
        this.barrel2Port = b2Port;
        this.urlHistory = new HashMap<>();
        this.currentSeqNumber = 0;
        this.name = "Gateway";
        this.gatewayIp = gatewayIp;
        this.gatewayPort = gatewayPort;
        this.globalStats = new SystemStats();

        // Initial connection
        reconnectBarrel1();
        reconnectBarrel2();
        if (barrel1 != null) {
            barrel1.resetSeqNumbers(name);
        }
        if (barrel2 != null) {
            barrel2.resetSeqNumbers(name);
        }
    }

    /**
     * Attempts to reconnect to Barrel 1 if unavailable.
     */
    private void reconnectBarrel1() {
        try {
            Registry registry = LocateRegistry.getRegistry(barrel1Ip, barrel1Port);
            barrel1 = (BarrelIndex) registry.lookup(barrel1Name);
            System.out.println("Barrel1 connected");
        } catch (Exception e) {
            System.err.println(barrel1Name + " unavailable: " + e.getMessage());
            barrel1 = null;
        }
    }

    /**
     * Attempts to reconnect to Barrel 2 if unavailable.
     */
    private void reconnectBarrel2() {
        try {
            Registry registry = LocateRegistry.getRegistry(barrel2Ip, barrel2Port);
            barrel2 = (BarrelIndex) registry.lookup(barrel2Name);
            System.out.println(barrel2Name + " connected");
        } catch (Exception e) {
            System.err.println("Barrel2 unavailable: " + e.getMessage());
            barrel2 = null;
        }
    }

    /**
     * Searches for pages matching the given query using an available Barrel.
     *
     * Alternates between Barrel 1 and Barrel 2 using round-robin. If one is unavailable,
     * falls back to the other. Updates search counts and barrel metrics.
     *
     * @param query search query string
     * @return list of matching pages
     * @throws RemoteException if no Barrel is available or if an RMI error occurs
     */
    @Override
    public List<PageInfo> search(String query) throws RemoteException {
        List<String> terms = List.of(query.toLowerCase().split("\\s+"));
        List<PageInfo> results = new ArrayList<>();

        // Increment search count
        Arrays.stream(query.toLowerCase().split("\\s+"))
                .forEach(globalStats::incrementSearchCount);

        // Reconnect if necessary
        if (barrel1 == null) reconnectBarrel1();
        if (barrel2 == null) reconnectBarrel2();

        try {
            if (nextBarrel == 1 && barrel1 != null) {
                long startTime = System.currentTimeMillis();
                results.addAll(barrel1.searchPages(terms));
                long duration = System.currentTimeMillis() - startTime;
                globalStats.updateBarrelMetrics(barrel1Name, barrel1.getPagesInfoMap().size(), duration);
                nextBarrel = 2;
                return results;
            } else if (nextBarrel == 2 && barrel2 != null) {
                long startTime = System.currentTimeMillis();
                results.addAll(barrel2.searchPages(terms));
                long duration = System.currentTimeMillis() - startTime;
                globalStats.updateBarrelMetrics(barrel2Name, barrel2.getPagesInfoMap().size(), duration);
                nextBarrel = 1;
                return results;
            }

            // Fallback
            if (barrel1 != null) {
                long startTime = System.currentTimeMillis();
                results.addAll(barrel1.searchPages(terms));
                long duration = System.currentTimeMillis() - startTime;
                globalStats.updateBarrelMetrics(barrel1Name, barrel1.getPagesInfoMap().size(), duration);
                nextBarrel = 2;
            } else if (barrel2 != null) {
                long startTime = System.currentTimeMillis();
                results.addAll(barrel2.searchPages(terms));
                long duration = System.currentTimeMillis() - startTime;
                globalStats.updateBarrelMetrics(barrel2Name, barrel2.getPagesInfoMap().size(), duration);
                nextBarrel = 1;
            } else {
                throw new RemoteException("No Barrel available for search.");
            }

        } catch (Exception e) {
            System.err.println("[Gateway] Error during search: " + e.getMessage());
        }

        return results;
    }

    /**
     * Retrieves aggregated system statistics from all connected Barrels.
     * Combines top searches from the Gateway and metrics from each Barrel.
     *
     * @return combined system statistics
     * @throws RemoteException if an RMI error occurs
     */
    @Override
    public SystemStats getSystemStats() throws RemoteException {
        SystemStats combined = new SystemStats();

        // Copy top 10 searches
        globalStats.getTop10Searches().forEach(e -> {
            for (int i = 0; i < e.getValue(); i++) {
                combined.incrementSearchCount(e.getKey());
            }
        });

        // Use Barrels as source of truth for response times
        if (barrel1 != null) {
            try {
                SystemStats b1Stats = barrel1.getStats();
                b1Stats.getBarrelMetrics().forEach((name, metrics) ->
                        combined.updateBarrelMetrics(name, metrics.getIndexSize(), metrics.getAvgResponseTimeMs())
                );
            } catch (Exception e) {
                System.err.println("Error fetching stats from Barrel1");
            }
        }
        if (barrel2 != null) {
            try {
                SystemStats b2Stats = barrel2.getStats();
                b2Stats.getBarrelMetrics().forEach((name, metrics) ->
                        combined.updateBarrelMetrics(name, metrics.getIndexSize(), metrics.getAvgResponseTimeMs())
                );
            } catch (Exception e) {
                System.err.println("Error fetching stats from Barrel2");
            }
        }

        return combined;
    }

    /**
     * Adds a URL to the indexing queue of all available Barrels
     * The URL is assigned a sequence number and stored in history for re-transmission if needed.
     *
     * @param url the URL to index
     * @throws RemoteException              if an RMI error occurs
     * @throws BarrelUnavailableException   if no Barrel is available
     * @throws UrlAlreadyIndexedException   if the URL is rejected by all Barrels
     */
    @Override
    public void addUrl(String url) throws RemoteException {
        if (DebugConfig.DEBUG_URL_INDEXAR || DebugConfig.DEBUG_MULTICAST_GATEWAY || DebugConfig.DEBUG_ALL) {
            System.out.println("[DEBUG]: Adding URL: " + url + " with SeqNumber: " + currentSeqNumber);
        }

        boolean anySuccess = false;
        boolean barrel1Available = false;
        boolean barrel2Available = false;

        urlHistory.put(currentSeqNumber, url);

        if (barrel1 == null) reconnectBarrel1();

        if (barrel1 != null) {
            barrel1Available = true;
            try {
                boolean added1 = barrel1.addUrlToQueue(url, currentSeqNumber, name, gatewayIp, gatewayPort);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (added1 ? " added" : " not added") + " to Barrel1 queue.");
                }
                anySuccess = added1;
            } catch (Exception e) {
                System.err.println("Error adding to Barrel1: " + e.getMessage());
                barrel1 = null;
                barrel1Available = false;
            }
        } else {
            System.err.println("Barrel1 not available");
        }

        if (barrel2 != null) {
            barrel2Available = true;
            try {
                boolean added2 = barrel2.addUrlToQueue(url, currentSeqNumber, name, gatewayIp, gatewayPort);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (added2 ? " added" : " not added") + " to Barrel2 queue.");
                }
                anySuccess = anySuccess || added2;
            } catch (Exception e) {
                System.err.println("Error adding to Barrel2: " + e.getMessage());
                barrel2 = null;
                barrel2Available = false;
            }
        } else {
            System.err.println("Barrel2 not available");
        }

        currentSeqNumber++;

        if (!barrel1Available && !barrel2Available) {
            throw new BarrelUnavailableException("No Barrel available at this time.");
        }

        if (!anySuccess) {
            throw new UrlAlreadyIndexedException("URL not accepted by Barrels (possibly already indexed).");
        }
    }

    /**
     * Re-sends a URL with the specified sequence number to a specific Barrel.
     * Used for recovering from lost messages in multicast scenarios. Retries up to 3 times.
     *
     * @param missingSeqNumber the sequence number of the missing URL
     * @param receiver         the Barrel that should receive the URL
     * @throws RemoteException if an RMI error occurs
     */
    public void reSendURL(int missingSeqNumber, BarrelIndex receiver) throws RemoteException {
        if (DebugConfig.DEBUG_MULTICAST_GATEWAY || DebugConfig.DEBUG_ALL) {
            System.out.println("[DEBUG]: Resending URL with SeqNumber: " + missingSeqNumber);
        }

        int tryNumber = 0;
        while (tryNumber < 3) {
            try {
                String url = urlHistory.get(missingSeqNumber);
                if (url != null) {
                    receiver.addUrlToQueue(url, missingSeqNumber, name, gatewayIp, gatewayPort);
                    return;
                } else {
                    System.err.println("[Gateway] URL with SeqNumber " + missingSeqNumber + " not found in history.");
                    return;
                }
            } catch (Exception e) {
                System.err.println("[Gateway] Error resending URL with SeqNumber " + missingSeqNumber + ": " + e.getMessage());
                tryNumber++;

                // Attempt reconnect
                if (receiver == barrel1) {
                    reconnectBarrel1();
                    receiver = barrel1;
                } else if (receiver == barrel2) {
                    reconnectBarrel2();
                    receiver = barrel2;
                }
            }
        }
    }

    /**
     * Searches for all pages that link to the given URL (inbound links).
     * Alternates between Barrel 1 and Barrel 2 using round robin.
     *
     * @param url the target URL to find inlinks for
     * @return list of URLs that link to the given URL
     * @throws RemoteException if no Barrel is available or if an RMI error occurs
     */
    @Override
    public List<String> searchInlinks(String url) throws RemoteException {
        List<String> inlinks = new ArrayList<>();

        // Reconnect if necessary
        if (barrel1 == null) reconnectBarrel1();
        if (barrel2 == null) reconnectBarrel2();

        try {
            if (nextBarrel == 1 && barrel1 != null) {
                inlinks.addAll(barrel1.getInLinks(url));
                nextBarrel = 2;
            } else if (nextBarrel == 2 && barrel2 != null) {
                inlinks.addAll(barrel2.getInLinks(url));
                nextBarrel = 1;
            } else if (barrel1 != null) {
                inlinks.addAll(barrel1.getInLinks(url));
                nextBarrel = 2;
            } else if (barrel2 != null) {
                inlinks.addAll(barrel2.getInLinks(url));
                nextBarrel = 1;
            } else {
                throw new RemoteException("No Barrel available to query inlinks.");
            }

        } catch (Exception e) {
            System.err.println("[Gateway] Error fetching inlinks: " + e.getMessage());
        }

        return inlinks;
    }
}
