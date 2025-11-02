import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class GatewayServer {
    public static void main(String[] args) {
        String filename = "config.txt";

        try {
            List<String> gatewayCfg = FileManipulation.lineSplitter(filename, 1, ";");
            List<String> barrel1Cfg = FileManipulation.lineSplitter(filename, 2, ";");
            List<String> barrel2Cfg = FileManipulation.lineSplitter(filename, 3, ";");

            String gatewayName = gatewayCfg.get(0).trim();
            String gatewayIp = gatewayCfg.get(1).trim();
            int gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());

            String barrel1Name = barrel1Cfg.get(0).trim();
            String barrel1Ip = barrel1Cfg.get(1).trim();
            int barrel1Port = Integer.parseInt(barrel1Cfg.get(2).trim());

            String barrel2Name = barrel2Cfg.get(0).trim();
            String barrel2Ip = barrel2Cfg.get(1).trim();
            int barrel2Port = Integer.parseInt(barrel2Cfg.get(2).trim());

            // Criar Gateway com info de conexão
            Gateway gateway = new Gateway(
                    barrel1Name, barrel1Ip, barrel1Port,
                    barrel2Name, barrel2Ip, barrel2Port,
                    gatewayIp, gatewayPort
            );

            // Registar Gateway
            try {
                LocateRegistry.createRegistry(gatewayPort);
            } catch (Exception ignored) {}

            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            registry.rebind(gatewayName, gateway);

            System.out.printf("Gateway '%s' pronto em %s:%d%n", gatewayName, gatewayIp, gatewayPort);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
