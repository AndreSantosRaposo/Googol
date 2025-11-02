import java.rmi.RemoteException;

public class BarrelUnavailableException extends RemoteException {
    public BarrelUnavailableException(String message) {
        super(message);
    }
}