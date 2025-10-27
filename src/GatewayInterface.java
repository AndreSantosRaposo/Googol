import java.rmi.*;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface remota da Gateway.
 * Define os métodos que o cliente pode invocar remotamente via RMI.
 */
public interface GatewayInterface extends Remote {

    /**
     * @param terms lista de termos a pesquisar
     * @return lista de PageInfo correspondentes
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    List<PageInfo> search(List<String> terms) throws RemoteException;

    /**
     * @param url URL a ser indexado
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    void addUrl(String url) throws RemoteException;
}
