import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

public class Cliente {

    public static void main(String[] args) {

        String filename = "config.txt";
        final int CLIENTE_LINE = 6;  // linha do cliente
        final int GATEWAY_LINE = 1;  // linha do gateway

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

            // === Interface de texto simples ===
            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.println("\n--- MENU CLIENTE ---");
                System.out.println("1. Pesquisar");
                System.out.println("2. Adicionar URL");
                System.out.println("3. Sair");
                System.out.print("Escolha: ");
                int opcao = Integer.parseInt(sc.nextLine());

                if (opcao == 1) {
                    System.out.print("Digite termos de pesquisa: ");
                    String query = sc.nextLine();
                    List<PageInfo> results = gateway.search(query);
                    System.out.println("Resultados:");
                    for (PageInfo p : results) {
                        System.out.printf("- %s (%s) citação: %s\n", p.getTitle(), p.getUrl(), p.getSmallText());
                    }

                } else if (opcao == 2) {
                    System.out.print("Digite a URL: ");
                    String url = sc.nextLine();
                    //==========================DEBUG=============================
                    if(DebugConfig.DEBUG_URL_INDEXAR){
                        System.out.println("[DEBUG]: A adicionar URL " + url + " para indexação.");
                    }
                    gateway.addUrl(url);

                } else if (opcao == 3) {
                    System.out.println("A sair...");
                    break;
                } else {
                    System.out.println("Opção inválida.");
                }
            }

        } catch (Exception e) {
            System.err.println("Erro ao iniciar Cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
