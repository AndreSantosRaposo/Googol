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
                System.err.println("Linha " + (CONFIG_LINE_INDEX + 1) + " do ficheiro de configuração está incompleta!");
                return;
            }

            String barrelName = parts.get(0).trim();
            String ip = parts.get(1).trim();
            int port = Integer.parseInt(parts.get(2).trim());

            System.out.printf("Barrel %s inicializado em %s:%d%n", barrelName, ip, port);

            String dbPath = barrelName + "_MapDB.db";

            Barrel barrel = new Barrel(dbPath, barrelName);

            LocateRegistry.createRegistry(port);
            System.out.println(" Registry criado no porto " + port);
            Registry registry = LocateRegistry.getRegistry(ip, port);
            registry.rebind(barrelName, barrel);

            System.out.printf("%s pronto em %s:%d%n", barrelName, ip, port);

        } catch (FileNotFoundException e) {
            System.err.println("Erro: ficheiro '" + filename + "' não encontrado!");
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: porto inválido no ficheiro de configuração.");
        } catch (Exception e) {
            System.err.println("Erro inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}