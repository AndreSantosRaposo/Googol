package webServer;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.mapdb.*;

/**
 * Barrel class responsible for storing and indexing web pages.
 * Uses MapDB for persistence and Bloom filters for efficient URL lookups.
 * Supports distributed indexing with multiple Barrel instances.
 *
 * @author Andre Raposo
 * @author Paulo Vilar
 * @version 1.0
 */
public class Barrel extends UnicastRemoteObject implements BarrelIndex {

    //Bloom filter parameters
    int expectedInsertionsBloomFilter = 100000;
    double fpp = 0.01;

    String registryName;

    private SystemStats stats;
    private List<Long> responseTimes;

    Queue<String> urlQueue;
    ConcurrentMap<String, Set<String>> adjacencyList;
    ConcurrentMap<String, PageInfo> pagesInfo;
    BloomFilter<String> filter;
    private ConcurrentMap<String, Integer> expectedSeqNumbers;
    private ConcurrentMap<String, Set<Integer>> receivedSeqNumbers;
    private ConcurrentMap<String, Set<String>> invertedIndex;

    //Synchronization locks */
    private final Object queueLock = new Object();
    private final Object adjacencyLock = new Object();
    private final Object filterLock = new Object();
    private final Object pageInfoLock = new Object();
    private final Object messageLock = new Object();
    private final Object invertedIndexLock = new Object();
    int semaforo;

    // Probability variables for simulating message loss (debugging)
    private double probabilidadeTemp = 0.0;
    private double probabilidadeTempDownlaoder = 0.0;



    DB db;
    String dbPath;

