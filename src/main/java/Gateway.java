import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Implementação da Gateway.
 * Atua como ponto de interação com o utilizador e coordena os Barrels via RMI.
 * A interação com o utilizador é feita localmente (menu no terminal).
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {

    private final List<BarrelIndex> barrels;
    private int nextBarrel = 0;
    private final Map<String, List<PageInfo>> cache = new HashMap<>();

    public Gateway(List<BarrelIndex> barrels) throws RemoteException {
        super();
        this.barrels = barrels;
    }

    /** Seleciona o próximo Barrel (round-robin) */
    private synchronized BarrelIndex getNextBarrel() {
        if (barrels.isEmpty()) throw new IllegalStateException("Nenhum Barrel disponível.");
        BarrelIndex b = barrels.get(nextBarrel);
        nextBarrel = (nextBarrel + 1) % barrels.size();
        return b;
    }

    /** Adiciona um URL à fila de um Barrel remoto */
    @Override
    public void addUrl(String url) throws RemoteException {
        for (int i = 0; i < barrels.size(); i++) {
            BarrelIndex barrel = getNextBarrel();
            try {
                barrel.addUrlToQueue(url);
                barrel.addToBloomFilter(url);
                System.out.println(" URL adicionado ao Barrel remoto: " + url);
                return;
            } catch (Exception e) {
                System.err.println(" Erro ao adicionar URL no Barrel: " + e.getMessage());
            }
        }
        System.err.println("Nenhum Barrel disponível para adicionar URL.");
    }

    /** Pesquisa por termos em um dos Barrels remotos */
    @Override
    public List<PageInfo> search(List<String> terms) throws RemoteException {
        if (cache.containsKey(terms)) return cache.get(terms);

        for (int i = 0; i < barrels.size(); i++) {
            BarrelIndex barrel = getNextBarrel();
            try {
                List<PageInfo> results = barrel.searchPages(terms);
                cache.put(terms.toString(), results);
                return results;
            } catch (Exception e) {
                System.err.println("⚠ Erro ao pesquisar no Barrel: " + e.getMessage());
            }
        }
        return List.of();
    }

    /** Menu de interação local (sem cliente RMI externo) */
    public static void main(String[] args) throws Exception {
        Registry r1 = LocateRegistry.getRegistry("localhost", 2001);
        Registry r2 = LocateRegistry.getRegistry("localhost", 2002);

        BarrelIndex b1 = (BarrelIndex) r1.lookup("barrel1");
        BarrelIndex b2 = (BarrelIndex) r2.lookup("barrel2");

        List<BarrelIndex> barrels = List.of(b1, b2);
        Gateway gateway = new Gateway(barrels);

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== MENU GATEWAY ===");
            System.out.println("1. Adicionar URL para indexar");
            System.out.println("2. Pesquisar termos");
            System.out.println("0. Sair");
            System.out.print("Opção: ");
            String op = sc.nextLine().trim();

            if (op.equals("0")) break;

            switch (op) {
                case "1" -> {
                    System.out.print("Digite o URL: ");
                    String url = sc.nextLine().trim();
                    if (!url.isEmpty()) gateway.addUrl(url);
                    else System.out.println("⚠ URL inválido.");
                }
                case "2" -> {
                    System.out.print("Digite os termos de pesquisa: ");
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) {
                        System.out.println("⚠ Nenhum termo introduzido.");
                        break;
                    }
                    List<String> terms = Arrays.asList(line.split("\\s+"));
                    List<PageInfo> results = gateway.search(terms);

                    if (results.isEmpty()) {
                        System.out.println("❌ Nenhuma página encontrada.");
                    } else {
                        int count = 0;
                        for (PageInfo p : results) {
                            count++;
                            System.out.printf("%d. %s\n   URL: %s\n   Texto: %s\n\n",
                                    count, p.getTitle(), p.getUrl(), p.getSmallText());
                        }
                    }
                }
                default -> System.out.println("⚠ Opção inválida.");
            }
        }

        System.out.println("🚪 Programa encerrado.");
    }
}
