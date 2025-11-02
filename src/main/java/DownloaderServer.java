import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Server application that initializes and runs a Downloader instance.
 *
 * <p>This server:
 * <ul>
 *     <li>Reads configuration from a file (default: {@code config.txt})</li>
 *     <li>Creates or reuses a local RMI registry on the specified port</li>
 *     <li>Instantiates a Downloader with connection details for two Barrels</li>
 *     <li>Binds the Downloader object in the RMI registry</li>
 *     <li>Runs an infinite loop processing URLs from Barrel queues</li>
 * </ul>
 *
 */
public class DownloaderServer {

    /**
     * Default configuration file name.
     */
    private static final String CONFIG_FILE = "config.txt";

    /**
     * Index of the configuration line for the local Downloader.
     */
    private static final int DOWNLOADER_LINE_INDEX = 4;

    /**
     * Index of the configuration line for Barrel 1.
     */
    private static final int BARREL1_LINE_INDEX = 2;

    /**
     * Index of the configuration line for Barrel 2.
     */
    private static final int BARREL2_LINE_INDEX = 3;

    /**
     * Main entry point for the Downloader server.
     *
     * <p>Reads configuration, creates the Downloader, registers it in RMI registry,
     * and continuously processes URLs from Barrel queues.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        try {
            // Read Downloader configuration
            List<String> downloaderConfig = FileManipulation.lineSplitter(CONFIG_FILE, DOWNLOADER_LINE_INDEX, ";");

            if (downloaderConfig.size() < 3) {
                System.err.println("Error: Incomplete configuration on line " + (DOWNLOADER_LINE_INDEX + 1));
                return;
            }

            String downloaderName = downloaderConfig.get(0).trim();
            String downloaderIp = downloaderConfig.get(1).trim();
            int downloaderPort = Integer.parseInt(downloaderConfig.get(2).trim());

            System.setProperty("java.rmi.server.hostname", downloaderIp);

            // Read Barrel configurations
            List<String> barrel1Config = FileManipulation.lineSplitter(CONFIG_FILE, BARREL1_LINE_INDEX, ";");
            List<String> barrel2Config = FileManipulation.lineSplitter(CONFIG_FILE, BARREL2_LINE_INDEX, ";");

            if (barrel1Config.size() < 3 || barrel2Config.size() < 3) {
                System.err.println("Error: Incomplete Barrel configuration");
                return;
            }

            // Create Downloader instance
            Downloader downloader = new Downloader(
                    downloaderName, downloaderIp, downloaderPort,
                    barrel1Config.get(1).trim(), Integer.parseInt(barrel1Config.get(2).trim()), barrel1Config.get(0).trim(),
                    barrel2Config.get(1).trim(), Integer.parseInt(barrel2Config.get(2).trim()), barrel2Config.get(0).trim()
            );

            // Register in RMI registry
            try {
                LocateRegistry.createRegistry(downloaderPort);
            } catch (Exception ignored) {
                // Registry may already exist
            }

            Registry registry = LocateRegistry.getRegistry(downloaderIp, downloaderPort);
            registry.rebind(downloaderName, downloader);

            System.out.printf("[DownloaderServer] '%s' registered and running at %s:%d%n",
                    downloaderName, downloaderIp, downloaderPort);

            // Main processing loop
            while (true) {
                try {
                    downloader.processNextUrl();

                    if ((DebugConfig.DEBUG_MULTICAST_DOWNLOADER || DebugConfig.DEBUG_DOWNLOADER || DebugConfig.DEBUG_ALL)
                            && DebugConfig.DEBUG_DOWNLOADER_SLEEP) {
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    System.err.println("[DownloaderServer] Error during processing cycle: " + e.getMessage());
                    Thread.sleep(2000);
                }
            }

        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port number in configuration file.");
        } catch (Exception e) {
            System.err.println("[DownloaderServer] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
