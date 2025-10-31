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

    public Gateway(BarrelIndex b1, BarrelIndex b2) throws RemoteException {
        super();
        this.barrel1 = b1;
        this.barrel2 = b2;
    }

    /**
     * Pesquisa nos dois Barrels e devolve a lista combinada.
     */
    @Override
    public List<PageInfo> search(String query) throws RemoteException {
        List<String> terms = List.of(query.toLowerCase().split("\\s+"));
        List<PageInfo> results = new ArrayList<>();

        try {
            results.addAll(barrel1.searchPages(terms));
        } catch (Exception e) {
            System.err.println("Erro ao pesquisar no Barrel1: " + e.getMessage());
        }

        try {
            results.addAll(barrel2.searchPages(terms));
        } catch (Exception e) {
            System.err.println("Erro ao pesquisar no Barrel2: " + e.getMessage());
        }

        return results;
    }

    /**
     * Adiciona URL para indexação (envia para o primeiro barrel disponível)
     */
    @Override
    public void addUrl(String url) throws RemoteException {
        try {
            //Mudar true false, mesnagem
            System.out.println("URL enviado para Barrel1: " + url);
            boolean adicionado = barrel1.addUrlToQueue(url);
            if(DebugConfig.DEBUG_URL_INDEXAR){
                System.out.println("[DEBUG]: URL " + url + (adicionado ? " adicionada" : " não adicionada") + " à fila do Barrel1.");
            }

        } catch (Exception e1) {
            try {
                System.out.println("URL enviado para Barrel2: " + url);
                barrel2.addUrlToQueue(url);
                if(DebugConfig.DEBUG_URL_INDEXAR){
                    System.out.println("[DEBUG]: URL " + url + " adicionada à fila do Barrel2.");
                }

            } catch (Exception e2) {
                System.err.println("Nenhum Barrel disponível: " + e2.getMessage());
            }
        }
    }
}
