package webServer;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

/**
 * Cliente que interage com o Gateway para realizar pesquisas, adicionar URLs e obter estatísticas.
 *
 * <p>Funcionalidades:
 * <ul>
 *     <li>Pesquisar páginas por palavras-chave com resultados paginados</li>
 *     <li>Adicionar URLs com lógica de tentativas e reconexão</li>
 *     <li>Pesquisar inlinks (páginas que apontam para uma URL)</li>
 *     <li>Consultar estatísticas do sistema</li>
 * </ul>
 */
public class Cliente {

    private static GatewayInterface gateway;

    /**
     * Mostra até 10 resultados de pesquisa.
     */
    private static void dezLinks(int limMax, int limMin, List<PageInfo> results) {
        while (limMin < limMax && limMin < results.size()) {
            PageInfo p = results.get(limMin);
            System.out.printf("- %s (%s) citation: %s%n",
                    p.getTitle(), p.getUrl(), p.getSmallText());
            limMin++;
        }
    }

    /**
     * Tenta reconectar ao Gateway até 3 vezes.
     */
    private static GatewayInterface reconnectGateway(String gatewayName, String gatewayIp, int gatewayPort) {
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                System.out.printf("Tentando reconectar ao Gateway (%s:%d)... (tentativa %d/3)%n",
                        gatewayIp, gatewayPort, tentativa);
                Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
                GatewayInterface gateway = (GatewayInterface) registry.lookup(gatewayName);
                System.out.println("Reconectado com sucesso!");
                return gateway;
            } catch (Exception e) {
                System.err.println("Falha ao reconectar: " + e.getMessage());
            }
        }
        System.err.println("Não foi possível reconectar ao Gateway após 3 tentativas.");
        return null;
    }

    /**
     * Adiciona uma URL ao Gateway com lógica de repetição em caso de falha temporária.
     */
    private static boolean adicionarUrlComRetry(GatewayInterface gateway, String url, int maxRetries, int delayMs) {
        for (int tentativa = 1; tentativa <= maxRetries; tentativa++) {
            try {
                gateway.addUrl(url);
                System.out.println("URL adicionada com sucesso!");
                return true;

            } catch (Exception e) {
                Throwable causa = e.getCause();

                if (causa instanceof UrlAlreadyIndexedException) {
                    System.err.println("⚠️ " + causa.getMessage());
                    return false;
                }

                if (causa instanceof BarrelUnavailableException) {
                    if (tentativa < maxRetries) {
                        System.err.println("Tentativa " + tentativa + " falhou: " + causa.getMessage());
                        System.out.println("A tentar novamente em " + delayMs + "ms...");
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    } else {
                        System.err.println(" Todas as " + maxRetries + " tentativas falharam: " + causa.getMessage());
                        return false;
                    }
                } else {
                    System.err.println("Erro inesperado: " + e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Ponto de entrada principal.
     */
    /* === Main method === */
    public static void main(String[] args) {
        final String filename = "config.txt";
        final int GATEWAY_LINE = 1;

        // Read Gateway configuration
        String gatewayName;
        String gatewayIp;
        int gatewayPort;
        try {
            List<String> gatewayCfg = FileManipulation.lineSplitter(filename, GATEWAY_LINE, ";");
            gatewayName = gatewayCfg.get(0).trim();
            gatewayIp = gatewayCfg.get(1).trim();
            gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());
        } catch (Exception e) {
            System.err.println("Error reading gateway configuration: " + e.getMessage());
            return;
        }

        // Connect to Gateway
        try {
            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            gateway = (GatewayInterface) registry.lookup(gatewayName);
            System.out.printf("Client connected to Gateway at %s:%d%n", gatewayIp, gatewayPort);
        } catch (Exception e) {
            System.err.println("Could not connect to Gateway: " + e.getMessage());

        }
        Scanner sc = new Scanner(System.in);

        try {
            while (true) {
                System.out.println("\n--- MENU CLIENTE ---");
                System.out.println("1. Pesquisar");
                System.out.println("2. Adicionar URL");
                System.out.println("3. Pesquisa por inlink");
                System.out.println("4. Ver estatísticas");
                System.out.println("5. Sair");
                System.out.print("Escolha: ");

                int opcao;
                try {
                    opcao = Integer.parseInt(sc.nextLine());
                } catch (NumberFormatException e) {
                    System.err.println("Erro: Insira um número válido (1-5).");
                    continue;
                }

                switch (opcao) {
                    case 1 -> { // PESQUISAR
                        System.out.print("Digite termos de pesquisa: ");
                        String query = sc.nextLine().trim();
                        if (query.isEmpty()) {
                            System.err.println("Erro: Pesquisa vazia.");
                            continue;
                        }

                        List<PageInfo> results = null;
                        try {
                            results = gateway.search(query);
                        } catch (Exception e) {
                            System.err.println(" Falha ao pesquisar: " + e.getMessage());
                            gateway = reconnectGateway(gatewayName, gatewayIp, gatewayPort);
                            if (gateway != null) {
                                try {
                                    System.out.println("A repetir pesquisa após reconexão...");
                                    results = gateway.search(query);
                                } catch (Exception ex) {
                                    System.err.println("Falhou novamente após reconexão.");
                                    continue;
                                }
                            } else continue;
                        }

                        if (results == null || results.isEmpty()) {
                            System.out.println("(Nenhum resultado encontrado)");
                            continue;
                        }

                        System.out.println("Resultados:");
                        int limMin = 0, limMax = 10;
                        dezLinks(limMax, limMin, results);

                        while (limMax < results.size()) {
                            System.out.print("\nVer próximos 10 links? (s/n): ");
                            if (sc.nextLine().trim().equalsIgnoreCase("s")) {
                                limMin = limMax;
                                limMax = Math.min(limMax + 10, results.size());
                                dezLinks(limMax, limMin, results);
                            } else break;
                        }
                    }

                    case 2 -> { // ADICIONAR URL
                        System.out.print("Digite a URL: ");
                        String url = sc.nextLine().trim();
                        if (url.isEmpty()) {
                            System.err.println("Erro: A URL não pode estar vazia.");
                            continue;
                        }
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            System.err.println("Aviso: A URL deve começar com http:// ou https://");
                            continue;
                        }

                        try {
                            adicionarUrlComRetry(gateway, url, 3, 2000);
                        } catch (Exception e) {
                            System.err.println("⚠ Falha ao adicionar URL: " + e.getMessage());
                            gateway = reconnectGateway(gatewayName, gatewayIp, gatewayPort);
                            if (gateway != null) {
                                try {
                                    System.out.println(" A repetir envio após reconexão...");
                                    adicionarUrlComRetry(gateway, url, 3, 2000);
                                } catch (Exception ex) {
                                    System.err.println(" Falhou novamente após reconexão.");
                                }
                            }
                        }
                    }

                    case 3 -> { // PESQUISAR INLINKS
                        System.out.print("Digite a URL para ver os inlinks: ");
                        String url = sc.nextLine().trim();
                        if (url.isEmpty()) {
                            System.err.println("Erro: A URL não pode estar vazia.");
                            continue;
                        }

                        List<String> inlinks = null;
                        try {
                            inlinks = gateway.searchInlinks(url);
                        } catch (Exception e) {
                            System.err.println("Erro ao obter inlinks: " + e.getMessage());
                            gateway = reconnectGateway(gatewayName, gatewayIp, gatewayPort);
                            if (gateway != null) {
                                try {
                                    System.out.println("A repetir busca após reconexão...");
                                    inlinks = gateway.searchInlinks(url);
                                } catch (Exception ex) {
                                    System.err.println(" Falhou novamente após reconexão.");
                                    continue;
                                }
                            } else continue;
                        }

                        if (inlinks == null || inlinks.isEmpty()) {
                            System.out.println("Nenhuma página conhecida aponta para esta URL.");
                        } else {
                            System.out.println("Páginas que apontam para " + url + ":");
                            inlinks.forEach(link -> System.out.println(" - " + link));
                        }
                    }

                    case 4 -> { // ESTATÍSTICAS
                        try {
                            SystemStats stats = gateway.getSystemStats();
                            System.out.println("\n=== ESTATÍSTICAS DO SISTEMA ===");
                            System.out.println("\n🔍 Top 10 Pesquisas:");
                            stats.getTop10Searches().forEach(e ->
                                    System.out.printf("  %s: %d vezes%n", e.getKey(), e.getValue()));

                            System.out.println("\nBarrels Ativos:");
                            stats.getBarrelMetrics().forEach((name, metrics) ->
                                    System.out.printf("  %s - Índice: %d páginas | Tempo médio: %.1f ms%n",
                                            name, metrics.getIndexSize(), (double) metrics.getAvgResponseTimeMs()));
                        } catch (Exception e) {
                            System.err.println(" Erro ao obter estatísticas: " + e.getMessage());
                            gateway = reconnectGateway(gatewayName, gatewayIp, gatewayPort);
                            if (gateway != null) {
                                try {
                                    System.out.println("🔄 A repetir pedido após reconexão...");
                                    SystemStats stats = gateway.getSystemStats();
                                    System.out.println("\n=== ESTATÍSTICAS DO SISTEMA ===");
                                    stats.getTop10Searches().forEach(e2 ->
                                            System.out.printf("  %s: %d vezes%n", e2.getKey(), e2.getValue()));
                                } catch (Exception ex) {
                                    System.err.println(" Falhou novamente após reconexão.");
                                }
                            }
                        }
                    }

                    case 5 -> { // SAIR
                        System.out.println("👋 A sair...");
                        return;
                    }

                    default -> System.err.println("Opção inválida.");
                }
            }
        } catch (Exception e) {
            System.err.println("Erro na execução do cliente: " + e.getMessage());
            e.printStackTrace();
        } finally {
            sc.close();
        }
    }
}
