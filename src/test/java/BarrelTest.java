import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
/*
public class BarrelTest {

    private Barrel barrel;
    private String testDbPath;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        // Path único para cada teste evitar conflitos de lock
        testDbPath = "testdb_barrel_" + System.nanoTime();
        barrel = new Barrel(testDbPath, "test-barrel");
        executor = Executors.newFixedThreadPool(10);
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
        int sizeBefore = map1.size();

        // Tenta modificar a cópia (não deve afetar o original)
        PageInfo fakePage = new PageInfo("Fake", "https://fake.com", List.of("fake"), "fake");
        map1.put("https://fake.com", fakePage);

        Map<String, PageInfo> map2 = barrel.getPagesInfoMap();
        assertEquals(sizeBefore, map2.size(), "Modificação da cópia não devia afetar o original");
        assertFalse(map2.containsKey("https://fake.com"), "URL fake não devia existir no original");
    }

    // ========== Testes para getAdjacencyListMap ==========

    @Test
    void getAdjacencyListMap_retorna_copia() throws Exception {
        barrel.addAdjacency("https://a.com", "https://b.com");

        Map<String, Set<String>> map1 = barrel.getAdjacencyListMap();
        int sizeBefore = map1.size();

        // Tenta modificar a cópia (não deve afetar o original)
        Set<String> fakeSet = new HashSet<>();
        fakeSet.add("https://fake-link.com");
        map1.put("https://fake.com", fakeSet);

        Map<String, Set<String>> map2 = barrel.getAdjacencyListMap();
        assertEquals(sizeBefore, map2.size(), "Modificação da cópia não devia afetar o original");
        assertFalse(map2.containsKey("https://fake.com"), "URL fake não devia existir no original");
    }

    // ========== Testes para getBloomFilterBytes ==========

    @Test
    void getBloomFilterBytes_retorna_bytes() throws Exception {
        barrel.addToBloomFilter("https://test.com");

        byte[] bytes = barrel.getBloomFilterBytes();

        assertNotNull(bytes, "Bytes do Bloom filter não deviam ser null");
        assertTrue(bytes.length > 0, "Bloom filter devia ter dados");
    }

    // ========== Teste 1: Múltiplas threads adicionando páginas ==========
    @Test
    void addPageInfo_multiplas_threads() throws Exception {
        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            int id = i;
            executor.submit(() -> {
                try {
                    PageInfo page = new PageInfo("Page" + id, "https://page" + id + ".com",
                            List.of("word" + id), "summary" + id);
                    barrel.addPageInfo(page);
                } catch (Exception e) {
                    fail("Exception in thread " + id + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        assertEquals(numThreads, pages.size(), "Todas as páginas deviam ter sido adicionadas");
    }

    // ========== Testes para getInvertedIndexMap ==========

    @Test
    void getInvertedIndexMap_retorna_indice_correto() throws Exception {
        PageInfo page1 = new PageInfo("P1", "https://p1.com", List.of("java", "programming"), "s1");
        PageInfo page2 = new PageInfo("P2", "https://p2.com", List.of("java", "spring"), "s2");

        barrel.addPageInfo(page1);
        barrel.addPageInfo(page2);

        Map<String, Set<String>> invertedIndex = barrel.getInvertedIndexMap();

        assertTrue(invertedIndex.containsKey("java"), "Palavra 'java' devia estar no índice");
        assertEquals(2, invertedIndex.get("java").size(), "'java' devia ter 2 URLs");
        assertTrue(invertedIndex.get("java").contains("https://p1.com"), "p1 devia estar em 'java'");
        assertTrue(invertedIndex.get("java").contains("https://p2.com"), "p2 devia estar em 'java'");
    }

    @Test
    void getInvertedIndexMap_case_insensitive() throws Exception {
        PageInfo page = new PageInfo("P", "https://p.com", List.of("Java", "PROGRAMMING"), "s");

        barrel.addPageInfo(page);

        Map<String, Set<String>> invertedIndex = barrel.getInvertedIndexMap();

        assertTrue(invertedIndex.containsKey("java"), "Palavra devia estar em lowercase");
        assertTrue(invertedIndex.containsKey("programming"), "Palavra devia estar em lowercase");
    }

    // ========== Testes para getExpectedSeqNumber ==========

    @Test
    void getExpectedSeqNumber_inicialmente_vazio() throws Exception {
        Map<String, Integer> expected = barrel.getExpectedSeqNumber();
        assertTrue(expected.isEmpty(), "Expected seq numbers devia estar vazio inicialmente");
    }

    @Test
    void getExpectedSeqNumber_atualiza_apos_receiveMessage() throws Exception {
        String nome = "down-test";
        PageInfo page = new PageInfo("T", "https://test.com", List.of("test"), "s");

        barrel.receiveMessage(0, page, List.of(), nome);

        Map<String, Integer> expected = barrel.getExpectedSeqNumber();
        assertTrue(expected.containsKey(nome), "Downloader devia estar no mapa");
        assertEquals(1, expected.get(nome), "Expected devia ser 1 após receber seq 0");
    }

    // ========== Testes para getReceivedSeqNumbers ==========

    @Test
    void getReceivedSeqNumbers_inicialmente_vazio() throws Exception {
        Map<String, Set<Integer>> received = barrel.getReceivedSeqNumbers();
        assertTrue(received.isEmpty(), "Received seq numbers devia estar vazio inicialmente");
    }

    @Test
    void getReceivedSeqNumbers_regista_mensagens_recebidas() throws Exception {
        String nome = "down-test";
        PageInfo page1 = new PageInfo("P1", "https://p1.com", List.of("w1"), "s1");
        PageInfo page2 = new PageInfo("P2", "https://p2.com", List.of("w2"), "s2");

        barrel.receiveMessage(0, page1, List.of(), nome);
        barrel.receiveMessage(1, page2, List.of(), nome);

        Map<String, Set<Integer>> received = barrel.getReceivedSeqNumbers();
        assertTrue(received.containsKey(nome), "Downloader devia estar no mapa");
        assertTrue(received.get(nome).contains(0), "Devia ter recebido seq 0");
        assertTrue(received.get(nome).contains(1), "Devia ter recebido seq 1");
    }

    // ========== Testes para receiveMessage com múltiplos downloaders ==========

    @Test
    void receiveMessage_multiplos_downloaders_independentes() throws Exception {
        String down1 = "down-A";
        String down2 = "down-B";

        PageInfo page1 = new PageInfo("P1", "https://p1.com", List.of("w1"), "s1");
        PageInfo page2 = new PageInfo("P2", "https://p2.com", List.of("w2"), "s2");

        barrel.receiveMessage(0, page1, List.of(), down1);
        barrel.receiveMessage(0, page2, List.of(), down2);

        Map<String, Integer> expected = barrel.getExpectedSeqNumber();
        assertEquals(1, expected.get(down1), "down-A expected devia ser 1");
        assertEquals(1, expected.get(down2), "down-B expected devia ser 1");

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        assertEquals(2, pages.size(), "Deviam existir 2 páginas de downloaders diferentes");
    }

    // ========== Testes para ordenação por relevância (adjacências) ==========

    @Test
    void searchPages_ordena_por_numero_de_adjacencias() throws Exception {
        // Página p1 tem 3 ligações recebidas
        PageInfo page1 = new PageInfo("P1", "https://p1.com", List.of("java"), "s1");
        PageInfo page2 = new PageInfo("P2", "https://p2.com", List.of("java"), "s2");
        PageInfo page3 = new PageInfo("P3", "https://p3.com", List.of("java"), "s3");

        barrel.addPageInfo(page1);
        barrel.addPageInfo(page2);
        barrel.addPageInfo(page3);

        // p1 recebe 3 ligações
        barrel.addAdjacency("https://other1.com", "https://p1.com");
        barrel.addAdjacency("https://other2.com", "https://p1.com");
        barrel.addAdjacency("https://other3.com", "https://p1.com");

        // p2 recebe 1 ligação
        barrel.addAdjacency("https://other4.com", "https://p2.com");

        List<PageInfo> results = barrel.searchPages(List.of("java"));

        assertEquals(3, results.size(), "Devia encontrar 3 páginas");

        // Verificar que p1 tem mais adjacências que p2
        Map<String, Set<String>> adj = barrel.getAdjacencyListMap();
        int p1Links = adj.getOrDefault("https://p1.com", Collections.emptySet()).size();
        int p2Links = adj.getOrDefault("https://p2.com", Collections.emptySet()).size();

        assertTrue(p1Links > p2Links, "p1 devia ter mais ligações que p2");
    }

    // ========== Testes para estatísticas e informações do sistema ==========

    @Test
    void barrel_mantem_estatisticas_corretas() throws Exception {
        PageInfo page1 = new PageInfo("P1", "https://p1.com", List.of("java", "spring"), "s1");
        PageInfo page2 = new PageInfo("P2", "https://p2.com", List.of("python", "django"), "s2");

        barrel.addPageInfo(page1);
        barrel.addPageInfo(page2);

        Map<String, PageInfo> pages = barrel.getPagesInfoMap();
        Map<String, Set<String>> invertedIndex = barrel.getInvertedIndexMap();

        assertEquals(2, pages.size(), "Devia ter 2 páginas indexadas");
        assertTrue(invertedIndex.size() >= 4, "Devia ter pelo menos 4 palavras no índice");
    }

    // ========== Teste para Bloom Filter falso-positivo ==========

    @Test
    void bloomFilter_permite_falsos_positivos_mas_nao_falsos_negativos() throws Exception {
        String url = "https://definitivamente-nao-existe.com";

        barrel.addToBloomFilter(url);

        // Após adicionar ao bloom filter, deve indicar que pode existir
        PageInfo page = new PageInfo("T", url, List.of("test"), "s");
        barrel.addPageInfo(page);

        // Tentar adicionar à fila (não deve adicionar pois está no bloom filter E em pagesInfo)
        barrel.addUrlToQueue(url);

        String retrieved = barrel.getUrlFromQueue();
        assertNull(retrieved, "URL no Bloom filter e pagesInfo não devia ser adicionado");
    }

    // ========== Teste para indexação recursiva de URLs ==========

    @Test
    void receiveMessage_adiciona_urls_recursivamente_a_fila() throws Exception {
        String nome = "down-recursive";
        PageInfo page = new PageInfo("P1", "https://p1.com", List.of("test"), "s1");
        List<String> links = List.of("https://link1.com", "https://link2.com", "https://link3.com");

        barrel.receiveMessage(0, page, links, nome);

        // Verificar que os links foram adicionados à fila
        Set<String> queueUrls = new HashSet<>();
        String url;
        while ((url = barrel.getUrlFromQueue()) != null) {
            queueUrls.add(url);
        }

        assertTrue(queueUrls.contains("https://link1.com"), "link1 devia estar na fila");
        assertTrue(queueUrls.contains("https://link2.com"), "link2 devia estar na fila");
        assertTrue(queueUrls.contains("https://link3.com"), "link3 devia estar na fila");
    }

}
*/
