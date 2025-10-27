import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;



public class Downloader {
    private final List<BarrelIndex> barrels = new ArrayList<>();

    /**
     * 🔹 Construtor para múltiplos Barrels via RMI.
     *
     * @param hosts lista dos endereços
     * @param ports lista das portas RMI
     * @param names lista dos nomes no registry
     */
    public Downloader(List<String> hosts, List<Integer> ports, List<String> names) {
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


    public void scrapURL(String url) {
        List<String> words = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url).get();
            String pageTitle = doc.title();
            String doctext = doc.text();
            words = List.of(doctext.split(" "));
            String[] sentences = doctext.split("\\.");
            String textSnippet = String.join(".", Arrays.copyOfRange(sentences, 0, Math.min(3, sentences.length))) + ".";

            PageInfo pageInformation = new PageInfo(pageTitle, url, words, textSnippet);

            List<String> hrefs = doc.select("a[href]")
                    .stream()
                    .map(link -> link.attr("abs:href"))
                    .filter(link -> !link.isEmpty())
                    .toList();

            System.out.println("Scraping concluído: " + url);

            // === Enviar aos Barrels via RMI ===
            for (BarrelIndex barrel : barrels) {
                try {
                    barrel.addPageInfo(pageInformation);
                    barrel.addToBloomFilter(url);
                    for (String link : hrefs) {
                        barrel.addAdjacency(url, link);
                        barrel.addUrlToQueue(link);
                    }
                    System.out.println("Página enviada ao Barrel remoto.");
                } catch (Exception e) {
                    System.err.println("Erro ao enviar dados a um Barrel: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println("Erro ao processar URL: " + e.getMessage());
        }
    }
}


