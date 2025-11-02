import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Gateway extends UnicastRemoteObject implements GatewayInterface {
    private  BarrelIndex barrel1;
    private  BarrelIndex barrel2;
    private int nextBarrel = 1;
    private final String barrel1Name;
    private final String barrel1Ip;
    private final int barrel1Port;

    private final String barrel2Name;
    private final String barrel2Ip;
    private final int barrel2Port;

    HashMap<Integer, String> urlHistory;
    int currentSeqNumber;
    private final String name;
    private final String gatewayIp;
    private final int gatewayPort;

    public Gateway(String b1Name, String b1Ip, int b1Port,
                   String b2Name, String b2Ip, int b2Port, String gatewayIp, int gatewayPort) throws RemoteException {
        super();
        this.barrel1Name = b1Name;
        this.barrel1Ip = b1Ip;
        this.barrel1Port = b1Port;
        this.barrel2Name = b2Name;
        this.barrel2Ip = b2Ip;
        this.barrel2Port = b2Port;
        this.urlHistory = new HashMap<>();
        this.currentSeqNumber = 0;
        this.name = "Gateway";
        this.gatewayIp = gatewayIp;
        this.gatewayPort = gatewayPort;

        // Conexão inicial
        reconnectBarrel1();
        reconnectBarrel2();
        if (barrel1 != null) {
            barrel1.resetSeqNumbers(name);
        }
        if (barrel2 != null) {
            barrel2.resetSeqNumbers(name);
        }
    }
    //Para escalar (professor disse que nao é necessário), em vez de usar funcoes diferentes podia ter um hashmap com "nome": info , e entao na funcao verificaria
    //o nome do barrel que esta down, e depois fazia get para ter o seu ip e porto, e o index (por referencia)

    /**
     * Tenta reconectar ao Barrel1 se estiver indisponível.
     */
    private void reconnectBarrel1() {
        try {
            Registry registry = LocateRegistry.getRegistry(barrel1Ip, barrel1Port);
            barrel1 = (BarrelIndex) registry.lookup(barrel1Name);
            System.out.println("Barrel1 conectado");
        } catch (Exception e) {
            System.err.println(barrel1Name + " indisponível: " + e.getMessage());
            barrel1 = null;
        }
    }

    private void reconnectBarrel2() {
        try {
            Registry registry = LocateRegistry.getRegistry(barrel2Ip, barrel2Port);
            barrel2 = (BarrelIndex) registry.lookup(barrel2Name);
            System.out.println(barrel2Name+ "conectado");
        } catch (Exception e) {
            System.err.println("Barrel2 indisponível: " + e.getMessage());
            barrel2 = null;
        }
    }

    /**
     * Pesquisa nos dois Barrels e devolve a lista combinada.
     */

    @Override
    public List<PageInfo> search(String query) throws RemoteException {
        List<String> terms = List.of(query.toLowerCase().split("\\s+"));
        List<PageInfo> results = new ArrayList<>();

        // tenta reconectar se necessário
        if (barrel1 == null) reconnectBarrel1();
        if (barrel2 == null) reconnectBarrel2();

        try {
            // tenta o barrel atual
            if (nextBarrel == 1 && barrel1 != null) {
                results.addAll(barrel1.searchPages(terms));
                nextBarrel = 2; // alterna para o outro da próxima vez
                return results;
            } else if (nextBarrel == 2 && barrel2 != null) {
                results.addAll(barrel2.searchPages(terms));
                nextBarrel = 1;
                return results;
            }

            // se o barrel da vez estiver indisponível, tenta o outro
            if (barrel1 != null) {
                results.addAll(barrel1.searchPages(terms));
                nextBarrel = 2;
            } else if (barrel2 != null) {
                results.addAll(barrel2.searchPages(terms));
                nextBarrel = 1;
            } else {
                throw new RemoteException("Nenhum Barrel disponível para pesquisa.");
            }

        } catch (Exception e) {
            System.err.println("[Gateway] Erro ao pesquisar: " + e.getMessage());
        }

        return results;


}

    public void addUrl(String url) throws RemoteException {
        if (DebugConfig.DEBUG_URL_INDEXAR || DebugConfig.DEBUG_MULTICAST_GATEWAY || DebugConfig.DEBUG_ALL) {
            System.out.println("[DEBUG]: Adicionando URL: " + url + " com SeqNumber: " + currentSeqNumber);
        }

        boolean algumSucesso = false;
        boolean barrel1Disponivel = false;
        boolean barrel2Disponivel = false;

        urlHistory.put(currentSeqNumber, url);

        if (barrel1 == null) reconnectBarrel1();

        if (barrel1 != null) {
            barrel1Disponivel = true;
            try {
                boolean adicionado1 = barrel1.addUrlToQueue(url, currentSeqNumber, name, gatewayIp, gatewayPort);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (adicionado1 ? " adicionada" : " não adicionada") + " à fila do Barrel1.");
                }
                algumSucesso = adicionado1;
            } catch (Exception e) {
                System.err.println("Erro ao adicionar ao Barrel1: " + e.getMessage());
                barrel1 = null;
                barrel1Disponivel = false;
            }
        } else {
            System.err.println("Barrel1 não disponível");
        }

        if (barrel2 != null) {
            barrel2Disponivel = true;
            try {
                boolean adicionado2 = barrel2.addUrlToQueue(url, currentSeqNumber, name, gatewayIp, gatewayPort);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (adicionado2 ? " adicionada" : " não adicionada") + " à fila do Barrel2.");
                }
                algumSucesso = adicionado2 || algumSucesso;
            } catch (Exception e) {
                System.err.println("Erro ao adicionar ao Barrel2: " + e.getMessage());
                barrel2 = null;
                barrel2Disponivel = false;
            }
        } else {
            System.err.println("Barrel2 não disponível");
        }

        currentSeqNumber++;

        if (!barrel1Disponivel && !barrel2Disponivel) {
            throw new BarrelUnavailableException("Nenhum Barrel disponível no momento.");
        }

        if (!algumSucesso) {
            throw new UrlAlreadyIndexedException("URL não aceite pelos Barrels (possivelmente já indexada).");
        }
    }



    public void reSendURL(int missingSeqNumber, BarrelIndex receiver) throws RemoteException {
        if (DebugConfig.DEBUG_MULTICAST_GATEWAY || DebugConfig.DEBUG_ALL) {
            System.out.println("[DEBUG]: Reenviando URL com SeqNumber: " + missingSeqNumber);
        }

        int tryNumber = 0;
        while (tryNumber < 3) {
            try {
                String url = urlHistory.get(missingSeqNumber);
                if (url != null) {
                    // USAR missingSeqNumber, NÃO currentSeqNumber
                    receiver.addUrlToQueue(url, missingSeqNumber, name, gatewayIp, gatewayPort);
                    return;
                } else {
                    System.err.println("[Gateway] URL com SeqNumber " + missingSeqNumber + " não encontrada no histórico.");
                    return;
                }
            } catch (Exception e) {
                System.err.println("[Gateway] Erro ao reenviar URL com SeqNumber " + missingSeqNumber + ": " + e.getMessage());
                tryNumber++;

                // Tenta reconectar
                if (receiver == barrel1) {
                    reconnectBarrel1();
                    receiver = barrel1;
                } else if (receiver == barrel2) {
                    reconnectBarrel2();
                    receiver = barrel2;
                }
            }
        }
    }
}
