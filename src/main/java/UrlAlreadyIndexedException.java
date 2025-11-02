import java.rmi.RemoteException;

public class UrlAlreadyIndexedException extends RemoteException {
    public UrlAlreadyIndexedException(String message) {
        super(message);
    }
}
