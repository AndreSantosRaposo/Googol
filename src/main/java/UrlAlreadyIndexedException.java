import java.rmi.RemoteException;

/**
 * Exceção lançada quando uma URL já foi indexada
 */
public class UrlAlreadyIndexedException extends RemoteException {
    public UrlAlreadyIndexedException(String message) {
        super(message);
    }
}
