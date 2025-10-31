import java.io.FileNotFoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class BarrelServer {
    public static void main(String[] args) {
        String filename = "config.txt";
        final int CONFIG_LINE_INDEX = 2;

        try {
            List<String> parts = FileManipulation.lineSplitter(filename, CONFIG_LINE_INDEX, ";");

            if (parts.size() < 3) {
                System.err.println("Linha " + (CONFIG_LINE_INDEX + 1) + " do ficheiro de configuração está incorreta");
                return;
            }

            String barrelName = parts.get(0).trim();
            String ip = parts.get(1).trim();
            int port = Integer.parseInt(parts.get(2).trim());

            System.setProperty("java.rmi.server.hostname", ip);

            String dbPath = barrelName + "_MapDB.db";
            Barrel barrel = new Barrel(dbPath, barrelName);

            // Criar registry local neste porto
            Registry registry = LocateRegistry.createRegistry(port);

            // Registar o objeto remoto
            registry.rebind(barrelName, barrel);

            System.out.println("[BarrelServer] '" + barrelName + "' registado e acessível em " + ip + ":" + port);

            // Notificar TODOS os Downloaders que o Barrel está UP
            notifyAllDownloaders(filename, barrelName);

        } catch (FileNotFoundException e) {
            System.err.println("Erro: ficheiro '" + filename + "' não encontrado!");
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: porto inválido no ficheiro de configuração.");
        } catch (Exception e) {
            System.err.println("Erro inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void notifyAllDownloaders(String filename, String barrelName) {
        // Assumindo que os Downloaders estão nas linhas 4 e 5 do config.txt
        int[] downloaderLines = {4, 5}; // Ajuste conforme necessário

        for (int lineIndex : downloaderLines) {
            try {
                List<String> downloaderConfig = FileManipulation.lineSplitter(filename, lineIndex, ";");

                if (downloaderConfig.size() < 3) {
                    System.err.println("[BarrelServer] Configuração do Downloader na linha " + (lineIndex + 1) + " incompleta");
                    continue;
                }

                String downloaderName = downloaderConfig.get(0).trim();
                String downloaderIp = downloaderConfig.get(1).trim();
                int downloaderPort = Integer.parseInt(downloaderConfig.get(2).trim());

                Registry downloaderRegistry = LocateRegistry.getRegistry(downloaderIp, downloaderPort);
                DownloaderIndex downloader = (DownloaderIndex) downloaderRegistry.lookup(downloaderName);

                downloader.notifyBarrelUp(barrelName);
                System.out.println("[BarrelServer] Downloader '" + downloaderName + "' notificado: " + barrelName + " está UP");

            } catch (Exception e) {
                System.out.println("[BarrelServer] Não foi possível notificar o Downloader na linha " + (lineIndex + 1) + ": " + e.getMessage());
            }
        }
    }
}
