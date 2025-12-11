package webServer.controllers;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired; // Adicionado para construtor se necessário
import java.util.Collections; // Import para lista vazia
import webServer.GatewayInterface;
import webServer.PageInfo;
import webServer.SystemStats;
import webServer.FileManipulation;
import webServer.webSock.StatsNotifierService;

import java.io.IOException;
import java.rmi.Naming;
import java.util.List;
import java.util.Properties;
import java.lang.Math; // Necessário para Math.min

@Controller
public class MenuController {

    private GatewayInterface gateway;
    private final StatsNotifierService statsNotifierService;
    private static final int PAGE_SIZE = 10;

    // Construtor corrigido e simplificado
    @Autowired // Mantenha @Autowired se a injeção do StatsNotifierService funcionar
    public MenuController(StatsNotifierService statsNotifierService) {
        this.statsNotifierService = statsNotifierService;
        try {
            List<String> cfg = FileManipulation.lineSplitter("config.txt", 1, ";");
            String name = cfg.get(0).trim();
            String ip = cfg.get(1).trim();
            int port = Integer.parseInt(cfg.get(2).trim());

            // Nota: Naming.lookup está a ser usado. Manter.
            String url = "rmi://" + ip + ":" + port + "/" + name;
            System.out.println("[RMI] Connecting to: " + url);

            gateway = (GatewayInterface) Naming.lookup(url);

            System.out.println("[RMI] Connected successfully!");

        } catch (Exception e) {
            System.err.println("[RMI] Failed to connect:");
            e.printStackTrace();
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
            gateway.addUrl(url);
            statsNotifierService.sendImmediateStatsUpdate();
            model.addAttribute("mensagem", "URL enviada ao gateway: " + url);
            model.addAttribute("tipo", "sucesso");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao indexar URL: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }
        return "mainMenu";
    }

    // MÉTODO ORIGINAL: Inicia a pesquisa (POST), redirecionando para a página 0.
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

    // MÉTODO UNIFICADO: Processa a pesquisa e carrega a página N
    private String showResultsPage(String termos, int currentPage, Model model) {
        try {
            // 1. OBTÉM TODOS OS RESULTADOS NOVAMENTE DO RMI (Compromisso sem Sessão)
            List<PageInfo> allResults = gateway.search(termos);
            statsNotifierService.sendImmediateStatsUpdate();
            String analysis = callGeminiAnalysis(termos);

            // 2. Cálculo dos índices da página atual
            int start = currentPage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allResults.size());

            // 3. Extrai apenas os resultados da página
            List<PageInfo> pageResults;
            if (start < end) {
                pageResults = allResults.subList(start, end);
            } else {
                pageResults = Collections.emptyList();
            }


            // 4. Variáveis de navegação
            boolean hasNext = end < allResults.size();
            boolean hasPrev = currentPage > 0;

            // Adiciona todos os atributos necessários para a View
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

        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao pesquisar: " + e.getMessage());
            model.addAttribute("tipo", "erro");
            return "mainMenu"; // Retorna ao menu em caso de falha RMI/Exceção
        }
        return "resultTerms";
    }

    @PostMapping("/searchInlinks")
    public String searchInlinks(@RequestParam("link") String link, Model model) {

        try {
            List<String> inlinks = gateway.searchInlinks(link);
            statsNotifierService.sendImmediateStatsUpdate();

            model.addAttribute("mensagem", "Inlinks de " + link);
            model.addAttribute("tipo", "sucesso");
            model.addAttribute("link", link);
            model.addAttribute("inlinks", inlinks);

        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao pesquisar inlinks: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }

        return "resultInlinks";
    }

    @GetMapping("/stats")
    public String stats(Model model) {
        // ... (Seu código original de stats) ...
        try {
            SystemStats stats = gateway.getSystemStats();
            model.addAttribute("stats", stats);
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