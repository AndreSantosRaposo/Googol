import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface BarrelIndex extends Remote {

    // Métodos de persistência
    void saveInfo() throws IOException, RemoteException;
    void loadInfo() throws IOException, ClassNotFoundException, RemoteException;

    // Métodos de URLs (Queue)
    void addUrlToQueue(String url) throws RemoteException;
    String getUrlFromQueue() throws RemoteException;

    // Métodos de PageInfo
    void addPageInfo(PageInfo pageInfo) throws RemoteException;

    // Métodos de Adjacência
    void addAdjacency(String fromUrl, String toUrl) throws RemoteException;

    // Métodos de Bloom Filter
    void addToBloomFilter(String url) throws RemoteException;
    boolean mightContain(String url) throws RemoteException;

    // (Opcional para debugging remoto)
    void printAll() throws RemoteException;
}
