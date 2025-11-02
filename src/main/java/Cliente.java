import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

/**
 * Client application that interacts with the Gateway to perform searches and manage URLs.
 *
 * <p>Features:
 * <ul>
 *     <li>Search for pages by keywords with paginated results</li>
 *     <li>Add URLs for indexing with retry logic</li>
 *     <li>Query inbound links (pages pointing to a given URL)</li>
 *     <li>View system statistics (top searches and barrel metrics)</li>
 * </ul>
 */
public class Cliente {

    /**
     * Displays a batch of up to 10 search results.
     *
     * @param limMax upper limit (exclusive)
     * @param limMin lower limit (inclusive)
     * @param results list of page results
     */
    private static void dezLinks(Integer limMax, Integer limMin, List<PageInfo> results) {
        while (limMin < limMax && limMin < results.size()) {
            PageInfo p = results.get(limMin);
            System.out.printf("- %s (%s) citation: %s%n",
                    p.getTitle(), p.getUrl(), p.getSmallText());
            limMin++;
        }
    }

    /**
     * Attempts to add a URL to the Gateway with retry logic.
     *
     * Retries up to a certain amoun of times if a  BarrelUnavailableException occurs.
     * Stops immediately if a UrlAlreadyIndexedException is thrown.
     *
     * @param gateway    the Gateway interface
     * @param url        the URL to add
     * @param maxRetries maximum number of attempts
     * @param delayMs    delay in milliseconds between retries
     * @return true if the URL was successfully added, false otherwise
     */
    private static boolean adicionarUrlComRetry(GatewayInterface gateway, String url, int maxRetries, int delayMs) {
        for (int tentativa = 1; tentativa <= maxRetries; tentativa++) {
            try {
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: Attempt " + tentativa + "/" + maxRetries + " to add URL: " + url);
                }

                gateway.addUrl(url);
                System.out.println("URL added successfully!");
                return true;

            } catch (Exception e) {
                Throwable causa = e.getCause();

                if (causa instanceof UrlAlreadyIndexedException) {
                    System.err.println("Warning: " + causa.getMessage());
                    return false;
                }

                if (causa instanceof BarrelUnavailableException) {
                    if (tentativa < maxRetries) {
                        System.err.println("Warning: Attempt " + tentativa + " failed: " + causa.getMessage());
                        System.out.println("Retrying in " + delayMs + "ms...");

                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    } else {
                        System.err.println("Error: All " + maxRetries + " attempts failed: " + causa.getMessage());
                        return false;
                    }
                } else {
                    System.err.println("Unexpected error: " + e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Main entry point for the Client application.
     * Reads configuration, connects to the Gateway, and presents an interactive menu.
     */
    public static void main(String[] args) {

        String filename = "config.txt";
        final int CLIENTE_LINE = 6;
        final int GATEWAY_LINE = 1;

        try {
            List<String> clienteCfg = FileManipulation.lineSplitter(filename, CLIENTE_LINE, ";");
            String clienteName = clienteCfg.get(0).trim();
            String clienteIp = clienteCfg.get(1).trim();
            int clientePort = Integer.parseInt(clienteCfg.get(2).trim());

            List<String> gatewayCfg = FileManipulation.lineSplitter(filename, GATEWAY_LINE, ";");
            String gatewayIp = gatewayCfg.get(1).trim();
            int gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());
            String gatewayName = gatewayCfg.get(0).trim();

            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            GatewayInterface gateway = (GatewayInterface) registry.lookup(gatewayName);

            System.out.printf("Client '%s' connected to Gateway at %s:%d%n", clienteName, gatewayIp, gatewayPort);

            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.println("\n--- CLIENT MENU ---");
                System.out.println("1. Search");
                System.out.println("2. Add URL");
                System.out.println("3. Search inlinks");
                System.out.println("4. View statistics");
                System.out.println("5. Exit");
                System.out.print("Choice: ");

                int opcao;
                try {
                    opcao = Integer.parseInt(sc.nextLine());
                } catch (NumberFormatException e) {
                    System.err.println("Error: Enter a valid number (1-5). Try again.");
                    continue;
                }

                if (opcao < 1 || opcao > 5) {
                    System.err.println("Error: Option out of range (1-5). Try again.");
                    continue;
                }

                if (opcao == 1) {
                    System.out.print("Enter search terms: ");
                    String query = sc.nextLine().trim();

                    if (query.isEmpty()) {
                        System.err.println("Error: Search cannot be empty. Try again.");
                        continue;
                    }

                    try {
                        List<PageInfo> results = gateway.search(query);
                        if (results.isEmpty()) {
                            System.out.println("(No results found)");
                            continue;
                        }

                        System.out.println("Results:");
                        int limMin = 0;
                        int limMax = 10;

                        dezLinks(limMax, limMin, results);

                        while (limMax <= results.size()) {
                            System.out.print("\nView next 10 links? (y/n): ");
                            String opcaoString = sc.nextLine().trim().toLowerCase();

                            if (opcaoString.equals("y")) {
                                limMin = limMax;
                                limMax = Math.min(limMax + 10, results.size());
                                dezLinks(limMax, limMin, results);

                                if (limMax >= results.size()) {
                                    System.out.println("No more links.");
                                    break;
                                }

                            } else if (opcaoString.equals("n")) {
                                break;
                            } else {
                                System.out.println("Invalid option. Enter 'y' or 'n'.");
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("Error during search: " + e.getMessage());
                    }

                } else if (opcao == 2) {
                    System.out.print("Enter URL: ");
                    String url = sc.nextLine().trim();

                    if (url.isEmpty()) {
                        System.err.println("Error: URL cannot be empty. Try again.");
                        continue;
                    }

                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        System.err.println("Warning: URL must start with 'http://' or 'https://'. Try again.");
                        continue;
                    }

                    try {
                        if (DebugConfig.DEBUG_URL_INDEXAR) {
                            System.out.println("[DEBUG]: Adding URL " + url + " for indexing.");
                        }
                        adicionarUrlComRetry(gateway, url, 3, 2000);
                    } catch (Exception e) {
                        System.err.println("Error adding URL: " + e.getMessage());
                    }

                } else if (opcao == 3) {
                    System.out.print("Enter URL to find pages pointing to it: ");
                    String url = sc.nextLine().trim();
                    if (url.isEmpty()) {
                        System.err.println("Error: URL cannot be empty.");
                        continue;
                    }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        System.err.println("Warning: URL must start with 'http://' or 'https://'.");
                        continue;
                    }

                    try {
                        List<String> inlinks = gateway.searchInlinks(url);
                        if (inlinks.isEmpty()) {
                            System.out.println("No known pages point to this URL.");
                        } else {
                            System.out.println("Pages pointing to " + url + ":\n");
                            for (String link : inlinks) {
                                System.out.println(" - " + link);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error fetching inlinks: " + e.getMessage());
                    }

                } else if (opcao == 4) {
                    try {
                        SystemStats stats = gateway.getSystemStats();

                        System.out.println("\n=== SYSTEM STATISTICS ===");
                        System.out.println("\nTop 10 Searches:");
                        stats.getTop10Searches().forEach(e ->
                                System.out.printf("  %s: %d times%n", e.getKey(), e.getValue()));

                        System.out.println("\nActive Barrels:");
                        stats.getBarrelMetrics().forEach((name, metrics) ->
                                System.out.printf("  %s - Index: %d pages | Avg time: %.1f ms%n",
                                        name, metrics.getIndexSize(), (double) metrics.getAvgResponseTimeMs()));

                    } catch (Exception e) {
                        System.err.println("Error fetching statistics: " + e.getMessage());
                    }

                } else if (opcao == 5) {
                    System.out.println("Exiting...");
                    break;
                }
            }

            sc.close();

        } catch (Exception e) {
            System.err.println("Error starting Client: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
