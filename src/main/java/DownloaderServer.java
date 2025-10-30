import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class DownloaderServer {
    public static void main(String[] args) {
        String filename = "config.txt";
        final int DOWNLOADER_LINE_INDEX = 4;

        try {
            List<String> downloaderConfig = FileManipulation.lineSplitter(filename, DOWNLOADER_LINE_INDEX, ";");

            if (downloaderConfig.size() < 3) {
                System.err.println(" Linha " + (DOWNLOADER_LINE_INDEX + 1) + " incompleta");
                return;
            }

            String downloaderName = downloaderConfig.get(0).trim();
            String downloaderIp = downloaderConfig.get(1).trim();
            int downloaderPort = Integer.parseInt(downloaderConfig.get(2).trim());

            List<String> barrel1Config = FileManipulation.lineSplitter(filename, 2, ";");
            List<String> barrel2Config = FileManipulation.lineSplitter(filename, 3, ";");

            Registry registry = LocateRegistry.createRegistry(downloaderPort);

            Downloader downloader = new Downloader(
                    downloaderName,
                    barrel1Config.get(1).trim(), Integer.parseInt(barrel1Config.get(2).trim()), barrel1Config.get(0).trim(),
                    barrel2Config.get(1).trim(), Integer.parseInt(barrel2Config.get(2).trim()), barrel2Config.get(0).trim()
            );

            registry.rebind(downloaderName, downloader);



        } catch (Exception e) {
            System.err.println(" Erro: " + e.getMessage());
        }
    }
}