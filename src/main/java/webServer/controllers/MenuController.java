package webServer.controllers;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import webServer.GatewayInterface;
import webServer.PageInfo;
import webServer.SystemStats;
import webServer.FileManipulation;

import java.io.IOException;
import java.rmi.Naming;
import java.util.List;
import java.util.Properties;

@Controller
public class MenuController {

    private GatewayInterface gateway;

    public MenuController() {
        try {
            List<String> cfg = FileManipulation.lineSplitter("config.txt", 1, ";");
            String name = cfg.get(0).trim();
            String ip = cfg.get(1).trim();
            int port = Integer.parseInt(cfg.get(2).trim());

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
            model.addAttribute("mensagem", "URL enviada ao gateway: " + url);
            model.addAttribute("tipo", "sucesso");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao indexar URL: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }
        return "mainMenu";
    }

    @PostMapping("/searchTerms")
    public String searchTerms(@RequestParam("termos") String termos, Model model) {

        try {
            List<PageInfo> resultados = gateway.search(termos);
            String analysis = callGeminiAnalysis(termos);

            model.addAttribute("mensagem", "Pesquisa realizada: " + termos);
            model.addAttribute("tipo", "sucesso");
            model.addAttribute("resultados", resultados);
            model.addAttribute("terms", List.of(termos.split(" ")));
            model.addAttribute("analise", analysis);

        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao pesquisar: " + e.getMessage());
            model.addAttribute("tipo", "erro");
        }

        return "resultTerms";
    }

    @PostMapping("/searchInlinks")
    public String searchInlinks(@RequestParam("link") String link, Model model) {

        try {
            List<String> inlinks = gateway.searchInlinks(link);

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
        try {
            Properties props = new Properties();
            props.load(getClass().getClassLoader().getResourceAsStream("config.properties"));
            return props.getProperty("geminiAPIKey");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
