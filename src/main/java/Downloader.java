import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Downloader extends UnicastRemoteObject implements DownloaderIndex {
    private HashMap<Integer, HistoryMessage> historyBuffer;
    private int seqNumber;
    private String name;
    private String ip;
    private int port;
    private int currentBarrel = 1; // Para round-robin

    // HashMap escalável: nome -> [ip, porta, conexão]
    private HashMap<String, Object[]> barrels;

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

        // Adicionar barrels: [ip, porta, conexão]
        barrels.put(nameBarrelA, new Object[]{IpBarrelA, PortBarrelA, null});
        barrels.put(nameBarrelB, new Object[]{IpBarrelB, PortBarrelB, null});

        // Tentar conexão inicial
        connectToBarrel(nameBarrelA);
        connectToBarrel(nameBarrelB);
    }

    public synchronized void connectToBarrel(String barrelName) {
        Object[] info = barrels.get(barrelName);
        if (info == null || info[2] != null) return;

        try {
            Registry reg = LocateRegistry.getRegistry((String) info[0], (int) info[1]);
            info[2] = (BarrelIndex) reg.lookup(barrelName);
            System.out.println("[Downloader] Conectado ao: " + barrelName + " IP: " + info[0] + " PORTA: " + info[1]);
        } catch (Exception e) {
            System.out.println("[Downloader] " + barrelName + " ainda não disponível: " + e.getMessage());
        }
    }

    @Override
    public synchronized void notifyBarrelUp(String barrelName) throws RemoteException {
        System.out.println("[Downloader] Recebida notificação: " + barrelName + " está UP");
        connectToBarrel(barrelName);
    }

    public void processNextUrl() throws Exception {
        BarrelIndex targetBarrel = null;
        List<BarrelIndex> activeBarrels = getActiveBarrels();

        // Se não há barrels ativos
        if (activeBarrels.isEmpty()) {
            System.err.println(" Nenhum Barrel disponível! A aguardar 5 segundos...");
            Thread.sleep(5000);
            return;
        }

        // Round-robin simples
        int index = (currentBarrel++) % activeBarrels.size();
        targetBarrel = activeBarrels.get(index);

        // Pede um URL à fila do Barrel atual
        String nextUrl = targetBarrel.getUrlFromQueue();

        if (nextUrl != null && !nextUrl.isEmpty()) {
            scrapURL(nextUrl);
        }
    }

    private synchronized List<BarrelIndex> getActiveBarrels() {
        List<BarrelIndex> active = new ArrayList<>();
        for (Object[] info : barrels.values()) {
            if (info[2] != null) {
                active.add((BarrelIndex) info[2]);
            }
        }
        return active;
    }

    private synchronized void disconnectBarrel(BarrelIndex barrel) {
        for (Object[] info : barrels.values()) {
            if (info[2] == barrel) {
                info[2] = null;
                break;
            }
        }
    }

    public void reSendMessages(int seqNumber, BarrelIndex requestingBarrel) throws RemoteException {
        HistoryMessage message = historyBuffer.get(seqNumber);

        if (message == null) {
            System.err.println("Mensagem com seqNumber " + seqNumber + " não encontrada no histórico.");
            return;
        }

        System.out.println("Reenviando mensagem com seqNumber: " + seqNumber + " ao barrel que solicitou.");

        try {
            requestingBarrel.receiveMessage(seqNumber, message.getPage(), message.getUrls(), name, ip, port);
            System.out.println("Mensagem reenviada com sucesso ao Barrel solicitante.");
        } catch (Exception e) {
            System.err.println("Erro ao reenviar dados ao Barrel: " + e.getMessage());
        }
    }

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
                        System.out.println("[DEBUG] Página enviada + " + pageInformation.getTitle() + " com seq=" + currentSeq + " ao Barrel from:" + name);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao enviar ao Barrel: " + e.getMessage());
                    disconnectBarrel(barrel);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar URL: " + e.getMessage());
        }
    }

    Downloader(String nome, HashMap<String, Object[]> barrels) throws RemoteException {
        this.name = nome;
        this.barrels = barrels;
        this.historyBuffer = new HashMap<>();
        this.seqNumber = 0;
    }

    void addToHistory(int seq, HistoryMessage msg) {
        historyBuffer.put(seq, msg);
    }
}
