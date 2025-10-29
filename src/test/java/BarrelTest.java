import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BarrelTest {

    private Barrel barrel;
    private String testDbPath;

    @BeforeEach
    void setUp() throws Exception {
        // Path único para cada teste evitar conflitos de lock
        testDbPath = "testdb_barrel_" + System.nanoTime();
        barrel = new Barrel(testDbPath, "test-barrel");
    }

    @AfterEach
    void tearDown() {
        // Fecha o DB e limpa ficheiros de teste
        if (barrel != null && barrel.db != null && !barrel.db.isClosed()) {
            barrel.db.close();
        }
        deleteTestFiles();
    }

    private void deleteTestFiles() {
        // Aguarda um pouco para garantir que o MapDB libertou os ficheiros
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        new File(testDbPath).delete();
        new File(testDbPath + ".p").delete();
        new File(testDbPath + ".t").delete();
        new File(testDbPath + "_bloom.bin").delete();
        new File(testDbPath + ".wal.0").delete();
    }

    @Test
    void receiveMessage_grava_page_adjacencia_e_queue_e_ignora_duplicados() throws Exception {
        String nome = "down-A";
        PageInfo page = new PageInfo("T", "https://x", List.of("a","b"), "s");
        List<String> urls = List.of("https://l1", "https://l2");

        // 1ª entrega
        barrel.receiveMessage(0, page, urls, nome);

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        assertTrue(pages.containsKey("https://x"), "Página não foi guardada");

        Map<String, Set<String>> adj = barrel.getAdjacencyListMap();
        assertTrue(adj.containsKey("https://l1"), "Adjacência https://l1 não existe");
        assertTrue(adj.get("https://l1").contains("https://x"), "https://x não está em adjacências de https://l1");

        String q1 = barrel.getUrlFromQueue();
        String q2 = barrel.getUrlFromQueue();
        assertNotNull(q1, "Primeira URL da fila é null");
        assertNotNull(q2, "Segunda URL da fila é null");
        assertTrue(List.of("https://l1", "https://l2").contains(q1), "URL inesperado na fila");
        assertTrue(List.of("https://l1", "https://l2").contains(q2), "URL inesperado na fila");

        // Duplicado (não deve adicionar novamente à fila)
        barrel.receiveMessage(0, page, urls, nome);
        String q3 = barrel.getUrlFromQueue();
        assertNull(q3, "Fila não deveria ter mais URLs após duplicado");
    }


    @Test
    void receiveMessage_deteta_lacunas_e_pede_reenvio() throws Exception {
        String nome = "down-B";
        PageInfo page1 = new PageInfo("P1", "https://p1", List.of("w1"), "s1");
        PageInfo page3 = new PageInfo("P3", "https://p3", List.of("w3"), "s3");

        // Recebe msg 0 (ok)
        barrel.receiveMessage(0, page1, List.of(), nome);

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        assertTrue(pages.containsKey("https://p1"), "Msg 0 devia ter sido aplicada");
        assertEquals(1, pages.size(), "Só devia existir 1 página após msg 0");

        // Recebe msg 2 (falta msg 1) - deve detetar lacuna mas aplicar na mesma
        barrel.receiveMessage(2, page3, List.of(), nome);

        pages = barrel.getPagesInfoMap();
        assertTrue(pages.containsKey("https://p1"), "Msg 0 continua aplicada");
        assertTrue(pages.containsKey("https://p3"), "Msg 2 foi aplicada (mesmo com lacuna)");
        assertEquals(2, pages.size(), "Deviam existir 2 páginas");
    }

    // ========== Testes para addUrlToQueue ==========

    @Test
    void addUrlToQueue_adiciona_url_nao_indexado() throws Exception {
        String url = "https://novo.com";

        barrel.addUrlToQueue(url);

        String retrieved = barrel.getUrlFromQueue();
        assertEquals(url, retrieved, "URL não foi adicionado à fila");
    }

    @Test
    void addUrlToQueue_nao_adiciona_url_ja_indexado() throws Exception {
        String url = "https://existente.com";
        PageInfo page = new PageInfo("E", url, List.of("teste"), "sumário");

        barrel.receiveMessage(0, page, List.of(), "down-test");

        barrel.addUrlToQueue(url);

        String retrieved = barrel.getUrlFromQueue();
        assertNull(retrieved, "URL indexado não devia ser adicionado à fila");
    }

    // ========== Testes para addPageInfo ==========

    @Test
    void addPageInfo_guarda_pagina() throws Exception {
        PageInfo page = new PageInfo("Titulo", "https://page.com", List.of("word1", "word2"), "resumo");

        barrel.addPageInfo(page);

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        assertTrue(pages.containsKey("https://page.com"), "Página não foi guardada");
        assertEquals("Titulo", pages.get("https://page.com").getTitle(), "Título incorreto");
    }

    // ========== Testes para addAdjacency ==========

    @Test
    void addAdjacency_cria_ligacao() throws Exception {
        String from = "https://from.com";
        String to = "https://to.com";

        barrel.addAdjacency(from, to);

        Map<String, Set<String>> adj = barrel.getAdjacencyListMap();
        assertTrue(adj.containsKey(to), "Adjacência não foi criada");
        assertTrue(adj.get(to).contains(from), "Ligação from->to não existe");
    }

    // ========== Testes para addToBloomFilter ==========

    @Test
    void addToBloomFilter_adiciona_url() throws Exception {
        String url = "https://bloom.com";

        barrel.addToBloomFilter(url);

        // Adiciona a página para garantir que não é falso-positivo
        PageInfo page = new PageInfo("B", url, List.of("b"), "s");
        barrel.addPageInfo(page);

        // Tenta adicionar novamente à fila (não deve adicionar)
        barrel.addUrlToQueue(url);
        String retrieved = barrel.getUrlFromQueue();
        assertNull(retrieved, "URL no Bloom filter não devia ser adicionado à fila");
    }

    // ========== Testes para getUrlFromQueue ==========

    @Test
    void getUrlFromQueue_retorna_null_se_vazia() throws Exception {
        String url = barrel.getUrlFromQueue();
        assertNull(url, "Fila vazia devia retornar null");
    }

    @Test
    void getUrlFromQueue_remove_urls_em_ordem_fifo() throws Exception {
        barrel.addUrlToQueue("https://url1.com");
        barrel.addUrlToQueue("https://url2.com");

        String url1 = barrel.getUrlFromQueue();
        String url2 = barrel.getUrlFromQueue();

        assertEquals("https://url1.com", url1, "Primeira URL incorreta");
        assertEquals("https://url2.com", url2, "Segunda URL incorreta");
    }

    // ========== Testes para searchPages ==========

    @Test
    void searchPages_encontra_paginas_com_todos_termos() throws Exception {
        PageInfo page1 = new PageInfo("P1", "https://p1.com", List.of("java", "programming", "tutorial"), "s1");
        PageInfo page2 = new PageInfo("P2", "https://p2.com", List.of("java", "spring", "framework"), "s2");
        PageInfo page3 = new PageInfo("P3", "https://p3.com", List.of("python", "django"), "s3");

        barrel.addPageInfo(page1);
        barrel.addPageInfo(page2);
        barrel.addPageInfo(page3);

        List<PageInfo> results = barrel.searchPages(List.of("java"));

        assertEquals(2, results.size(), "Devia encontrar 2 páginas com 'java'");
        assertTrue(results.stream().anyMatch(p -> p.getUrl().equals("https://p1.com")), "P1 não encontrado");
        assertTrue(results.stream().anyMatch(p -> p.getUrl().equals("https://p2.com")), "P2 não encontrado");
    }

    @Test
    void searchPages_nao_encontra_se_falta_termo() throws Exception {
        PageInfo page = new PageInfo("P", "https://p.com", List.of("java", "programming"), "s");
        barrel.addPageInfo(page);

        List<PageInfo> results = barrel.searchPages(List.of("java", "python"));

        assertTrue(results.isEmpty(), "Não devia encontrar páginas (falta 'python')");
    }

    @Test
    void searchPages_case_insensitive() throws Exception {
        PageInfo page = new PageInfo("P", "https://p.com", List.of("java", "programming"), "s");
        barrel.addPageInfo(page);

        List<PageInfo> results = barrel.searchPages(List.of("JAVA", "Programming"));

        assertEquals(1, results.size(), "Devia encontrar 1 página (case-insensitive)");
    }

    // ========== Testes para getPagesInfoMap ==========

    @Test
    void getPagesInfoMap_retorna_copia() throws Exception {
        PageInfo page = new PageInfo("P", "https://p.com", List.of("w"), "s");
        barrel.addPageInfo(page);

        Map<String, PageInfo> map1 = barrel.getPagesInfoMap();
        map1.put("https://fake.com", null); // Modifica a cópia

        Map<String, PageInfo> map2 = barrel.getPagesInfoMap();
        assertFalse(map2.containsKey("https://fake.com"), "Modificação da cópia não devia afetar o original");
    }

    // ========== Testes para getAdjacencyListMap ==========

    @Test
    void getAdjacencyListMap_retorna_copia() throws Exception {
        barrel.addAdjacency("https://a.com", "https://b.com");

        Map<String, Set<String>> map1 = barrel.getAdjacencyListMap();
        map1.put("https://fake.com", null); // Modifica a cópia

        Map<String, Set<String>> map2 = barrel.getAdjacencyListMap();
        assertFalse(map2.containsKey("https://fake.com"), "Modificação da cópia não devia afetar o original");
    }

    // ========== Testes para getBloomFilterBytes ==========

    @Test
    void getBloomFilterBytes_retorna_bytes() throws Exception {
        barrel.addToBloomFilter("https://test.com");

        byte[] bytes = barrel.getBloomFilterBytes();

        assertNotNull(bytes, "Bytes do Bloom filter não deviam ser null");
        assertTrue(bytes.length > 0, "Bloom filter devia ter dados");
    }
}

