import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public interface BarrelIndex extends Remote {

    // Métodos de adicao
    boolean addUrlToQueue(String url, int seqNumber, String nome, String ip, Integer port) throws RemoteException;
    void addPageInfo(PageInfo pageInfo) throws RemoteException;
    void addAdjacency(String fromUrl, String toUrl) throws RemoteException;

    // Métodos de obtenção de dados
    ConcurrentMap<String, PageInfo> getPagesInfoMap() throws RemoteException;
    ConcurrentMap<String, Set<String>> getAdjacencyListMap() throws RemoteException;
    byte[] getBloomFilterBytes() throws RemoteException;
    String getUrlFromQueue() throws RemoteException;
    ConcurrentMap<String, Set<String>> getInvertedIndexMap() throws RemoteException;
    ConcurrentMap<String, Integer> getExpectedSeqNumber() throws RemoteException;
    ConcurrentMap<String, Set<Integer>> getReceivedSeqNumbers() throws RemoteException;

    void resetSeqNumbers(String nome) throws RemoteException;
    void receiveMessage(int seqNumber, PageInfo page, List<String> urls, String nome, String io, Integer port) throws RemoteException;

    // Pesquisa remota
    List<PageInfo> searchPages(List<String> terms) throws RemoteException;

    List<String> getInLinks(String url) throws RemoteException;

}