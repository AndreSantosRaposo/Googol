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
 * Barrel class using MapDB instead of files for persistence
 */
public class Barrel extends UnicastRemoteObject implements BarrelIndex {

    int expectedInsertionsBloomFilter = 100000;
    double fpp = 0.01;


    Queue<String> urlQueue;
    ConcurrentMap<String, Set<String>> adjacencyList;
    ConcurrentMap<String, PageInfo> pagesInfo;
    BloomFilter<String> filter;
    String registryName;
    private ConcurrentMap<String, Integer> expectedSeqNumbers;
    private ConcurrentMap<String, Set<Integer>> receivedSeqNumbers;
    private ConcurrentMap<String, Set<String>> invertedIndex;

    //Sync variables
    private final Object queueLock = new Object();
    private final Object adjacencyLock = new Object();
    private final Object filterLock = new Object();
    private final Object pageInfoLock = new Object();
    private final Object messageLock = new Object();
    private final Object invertedIndexLock = new Object();

    private double probabilidadeTemp = 0.0;
    private double probabilidadeTempDownlaoder = 0.0;

    int semaforo;
    DB db;
    String dbPath;

    public Barrel(String dbPath, String registryName) throws IOException {
        super();
        this.registryName = registryName;
        semaforo = 0;
        this.dbPath = dbPath;

        // Concurrency structures
        urlQueue = new ConcurrentLinkedQueue<>();
        filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
        expectedSeqNumbers = new java.util.concurrent.ConcurrentHashMap<>();
        receivedSeqNumbers = new java.util.concurrent.ConcurrentHashMap<>();
        // No construtor (antes de askForInfo)
        invertedIndex = new ConcurrentHashMap<>();

        askForInfo();
        semaforo = 1;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutdown detetado...");
            shutdown();
        }));
    }

    private void askForInfo() throws IOException {
        String filename = "config.txt";
        final int OTHER_BARREL_INDEX = 3;

        try {
            List<String> parts = FileManipulation.lineSplitter(filename, OTHER_BARREL_INDEX, ";");

            if (parts.size() < 3) {
                System.err.println("Linha " + (OTHER_BARREL_INDEX + 1) + " do ficheiro de configuração está incorreta");
                loadInfo();
                return;
            }

            String otherName = parts.get(0).trim();
            String otherIp = parts.get(1).trim();
            int otherPort = Integer.parseInt(parts.get(2).trim());

            Registry registry = LocateRegistry.getRegistry(otherIp, otherPort);
            System.out.println("Registry obtido com sucesso");

            BarrelIndex otherBarrel = (BarrelIndex) registry.lookup(otherName);
            System.out.println("Lookup bem-sucedido! Outro Barrel encontrado: " + otherName);

            if (otherBarrel != null) {
                loadFromOtherBarrel(otherBarrel);
            } else {
                loadInfo();
            }

        } catch (Exception e) {
            System.out.println("Erro ao obter info do outro barrel: " + e.getMessage());
            loadInfo();
        }
    }

    private void loadFromOtherBarrel(BarrelIndex barrelIndex) throws IOException {
        try {
            if(DebugConfig.DEBUG_FICHEIROS || DebugConfig.DEBUG_ALL){
                System.out.println("[DEBUG]: Loading info from other barrel");
            }
            // 1. PRIMEIRO: Inicializar MapDB
            db = DBMaker.fileDB(dbPath)
                    .fileMmapEnableIfSupported()
                    .transactionEnable()
                    .make();

            // 2. Criar os mapas do MapDB
            pagesInfo = db.hashMap("pagesInfo", Serializer.STRING, Serializer.JAVA).createOrOpen();
            adjacencyList = db.hashMap("adjacencyList", Serializer.STRING, Serializer.JAVA).createOrOpen();
            invertedIndex = db.hashMap("invertedIndex", Serializer.STRING, Serializer.JAVA).createOrOpen();

            // 3. Agora copiar dados do outro barrel
            expectedSeqNumbers = barrelIndex.getExpectedSeqNumber();
            receivedSeqNumbers = barrelIndex.getReceivedSeqNumbers();

            pagesInfo.putAll(barrelIndex.getPagesInfoMap());
            adjacencyList.putAll(barrelIndex.getAdjacencyListMap());
            invertedIndex.putAll(barrelIndex.getInvertedIndexMap());

            // 4. Carregar BloomFilter
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

            // 5. Agora db está inicializado e pode fazer commit
            saveInfo();

        } catch(Exception e) {
            System.err.println("Error obtaining info from other barrel: " + e.getMessage());
            loadInfo();
        }
    }

    /**
     * Load info from MapDB
     * This will be used in case I use my own files (I am the reference barrel)
     * This will happen to the first barrel created
     * */

    private void loadInfo() {
        if(DebugConfig.DEBUG_FICHEIROS){
            System.out.println("[DEBUG]: Loading info from MapDB storage...");
        }
        db = DBMaker.fileDB(dbPath)
                .fileMmapEnableIfSupported()
                .transactionEnable()
                .make();

        pagesInfo = db.hashMap("pagesInfo", Serializer.STRING, Serializer.JAVA).createOrOpen();
        adjacencyList = db.hashMap("adjacencyList", Serializer.STRING, Serializer.JAVA).createOrOpen();
        invertedIndex = db.hashMap("invertedIndex", Serializer.STRING, Serializer.JAVA).createOrOpen();

        // Load BloomFilter
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
     * Save all the information to MapDB
     * */
    private void saveInfo() throws IOException {
        ConcurrentMap<String, Integer> dbExpected = db.hashMap("expectedSeqNumbers",
                Serializer.STRING, Serializer.INTEGER).createOrOpen();
        dbExpected.clear();
        dbExpected.putAll(expectedSeqNumbers);

        ConcurrentMap<String, Set<Integer>> dbReceived = db.hashMap("receivedSeqNumbers", Serializer.STRING, Serializer.JAVA).createOrOpen();
        dbReceived.clear();
        dbReceived.putAll(receivedSeqNumbers);

        db.commit();

        try (OutputStream out = new FileOutputStream(dbPath + "_bloom.bin")) {
            synchronized (filterLock){
                filter.writeTo(out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(DebugConfig.DEBUG_FICHEIROS){
            System.out.println("[DEBUG]: Info saved to MapDB storage.");
        }
    }

    public void receiveMessage(int seqNumber, PageInfo page, List<String> urls, String nome, String ip, Integer port) throws RemoteException {
        if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
            if (Math.random() > probabilidadeTempDownlaoder) {
                System.out.println("[DEBUG]: Mensagem falhou a ser entregue (seqNumber: " + seqNumber + ")");
                probabilidadeTempDownlaoder += 0.5;
                return;
            }
        }

        List<Integer> missingSeqNumbers = new ArrayList<>();

        synchronized (messageLock) {
            Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());
            expectedSeqNumbers.computeIfAbsent(nome, k -> 0);

            if (received.contains(seqNumber)) {
                if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                    System.out.println("[DEBUG] Mensagem duplicada recebida com seqNumber: " + seqNumber + ". Ignorada.");
                }
                return;
            }

            // Detetar lacunas (APENAS guardar)
            int expectedSeqNumber = expectedSeqNumbers.get(nome);
            if (seqNumber > expectedSeqNumber) {
                System.out.println("Detetada falha! Esperava " + expectedSeqNumber + ", recebi " + seqNumber);
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
            System.out.println("Mensagem aplicada com seqNumber: " + seqNumber + " (expected agora=" + e + ")");
        }

        // PEDIR REENVIOS FORA DO LOCK
        for (int missing : missingSeqNumbers) {
            System.out.println("A pedir reenvio da mensagem com seqNumber: " + missing);
            new Thread(() -> requestMissingMessage(missing, nome, ip, port)).start();
        }

        // Aplicar efeitos (sem lock de message)
        addPageInfo(page);
        for (String link : urls) {
            addAdjacency(page.getUrl(), link);
            addUrlToQueue(link);
        }
    }

    public void shutdown() {
        try {
            if (db != null && !db.isClosed()) {
                saveInfo();
                db.commit();
                db.close();
                System.out.println("Barrel encerrado com sucesso");
            }
        } catch (Exception e) {
            System.err.println("Erro ao encerrar Barrel: " + e.getMessage());
        }
    }

    /**
     * Add URL to URL queue if it hasen't been indexed yet
     * This will be stored by URLs that downlaoder will parse
     * @param url
     */
    public boolean addUrlToQueue(String url) throws RemoteException {
            synchronized (filterLock) {
                if (mightContain(url)) {
                    System.out.println("[DEBUG]: URL já indexada, não adicionada à fila: " + url);
                    return false;
                }
            }

        synchronized (queueLock) {
            urlQueue.add(url);
            addToBloomFilter(url);
            System.out.println("[DEBUG]: URL adicionada à fila: " + url);
        }

        return true;
    }

    public boolean addUrlToQueue(String url, int seqNumber, String nome, String ip, Integer port) throws RemoteException {
        // 1. Simular perda de mensagens (DEBUG)
        if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
            if (Math.random() > probabilidadeTemp) {
                System.out.println("[DEBUG]: URL falhou a ser entregue (seqNumber: " + seqNumber + ")");
                probabilidadeTemp += 0.5; // diminuir probabilidade de falha na próxima
                return false;
            }
        }

        List<Integer> missingSeqNumbers = new ArrayList<>();

        // 2. Verificar duplicados e detetar lacunas (dentro do lock)
        synchronized (messageLock) {
            Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());
            int expectedSeqNumber = expectedSeqNumbers.computeIfAbsent(nome, k -> 0);

            // Se já foi recebido, ignorar
            if (received.contains(seqNumber)) {
                if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                    System.out.println("[DEBUG]: SeqNumber " + seqNumber + " duplicado. Ignorado.");
                }
                return false;
            }

            // Se há lacuna, guardar números em falta (MAS NÃO PEDIR AINDA)
            if (seqNumber > expectedSeqNumber) {
                System.out.println("Detetada falha! Esperava " + expectedSeqNumber + ", recebi " + seqNumber);

                for (int missing = expectedSeqNumber; missing < seqNumber; missing++) {
                    if (!received.contains(missing)) {
                        missingSeqNumbers.add(missing);
                    }
                }
            }

            // Marcar como recebido
            received.add(seqNumber);

            // Atualizar expectedSeqNumber
            int e = expectedSeqNumber;
            while (received.contains(e)) e++;
            expectedSeqNumbers.put(nome, e);

            System.out.println("SeqNumber " + seqNumber + " processado. Expected agora: " + e);
        }

        // 3. Pedir reenvios FORA do lock (evita deadlock)
        for (int missing : missingSeqNumbers) {
            System.out.println("A pedir reenvio da URL com seqNumber: " + missing);
            new Thread(() -> requestMissingUrl(missing, nome, ip, port)).start();
        }


        boolean urlAdded = false;

        // 4. Verificar se URL já foi indexada
        synchronized (filterLock) {
            if (mightContain(url)) {
                System.out.println("[DEBUG]: URL já indexada: " + url);
            } else {
                synchronized (queueLock) {
                    urlQueue.add(url);
                    addToBloomFilter(url);
                    urlAdded = true;
                    if (DebugConfig.DEBUG_URL_INDEXAR) {
                        System.out.println("[DEBUG]: URL adicionada: " + url);
                    }
                }
            }
        }

        return urlAdded;
    }


    private void checkForMissingUrls(int receivedSeqNumber, String nome, String ip, Integer port) {
        int expectedSeqNumber;
        Set<Integer> received;

        synchronized (messageLock) {
            expectedSeqNumber = expectedSeqNumbers.computeIfAbsent(nome, k-> 0);
            received = new HashSet<>(receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>()));
        }

        // Verificar lacunas
        if (receivedSeqNumber > expectedSeqNumber) {
            System.out.println("Detetada falha! Esperava " + expectedSeqNumber + ", recebi " + receivedSeqNumber);

            for (int missing = expectedSeqNumber; missing < receivedSeqNumber; missing++) {
                if (!received.contains(missing)) {
                    System.out.println("A pedir reenvio da URL com seqNumber: " + missing);
                    requestMissingUrl(missing, nome, ip, port);
                }
            }
        }
    }

    private void requestMissingUrl(int missingSeqNumber, String nome, String ip, Integer port) {
        try {
            System.setProperty("java.rmi.server.hostname", ip);

            Registry reg = LocateRegistry.getRegistry(ip, port);
            GatewayInterface gateway = (GatewayInterface) reg.lookup(nome);

            if (gateway == null) {
                System.err.println("Downloader não está disponível para pedir reenvio.");
                return;
            }

            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.out.println("A pedir reenvio da URL com seqNumber: " + missingSeqNumber);
            }
            gateway.reSendURL(missingSeqNumber, this);
        } catch (NotBoundException e) {
            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.err.println("Nome não ligado no RMI Registry: " + nome);
            }
        } catch (RemoteException e) {
            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.err.println("Erro de RMI ao pedir reenvio da URL " + missingSeqNumber + ": " + e.getMessage());
            }
        }
    }

    // Substituir addPageInfo() completamente:
    public void addPageInfo(PageInfo pageInfo) throws RemoteException {
        synchronized (pageInfoLock) {
            pagesInfo.put(pageInfo.getUrl(), pageInfo);
           // System.out.println("Added PageInfo for URL: " + pageInfo.getUrl());
        }

        // Atualizar índice invertido com as palavras desta página
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
     * Add adjacency (fromUrl → toUrl)
     * @param fromUrl
     * @param toUrl
     */

    public void addAdjacency(String fromUrl, String toUrl) throws RemoteException {
        synchronized (adjacencyLock) {
            // Obter Set atual ou criar novo
            Set<String> adjacencies = adjacencyList.getOrDefault(toUrl, ConcurrentHashMap.newKeySet());

            // Adicionar nova adjacência
            adjacencies.add(fromUrl);

            // Put explícito para garantir persistência no MapDB
            adjacencyList.put(toUrl, adjacencies);


           // System.out.println("Adjacência adicionada: " + fromUrl + " -> " + toUrl);
}
    }

    /**
     * Add URL to Bloom filter
     * @param url
     */
    private void addToBloomFilter(String url) throws RemoteException{
        synchronized (filterLock){
            filter.put(url);
            if(DebugConfig.DEBUG_URL_INDEXAR) {
                System.out.println("[DEBUG]: URL adicionada ao Bloom filter: " + url);
            }
        }
    }

    /**
     * Check if URL might have been already parsed
     * @param url
     * @return
     */
    //This needs to be called by the gateway when he is going to add urls manually
    private boolean mightContain(String url) {
            return filter.mightContain(url);
    }


    /**
     * Get URL from queue
     * @return URL or null if queue is empty
     */
    public String getUrlFromQueue() throws RemoteException {
        synchronized (queueLock){
            return urlQueue.poll();
        }
    }


    public ConcurrentMap<String, Integer> getExpectedSeqNumber() throws RemoteException {
        return new ConcurrentHashMap<>(expectedSeqNumbers);
    }

    public ConcurrentMap<String, Set<Integer>> getReceivedSeqNumbers() throws RemoteException {
        // Cópia profunda
        ConcurrentMap<String, Set<Integer>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : receivedSeqNumbers.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Getter for pagesInfo map
     * @return pagesInfo map
     * @throws RemoteException
     */
    public ConcurrentMap<String, PageInfo> getPagesInfoMap() throws RemoteException {
        synchronized (pageInfoLock) {
            return new ConcurrentHashMap<>(pagesInfo);
        }
    }

    /**
     * Getter for adjacencyList map
     * @return adjacencyList map
     * @throws RemoteException
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
     * Getter for Bloom filter bytes
     * @return bloom filter as byte array
     * @throws RemoteException
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

    public ConcurrentMap<String, Set<String>> getInvertedIndexMap() throws RemoteException {
        synchronized (invertedIndexLock) {
            // Cópia profunda
            ConcurrentMap<String, Set<String>> copy = new ConcurrentHashMap<>();
            for (Map.Entry<String, Set<String>> entry : invertedIndex.entrySet()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return copy;
        }
    }

    /**
     * Print all current state (for debugging or statistics)
     */
    private void printAll() {
        System.out.println("\n===== Barrel Current State =====");
        System.out.println("URLs in queue: " + urlQueue);
        System.out.println("Adjacency List: " + adjacencyList);
        System.out.println("Pages Info keys: " + pagesInfo.keySet());
        System.out.println("Bloom Filter test (example.com): " + filter.mightContain("https://example.com"));
        System.out.println("================================\n");
    }

    // Substituir searchPages() para usar o índice invertido:
    public List<PageInfo> searchPages(List<String> terms) throws RemoteException {
        if (terms == null || terms.isEmpty()) return new ArrayList<>();

        synchronized (invertedIndexLock) {
            synchronized (pageInfoLock) {
                // Obter URLs que contêm o primeiro termo
                Set<String> resultUrls = new HashSet<>(
                        invertedIndex.getOrDefault(terms.get(0).toLowerCase(), Collections.emptySet())
                );

                // Interseção com URLs dos restantes termos
                for (int i = 1; i < terms.size(); i++) {
                    Set<String> termUrls = invertedIndex.getOrDefault(terms.get(i).toLowerCase(), Collections.emptySet());
                    resultUrls.retainAll(termUrls);
                    if (resultUrls.isEmpty()) break; // otimização
                }

                // Converter URLs para PageInfo
                List<PageInfo> results = new ArrayList<>();
                for (String url : resultUrls) {
                    PageInfo page = pagesInfo.get(url);
                    if (page != null) results.add(page);
                }

                System.out.println("Search for " + terms + " returned " + results.size() + " results.");
                return results;
            }
        }
    }

    private void checkForMissingMessages(int receivedSeqNumber, String nome, String ip, Integer port) {
        synchronized (messageLock) {
            int expectedSeqNumber = expectedSeqNumbers.computeIfAbsent(nome, k-> 0);
            Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());

            // Verificar se há falhas entre expectedSeqNumber e receivedSeqNumber
            if (receivedSeqNumber > expectedSeqNumber) {
                System.out.println("Detetada falha! Esperava " + expectedSeqNumber + ", recebi " + receivedSeqNumber);

                // Pedir reenvio de todas as mensagens em falta
                for (int missing = expectedSeqNumber; missing < receivedSeqNumber; missing++) {
                    if (!received.contains(missing)) {
                        requestMissingMessage(missing, nome, ip, port);
                    }
                }
            }
        }
    }

    private void requestMissingMessage(int missingSeqNumber, String nome, String ip, Integer port) {
        try {
            System.setProperty("java.rmi.server.hostname", ip);

            Registry reg = LocateRegistry.getRegistry(port); // host/port por omissão; ajuste se necessário
            DownloaderIndex downloader = (DownloaderIndex) reg.lookup(nome);

            if (downloader == null) {
                System.err.println("Downloader não está disponível para pedir reenvio.");
                return;
            }

            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER) {
                System.out.println("A pedir reenvio da mensagem com seqNumber: " + missingSeqNumber);
            }

            downloader.reSendMessages(missingSeqNumber, this);
        } catch (NotBoundException e) {
            System.err.println("Nome não ligado no RMI Registry: " + nome);
        } catch (RemoteException e) {
            System.err.println("Erro de RMI ao pedir reenvio da mensagem " + missingSeqNumber + ": " + e.getMessage());
        }
    }

    /**
     * Reset seq numbers for the gateway
     * @param gatewayName
     * @throws RemoteException
     */
    public synchronized void resetSeqNumbers(String gatewayName) throws RemoteException {
        try {
            // Inicializar HashMaps se necessário
            if (receivedSeqNumbers == null) {
                receivedSeqNumbers = new ConcurrentHashMap<>();
            }
            if (expectedSeqNumbers == null) {
                expectedSeqNumbers = new ConcurrentHashMap<>();
            }

            // Limpar ou inicializar para este downloader
            receivedSeqNumbers.put(gatewayName, new HashSet<>());
            expectedSeqNumbers.put(gatewayName, 0);

            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                System.out.println("[DEBUG] Seq numbers reset for: " + gatewayName + "in Barrel: " + registryName);
            }
        } catch (Exception e) {
            System.err.println("[" + registryName + "] Erro ao fazer reset: " + e.getMessage());
            throw new RemoteException("Erro no reset de seqNumbers", e);
        }
    }

}
