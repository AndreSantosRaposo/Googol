package webServer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Downloader responsible for fetching and parsing web pages, then distributing the data to multiple Barrels.
 *
 * <p>The Downloader:
 * <ul>
 *     <li>Connects to multiple Barrel instances</li>
 *     <li>Requests URLs from Barrel queues in round-robin fashion</li>
 *     <li>Scrapes pages using Jsoup and extracts text, links, and metadata</li>
 *     <li>Sends parsed data to all active Barrels with sequence numbers for reliability</li>
 *     <li>Maintains a history buffer for re-transmission of lost messages</li>
 * </ul>
 *
 * Thread-safety: Methods use synchronized blocks where necessary to manage shared state.
 */
public class Downloader extends UnicastRemoteObject implements DownloaderIndex {
    private HashMap<Integer, HistoryMessage> historyBuffer;
    private int seqNumber;
    private final String name;
    private final String ip;
    private final int port;
    private int currentBarrel = 1;

    private HashMap<String, Object[]> barrels;

    /**
     * Constructs a new Downloader and attempts to connect to the specified Barrels.
     *
     * @param name         name of this Downloader
     * @param ip           IP address where this Downloader is bound
     * @param port         RMI registry port for this Downloader
     * @param IpBarrelA    IP address of Barrel A
     * @param PortBarrelA  RMI registry port of Barrel A
     * @param nameBarrelA  name of Barrel A in RMI registry
     * @param IpBarrelB    IP address of Barrel B
     * @param PortBarrelB  RMI registry port of Barrel B
     * @param nameBarrelB  name of Barrel B in RMI registry
     * @throws RemoteException if RMI export fails
     */
    public Downloader(String name, String ip, Integer port,
                      String IpBarrelA, int PortBarrelA, String nameBarrelA,
                      String IpBarrelB, int PortBarrelB, String nameBarrelB) throws RemoteException {
        super();
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.historyBuffer = new HashMap<>();
        this.seqNumber = 0;
        this.barrels = new HashMap<>();

        // Add barrels: [IP, Port, connection]
        barrels.put(nameBarrelA, new Object[]{IpBarrelA, PortBarrelA, null});
        barrels.put(nameBarrelB, new Object[]{IpBarrelB, PortBarrelB, null});

        // Attempt initial connection
        connectToBarrel(nameBarrelA);
        connectToBarrel(nameBarrelB);
    }

    /**
     * Attempts to connect to a Barrel and reset its sequence numbers.
     * If the connection is successful, the Barrel is notified of this Downloader's identity.
     *
     * @param barrelName name of the Barrel to connect to
     */
    public synchronized void connectToBarrel(String barrelName) {
        Object[] info = barrels.get(barrelName);
        if (info == null || info[2] != null) return;

        try {
            Registry reg = LocateRegistry.getRegistry((String) info[0], (int) info[1]);
            BarrelIndex barrel = (BarrelIndex) reg.lookup(barrelName);
            info[2] = barrel;

            barrel.resetSeqNumbers(name);

            System.out.println("[Downloader] Connected to: " + barrelName + " IP: " + info[0] + " PORT: " + info[1]);
        } catch (Exception e) {
            System.out.println("[Downloader] " + barrelName + " not yet available: " + e.getMessage());
        }
    }

    /**
     * Notifies the Downloader that a Barrel is now available. (called by a Barrel)
     * Attempts to connect to the Barrel and reset sequence numbers.
     *
     * @param barrelName name of the Barrel that is now UP
     * @throws RemoteException if an RMI error occurs
     */
    @Override
    public synchronized void notifyBarrelUp(String barrelName) throws RemoteException {
        System.out.println("[Downloader] Notification received: " + barrelName + " is UP");
        connectToBarrel(barrelName);

        Object[] info = barrels.get(barrelName);
        if (info != null && info[2] != null) {
            try {
                ((BarrelIndex) info[2]).resetSeqNumbers(name);
            } catch (RemoteException e) {
                System.err.println("[Downloader] Error resetting seqNumbers: " + e.getMessage());
            }
        }
    }

    /**
     * Processes the next URL from a Barrel's queue using round robin.
     * If no Barrels are available, waits 5 seconds before retrying.
     *
     * @throws Exception if an error occurs during URL processing
     */
    public void processNextUrl() throws Exception {
        List<BarrelIndex> activeBarrels = getActiveBarrels();

        // If no active Barrels
        if (activeBarrels.isEmpty()) {
            System.err.println("No Barrel available! Waiting 5 seconds...");
            Thread.sleep(5000);
            return;
        }

        // Simple round-robin
        int index = (currentBarrel++) % activeBarrels.size();
        BarrelIndex targetBarrel = activeBarrels.get(index);

        // Request a URL from the current Barrel's queue
        String nextUrl = targetBarrel.getUrlFromQueue();

        if (nextUrl != null && !nextUrl.isEmpty()) {
            scrapURL(nextUrl);
        }
    }

    /**
     * Returns a list of all currently active (connected) Barrels.
     * @return list of active Barrel connections
     */
    private synchronized List<BarrelIndex> getActiveBarrels() {
        List<BarrelIndex> active = new ArrayList<>();
        for (Object[] info : barrels.values()) {
            if (info[2] != null) {
                active.add((BarrelIndex) info[2]);
            }
        }
        return active;
    }

