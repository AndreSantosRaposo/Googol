package webServer.controllers;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import webServer.GatewayInterface;
import webServer.PageInfo;
import webServer.SystemStats;
import webServer.FileManipulation;
import webServer.webSock.StatsNotifierService;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.lang.Math;

/**
 * Controlador principal da aplicação web que gere todas as interações entre
 * a interface web e o backend distribuído (Gateway/Barrels/Downloaders).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Comunicação RMI com o Gateway para pesquisas, indexação e estatísticas</li>
 *   <li>Integração REST com Google Gemini API para análise contextualizada</li>
 *   <li>Gestão de paginação de resultados de pesquisa</li>
 *   <li>Reconexão automática ao Gateway em caso de falha RMI</li>
 * </ul>
 *
 * @author Paulo Vilar, André Raposo
 * @version 2.0
 * @see GatewayInterface
 * @see StatsNotifierService
 */
@Controller
public class MenuController {

    /** Referência RMI para o Gateway (ponto de entrada do sistema distribuído) */
    private GatewayInterface gateway;

    /** Serviço de notificação WebSocket para updates em tempo real de estatísticas */
    private final StatsNotifierService statsNotifierService;

    /** Número de resultados exibidos por página */
    private static final int PAGE_SIZE = 10;

    /** Nome do Gateway no registry RMI (lido de config.txt) */
    private final String gatewayName;

    /** Endereço IP do Gateway (lido de config.txt) */
    private final String gatewayIp;

    /** Porta do registry RMI do Gateway (lido de config.txt) */
    private final int gatewayPort;

    /**
     * Construtor com injeção de dependência do serviço de notificações.
     * Lê configuração do Gateway de `config.txt` e tenta conexão inicial.
     *
     * @param statsNotifierService Serviço para enviar updates via WebSocket
     */
    @Autowired
    public MenuController(StatsNotifierService statsNotifierService) {
        this.statsNotifierService = statsNotifierService;
        String name = "", ip = "", portStr = "";
        int port = 0;
        try {
            List<String> cfg = FileManipulation.lineSplitter("config.txt", 1, ";");
            name = cfg.get(0).trim();
            ip = cfg.get(1).trim();
            portStr = cfg.get(2).trim();
            port = Integer.parseInt(portStr);
        } catch (Exception e) {
            System.err.println("[RMI] Erro ao ler config.txt: " + e.getMessage());
            e.printStackTrace();
        }

        this.gatewayName = name;
        this.gatewayIp = ip;
        this.gatewayPort = port;

        connectToGateway();
    }

    /**
     * Estabelece conexão RMI com o Gateway usando configuração carregada.
     * Chamado automaticamente no construtor e após falhas de comunicação.
     *
     * <p>Em caso de falha, define `gateway = null` e registra erro no console.
     */
    private void connectToGateway() {
        if (gatewayName.isEmpty() || gatewayIp.isEmpty() || gatewayPort == 0) return;

        try {
            System.out.printf("[RMI] Tentando conectar/reconectar ao Gateway '%s' em %s:%d...%n",
                    gatewayName, gatewayIp, gatewayPort);

            Registry registry = LocateRegistry.getRegistry(gatewayIp, gatewayPort);
            this.gateway = (GatewayInterface) registry.lookup(gatewayName);

            System.out.println("[RMI] Conexão/Reconexão bem-sucedida!");

        } catch (Exception e) {
            System.err.println("[RMI] Falha na conexão/reconexão: " + e.getMessage());
            this.gateway = null;
        }
    }

    /**
     * Endpoint raiz - redireciona para página principal do menu.
     *
     * @return String com redirect para /menu
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/menu";
    }

    /**
     * Exibe o menu principal da aplicação.
     *
     * @return Nome da view Thymeleaf `mainMenu`
     */
    @GetMapping("/menu")
    public String showMenu() {
        return "mainMenu";
    }

