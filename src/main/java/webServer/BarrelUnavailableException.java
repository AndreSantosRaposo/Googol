package webServer;
import java.rmi.RemoteException;

/**
 * Exceção lançada quando um Barrel não está disponível
 */
public class BarrelUnavailableException extends RemoteException {
    public BarrelUnavailableException(String message) {
        super(message);
    }
}