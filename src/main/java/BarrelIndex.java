import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface BarrelIndex extends Remote {

    // Method to get URL from the queue (called from Downloader)
    String getUrlFromQueue() throws RemoteException;
    //Method to add URL to the queue (called from Gateway and Downloader)
    void addUrlToQueue(String url) throws RemoteException;

    // Métodos de PageInfo
    void addPageInfo(PageInfo pageInfo) throws RemoteException;

    // Métodos de obtenção de dados
    Map<String, PageInfo> getPagesInfoMap() throws RemoteException;
    Map<String, Set<String>> getAdjacencyListMap() throws RemoteException;
    byte[] getBloomFilterBytes() throws RemoteException;

    // Pesquisa remota
    List<PageInfo> searchPages(List<String> terms) throws RemoteException;
}