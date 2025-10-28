import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.rmi.RemoteException;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;



public class Downloader {
    private final List<BarrelIndex> barrels;
    private HashMap<Integer,HistoryMessage> historyBuffer;
    int seqNumber;
    List<String> hosts;
    List<Integer> ports;
    List<String> names;
    String name;
    /**
     * 🔹 Construtor para múltiplos Barrels via RMI.
     *
     * @param hosts lista dos endereços
     * @param ports lista das portas RMI
     * @param names lista dos nomes no registry
     */
    public Downloader(List<String> hosts, List<Integer> ports, List<String> names, String name) {
        historyBuffer = new HashMap<>();
        barrels = new ArrayList<>();
        seqNumber =0;
        this.hosts = hosts;
        this.ports = ports;
        this.names = names;
        this.name = name;

        if (hosts.size() != ports.size() || hosts.size() != names.size()) {
            throw new IllegalArgumentException("As listas de hosts, ports e names devem ter o mesmo tamanho!");
        }

        for (int i = 0; i < hosts.size(); i++) {
            try {
                Registry registry = LocateRegistry.getRegistry(hosts.get(i), ports.get(i));
                BarrelIndex barrel = (BarrelIndex) registry.lookup(names.get(i));
                barrels.add(barrel);
                System.out.println("Ligado ao Barrel remoto: " + names.get(i));
            } catch (Exception e) {
                System.err.println(" Erro ao conectar ao Barrel " + names.get(i) + ": " + e.getMessage());
            }
        }
    }

    // Construtor auxiliar só para testes (não abre RMI)
    Downloader(String nome, List<BarrelIndex> barrels) {
        this.name = nome;
        this.barrels = barrels;
        this.historyBuffer = new HashMap<>();
        this.seqNumber = 0;
        this.hosts = List.of();
        this.ports = List.of();
        this.names = List.of();
    }

    // Helper de teste para povoar o histórico
    void addToHistory(int seq, HistoryMessage msg) {
        historyBuffer.put(seq, msg);
    }

    /**
     * Method that resends lost messages detected by barrels
     * @param seqNumber
     */
    public void reSendMessages(int seqNumber, BarrelIndex requestingBarrel) throws RemoteException {
        HistoryMessage message = historyBuffer.get(seqNumber);

        if (message == null) {
            System.err.println("Mensagem com seqNumber " + seqNumber + " não encontrada no histórico.");
            return;
        }

        System.out.println("Reenviando mensagem com seqNumber: " + seqNumber + " ao barrel que solicitou.");

        try {
            requestingBarrel.receiveMessage(seqNumber, message.getPage(), message.getUrls(), name);
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

            // gerar seq, guardar no histórico e enviar com seqNumber
            int currentSeq = seqNumber++;
            historyBuffer.put(currentSeq, new HistoryMessage(pageInformation, hrefs));

            for (BarrelIndex barrel : barrels) {
                try {
                    barrel.receiveMessage(currentSeq, pageInformation, hrefs, name);
                    System.out.println("Página enviada com seq=" + currentSeq + " ao Barrel remoto.");
                } catch (Exception e) {
                    System.err.println("Erro ao enviar ao Barrel: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar URL: " + e.getMessage());
        }
    }
}