    /**
     * Marks a Barrel as disconnected by clearing its connection reference.
     * @param barrel the Barrel to disconnect
     */
    private synchronized void disconnectBarrel(BarrelIndex barrel) {
        for (Object[] info : barrels.values()) {
            if (info[2] == barrel) {
                info[2] = null;
                break;
            }
        }
    }

    /**
     * Re-sends a previously sent message to a requesting Barrel.
     * Used for recovering from lost messages in multicast scenarios.
     *
     * @param seqNumber        the sequence number of the message to re-send
     * @param requestingBarrel the Barrel requesting the message
     * @throws RemoteException if an RMI error occurs
     */
    public void reSendMessages(int seqNumber, BarrelIndex requestingBarrel) throws RemoteException {
        HistoryMessage message = historyBuffer.get(seqNumber);

        if (message == null) {
            if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                System.out.println("[DEBUG] Message with seqNumber: " + seqNumber + " not found in history buffer.");
            }
            return;
        }

        if (DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_ALL) {
            System.out.println("[DEBUG] Resending message with seqNumber: " + seqNumber + " to requesting Barrel.");
        }

        try {
            requestingBarrel.receiveMessage(seqNumber, message.getPage(), message.getUrls(), name, ip, port);
            System.out.println("Message successfully resent to requesting Barrel.");
        } catch (Exception e) {
            System.err.println("Error resending data to Barrel: " + e.getMessage());
        }
    }

    /**
     * Scrapes a given URL, extracts page information and links, and distributes the data to all active Barrels.
     *
     * <p>Steps:
     * <ul>
     *     <li>Connects to the URL using Jsoup</li>
     *     <li>Extracts title, full text, word list, and a short snippet</li>
     *     <li>Extracts all absolute links</li>
     *     <li>Assigns a sequence number and stores in history buffer</li>
     *     <li>Sends data to all active Barrels</li>
     * </ul>
     *
     * @param url the URL to scrape
     */
    public void scrapURL(String url) {
        try {
            Document doc = Jsoup.connect(url).get();
            String pageTitle = doc.title();
            String doctext = doc.text();
            List<String> words = List.of(doctext.split(" "));
            String[] sentences = doctext.split("\\.");
            String textSnippet = String.join(".", Arrays.copyOfRange(sentences, 0, Math.min(3, sentences.length))) + ".";
            PageInfo pageInformation = new PageInfo(pageTitle, url, words, textSnippet);

            List<String> hrefs = doc.select("a[href]")
                    .stream().map(link -> link.attr("abs:href"))
                    .filter(link -> !link.isEmpty()).toList();

            int currentSeq = seqNumber++;
            historyBuffer.put(currentSeq, new HistoryMessage(pageInformation, hrefs));

            List<BarrelIndex> activeBarrels = getActiveBarrels();

            for (BarrelIndex barrel : activeBarrels) {
                try {
                    barrel.receiveMessage(currentSeq, pageInformation, hrefs, name, ip, port);
                    if (DebugConfig.DEBUG_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                        System.out.println("[DEBUG] Page sent: " + pageInformation.getTitle() + " with seq=" + currentSeq + " to Barrel from: " + name);
                    }
                } catch (Exception e) {
                    System.err.println("Error sending to Barrel: " + e.getMessage());
                    disconnectBarrel(barrel);
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing URL: " + e.getMessage());
        }
    }

    public void scrapURL(String url, Set<String> keywords) {
        try {
            Document doc = Jsoup.connect(url).get();
            String pageTitle = doc.title();
            String doctext = doc.text();
            List<String> words = new ArrayList<>(List.of(doctext.split(" ")));
            String[] sentences = doctext.split("\\.");
            String textSnippet = String.join(".", Arrays.copyOfRange(sentences, 0, Math.min(3, sentences.length))) + ".";
            PageInfo pageInformation = new PageInfo(pageTitle, url, words, textSnippet);

            List<String> hrefs = doc.select("a[href]")
                    .stream().map(link -> link.attr("abs:href"))
                    .filter(link -> !link.isEmpty()).toList();

            for(String word : keywords) {
                if(words.isEmpty()) break;
                words.remove(word);
            }

            if(!words.isEmpty()) return;

            int currentSeq = seqNumber++;
            historyBuffer.put(currentSeq, new HistoryMessage(pageInformation, hrefs));

            List<BarrelIndex> activeBarrels = getActiveBarrels();

            for (BarrelIndex barrel : activeBarrels) {
                try {
                    barrel.receiveMessage(currentSeq, pageInformation, hrefs, name, ip, port);
                    if (DebugConfig.DEBUG_DOWNLOADER || DebugConfig.DEBUG_ALL) {
                        System.out.println("[DEBUG] Page sent: " + pageInformation.getTitle() + " with seq=" + currentSeq + " to Barrel from: " + name);
                    }
                } catch (Exception e) {
                    System.err.println("Error sending to Barrel: " + e.getMessage());
                    disconnectBarrel(barrel);
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing URL: " + e.getMessage());
        }
    }
}
