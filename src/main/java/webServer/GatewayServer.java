package webServer;
import java.io.FileNotFoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Server application that initializes and registers a Gateway instance in the RMI registry.
 *
 * <p>This server:
 * <ul>
 *     <li>Reads configuration from a file</li>
 *     <li>Creates or reuses a local RMI registry on the specified port</li>
 *     <li>Instantiates a Gateway with connection details for two Barrels</li>
 *     <li>Binds the Gateway object in the RMI registry</li>
 * </ul>
 *
 */
public class GatewayServer {

    private static final String DEFAULT_CONFIG_FILE = "config.txt";

    /**
     * Main etry point for the Gateway server.
     * Reads configuration, creates the Gateway, and registers it in the RMI registry.
     *
     */
    public static void main(String[] args) {
        String filename = DEFAULT_CONFIG_FILE;

        try {
            List<String> gatewayCfg = FileManipulation.lineSplitter(filename, 1, ";");
            List<String> barrel1Cfg = FileManipulation.lineSplitter(filename, 2, ";");
            List<String> barrel2Cfg = FileManipulation.lineSplitter(filename, 3, ";");

            if (gatewayCfg.size() < 3 || barrel1Cfg.size() < 3 || barrel2Cfg.size() < 3) {
                System.err.println("Error: Incomplete configuration. Each line must contain Name;IP;Port");
                return;
            }

            String gatewayName = gatewayCfg.get(0).trim();
            String gatewayIp = gatewayCfg.get(1).trim();
            int gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());

            String barrel1Name = barrel1Cfg.get(0).trim();
            String barrel1Ip = barrel1Cfg.get(1).trim();
            int barrel1Port = Integer.parseInt(barrel1Cfg.get(2).trim());

            String barrel2Name = barrel2Cfg.get(0).trim();
            String barrel2Ip = barrel2Cfg.get(1).trim();
            int barrel2Port = Integer.parseInt(barrel2Cfg.get(2).trim());

            System.setProperty("java.rmi.server.hostname", gatewayIp);

            // Create Gateway with connection info
            Gateway gateway = new Gateway(
                    barrel1Name, barrel1Ip, barrel1Port,
                    barrel2Name, barrel2Ip, barrel2Port,
                    gatewayIp, gatewayPort
            );

            // Register Gateway
            try {
                LocateRegistry.createRegistry(gatewayPort);
            } catch (Exception ignored) {
                // Registry may already exist
            }

            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            registry.rebind(gatewayName, gateway);

            System.out.printf("[GatewayServer] Gateway '%s' ready at %s:%d%n", gatewayName, gatewayIp, gatewayPort);

        } catch (FileNotFoundException e) {
            System.err.println("Error: configuration file '" + filename + "' not found!");
        } catch (NumberFormatException e) {
            System.err.println("Error: invalid port number in configuration file.");
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
