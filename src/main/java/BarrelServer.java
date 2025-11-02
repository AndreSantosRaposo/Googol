import java.io.FileNotFoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Server application that initializes and registers a Barrel instance in the RMI registry.
 *
 * <p>This server:
 * <ul>
 *     <li>Reads configuration from a file (default: {@code config.txt})</li>
 *     <li>Creates a local RMI registry on the specified port</li>
 *     <li>Instantiates and binds a {@link Barrel} object</li>
 *     <li>Notifies all configured Downloaders that the Barrel is available</li>
 * </ul>
 *
 *
 */
public class BarrelServer {


    private static final int CONFIG_LINE_INDEX = 2;
    private static final String DEFAULT_CONFIG_FILE = "config.txt";

    /**
     * Main entry point for the Barrel server.
     *
     * Reads configuration, creates the Barrel, registers it in RMI registry, and notifies all Downloaders.
     *
     */
    public static void main(String[] args) {
        String filename = DEFAULT_CONFIG_FILE;

        try {
            List<String> parts = FileManipulation.lineSplitter(filename, CONFIG_LINE_INDEX, ";");

            if (parts.size() < 3) {
                System.err.println("Configuration line " + (CONFIG_LINE_INDEX + 1) + " is incomplete");
                return;
            }

            String barrelName = parts.get(0).trim();
            String ip = parts.get(1).trim();
            int port = Integer.parseInt(parts.get(2).trim());

            System.setProperty("java.rmi.server.hostname", ip);

            String dbPath = barrelName + "_MapDB.db";
            Barrel barrel = new Barrel(dbPath, barrelName);

            Registry registry = LocateRegistry.createRegistry(port);

            registry.rebind(barrelName, barrel);

            System.out.println("[BarrelServer] '" + barrelName + "' registered and accessible at " + ip + ":" + port);

            // Notify ALL Downloaders that the Barrel is UP
            notifyAllDownloaders(filename, barrelName);

        } catch (FileNotFoundException e) {
            System.err.println("Error: configuration file '" + filename + "' not found!");
        } catch (NumberFormatException e) {
            System.err.println("Error: invalid port number in configuration file.");
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Notifies all configured Downloaders that a Barrel is available.
     *
     * Assumes Downloaders are configured on lines 4 and 5 of the config file.
     *
     * @param filename   path to configuration file
     * @param barrelName name of the Barrel that is now available
     */
    private static void notifyAllDownloaders(String filename, String barrelName) {
        // Assuming Downloaders are on lines 4 and 5 of config.txt
        int[] downloaderLines = {4, 5}; // Adjust as needed

        for (int lineIndex : downloaderLines) {
            try {
                List<String> downloaderConfig = FileManipulation.lineSplitter(filename, lineIndex, ";");

                if (downloaderConfig.size() < 3) {
                    System.err.println("[BarrelServer] Downloader configuration on line " + (lineIndex + 1) + " is incomplete");
                    continue;
                }

                String downloaderName = downloaderConfig.get(0).trim();
                String downloaderIp = downloaderConfig.get(1).trim();
                int downloaderPort = Integer.parseInt(downloaderConfig.get(2).trim());

                Registry downloaderRegistry = LocateRegistry.getRegistry(downloaderIp, downloaderPort);
                DownloaderIndex downloader = (DownloaderIndex) downloaderRegistry.lookup(downloaderName);

                downloader.notifyBarrelUp(barrelName);
                System.out.println("[BarrelServer] Downloader '" + downloaderName + "' notified: " + barrelName + " is UP");

            } catch (NumberFormatException e) {
                System.err.println("[BarrelServer] Invalid port number for Downloader on line " + (lineIndex + 1));
            } catch (Exception e) {
                System.out.println("[BarrelServer] Could not notify Downloader on line " + (lineIndex + 1) + ": " + e.getMessage());
            }
        }
    }
}
