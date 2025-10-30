import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public interface BarrelIndex extends Remote {
    //Method to add URL to the queue (called from Gateway and Downloader)
    void addUrlToQueue(String url) throws RemoteException;

    // Métodos de adi!ao
    void addPageInfo(PageInfo pageInfo) throws RemoteException;
    void addAdjacency(String fromUrl, String toUrl) throws RemoteException;
    void addToBloomFilter(String url) throws RemoteException;

    // Métodos de obtenção de dados
    ConcurrentMap<String, PageInfo> getPagesInfoMap() throws RemoteException;
    ConcurrentMap<String, Set<String>> getAdjacencyListMap() throws RemoteException;
    byte[] getBloomFilterBytes() throws RemoteException;
    String getUrlFromQueue() throws RemoteException;
    ConcurrentMap<String, Set<String>> getInvertedIndexMap() throws RemoteException;
    ConcurrentMap<String, Integer> getExpectedSeqNumber() throws RemoteException;
    ConcurrentMap<String, Set<Integer>> getReceivedSeqNumbers() throws RemoteException;

    void receiveMessage(int seqNumber, PageInfo page, List<String> urls, String nome) throws RemoteException;

    // Pesquisa remota
    List<PageInfo> searchPages(List<String> terms) throws RemoteException;
}