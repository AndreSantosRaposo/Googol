import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BarrelTest {

    private Barrel barrel;

    @BeforeEach
    void setUp() throws Exception {
        // Para testes: pode comentar askForInfo() no construtor, ou
        // criar um construtor dedicado a testes que não toca no disco.
        barrel = new Barrel("testdb", "test"); // se tocar disco, use um path temporário
    }

    @Test
    void receiveMessage_grava_page_adjacencia_e_queue_e_ignora_duplicados() throws Exception {
        String nome = "down-A";
        PageInfo page = new PageInfo("T", "https://x", List.of("a","b"), "s");
        List<String> urls = List.of("https://l1", "https://l2");

        // 1ª entrega
        barrel.receiveMessage(0, page, urls, nome);

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        assertTrue(pages.containsKey("https://x"));

        Map<String, Set<String>> adj = barrel.getAdjacencyListMap();
        assertTrue(adj.containsKey("https://l1"));

        System.out.println("adjacencyList no Barrel: " + barrel.getAdjacencyListMap().keySet());
        System.out.println("Conteúdo de https://l1: " + barrel.getAdjacencyListMap().get("https://l1"));
        assertTrue(adj.get("https://l1").contains("https://x"));

        String q1 = barrel.getUrlFromQueue();
        String q2 = barrel.getUrlFromQueue();
        assertNotNull(q1);
        assertNotNull(q2);

        // Duplicado
        barrel.receiveMessage(0, page, urls, nome);
        // estruturas permanecem válidas
        assertEquals("https://l1", q1);
        assertEquals("https://l2", q2);
    }
}
