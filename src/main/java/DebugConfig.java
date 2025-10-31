/**
 * Configuração centralizada de flags de debug para todo o sistema.
 */
public class DebugConfig {

    // Flags de debug por categoria

    //Debug para verificar a adição de URLs para indexar (verificar duplicados, etc)
    public static boolean DEBUG_URL_INDEXAR = false;
    //Verificar que caminho barrel ecolhe (pedir infomracão a outro ou indexar, verificar se leitura e escrita nos ficheiros funciona)
    public static boolean DEBUG_FICHEIROS = false;
    public static boolean DEBUG_SEARCH = false;
    public static boolean DEBUG_INDEXING = false;
    public static boolean DEBUG_MESSAGES = false;
    public static boolean DEBUG_CLAIMS = false;

    // Ativar/desativar todos os debugs de uma vez
    public static boolean DEBUG_ALL = false;

}
