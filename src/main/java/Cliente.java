import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

public class Cliente {

    private static void dezLinks(Integer LimMax, Integer LimMin, List<PageInfo> results){
        while(LimMin < LimMax && LimMin < results.size()){
            PageInfo p = results.get(LimMin);
            System.out.printf("- %s (%s) citação: %s\n",
                    p.getTitle(), p.getUrl(), p.getSmallText());
            LimMin++;

        }
    }

    private static boolean adicionarUrlComRetry(GatewayInterface gateway, String url, int maxRetries, int delayMs) {
        for (int tentativa = 1; tentativa <= maxRetries; tentativa++) {
            try {
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: Tentativa " + tentativa + "/" + maxRetries + " de adicionar URL: " + url);
                }

                gateway.addUrl(url);
                System.out.println("✅ URL adicionada com sucesso!");
                return true;

            } catch (Exception e) {
                // Obter a causa raiz (exceção original do servidor)
                Throwable causa = e.getCause();

                // Verificar se é UrlAlreadyIndexedException
                if (causa instanceof UrlAlreadyIndexedException) {
                    System.err.println("⚠️ " + causa.getMessage());
                    return false;
                }

                // Verificar se é BarrelUnavailableException
                if (causa instanceof BarrelUnavailableException) {
                    if (tentativa < maxRetries) {
                        System.err.println("⚠️ Tentativa " + tentativa + " falhou: " + causa.getMessage());
                        System.out.println("🔄 A tentar novamente em " + delayMs + "ms...");

                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    } else {
                        System.err.println("❌ Todas as " + maxRetries + " tentativas falharam: " + causa.getMessage());
                        return false;
                    }
                } else {
                    // Outro erro desconhecido
                    System.err.println("❌ Erro inesperado: " + e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }



    public static void main(String[] args) {

        String filename = "config.txt";
        final int CLIENTE_LINE = 6;
        final int GATEWAY_LINE = 1;
        int LimMax=10;
        int LimMin=0;

        try {
            // === Ler config do cliente ===
            List<String> clienteCfg = FileManipulation.lineSplitter(filename, CLIENTE_LINE, ";");
            String clienteName = clienteCfg.get(0).trim();
            String clienteIp = clienteCfg.get(1).trim();
            int clientePort = Integer.parseInt(clienteCfg.get(2).trim());

            // === Ler config do gateway ===
            List<String> gatewayCfg = FileManipulation.lineSplitter(filename, GATEWAY_LINE, ";");
            String gatewayIp = gatewayCfg.get(1).trim();
            int gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());
            String gatewayName = gatewayCfg.get(0).trim();

            // === Obter referência ao Gateway ===
            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            GatewayInterface gateway = (GatewayInterface) registry.lookup(gatewayName);

            System.out.printf("Cliente '%s' ligado ao Gateway em %s:%d%n", clienteName, gatewayIp, gatewayPort);

            // === Interface de texto com validações ===
            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.println("\n--- MENU CLIENTE ---");
                System.out.println("1. Pesquisar");
                System.out.println("2. Adicionar URL");
                System.out.println("3. Ver estatísticas");
                System.out.println("4. Sair");
                System.out.print("Escolha: ");

                int opcao;
                try {
                    opcao = Integer.parseInt(sc.nextLine());
                } catch (NumberFormatException e) {
                    System.err.println("Erro: Insira um número válido (1-4). Tente novamente.");
                    continue;
                }

                if (opcao < 1 || opcao > 4) {
                    System.err.println("Erro: Opção fora do intervalo (1-4). Tente novamente.");
                    continue;
                }

                if (opcao == 1) {
                    System.out.print("Digite termos de pesquisa: ");
                    String query = sc.nextLine().trim();

                    if (query.isEmpty()) {
                        System.err.println("Erro: A pesquisa não pode estar vazia. Tente novamente.");
                        continue;
                    }

                    try {
                        List<PageInfo> results = gateway.search(query);
                        if (results.isEmpty()) {
                            System.out.println("(Nenhum resultado encontrado)");
                            continue;
                        }

                        System.out.println("Resultados:");
                        int limMin = 0;
                        int limMax = 10;

                        // mostra a primeira página
                        dezLinks(limMax, limMin, results);

                        // paginação
                        while (limMax <= results.size()) {
                            System.out.print("\nDeseja ver os próximos 10 links? (s/n): ");
                            String opcaoString = sc.nextLine().trim().toLowerCase();

                            if (opcaoString.equals("s")) {
                                limMin = limMax;
                                limMax = Math.min(limMax + 10, results.size());
                                dezLinks(limMax, limMin, results);

                                if (limMax >= results.size()) {
                                    System.out.println("Acabaram os links.");
                                    break;
                                }

                            } else if (opcaoString.equals("n")) {
                                break;
                            } else {
                                System.out.println("Opção inválida. Digite 's' ou 'n'.");
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("Erro ao pesquisar: " + e.getMessage());
                    }


                } else if (opcao == 2) {
                    System.out.print("Digite a URL: ");
                    String url = sc.nextLine().trim();

                    if (url.isEmpty()) {
                        System.err.println("Erro: A URL não pode estar vazia. Tente novamente.");
                        continue;
                    }

                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        System.err.println(" Aviso: A URL deve começar com 'http://' ou 'https://'. Tente novamente.");
                        continue;
                    }

                    try {
                        if (DebugConfig.DEBUG_URL_INDEXAR) {
                            System.out.println("[DEBUG]: A adicionar URL " + url + " para indexação.");
                        }
                        adicionarUrlComRetry(gateway, url, 3, 2000);
                    } catch (Exception e) {
                        System.err.println("Erro ao adicionar URL: " + e.getMessage());
                    }

                } else if (opcao == 3) {
                    try {
                        SystemStats stats = gateway.getSystemStats();

                        System.out.println("\n=== ESTATÍSTICAS DO SISTEMA ===");
                        System.out.println("\n🔍 Top 10 Pesquisas:");
                        stats.getTop10Searches().forEach(e ->
                                System.out.printf("  %s: %d vezes\n", e.getKey(), e.getValue()));

                        System.out.println("\n📦 Barrels Ativos:");
                        stats.getBarrelMetrics().forEach((name, metrics) ->
                                System.out.printf("  %s - Índice: %d páginas | Tempo médio: %.1f ms\n",
                                        name, metrics.getIndexSize(), metrics.getAvgResponseTimeMs() / 10.0));

                    } catch (Exception e) {
                        System.err.println("Erro ao obter estatísticas: " + e.getMessage());
                    }

                } else if (opcao == 4) {
                    System.out.println("A sair...");
                    break;
                }
            }

            sc.close();

        } catch (Exception e) {
            System.err.println("Erro ao iniciar Cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }
}