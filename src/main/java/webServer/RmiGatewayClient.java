package webServer;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.List;

public class RmiGatewayClient {

    private GatewayInterface gateway;
    private static final String DEFAULT_CONFIG_FILE = "config.txt";


    public void init() {
        String filename=DEFAULT_CONFIG_FILE;
        try {

            List<String> gatewayCfg = FileManipulation.lineSplitter(DEFAULT_CONFIG_FILE, 1, ";");
            String gatewayName = gatewayCfg.get(0).trim();
            String gatewayIp = gatewayCfg.get(1).trim();
            int gatewayPort = Integer.parseInt(gatewayCfg.get(2).trim());

            String url = "rmi://" + gatewayIp + ":" + gatewayPort + "/" + gatewayName;
            System.out.println("[RMI] Connecting to: " + url);
            gateway = (GatewayInterface) Naming.lookup(url);
            System.out.println("[RMI] Connected successfully.");
        } catch (Exception e) {
            System.err.println("[RMI] Failed to connect:");
            e.printStackTrace();
        }
    }

    public List<PageInfo> search(String query) throws RemoteException {
        return gateway.search(query);
    }

    public void addUrl(String url) throws RemoteException {
        gateway.addUrl(url);
    }

    public SystemStats getSystemStats() throws RemoteException {
        return gateway.getSystemStats();
    }

    public List<String> searchInlinks(String url) throws RemoteException {
        return gateway.searchInlinks(url);
    }

}
