import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;


public class DownloaderServer {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Uso: java DownloaderServer <hosts> <ports> <names>");
            System.err.println("Exemplo: java DownloaderServer localhost,localhost port,port barrel1,barrel2");
            System.exit(1);
        }

        List<String> hosts = List.of(args[0].split(","));
        List<Integer> ports = List.of(args[1].split(",")).stream().map(Integer::parseInt).toList();
        List<String> names = List.of(args[2].split(","));

        try {
            Downloader downloader = new Downloader(hosts, ports, names, "nome_downloader");
            System.out.println("Downloader iniciado e ligado aos Barrels.");


            Registry registry = LocateRegistry.getRegistry(hosts.get(0), ports.get(0));
            BarrelIndex mainBarrel = (BarrelIndex) registry.lookup(names.get(0));

            System.out.println("A processar URLs da fila...");

            while (true) {
                try {
                    // Pede um URL ao Barrel principal
                    String nextUrl = mainBarrel.getUrlFromQueue();

                    if (nextUrl != null) {
                        System.out.println("Processando URL: " + nextUrl);
                        // Faz o scraping e envia os resultados a todos os Barrels
                        downloader.scrapURL(nextUrl);
                    } else {
                        // Nenhum URL disponível — espera 3 segundos antes de tentar novamente
                        Thread.sleep(3000);
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao processar URL: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o DownloaderServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
