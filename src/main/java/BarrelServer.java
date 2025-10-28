import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class BarrelServer {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println(" <barrelName> <port>");
            System.exit(1);
        }

        String barrelName = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            Barrel barrel = new Barrel(
                    barrelName + "_pageInfo.ser",
                    barrelName + "_adjacency.ser"
            );

            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind(barrelName, barrel);

            System.out.printf(" %s esta pronto %d.%n", barrelName, port);
        } catch (Exception e) {
            System.err.println("ERRO " + e.getMessage());
            e.printStackTrace();
        }
    }
}