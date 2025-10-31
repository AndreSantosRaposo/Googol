import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class DownloaderServer {

    public static void main(String[] args) {
        final String CONFIG_FILE = "config.txt";
        final int DOWNLOADER_LINE_INDEX = 4; // linha do Downloader local

        try {
            // Ler configuração do Downloader
            List<String> downloaderConfig = FileManipulation.lineSplitter(CONFIG_FILE, DOWNLOADER_LINE_INDEX, ";");

            if (downloaderConfig.size() < 3) {
                System.err.println("Linha " + (DOWNLOADER_LINE_INDEX + 1) + " incompleta no ficheiro de configuração.");
                return;
            }

            String downloaderName = downloaderConfig.get(0).trim();
            String downloaderIp = downloaderConfig.get(1).trim();
            int downloaderPort = Integer.parseInt(downloaderConfig.get(2).trim());

            System.setProperty("java.rmi.server.hostname", downloaderIp);

            // Ler configuração dos dois Barrels
            List<String> barrel1Config = FileManipulation.lineSplitter(CONFIG_FILE, 2, ";");
            List<String> barrel2Config = FileManipulation.lineSplitter(CONFIG_FILE, 3, ";");

            // Criar registry local
            Registry registry = LocateRegistry.createRegistry(downloaderPort);

            // Criar instância do Downloader
            Downloader downloader = new Downloader(
                    downloaderName, downloaderIp, downloaderPort,
                    barrel1Config.get(1).trim(), Integer.parseInt(barrel1Config.get(2).trim()), barrel1Config.get(0).trim(),
                    barrel2Config.get(1).trim(), Integer.parseInt(barrel2Config.get(2).trim()), barrel2Config.get(0).trim()
            );

            // Registar o Downloader no registry local
            registry.rebind(downloaderName, downloader);
            BarrelIndex barrel1=null;
            BarrelIndex barrel2=null;
            try {
                Registry regBarrel1 = LocateRegistry.getRegistry(barrel1Config.get(1).trim(), Integer.parseInt(barrel1Config.get(2).trim()));
                barrel1 = (BarrelIndex) regBarrel1.lookup(barrel1Config.get(0).trim());
            }catch (Exception e) {
                System.out.println("Erro ao obter o registro de barrel 1: " + e.getMessage());
            }
            try {
                Registry regBarrel2 = LocateRegistry.getRegistry(barrel2Config.get(1).trim(), Integer.parseInt(barrel2Config.get(2).trim()));
                barrel2 = (BarrelIndex) regBarrel2.lookup(barrel2Config.get(0).trim());
            }catch (Exception e){
                System.out.println("Erro ao obter o registro de barrel 2: " + e.getMessage());
            }
            // Ciclo principal
            int currentBarrel = 1;

            while (true) {
                try {
                    BarrelIndex targetBarrel = null;

                    // Tenta selecionar um Barrel disponível (com round-robin)
                    if (currentBarrel == 1) {
                        if (barrel1 != null) {
                            targetBarrel = barrel1;
                        } else if (barrel2 != null) {
                            targetBarrel = barrel2;
                            System.out.println(" Barrel1 indisponível, a usar Barrel2");
                        }
                        currentBarrel = 2;
                    } else {
                        if (barrel2 != null) {
                            targetBarrel = barrel2;
                        } else if (barrel1 != null) {
                            targetBarrel = barrel1;
                            System.out.println(" Barrel2 indisponível, a usar Barrel1");
                        }
                        currentBarrel = 1;
                    }

                    // Se nenhum Barrel está disponível
                    if (targetBarrel == null) {
                        System.err.println(" Nenhum Barrel disponível! A aguardar 5 segundos...");
                        Thread.sleep(5000);
                        continue;
                    }

                    // Pede um URL à fila do Barrel atual
                    String nextUrl = targetBarrel.getUrlFromQueue();

                    if (nextUrl != null && !nextUrl.isEmpty()) {
                        downloader.scrapURL(nextUrl);
                        Thread.sleep(3000);
                    } else {
                        Thread.sleep(3000);
                    }

                } catch (Exception e) {
                    System.err.println("[Downloader] Erro durante o ciclo: " + e.getMessage());
                    Thread.sleep(2000);
                }
            }

        } catch (Exception e) {
            System.err.println("[DownloaderServer] Erro ao iniciar: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
