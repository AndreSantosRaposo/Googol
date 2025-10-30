import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface remota da Gateway.
 * Define os métodos que o cliente pode invocar remotamente via RMI.
 */
public interface GatewayInterface extends Remote {

    /**
     * Pesquisa páginas que contêm os termos fornecidos.
     * @param query consulta de pesquisa (palavras separadas por espaços)
     * @return lista de PageInfo correspondentes
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    List<PageInfo> search(String query) throws RemoteException;

    /**
     * Adiciona uma nova URL para ser indexada.
     * @param url URL a ser indexada
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    void addUrl(String url) throws RemoteException;
}
