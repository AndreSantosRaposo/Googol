/**
 * Centralized configuration for all the aplication debug flags.
 * This class provides boolean flags to enable/disable debug prints
 */
package webServer;
public class DebugConfig {

    // Flags de debug por categoria

    public static boolean DEBUG_URL_INDEXAR = true;
    public static boolean DEBUG_FICHEIROS = true;
    public static boolean DEBUG_MULTICAST_GATEWAY = false;
    public static boolean DEBUG_MULTICAST_DOWNLOADER = false;
    public static boolean DEBUG_DOWNLOADER_SLEEP = false;
    public static boolean DEBUG_DOWNLOADER = true;
    public static boolean DEBUG_HACKER_NEWS = true;


    // Ativar/desativar todos os debugs de uma vez
    public static boolean DEBUG_ALL = false;
}