    /**
     * Adiciona URL para indexação no sistema distribuído.
     * Envia URL ao Gateway via RMI, que a distribui aos Barrels para processamento.
     *
     * @param url URL a ser indexada (validada no Gateway)
     * @param model Model do Spring para passar dados à view
     * @return Nome da view `mainMenu` com mensagem de sucesso/erro
     */
    @PostMapping("/addUrl")
    public String indexURL(@RequestParam("url") String url, Model model) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) throw new RemoteException("Gateway indisponível.");
            gateway.addUrl(url);
            statsNotifierService.sendImmediateStatsUpdate();
            model.addAttribute("mensagem", "URL enviada ao gateway: " + url);
            model.addAttribute("tipo", "sucesso");
        } catch (RemoteException re) {
            model.addAttribute("mensagem", "Erro de comunicação com o Gateway: " + re.getMessage());
            model.addAttribute("tipo", "erro");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao indexar URL: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }
        return "mainMenu";
    }

    /**
     * Processa pesquisa inicial de termos (primeira página de resultados).
     *
     * @param termos Termos de pesquisa separados por espaço
     * @param model Model do Spring
     * @return View com resultados paginados
     */
    @PostMapping("/searchTerms")
    public String searchTerms(
            @RequestParam("termos") String termos,
            Model model
    ) {
        return showResultsPage(termos, 0, model);
    }

    /**
     * Navega para página seguinte/anterior de resultados de pesquisa.
     * Re-executa a pesquisa no Gateway e retorna apenas a página solicitada.
     *
     * @param termos Termos de pesquisa originais
     * @param page Número da página (0-indexed)
     * @param model Model do Spring
     * @return View com resultados da página especificada
     */
    @GetMapping("/searchNextPage")
    public String searchNextPage(
            @RequestParam("termos") String termos,
            @RequestParam("page") int page,
            Model model
    ) {
        return showResultsPage(termos, page, model);
    }

    /**
     * Método auxiliar que executa pesquisa e renderiza página específica de resultados.
     * Integra análise contextualizada via Google Gemini API.
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Consulta Gateway via RMI (`gateway.search(termos)`)</li>
     *   <li>Gera análise contextualizada com Gemini (`callGeminiAnalysis(termos)`)</li>
     *   <li>Extrai subconjunto de 10 resultados para a página atual</li>
     *   <li>Calcula variáveis de navegação (hasNext/hasPrev)</li>
     *   <li>Renderiza view com resultados e análise</li>
     * </ol>
     *
     * @param termos Termos de pesquisa
     * @param currentPage Número da página atual (0-indexed)
     * @param model Model do Spring
     * @return View `resultTerms` ou `mainMenu` em caso de erro
     */
    private String showResultsPage(String termos, int currentPage, Model model) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) throw new RemoteException("Gateway indisponível.");

            List<PageInfo> allResults = gateway.search(termos);
            statsNotifierService.sendImmediateStatsUpdate();
            String analysis = callGeminiAnalysis(termos);

            int start = currentPage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allResults.size());

            List<PageInfo> pageResults;
            if (start < end) {
                pageResults = allResults.subList(start, end);
            } else {
                pageResults = Collections.emptyList();
            }

            boolean hasNext = end < allResults.size();
            boolean hasPrev = currentPage > 0;

            model.addAttribute("mensagem", "Pesquisa realizada: " + termos);
            model.addAttribute("tipo", "sucesso");
            model.addAttribute("resultados", pageResults);
            model.addAttribute("terms", List.of(termos.split(" ")));
            model.addAttribute("analise", analysis);
            model.addAttribute("termos", termos);
            model.addAttribute("currentPage", currentPage);
            model.addAttribute("hasNext", hasNext);
            model.addAttribute("hasPrev", hasPrev);

        } catch (RemoteException e) {
            System.err.println("⚠ Falha RMI em search. Tentando reconectar...");
            connectToGateway();
            model.addAttribute("mensagem", "Erro RMI: Gateway indisponível. Tente novamente.");
            model.addAttribute("tipo", "erro");
            return "mainMenu";
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao pesquisar: " + e.getMessage());
            model.addAttribute("tipo", "erro");
            return "mainMenu";
        }
        return "resultTerms";
    }

    /**
     * Pesquisa páginas que apontam para um URL específico (incoming links).
     * Consulta adjacency list nos Barrels via Gateway.
     *
     * @param link URL para pesquisar inlinks
     * @param model Model do Spring
     * @return View `resultInlinks` com lista de URLs que apontam para o link
     */
    @PostMapping("/searchInlinks")
    public String searchInlinks(@RequestParam("link") String link, Model model) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) throw new RemoteException("Gateway indisponível.");

            List<String> inlinks = gateway.searchInlinks(link);
            statsNotifierService.sendImmediateStatsUpdate();

            model.addAttribute("mensagem", "Inlinks de " + link);
            model.addAttribute("tipo", "sucesso");
            model.addAttribute("link", link);
            model.addAttribute("inlinks", inlinks);

        } catch (RemoteException e) {
            System.err.println("⚠ Falha RMI em inlinks. Tentando reconectar...");
            connectToGateway();
            model.addAttribute("mensagem", "Erro RMI: Gateway indisponível. Tente novamente.");
            model.addAttribute("tipo", "erro");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao pesquisar inlinks: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }

        return "resultInlinks";
    }

    /**
     * Exibe página de estatísticas do sistema (URLs indexadas, top pesquisas, etc.).
     * Usa WebSocket para updates em tempo real.
     *
     * @param model Model do Spring
     * @return View `statsPage` com objeto SystemStats
     */
    @GetMapping("/stats")
    public String stats(Model model) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) throw new RemoteException("Gateway indisponível.");

            SystemStats stats = gateway.getSystemStats();
            model.addAttribute("stats", stats);

        } catch (RemoteException e) {
            System.err.println("⚠ Falha RMI em stats. Tentando reconectar...");
            connectToGateway();
            model.addAttribute("mensagem", "Erro RMI: Gateway indisponível. Tente novamente.");
            model.addAttribute("tipo", "erro");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao obter estatísticas: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }

        return "statsPage";
    }

    /**
     * Gera análise contextualizada dos termos de pesquisa usando Google Gemini API.
     *
     * <p>Integração REST:
     * <ul>
     *   <li>API: Google Generative AI - Gemini 2.5 Flash</li>
     *   <li>Método: POST para endpoint de chat completions</li>
     *   <li>Formato: JSON (request/response)</li>
     *   <li>Autenticação: API Key em header</li>
     * </ul>
     *
     * <p>Prompt utilizado:
     * <pre>
     * "These are the search terms: {termos}.
     * Generate a clear 4–5 sentence contextual analysis in Portuguese."
     * </pre>
     *
     * @param search Termos de pesquisa para análise
     * @return Texto em linguagem natural com análise contextualizada
     *         ou mensagem de erro em caso de falha
     */
    public String callGeminiAnalysis(String search){
        try {
            String promtBuilder = String.format(
                    "These are the search terms: %s.\nGenerate a clear 4–5 sentence contextual analysis in Portuguese.",
                    search
            );

            String apiKey = readApiKey();
            Client client = Client.builder()
                    .apiKey(apiKey)
                    .build();

            GenerateContentResponse response =
                    client.models.generateContent("gemini-2.5-flash", promtBuilder, null);

            return response.text();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Lê API key do Google Gemini de ficheiro de configuração.
     *
     * <p>Localização: `src/main/resources/config.properties`
     * Propriedade: `geminiAPIKey`
     *
     * <p><b>Segurança</b>: Este ficheiro está em `.gitignore` para evitar
     * exposição de credenciais no repositório.
     *
     * @return API key em texto puro
     * @throws RuntimeException se ficheiro não existir ou propriedade não estiver definida
     */
    private String readApiKey(){
        try {
            Properties props = new Properties();
            props.load(getClass().getClassLoader().getResourceAsStream("config.properties"));
            return props.getProperty("geminiAPIKey");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
