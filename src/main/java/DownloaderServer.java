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


            // Criar instância do Downloader
            Downloader downloader = new Downloader(
                    downloaderName, downloaderIp, downloaderPort,
                    barrel1Config.get(1).trim(), Integer.parseInt(barrel1Config.get(2).trim()), barrel1Config.get(0).trim(),
                    barrel2Config.get(1).trim(), Integer.parseInt(barrel2Config.get(2).trim()), barrel2Config.get(0).trim()
            );

            // Registar Gateway
            try {
                LocateRegistry.createRegistry(downloaderPort);
            } catch (Exception ignored) {}

            Registry registry = LocateRegistry.getRegistry(downloaderIp, downloaderPort);
            registry.rebind(downloaderName, downloader);

            // Ciclo principal simplificado
            while (true) {
                try {
                    downloader.processNextUrl();
                    Thread.sleep(3000);
                } catch (Exception e) {
                    System.err.println("[Downloader] Erro durante o ciclo: " + e.getMessage());
                    Thread.sleep(2000);
                }
            }

        } catch (Exception e) {
            System.err.println("[DownloaderServer] Erro fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
