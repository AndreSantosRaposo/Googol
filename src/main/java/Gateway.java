import kotlin.jvm.Synchronized;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
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

    public Gateway(String b1Name, String b1Ip, int b1Port,
                   String b2Name, String b2Ip, int b2Port) throws RemoteException {
        super();
        this.barrel1Name = b1Name;
        this.barrel1Ip = b1Ip;
        this.barrel1Port = b1Port;
        this.barrel2Name = b2Name;
        this.barrel2Ip = b2Ip;
        this.barrel2Port = b2Port;

        // Conexão inicial
        reconnectBarrel1();
        reconnectBarrel2();
    }
    //Para escalar (professor disse que nao é necessário), em vez de usar funcoes diferentes podia ter um hashmap com "nome": info , e entao na funcao verificaria
    //o nome do barrel que esta down, e depois fazia get para ter o seu ip e porto, e o index (por referencia)
    private void reconnectBarrel1() {
        try {
            Registry registry = LocateRegistry.getRegistry(barrel1Ip, barrel1Port);
            barrel1 = (BarrelIndex) registry.lookup(barrel1Name);
            System.out.println("Barrel1 conectado");
        } catch (Exception e) {
            System.err.println("Barrel1 indisponível: " + e.getMessage());
            barrel1 = null;
        }
    }

    private void reconnectBarrel2() {
        try {
            Registry registry = LocateRegistry.getRegistry(barrel2Ip, barrel2Port);
            barrel2 = (BarrelIndex) registry.lookup(barrel2Name);
            System.out.println("Barrel2 conectado");
        } catch (Exception e) {
            System.err.println("Barrel2 indisponível: " + e.getMessage());
            barrel2 = null;
        }
    }


    /**
     * Pesquisa nos dois Barrels e devolve a lista combinada.
     */

    public  List<PageInfo> search(String query) throws RemoteException {

        List<String> terms = List.of(query.toLowerCase().split("\\s+"));
        List<PageInfo> results = new ArrayList<>();

        // Tenta reconectar se necessário
        if (barrel1 == null) reconnectBarrel1();
        if (barrel2 == null) reconnectBarrel2();

        try {
            if(nextBarrel==1) {
                results.addAll(barrel1.searchPages(terms));
                nextBarrel=2;
            }else{
                results.addAll(barrel2.searchPages(terms));
                nextBarrel=1;
            }
        } catch (Exception e) {
            System.err.println("Erro ao pesquisar no Barrel" + nextBarrel + ": " + e.getMessage());


        }
        return results;
    }

    @Override
    public void addUrl(String url) throws RemoteException {
        boolean algumSucesso = false;

        // Tenta reconectar se necessário
        if (barrel1 == null) reconnectBarrel1();
        if (barrel2 == null) reconnectBarrel2();

        if (barrel1 != null) {
            try {
                boolean adicionado1 = barrel1.addUrlToQueue(url);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (adicionado1 ? " adicionada" : " não adicionada") + " à fila do Barrel1.");
                }
                algumSucesso = adicionado1 || algumSucesso;
            } catch (Exception e) {
                System.err.println("Erro ao adicionar ao Barrel1: " + e.getMessage());
                barrel1 = null;
            }
        } else {
            System.err.println("Barrel1 não disponível");
        }

        if (barrel2 != null) {
            try {
                boolean adicionado2 = barrel2.addUrlToQueue(url);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (adicionado2 ? " adicionada" : " não adicionada") + " à fila do Barrel2.");
                }
                algumSucesso = adicionado2 || algumSucesso;
            } catch (Exception e) {
                System.err.println("Erro ao adicionar ao Barrel2: " + e.getMessage());
                barrel2 = null;
            }
        } else {
            System.err.println("Barrel2 não disponível");
        }

        if (!algumSucesso) {
            throw new RemoteException("Nenhum Barrel disponível para indexar: " + url);
        }
    }
}
