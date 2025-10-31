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
            System.out.println("Obtaining info from other barrel...");
            // 1. PRIMEIRO: Inicializar MapDB
            db = DBMaker.fileDB(dbPath)
                    .fileMmapEnableIfSupported()
                    .closeOnJvmShutdown()
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
                .closeOnJvmShutdown()
                .transactionEnable()
                .make();

        pagesInfo = db.hashMap("pagesInfo", Serializer.STRING, Serializer.JAVA).createOrOpen();
        adjacencyList = db.hashMap("adjacencyList", Serializer.STRING, Serializer.JAVA).createOrOpen();
        invertedIndex = db.hashMap("invertedIndex", Serializer.STRING, Serializer.JAVA).createOrOpen();

        ConcurrentMap<String, Integer> loadedExpected = db.hashMap("expectedSeqNumbers",
                Serializer.STRING, Serializer.INTEGER).createOrOpen();
        expectedSeqNumbers = new ConcurrentHashMap<>(loadedExpected);

        ConcurrentMap<String, Set<Integer>> loadedReceived = db.hashMap("receivedSeqNumbers",
                Serializer.STRING, Serializer.JAVA).createOrOpen();
        receivedSeqNumbers = new ConcurrentHashMap<>(loadedReceived);

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
        synchronized (messageLock){
            // garantir estruturas
            Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());
            expectedSeqNumbers.computeIfAbsent(nome, k -> 0);

            // ignorar duplicados
            if (received.contains(seqNumber)) {
                System.out.println("Seq " + seqNumber + " já recebido. Ignorado.");
                return;
            }

            // detetar lacunas antes de qualquer retorno
            checkForMissingMessages(seqNumber, nome, ip,port);

            // aplicar efeitos
            addPageInfo(page);
            for (String link : urls) {
                if (DebugConfig.DEBUG_URL) {
                    System.out.println("[DEBUG]Scraping link: " + link);

                }
                addAdjacency(page.getUrl(),link);
                addUrlToQueue(link);
            }
            addToBloomFilter(page.getUrl());

            // marcar como recebido (mesmo Set guardado no mapa)
            received.add(seqNumber);

            // avançar expected apenas enquanto houver sequência contígua recebida
            int e = expectedSeqNumbers.get(nome);
            while (received.contains(e)) e++;
            expectedSeqNumbers.put(nome, e);

            System.out.println("Mensagem aplicada com seqNumber: " + seqNumber + " (expected agora=" + e + ")");
        }
    }

    /**
     * Add URL to URL queue if it hasen't been indexed yet
     * This will be stored by URLs that downlaoder will parse
     * @param url
     */
    public boolean addUrlToQueue(String url) throws RemoteException {
        synchronized (pageInfoLock) {
            synchronized (filterLock) {
                if (mightContain(url) && pagesInfo.containsKey(url)) {
                    if (DebugConfig.DEBUG_URL_INDEXAR || DebugConfig.DEBUG_ALL) {
                        System.out.println("[DEBUG]: URL já indexada, não adicionada à fila: " + url);
                    }
                    return false;
                }
            }
        }

        synchronized (queueLock) {
            urlQueue.add(url);

            if(DebugConfig.DEBUG_URL_INDEXAR) {
                System.out.println("[DEBUG]: URL adicionada à fila: " + url);
            }
        }

        return true;
    }


    // Substituir addPageInfo() completamente:
    public void addPageInfo(PageInfo pageInfo) throws RemoteException {
        synchronized (pageInfoLock) {
            pagesInfo.put(pageInfo.getUrl(), pageInfo);
            System.out.println("Added PageInfo for URL: " + pageInfo.getUrl());
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
    public void addToBloomFilter(String url) throws RemoteException{
        synchronized (filterLock){
            filter.put(url);
            System.out.println("URL added to Bloom filter: " + url);
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
        int expectedSeqNumber = expectedSeqNumbers.computeIfAbsent(nome, k-> 0);
        Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());

        // Verificar se há falhas entre expectedSeqNumber e receivedSeqNumber
        if (receivedSeqNumber > expectedSeqNumber) {
            System.out.println("Detetada lacuna! Esperava " + expectedSeqNumber + ", recebi " + receivedSeqNumber);

            // Pedir reenvio de todas as mensagens em falta
            for (int missing = expectedSeqNumber; missing < receivedSeqNumber; missing++) {
                if (!receivedSeqNumbers.get(nome).contains(missing)) {
                    requestMissingMessage(missing,nome,ip,port);
                }
            }
        }
    }

    private void requestMissingMessage(int missingSeqNumber, String nome, String ip, Integer port) {
        try {
            System.setProperty("java.rmi.server.hostname", ip);
            /*Nao sei se é preciso*/
            Registry reg = LocateRegistry.getRegistry(port); // host/port por omissão; ajuste se necessário
            DownloaderIndex downloader = (DownloaderIndex) reg.lookup(nome);

            if (downloader == null) {
                System.err.println("Downloader não está disponível para pedir reenvio.");
                return;
            }

            System.out.println("A pedir reenvio da mensagem com seqNumber: " + missingSeqNumber);
            downloader.reSendMessages(missingSeqNumber, this);
        } catch (NotBoundException e) {
            System.err.println("Nome não ligado no RMI Registry: " + nome);
        } catch (RemoteException e) {
            System.err.println("Erro de RMI ao pedir reenvio da mensagem " + missingSeqNumber + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) throws RemoteException, Exception {
        String dbPath = "barrelMapDB.db";

        Barrel barrel = new Barrel(dbPath,"ahhhh");

        if (barrel.pagesInfo.isEmpty() && barrel.adjacencyList.isEmpty()) {
            System.out.println("🔧 Filling structures for the first time...");

            barrel.addUrlToQueue("https://example.com");
            barrel.addUrlToQueue("https://openai.com");
            barrel.addUrlToQueue("https://uc.pt");

            PageInfo page1 = new PageInfo("Example", "https://example.com", List.of("sample", "page", "example"), "Example summary");
            PageInfo page2 = new PageInfo("OpenAI", "https://openai.com", List.of("ai", "language", "model"), "OpenAI summary");
            PageInfo page3 = new PageInfo("UC", "https://uc.pt", List.of("university", "coimbra", "education"), "UC summary");

            barrel.addPageInfo(page1);
            barrel.addPageInfo(page2);
            barrel.addPageInfo(page3);

            barrel.addAdjacency("https://example.com", "https://openai.com");
            barrel.addAdjacency("https://example.com", "https://uc.pt");
            barrel.addAdjacency("https://openai.com", "https://example.com");

            barrel.addToBloomFilter("https://example.com");
            barrel.addToBloomFilter("https://openai.com");
            barrel.addToBloomFilter("https://uc.pt");

            barrel.saveInfo();
        }

        barrel.printAll();
    }
}
