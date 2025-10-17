import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.io.*;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

/**
 * Barrel class that serves as storage of the app info
 */
public class Barrel extends UnicastRemoteObject implements BarrelIndex {
    // Constant variables for bloom filter creation
    int expectedInsertionsBloomFilter = 100000;
    double fpp = 0.01;

    Queue<String> urlQueue;
    //THis adjacency list will have the inlinks (values indicate the pages with links to the key page)
    HashMap<String, Set<String>> adjacencyList;
    HashMap<String, PageInfo> pagesInfo;
    BloomFilter<String> filter;

    String pageInfoFileName;
    String adjacencyFileName;
    String bloomFileName;

    public Barrel(String pageInfoFileName, String adjacencyFileName, String bloomFileName) throws RemoteException {
        super();
        urlQueue = new LinkedList<>();
        adjacencyList = new HashMap<>();
        pagesInfo = new HashMap<>();
        filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);

        this.pageInfoFileName = pageInfoFileName;
        this.adjacencyFileName = adjacencyFileName;
        this.bloomFileName = bloomFileName;

        loadInfo();
    }

    /**
     * Stores pagesInfo and adjacencyList with serialization and BloomFilter in binary
     */
    public void saveInfo() throws IOException, RemoteException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(pageInfoFileName))) {
            oos.writeObject(pagesInfo);
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(adjacencyFileName))) {
            oos.writeObject(adjacencyList);
        }

        try (OutputStream out = new FileOutputStream(bloomFileName)) {
            filter.writeTo(out);
        }

        System.out.println("Info saved to disk");
    }

    /** LOads pagesInfo, adjacencyList amd BloomFilter from disk to memory */
    public void loadInfo() {
        File pageInfoFile = new File(pageInfoFileName);
        if (pageInfoFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pageInfoFileName))) {
                pagesInfo = (HashMap<String, PageInfo>) ois.readObject();
                System.out.println("pageInfo loaded from disk.");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("⚠Error loading file: " + pageInfoFileName + " " + e.getMessage());
                pagesInfo = new HashMap<>();
            }
        } else {
            pagesInfo = new HashMap<>();
            System.out.println("File not found. Created new empty pageInfo.");
        }

        File adjacencyFile = new File(adjacencyFileName);
        if (adjacencyFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(adjacencyFileName))) {
                adjacencyList = (HashMap<String, Set<String>>) ois.readObject();
                System.out.println("AdjacencyList loaded from disk.");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error loading adjacencyList: " + e.getMessage());
                adjacencyList = new HashMap<>();
            }
        } else {
            adjacencyList = new HashMap<>();
            System.out.println("File not found. Creating new empty adjacencyList.");
        }

        File bloomFile = new File(bloomFileName);
        if (bloomFile.exists()) {
            try (InputStream in = new FileInputStream(bloomFileName)) {
                filter = BloomFilter.readFrom(in, Funnels.unencodedCharsFunnel());
                System.out.println("Bloom filter loaded from disk.");
            } catch (IOException e) {
                System.err.println("Error loading Bloom filter: " + e.getMessage());
                filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
            }
        } else {
            filter = BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedInsertionsBloomFilter, fpp);
            System.out.println("File not found. Creating new empty Bloom filter.");
        }
    }

    /**
     * Adds new URL to the queue
     * @param url
     */
    public void addUrlToQueue(String url) {
        urlQueue.add(url);
        System.out.println("URL added to queue: " + url);
    }

    /**
     * Gets next URL from the queue
     */
    public String getUrlFromQueue() {
        return urlQueue.poll();
    }

    /**
     * Adds new page info to the index
     * @param pageInfo
     */
    public void addPageInfo(PageInfo pageInfo) {
        pagesInfo.put(pageInfo.getUrl(), pageInfo);
        System.out.println("Added PageInfo for URL: " + pageInfo.getUrl());
    }

    /**
     * Adiciona um link na adjacencyList
     * @param fromUrl
     * @param toUrl
     */
    public void addAdjacency(String fromUrl, String toUrl) {
        adjacencyList.computeIfAbsent(toUrl, k -> new HashSet<>()).add(fromUrl);
        System.out.println("Added edge: " + fromUrl + " → " + toUrl);
    }

    /**
     * Adiciona URL ao Bloom filter
     * @param url
     */
    public void addToBloomFilter(String url) {
        filter.put(url);
        System.out.println("URL added to Bloom filter: " + url);
    }

    /**
     * Verifica se o Bloom filter contém uma URL
     * @param url
     */
    public boolean mightContain(String url) {
        return filter.mightContain(url);
    }

    /** Imprime o estado atual para debug */
    public void printAll() {
        System.out.println("\n===== Barrel Current State =====");
        System.out.println("URLs in queue: " + urlQueue);
        System.out.println("Adjacency List: " + adjacencyList);
        System.out.println("Pages Info keys: " + pagesInfo.keySet());
        System.out.println("Bloom Filter test (example.com): " + filter.mightContain("https://example.com"));
        System.out.println("================================\n");
    }

    //TODO apagar  a maior parte das coisas aqui, estou a usar so para testar, gerado pelo chat
    public static void main(String[] args) throws Exception {
        String pageInfoFileName = "pageInfoTeste.ser";
        String adjacencyFileName = "adjacencyTeste.ser";
        String bloomFileName = "bloomTeste.bin";

        Barrel barrel = new Barrel(pageInfoFileName, adjacencyFileName, bloomFileName);

        // Se o ficheiro estiver vazio (primeira execução), adiciona dados de teste
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

            barrel.saveInfo(); // salva tudo
        }

        // Mostra o estado atual
        barrel.printAll();

        // Testa se o bloom filter funciona
        System.out.println("Contains https://uc.pt ? " + barrel.mightContain("https://uc.pt"));
        System.out.println("Contains https://google.com ? " + barrel.mightContain("https://google.com"));

        // Recarrega tudo do disco (simula próxima execução)
        System.out.println("\nReloading from disk...");
        Barrel barrelReloaded = new Barrel(pageInfoFileName, adjacencyFileName, bloomFileName);
        barrelReloaded.printAll();
    }
}
