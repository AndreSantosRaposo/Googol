import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DownloaderIndex extends Remote {
    void reSendMessages(int seqNumber, BarrelIndex requestingBarrel) throws RemoteException;
    void notifyBarrelUp(String barrelName) throws RemoteException;
}
