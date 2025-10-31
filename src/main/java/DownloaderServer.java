import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class DownloaderServer {
    public static void main(String[] args) {
        try {
            // Configurações dos Barrels
            String nameBarrelA = "Barrel1";
            String IpBarrelA = "10.196.247.138";
            int PortBarrelA = 1099;

            String nameBarrelB = "Barrel2";
            String IpBarrelB = "10.196.247.181";
            int PortBarrelB = 1100;

            // Configurações do Downloader
            String downName = "Downloader2";
            String downIp = "10.196.247.181";
            Integer downPort = 1102;

            System.out.println(" [" + downName + ", " + downIp + ", " + downPort + "]");
            System.out.println(" [" + nameBarrelB + ", " + IpBarrelB + ", " + PortBarrelB + "]");
            System.out.println(" [" + nameBarrelA + ", " + IpBarrelA + ", " + PortBarrelA + "]");

            // Criar e registrar o Downloader
            Downloader downloader = new Downloader(downName, downIp, downPort,
                    IpBarrelA, PortBarrelA, nameBarrelA,
                    IpBarrelB, PortBarrelB, nameBarrelB);

            Registry r = LocateRegistry.createRegistry(downPort);
            r.rebind(downName, downloader);

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
