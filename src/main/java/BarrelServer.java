import java.io.FileNotFoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class BarrelServer {
    public static void main(String[] args) {
        String filename = "config.txt";
        final int CONFIG_LINE_INDEX = 2; // altera conforme o barrel

        try {
            // **ADICIONAR ESTAS LINHAS ANTES DE QUALQUER CÓDIGO RMI:**
            String barrelIp = "192.84.13.8"; // ou obter do config.txt
            System.setProperty("java.rmi.server.hostname", barrelIp);
            System.out.println("[BarrelServer] RMI hostname configurado para: " + barrelIp);

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