    /**
     * Constructs a new Barrel instance.
     * Initializes data structures, loads existing data, and registers shutdown hook.
     *
     * @param dbPath Path to the MapDB database file
     * @param registryName Name for RMI registry binding
     * @throws IOException if database initialization fails
     */
    public Barrel(String dbPath, String registryName) throws IOException {
        super();
        this.registryName = registryName;
        semaforo = 0;
        this.dbPath = dbPath;

        // Initialize concurrent data structures
        urlQueue = new ConcurrentLinkedQueue<>();
        filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
        expectedSeqNumbers = new java.util.concurrent.ConcurrentHashMap<>();
        receivedSeqNumbers = new java.util.concurrent.ConcurrentHashMap<>();
        invertedIndex = new ConcurrentHashMap<>();

        this.stats = new SystemStats();
        this.responseTimes = Collections.synchronizedList(new ArrayList<>());

        askForInfo();
        semaforo = 1;

        // Register shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown detected...");
            shutdown();
        }));
    }

    /**
     * Attempts to load data from another Barrel instance or from local storage.
     * First tries to connect to another Barrel via RMI, falls back to local files if unavailable.
     *
     * @throws IOException if file reading fails
     */
    private void askForInfo() throws IOException {
        String filename = "config.txt";
        final int OTHER_BARREL_INDEX = 3;

        try {
            List<String> parts = FileManipulation.lineSplitter(filename, OTHER_BARREL_INDEX, ";");

            if (parts.size() < 3) {
                System.err.println("Configuration file line " + (OTHER_BARREL_INDEX + 1) + " is incorrect");
                loadInfo();
                return;
            }

            String otherName = parts.get(0).trim();
            String otherIp = parts.get(1).trim();
            int otherPort = Integer.parseInt(parts.get(2).trim());

            Registry registry = LocateRegistry.getRegistry(otherIp, otherPort);
            System.out.println("Registry obtained successfully");

            BarrelIndex otherBarrel = (BarrelIndex) registry.lookup(otherName);
            System.out.println("Lookup sucecssful! Other Barrel found: " + otherName);

            if (otherBarrel != null) {
                loadFromOtherBarrel(otherBarrel);
            } else {
                loadInfo();
            }

        } catch (Exception e) {
            System.out.println("Error obtaining info from other barrel: " + e.getMessage());
            loadInfo();
        }
    }

    /**
     * Loads data from another Barrel instance via RMI.
     * Copies all indexes, page information, and synchronization state.
     *
     * @param barrelIndex Reference to the other Barrel instance
     * @throws IOException if data transfer fails
     */
    private void loadFromOtherBarrel(BarrelIndex barrelIndex) throws IOException {
        try {
            if(DebugConfig.DEBUG_FICHEIROS || DebugConfig.DEBUG_ALL){
                System.out.println("[DEBUG] Loading info from other barrel");
            }

            // Initialize MapDB
            db = DBMaker.fileDB(dbPath)
                    .fileMmapEnableIfSupported()
                    .transactionEnable()
                    .make();

            // Create MapDB maps
            pagesInfo = db.hashMap("pagesInfo", Serializer.STRING, Serializer.JAVA).createOrOpen();
            adjacencyList = db.hashMap("adjacencyList", Serializer.STRING, Serializer.JAVA).createOrOpen();
            invertedIndex = db.hashMap("invertedIndex", Serializer.STRING, Serializer.JAVA).createOrOpen();

            // Copy data from other barrel
            expectedSeqNumbers = barrelIndex.getExpectedSeqNumber();
            receivedSeqNumbers = barrelIndex.getReceivedSeqNumbers();

            pagesInfo.putAll(barrelIndex.getPagesInfoMap());
            adjacencyList.putAll(barrelIndex.getAdjacencyListMap());
            invertedIndex.putAll(barrelIndex.getInvertedIndexMap());

            // Load Bloom filter
            byte[] bloomFilterBytes = barrelIndex.getBloomFilterBytes();
            try (ByteArrayInputStream in = new ByteArrayInputStream(bloomFilterBytes)) {
                filter = BloomFilter.readFrom(in, Funnels.unencodedCharsFunnel());
                System.out.println("Bloom filter loaded from other barrel.");
            }

            if (DebugConfig.DEBUG_FICHEIROS) {
                System.out.println("=================== [DEBUG] ===================");
                System.out.println("PagesInfo loaded: " + pagesInfo.size());
                System.out.println("AdjacencyList loaded: " + adjacencyList.size());
                System.out.println("InvertedIndex loaded: " + invertedIndex.size());
                System.out.println("ExpectedSeqNumbers loaded: " + expectedSeqNumbers.size());
                System.out.println("ReceivedSeqNumbers loaded: " + receivedSeqNumbers.size());
                System.out.println("BloomFilter mightContain('https://example.com'): " + filter.mightContain("https://example.com"));
                System.out.println("===============================================");
            }

            saveInfo();

        } catch(Exception e) {
            System.err.println("Error obtaining info from other barrel: " + e.getMessage());
            loadInfo();
        }
    }

    /**
     * Loads data from local MapDB storage.
     * Used when no other Barrel instance is available (first Barrel startup).
     */
    private void loadInfo() {
        if(DebugConfig.DEBUG_FICHEIROS){
            System.out.println("[DEBUG] Loading info from MapDB storage...");
        }

        db = DBMaker.fileDB(dbPath)
                .fileMmapEnableIfSupported()
                .transactionEnable()
                .make();

        pagesInfo = db.hashMap("pagesInfo", Serializer.STRING, Serializer.JAVA).createOrOpen();
        adjacencyList = db.hashMap("adjacencyList", Serializer.STRING, Serializer.JAVA).createOrOpen();
        invertedIndex = db.hashMap("invertedIndex", Serializer.STRING, Serializer.JAVA).createOrOpen();

        // Load Bloom filter from file
        File bloomFile = new File(dbPath + "_bloom.bin");
        if (bloomFile.exists()) {
            try (InputStream in = new FileInputStream(bloomFile)) {
                filter = BloomFilter.readFrom(in, Funnels.unencodedCharsFunnel());
                if(DebugConfig.DEBUG_FICHEIROS){
                    System.out.println("[DEBUG] Bloom filter loaded from file.");
                }
            } catch (IOException e) {
                if(DebugConfig.DEBUG_FICHEIROS){
                    System.out.println("[DEBUG] Error loading Bloom filter from file. Creating new empty filter.");
                }
                filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
            }
        } else {
            if(DebugConfig.DEBUG_FICHEIROS){
                System.out.println("[DEBUG] Bloom filter file not found. Creating new empty filter.");
            }
            filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
        }

        if (DebugConfig.DEBUG_FICHEIROS) {
            System.out.println("=================== [DEBUG] ===================");
            System.out.println("PagesInfo loaded: " + pagesInfo.size());
            System.out.println("AdjacencyList loaded: " + adjacencyList.size());
            System.out.println("InvertedIndex loaded: " + invertedIndex.size());
            System.out.println("ExpectedSeqNumbers loaded: " + expectedSeqNumbers.size());
            System.out.println("ReceivedSeqNumbers loaded: " + receivedSeqNumbers.size());
            System.out.println("BloomFilter mightContain('https://example.com'): " + filter.mightContain("https://example.com"));
            System.out.println("===============================================");
        }
    }

    /**
     * Persists all in-memory data structures to MapDB storage.
     * Saves sequence numbers, Bloom filter, and commits transaction.
     *
     * @throws IOException if file writing fails
     */
    private void saveInfo() throws IOException {
        ConcurrentMap<String, Integer> dbExpected = db.hashMap("expectedSeqNumbers",
                Serializer.STRING, Serializer.INTEGER).createOrOpen();
        dbExpected.clear();
        dbExpected.putAll(expectedSeqNumbers);

        ConcurrentMap<String, Set<Integer>> dbReceived = db.hashMap("receivedSeqNumbers", Serializer.STRING, Serializer.JAVA).createOrOpen();
        dbReceived.clear();
        dbReceived.putAll(receivedSeqNumbers);

        db.commit();

        // Save Bloom filter to separate file
        try (OutputStream out = new FileOutputStream(dbPath + "_bloom.bin")) {
            synchronized (filterLock){
                filter.writeTo(out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(DebugConfig.DEBUG_FICHEIROS){
            System.out.println("[DEBUG] Info saved to MapDB storage.");
        }
    }

    /**
     * Returns system statistics including index size and response times.
     *
     * @return SystemStats object containing current metrics
     * @throws RemoteException if RMI communication fails
     */
    public SystemStats getStats() throws RemoteException {
        int indexSize = pagesInfo.size();
        long avgTime = calculateAvgResponseTime();
        stats.updateBarrelMetrics(registryName, indexSize, avgTime);
        return stats;
    }

    /**
     * Calculates average response time from recorded search operations.
     *
     * @return Average response time in milliseconds, or 0 if no data available
     */
    private long calculateAvgResponseTime() {
        if (responseTimes.isEmpty()) return 0;
        return (long) responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Receives and processes a message from a Downloader containing page information.
     * Handles sequence number validation, duplicate detection, and missing message recovery.
     *
     * @param seqNumber Sequence number of this message
     * @param page Page information to index
     * @param urls List of URLs found on the page
     * @param nome Name of the sender (Downloader)
     * @param ip IP address of the sender
     * @param port Port of the sender
     * @throws RemoteException if RMI communication fails
     */
    public void receiveMessage(int seqNumber, PageInfo page, List<String> urls, String nome, String ip, Integer port) throws RemoteException {
        // Simulate message loss for debugging
        if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
            if (Math.random() > probabilidadeTempDownlaoder) {
                System.out.println("[DEBUG] Message delivery failed (seqNumber: " + seqNumber + ")");
                probabilidadeTempDownlaoder += 0.5;
                return;
            }
        }

        List<Integer> missingSeqNumbers = new ArrayList<>();

        synchronized (messageLock) {
            Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());
            expectedSeqNumbers.computeIfAbsent(nome, k -> 0);

            // Check for duplicate messages
            if (received.contains(seqNumber)) {
                if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                    System.out.println("[DEBUG] Duplicate message received with seqNumber: " + seqNumber + ". Ignored.");
                }
                return;
            }

            // Detect gaps in sequence numbers
            int expectedSeqNumber = expectedSeqNumbers.get(nome);
            if (seqNumber > expectedSeqNumber) {
                System.out.println("Gap detected! Expected " + expectedSeqNumber + ", received " + seqNumber);
                for (int missing = expectedSeqNumber; missing < seqNumber; missing++) {
                    if (!received.contains(missing)) {
                        missingSeqNumbers.add(missing);
                    }
                }
            }

            received.add(seqNumber);
            int e = expectedSeqNumber;
            while (received.contains(e)) e++;
            expectedSeqNumbers.put(nome, e);
            System.out.println("Message applied with seqNumber: " + seqNumber + " (expected now=" + e + ")");
        }

        // Request missing messages outside lock
        for (int missing : missingSeqNumbers) {
            System.out.println("Requesting resend of message with seqNumber: " + missing);
            new Thread(() -> requestMissingMessage(missing, nome, ip, port)).start();
        }

        // Apply message effects
        addPageInfo(page);
        for (String link : urls) {
            addAdjacency(page.getUrl(), link);
            addUrlToQueue(link);
        }
    }

    /**
     * Gracefully shuts down the Barrel, saving all data to disk.
     */
    public void shutdown() {
        try {
            if (db != null && !db.isClosed()) {
                saveInfo();
                db.commit();
                db.close();
                System.out.println("Barrel shut down successfully");
            }
        } catch (Exception e) {
            System.err.println("Error shutting down Barrel: " + e.getMessage());
        }
    }

    /**
     * Adds a URL to the processing queue if not already indexed.
     *
     * @param url URL to add to queue
     * @return true if URL was added, false if already indexed
     * @throws RemoteException if RMI communication fails
     */
    public boolean addUrlToQueue(String url) throws RemoteException {
        synchronized (filterLock) {
            if (mightContain(url)) {
                System.out.println("[DEBUG] URL already indexed, not added to queue: " + url);
                return false;
            }
        }

        synchronized (queueLock) {
            urlQueue.add(url);
            addToBloomFilter(url);
            System.out.println("[DEBUG] URL added to queue: " + url);
        }

        return true;
    }


    /**
     * Adds a URL to the processing queue with sequence number tracking.
     * Handles duplicate detection and missing message recovery from Gateway.
     *
     * @param url URL to add
     * @param seqNumber Sequence number from Gateway
     * @param nome Name of Gateway
     * @param ip IP of Gateway
     * @param port Port of Gateway
     * @return true if URL was added, false otherwise
     * @throws RemoteException if RMI communication fails
     */
    public boolean addUrlToQueue(String url, int seqNumber, String nome, String ip, Integer port) throws RemoteException {
        // Simulate message loss for debugging
        if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
            if (Math.random() > probabilidadeTemp) {
                System.out.println("[DEBUG] URL delivery failed (seqNumber: " + seqNumber + ")");
                probabilidadeTemp += 0.5;
                return false;
            }
        }

        List<Integer> missingSeqNumbers = new ArrayList<>();

        // Check duplicates and detect gaps
        synchronized (messageLock) {
            Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());
            int expectedSeqNumber = expectedSeqNumbers.computeIfAbsent(nome, k -> 0);

            if (received.contains(seqNumber)) {
                if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                    System.out.println("[DEBUG] SeqNumber " + seqNumber + " duplicate. Ignored.");
                }
                return false;
            }

            if (seqNumber > expectedSeqNumber) {
                System.out.println("Gap detected! Expected " + expectedSeqNumber + ", received " + seqNumber);

                for (int missing = expectedSeqNumber; missing < seqNumber; missing++) {
                    if (!received.contains(missing)) {
                        missingSeqNumbers.add(missing);
                    }
                }
            }

            received.add(seqNumber);

            int e = expectedSeqNumber;
            while (received.contains(e)) e++;
            expectedSeqNumbers.put(nome, e);

            System.out.println("SeqNumber " + seqNumber + " processed. Expected now: " + e);
        }

        // Request missing URLs outside lock
        for (int missing : missingSeqNumbers) {
            System.out.println("Requesting resend of URL with seqNumber: " + missing);
            new Thread(() -> requestMissingUrl(missing, nome, ip, port)).start();
        }

        boolean urlAdded = false;

        // Check if URL already indexed
        synchronized (filterLock) {
            if (mightContain(url)) {
                System.out.println("[DEBUG] URL already indexed: " + url);
            } else {
                synchronized (queueLock) {
                    urlQueue.add(url);
                    addToBloomFilter(url);
                    urlAdded = true;
                    if (DebugConfig.DEBUG_URL_INDEXAR) {
                        System.out.println("[DEBUG] URL added: " + url);
                    }
                }
            }
        }

        return urlAdded;
    }


    /**
     * Requests a missing URL from the Gateway.
     *
     * @param missingSeqNumber Sequence number of missing URL
     * @param nome Gateway name
     * @param ip Gateway IP
     * @param port Gateway port
     */
    private void requestMissingUrl(int missingSeqNumber, String nome, String ip, Integer port) {
        try {
            System.setProperty("java.rmi.server.hostname", ip);

            Registry reg = LocateRegistry.getRegistry(ip, port);
            GatewayInterface gateway = (GatewayInterface) reg.lookup(nome);

            if (gateway == null) {
                System.err.println("Gateway not available to request resend.");
                return;
            }

            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.out.println("Requesting resend of URL with seqNumber: " + missingSeqNumber);
            }
            gateway.reSendURL(missingSeqNumber, this);
        } catch (NotBoundException e) {
            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.err.println("Name not bound in RMI Registry: " + nome);
            }
        } catch (RemoteException e) {
            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.err.println("RMI error requesting resend of URL " + missingSeqNumber + ": " + e.getMessage());
            }
        }
    }

    /**
     * Adds page information to the index and updates the inverted index.
     *
     * @param pageInfo Page information to add
     * @throws RemoteException if RMI communication fails
     */
    public void addPageInfo(PageInfo pageInfo) throws RemoteException {
        synchronized (pageInfoLock) {
            pagesInfo.put(pageInfo.getUrl(), pageInfo);
        }

        // Update inverted index with page words
        synchronized (invertedIndexLock) {
            for (String word : pageInfo.getWords()) {
                String lowerWord = word.toLowerCase();
                Set<String> urls = invertedIndex.getOrDefault(lowerWord, ConcurrentHashMap.newKeySet());
                urls.add(pageInfo.getUrl());
                invertedIndex.put(lowerWord, urls);
            }
            System.out.println("Updated inverted index for URL: " + pageInfo.getUrl());
        }
    }

    /**
     * Adds an adjacency relationship (fromUrl points to toUrl).
     *
     * @param fromUrl Source URL
     * @param toUrl Target URL
     * @throws RemoteException if RMI communication fails
     */
    public void addAdjacency(String fromUrl, String toUrl) throws RemoteException {
        synchronized (adjacencyLock) {
            Set<String> adjacencies = adjacencyList.getOrDefault(toUrl, ConcurrentHashMap.newKeySet());
            adjacencies.add(fromUrl);
            adjacencyList.put(toUrl, adjacencies);
        }
    }

    /**
     * Adds a URL to the Bloom filter for fast existence checks.
     *
     * @param url URL to add
     * @throws RemoteException if RMI communication fails
     */
    private void addToBloomFilter(String url) throws RemoteException{
        synchronized (filterLock){
            filter.put(url);
            if(DebugConfig.DEBUG_URL_INDEXAR) {
                System.out.println("[DEBUG] URL added to Bloom filter: " + url);
            }
        }
    }

    /**
     * Checks if a URL might have been already indexed using Bloom filter.
     *
     * @param url URL to check
     * @return true if URL might exist, false if definitely doesn't exist
     */
    private boolean mightContain(String url) {
        return filter.mightContain(url);
    }

    /**
     * Retrieves and removes the next URL from the processing queue.
     *
     * @return Next URL or null if queue is empty
     * @throws RemoteException if RMI communication fails
     */
    public String getUrlFromQueue() throws RemoteException {
        synchronized (queueLock){
            return urlQueue.poll();
        }
    }

    /**
     * Returns a copy of expected sequence numbers map.
     *
     * @return Map of sender names to expected sequence numbers
     * @throws RemoteException if RMI communication fails
     */
    public ConcurrentMap<String, Integer> getExpectedSeqNumber() throws RemoteException {
        return new ConcurrentHashMap<>(expectedSeqNumbers);
    }

    /**
     * Returns a deep copy of received sequence numbers map.
     *
     * @return Map of sender names to sets of received sequence numbers
     * @throws RemoteException if RMI communication fails
     */
    public ConcurrentMap<String, Set<Integer>> getReceivedSeqNumbers() throws RemoteException {
        ConcurrentMap<String, Set<Integer>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : receivedSeqNumbers.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Returns a copy of the pages info map.
     *
     * @return Map of URLs to PageInfo objects
     * @throws RemoteException if RMI communication fails
     */
    public ConcurrentMap<String, PageInfo> getPagesInfoMap() throws RemoteException {
        synchronized (pageInfoLock) {
            return new ConcurrentHashMap<>(pagesInfo);
        }
    }

    /**
     * Returns a deep copy of the adjacency list.
     *
     * @return Map of URLs to sets of incoming link URLs
     * @throws RemoteException if RMI communication fails
     */
    public ConcurrentMap<String, Set<String>> getAdjacencyListMap() throws RemoteException {
        synchronized (adjacencyLock) {
            ConcurrentMap<String, Set<String>> copy = new ConcurrentHashMap<>();
            for (Map.Entry<String, Set<String>> entry : adjacencyList.entrySet()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return copy;
        }
    }

    /**
     * Serializes the Bloom filter to a byte array.
     *
     * @return Bloom filter as byte array
     * @throws RemoteException if RMI communication or serialization fails
     */
    public byte[] getBloomFilterBytes() throws RemoteException {
        synchronized (filterLock){
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                filter.writeTo(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new RemoteException("Error getting Bloom filter bytes", e);
            }
        }
    }

    /**
     * Returns a deep copy of the inverted index.
     *
     * @return Map of words to sets of URLs containing them
     * @throws RemoteException if RMI communication fails
     */
    public ConcurrentMap<String, Set<String>> getInvertedIndexMap() throws RemoteException {
        synchronized (invertedIndexLock) {
            ConcurrentMap<String, Set<String>> copy = new ConcurrentHashMap<>();
            for (Map.Entry<String, Set<String>> entry : invertedIndex.entrySet()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return copy;
        }
    }

    /**
     * Prints current Barrel state for debugging.
     */
    private void printAll() {
        System.out.println("\n===== Barrel Current State =====");
        System.out.println("URLs in queue: " + urlQueue);
        System.out.println("Adjacency List: " + adjacencyList);
        System.out.println("Pages Info keys: " + pagesInfo.keySet());
        System.out.println("Bloom Filter test (example.com): " + filter.mightContain("https://example.com"));
        System.out.println("================================\n");
    }

    /**
     * Searches for pages containing all specified terms using the inverted index.
     * Results are sorted by number of incoming links (PageRank-like).
     *
     * @param terms List of search terms
     * @return List of PageInfo objects matching all terms, sorted by relevance
     * @throws RemoteException if RMI communication fails
     */
    public List<PageInfo> searchPages(List<String> terms) throws RemoteException {
        long startTime = System.currentTimeMillis();

        if (terms == null || terms.isEmpty()) return new ArrayList<>();

        synchronized (invertedIndexLock) {
            synchronized (pageInfoLock) {
                Set<String> resultUrls = new HashSet<>(
                        invertedIndex.getOrDefault(terms.get(0).toLowerCase(), Collections.emptySet())
                );

                for (int i = 1; i < terms.size(); i++) {
                    Set<String> termUrls = invertedIndex.getOrDefault(terms.get(i).toLowerCase(), Collections.emptySet());
                    resultUrls.retainAll(termUrls);
                    if (resultUrls.isEmpty()) break;
                }

                List<PageInfo> results = new ArrayList<>();
                for (String url : resultUrls) {
                    PageInfo page = pagesInfo.get(url);
                    if (page != null) results.add(page);
                }

                synchronized (adjacencyLock) {
                    results.sort((p1, p2) -> {
                        int linksP1 = adjacencyList.getOrDefault(p1.getUrl(), Set.of()).size();
                        int linksP2 = adjacencyList.getOrDefault(p2.getUrl(), Set.of()).size();
                        return Integer.compare(linksP2, linksP1);
                    });
                }

                long duration = System.currentTimeMillis() - startTime;
                responseTimes.add(duration);

                System.out.println("Search for " + terms + " returned " + results.size() + " results.");
                return results;
            }
        }
    }

    /**
     * Requests a missing message from a Downloader.
     *
     * @param missingSeqNumber Sequence number of missing message
     * @param nome Downloader name
     * @param ip Downloader IP
     * @param port Downloader port
     */
    private void requestMissingMessage(int missingSeqNumber, String nome, String ip, Integer port) {
        try {
            System.setProperty("java.rmi.server.hostname", ip);

            Registry reg = LocateRegistry.getRegistry(port);
            DownloaderIndex downloader = (DownloaderIndex) reg.lookup(nome);

            if (downloader == null) {
                System.err.println("Downloader not available to request resend.");
                return;
            }

            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.out.println("Requesting resend of message with seqNumber: " + missingSeqNumber);
            }

            downloader.reSendMessages(missingSeqNumber, this);
        } catch (NotBoundException e) {
            System.err.println("Name not bound in RMI Registry: " + nome);
        } catch (RemoteException e) {
            System.err.println("RMI error requesting resend of message " + missingSeqNumber + ": " + e.getMessage());
        }
    }

    /**
     * Resets sequence number tracking for a specific sender (Gateway or Downloader).
     * Called when a sender reconnects or restarts.
     *
     * @param gatewayName Name of the sender to reset
     * @throws RemoteException if RMI communication fails
     */
    public synchronized void resetSeqNumbers(String gatewayName) throws RemoteException {
        try {
            if (receivedSeqNumbers == null) {
                receivedSeqNumbers = new ConcurrentHashMap<>();
            }
            if (expectedSeqNumbers == null) {
                expectedSeqNumbers = new ConcurrentHashMap<>();
            }

            receivedSeqNumbers.put(gatewayName, new HashSet<>());
            expectedSeqNumbers.put(gatewayName, 0);

            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                System.out.println("[DEBUG] Seq numbers reset for: " + gatewayName + " in Barrel: " + registryName);
            }

        } catch (Exception e) {
            System.err.println("[" + registryName + "] Error resetting: " + e.getMessage());
            throw new RemoteException("Error resetting seqNumbers", e);
        }
    }

    /**
     * Returns list of URLs that link to the specified URL (incoming links).
     *
     * @param url Target URL
     * @return List of URLs pointing to the target URL
     * @throws RemoteException if RMI communication fails
     */
    public List<String> getInLinks(String url) throws RemoteException {
        synchronized (adjacencyLock) {
            Set<String> inlinks = adjacencyList.getOrDefault(url, Set.of());
            return new ArrayList<>(inlinks);
        }
    }
}
