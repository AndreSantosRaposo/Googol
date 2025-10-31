import kotlin.jvm.Synchronized;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação da Gateway — intermedeia comunicação entre Cliente e Barrels.
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {
    private final BarrelIndex barrel1;
    private final BarrelIndex barrel2;
    private int nextBarrel = 1;


    public Gateway(BarrelIndex b1, BarrelIndex b2) throws RemoteException {
        super();
        this.barrel1 = b1;
        this.barrel2 = b2;
    }

    /**
     * Pesquisa nos dois Barrels e devolve a lista combinada.
     */

    public  List<PageInfo> search(String query) throws RemoteException {
        List<String> terms = List.of(query.toLowerCase().split("\\s+"));
        List<PageInfo> results = new ArrayList<>();

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

    /**
     * Adiciona URL para indexação (envia para o primeiro barrel disponível)
     */
    public void addUrl(String url) throws RemoteException {
        boolean algumSucesso = false;

        // Tenta adicionar ao Barrel1
        if (barrel1 != null) {
            try {
                boolean adicionado1 = barrel1.addUrlToQueue(url);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (adicionado1 ? " adicionada" : " não adicionada") + " à fila do Barrel1.");
                }
                algumSucesso = adicionado1 || algumSucesso;
            } catch (Exception e) {
                System.err.println("Erro ao adicionar ao Barrel1: " + e.getMessage());
            }
        } else {
            System.err.println("Barrel1 não disponível");
        }

        // Tenta adicionar ao Barrel2
        if (barrel2 != null) {
            try {
                boolean adicionado2 = barrel2.addUrlToQueue(url);
                if (DebugConfig.DEBUG_URL_INDEXAR) {
                    System.out.println("[DEBUG]: URL " + url + (adicionado2 ? " adicionada" : " não adicionada") + " à fila do Barrel2.");
                }
                algumSucesso = adicionado2 || algumSucesso;
            } catch (Exception e) {
                System.err.println("Erro ao adicionar ao Barrel2: " + e.getMessage());
            }
        } else {
            System.err.println("Barrel2 não disponível");
        }

        // Se nenhum Barrel conseguiu processar
        if (!algumSucesso) {
            throw new RemoteException("Nenhum Barrel disponível para indexar: " + url);
        }
    }
}
