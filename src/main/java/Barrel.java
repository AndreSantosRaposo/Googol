import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.io.*;
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

    // O downlaoder vai chamar add e remove (URLs) no multicast por isso não vai haver problemas de inconsistencia
    Queue<String> urlQueue;
    ConcurrentMap<String, Set<String>> adjacencyList;
    ConcurrentMap<String, PageInfo> pagesInfo;
    BloomFilter<String> filter;
    String registryName;
    private HashMap<String,Integer> expectedSeqNumbers;
    private HashMap<String,Set<Integer>> receivedSeqNumbers;
    int semaforo;
    DB db;
    String dbPath;

    public Barrel(String dbPath, String registryName) throws IOException {
        super();
        this.registryName = registryName;
        semaforo = 0;
        this.dbPath = dbPath;
        urlQueue = new LinkedList<>();
        filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
        expectedSeqNumbers = new HashMap<>();
        receivedSeqNumbers = new HashMap<>();

        askForInfo();
        semaforo = 1;
    }

    private void askForInfo() throws IOException {
        try {
            if (true) { //se for o primeiro barrel, fazer laod normalmente
                loadInfo();
            } else {
                /*
                //pedir info ao outro barrel (como vou buscar esse barrel no registry)
                pagesInfo = barrelIndex.getPagesInfoMap();
                adjacencyList = barrelIndex.getAdjacencyListMap();
                // Load BloomFilter
                File bloomFile = new File(dbPath + "_bloom.bin");
                if (bloomFile.exists()) {
                    try (InputStream in = new FileInputStream(bloomFile)) {
                        filter = barrelIndex.getBloomFilterBytes();
                        System.out.println("Bloom filter loaded from other barrel.");
                    } catch (IOException e) {
                        System.err.println("Error loading Bloom filter: " + e.getMessage());
                        filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
                    }
                } else {
                    System.out.println("Bloom filter file not found. Created new empty filter.");
                }
                saveInfo();

                 */
            }

        }catch(Exception e){
            System.err.println("Error obtaining info from other barrel: " + e.getMessage());
            // Fallback to loading from MapDB
            loadInfo();
        }/*catch (IOException e) {
            throw new IOException("Error: " + e.getMessage());
        }*/
    }

    /**
     * Load info from MapDB
     * This will be used in case I use my own files (I am the reference barrel)
     * This will happen to the first barrel created
     * */
    private void loadInfo() {
        db = DBMaker.fileDB(dbPath)
                .fileMmapEnableIfSupported()
                .closeOnJvmShutdown()
                .transactionEnable()
                .make();

        pagesInfo = db.hashMap("pagesInfo", Serializer.STRING, Serializer.JAVA).createOrOpen();
        adjacencyList = db.hashMap("adjacencyList", Serializer.STRING, Serializer.JAVA).createOrOpen();

        // Load BloomFilter
        File bloomFile = new File(dbPath + "_bloom.bin");
        if (bloomFile.exists()) {
            try (InputStream in = new FileInputStream(bloomFile)) {
                filter = BloomFilter.readFrom(in, Funnels.unencodedCharsFunnel());
                System.out.println("Bloom filter loaded from MapDB storage.");
            } catch (IOException e) {
                System.err.println("Error loading Bloom filter: " + e.getMessage());
                filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
            }
        } else {
            System.out.println("Bloom filter file not found. Created new empty filter.");
        }
    }

    /**
     * Save all the information to MapDB
     * */
    private void saveInfo() throws IOException {
        db.commit();
        try (OutputStream out = new FileOutputStream(dbPath + "_bloom.bin")) {
            filter.writeTo(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Info saved to MapDB.");
    }

    public void receiveMessage(int seqNumber, PageInfo page, List<String> urls, String nome) throws RemoteException {
        // garantir estruturas para o remetente
        Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());
        expectedSeqNumbers.computeIfAbsent(nome, k -> 0);

        // ignorar duplicados
        if (received.contains(seqNumber)) {
            System.out.println("Seq " + seqNumber + " já recebido. Ignorado.");
            return;
        }

        // detetar lacunas antes de qualquer retorno
        checkForMissingMessages(seqNumber, nome);

        // aplicar efeitos
        pagesInfo.put(page.getUrl(), page);
        filter.put(page.getUrl());
        for (String link : urls) {
            Set<String> current = adjacencyList.getOrDefault(link, new HashSet<>());
            current.add(page.getUrl());
            adjacencyList.put(link, current); // ← Força o MapDB a detetar a mudança
            urlQueue.add(link);
        }

        // marcar como recebido (mesmo Set guardado no mapa)
        received.add(seqNumber);

        // avançar expected apenas enquanto houver sequência contígua recebida
        int e = expectedSeqNumbers.get(nome);
        while (received.contains(e)) e++;
        expectedSeqNumbers.put(nome, e);

        System.out.println("Mensagem aplicada com seqNumber: " + seqNumber + " (expected agora=" + e + ")");
    }

    /**
     * Add URL to URL queue if it hasen't been indexed yet
     * This will be stored by URLs that downlaoder will parse
     * @param url
     */
    public void addUrlToQueue(String url) throws RemoteException {
        // Check if URL exists in Bloom filter, if it does check for false-positive
        if(mightContain(url) && pagesInfo.containsKey(url)) {
            System.out.println("URL was already indexed, not adding to queue: " + url);
            return;
        }
        urlQueue.add(url);
        System.out.println("URL added to queue: " + url);
    }

    // ONly used in the main (for testing) when there is no data
    //Delete when not needed
    public void addPageInfo(PageInfo pageInfo) throws RemoteException {
        pagesInfo.put(pageInfo.getUrl(), pageInfo);
        System.out.println("Added PageInfo for URL: " + pageInfo.getUrl());
    }

    /**
     * Add adjacency (fromUrl → toUrl)
     * @param fromUrl
     * @param toUrl
     */
    public void addAdjacency(String fromUrl, String toUrl) throws RemoteException{
        adjacencyList.computeIfAbsent(toUrl, k -> new HashSet<>()).add(fromUrl);
        System.out.println("Added edge: " + fromUrl + " -> " + toUrl);
    }

    /**
     * Add URL to Bloom filter
     * @param url
     */
    public void addToBloomFilter(String url) throws RemoteException{
        filter.put(url);
        System.out.println("URL added to Bloom filter: " + url);
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
        return urlQueue.poll();
    }

    /**
     * Getter for pagesInfo map
     * @return pagesInfo map
     * @throws RemoteException
     */
    public Map<String, PageInfo> getPagesInfoMap() throws RemoteException {
        return new HashMap<>(pagesInfo);
    }

    /**
     * Getter for adjacencyList map
     * @return adjacencyList map
     * @throws RemoteException
     */
    public Map<String, Set<String>> getAdjacencyListMap() throws RemoteException {
        return new HashMap<>(adjacencyList);
    }

    /**
     * Getter for Bloom filter bytes
     * @return bloom filter as byte array
     * @throws RemoteException
     */
    public byte[] getBloomFilterBytes() throws RemoteException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filter.writeTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RemoteException("Error getting Bloom filter bytes", e);
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

    public List<PageInfo> searchPages(List<String> terms) throws RemoteException {
        List<PageInfo> results = new ArrayList<>();
        for (PageInfo page : pagesInfo.values()) {
            boolean allPresent = true;
            for (String term : terms) {
                if (!page.getWords().contains(term.toLowerCase())) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) results.add(page);
        }


        return results;
    }

    private void checkForMissingMessages(int receivedSeqNumber, String nome) {
        int expectedSeqNumber = expectedSeqNumbers.computeIfAbsent(nome, k-> 0);
        Set<Integer> received = receivedSeqNumbers.computeIfAbsent(nome, k -> new HashSet<>());

        // Verificar se há falhas entre expectedSeqNumber e receivedSeqNumber
        if (receivedSeqNumber > expectedSeqNumber) {
            System.out.println("Detetada lacuna! Esperava " + expectedSeqNumber + ", recebi " + receivedSeqNumber);

            // Pedir reenvio de todas as mensagens em falta
            for (int missing = expectedSeqNumber; missing < receivedSeqNumber; missing++) {
                if (!receivedSeqNumbers.get(nome).contains(missing)) {
                    requestMissingMessage(missing,nome);
                }
            }
        }
    }

    private void requestMissingMessage(int missingSeqNumber, String nome) {
        try {
            Registry reg = LocateRegistry.getRegistry(); // host/port por omissão; ajuste se necessário
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
