import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class GatewayServer {
    public static void main(String[] args) {
        String filename = "config.txt";
        final int GATEWAY_LINE = 1;
        final int BARREL1_LINE = 2;
        final int BARREL2_LINE = 3;

        try {
            // === Ler configuração do Gateway ===
            List<String> gatewayCfg = FileManipulation.lineSplitter(filename, GATEWAY_LINE, ";");
            String gatewayName = gatewayCfg.get(0).trim();
            String gatewayIp = gatewayCfg.get(1).trim();
            int gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());

            // === Ler configuração dos Barrels ===
            List<String> barrel1Cfg = FileManipulation.lineSplitter(filename, BARREL1_LINE, ";");
            List<String> barrel2Cfg = FileManipulation.lineSplitter(filename, BARREL2_LINE, ";");

            String barrel1Ip = barrel1Cfg.get(1).trim();
            int barrel1Port = Integer.parseInt(barrel1Cfg.get(2).trim());
            String barrel1Name = barrel1Cfg.get(0).trim();

            String barrel2Ip = barrel2Cfg.get(1).trim();
            int barrel2Port = Integer.parseInt(barrel2Cfg.get(2).trim());
            String barrel2Name = barrel2Cfg.get(0).trim();

            // === Obter referências RMI aos barrels ===
            Registry reg1 = LocateRegistry.getRegistry(barrel1Ip, barrel1Port);
            Registry reg2 = LocateRegistry.getRegistry(barrel2Ip, barrel2Port);

            BarrelIndex barrel1 = (BarrelIndex) reg1.lookup(barrel1Name);
            BarrelIndex barrel2 = (BarrelIndex) reg2.lookup(barrel2Name);

            // === Criar o objeto Gateway ===
            Gateway gateway = new Gateway(barrel1, barrel2);

            // === Criar (ou ligar) Registry e registar o Gateway ===
            try {
                LocateRegistry.createRegistry(gatewayPort);
                System.out.println("Registry criado no porto " + gatewayPort);
            } catch (Exception ignored) {
                System.out.println("Registry já existente no porto " + gatewayPort);
            }

            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            registry.rebind(gatewayName, gateway);

            System.out.printf("Gateway '%s' pronto em %s:%d%n", gatewayName, gatewayIp, gatewayPort);

        } catch (Exception e) {
            System.err.println("Erro ao iniciar GatewayServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
