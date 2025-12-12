package webServer.controllers;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired; // Adicionado para construtor se necessário

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

@Controller
public class MenuController {

    private GatewayInterface gateway;
    private final StatsNotifierService statsNotifierService;
    private static final int PAGE_SIZE = 10;
    private final String gatewayName;
    private final String gatewayIp;
    private final int gatewayPort;

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

        // Guarda as variáveis para reconexão
        this.gatewayName = name;
        this.gatewayIp = ip;
        this.gatewayPort = port;

        connectToGateway();
    }
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



    @GetMapping("/")
    public String home() {
        return "redirect:/menu";
    }

    @GetMapping("/menu")
    public String showMenu() {
        return "mainMenu";
    }

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

    @PostMapping("/searchTerms")
    public String searchTerms(
            @RequestParam("termos") String termos,
            Model model
    ) {
        return showResultsPage(termos, 0, model);
    }

    // NOVO MÉTODO: Lida com a navegação de página (GET, re-executa pesquisa)
    @GetMapping("/searchNextPage")
    public String searchNextPage(
            @RequestParam("termos") String termos,
            @RequestParam("page") int page,
            Model model
    ) {
        return showResultsPage(termos, page, model);
    }

    private String showResultsPage(String termos, int currentPage, Model model) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) throw new RemoteException("Gateway indisponível.");

            // Obtém os resultados ordenados do RMI
            List<PageInfo> allResults = gateway.search(termos);
            statsNotifierService.sendImmediateStatsUpdate();
            String analysis = callGeminiAnalysis(termos);

            // Paginação: Extrai a lista de 10 resultados para a página atual
            int start = currentPage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allResults.size());

            List<PageInfo> pageResults;
            if (start < end) {
                // A ordem de ranking é preservada pela subList
                pageResults = allResults.subList(start, end);
            } else {
                pageResults = Collections.emptyList();
            }

            // Variáveis de navegação
            boolean hasNext = end < allResults.size();
            boolean hasPrev = currentPage > 0;

            // Adiciona atributos para a View
            model.addAttribute("mensagem", "Pesquisa realizada: " + termos);
            model.addAttribute("tipo", "sucesso");
            model.addAttribute("resultados", pageResults);
            model.addAttribute("terms", List.of(termos.split(" ")));
            model.addAttribute("analise", analysis);

            // Atributos de Paginação
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

    @GetMapping("/stats")
    public String stats(Model model) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) throw new RemoteException("Gateway indisponível.");

            SystemStats stats = gateway.getSystemStats();
            model.addAttribute("stats", stats);

        } catch (RemoteException e) {
            System.err.println(" Falha RMI em stats. Tentando reconectar...");
            connectToGateway();
            model.addAttribute("mensagem", "Erro RMI: Gateway indisponível. Tente novamente.");
            model.addAttribute("tipo", "erro");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao obter estatísticas: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }

        return "statsPage";
    }


    public String callGeminiAnalysis(String search){
        // ... (Código Gemini) ...
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

    private String readApiKey(){
        // ... (Código API Key) ...
        try {
            Properties props = new Properties();
            props.load(getClass().getClassLoader().getResourceAsStream("config.properties"));
            return props.getProperty("geminiAPIKey");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}