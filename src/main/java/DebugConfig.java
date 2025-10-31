/**
 * Configuração centralizada de flags de debug para todo o sistema.
 */
public class DebugConfig {

    // Flags de debug por categoria
    public static boolean DEBUG_URL = true;
    public static boolean DEBUG_RMI = false;
    public static boolean DEBUG_SEARCH = false;
    public static boolean DEBUG_INDEXING = false;
    public static boolean DEBUG_MESSAGES = false;
    public static boolean DEBUG_CLAIMS = false;
    public static boolean DEBUG_DOWNLOADER = true;



    // Ativar/desativar todos os debugs de uma vez
    public static boolean DEBUG_ALL = false;

    // Método auxiliar para verificar se o debug está ativo
}