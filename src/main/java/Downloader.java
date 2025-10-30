import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Downloader extends UnicastRemoteObject implements DownloaderIndex {
    private final List<BarrelIndex> barrels;
    private HashMap<Integer, HistoryMessage> historyBuffer;
    private int seqNumber;
    private String name;


    public Downloader(String name, String ipA, int portA, String nameA,
                      String ipB, int portB, String nameB) throws RemoteException {
        super();
        this.name = name;
        this.historyBuffer = new HashMap<>();
        this.seqNumber = 0;
        this.barrels = new ArrayList<>();

        try {
            Registry regA = LocateRegistry.getRegistry(ipA, portA);
            BarrelIndex barrel1 = (BarrelIndex) regA.lookup(nameA);
            barrels.add(barrel1);
            System.out.println(" Ligado ao barrel : " + nameA);
        } catch (Exception e) {
            System.err.println(" Erro ao ligar ao " + nameA + " " + e.getMessage());
        }

        try {
            Registry regB = LocateRegistry.getRegistry(ipB, portB);
            BarrelIndex barrel2 = (BarrelIndex) regB.lookup(nameB);
            barrels.add(barrel2);
            System.out.println(" Ligado ao barrel : " + nameA);
        } catch (Exception e) {
            System.err.println(" Erro ao ligar ao " + nameA + " " + e.getMessage());
        }

    }

    // Construtor auxiliar só para testes (não abre RMI)
    Downloader(String nome, List<BarrelIndex> barrels) throws RemoteException {
        this.name = nome;
        this.barrels = barrels;
        this.historyBuffer = new HashMap<>();
        this.seqNumber = 0;

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


