package webServer.controllers;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Controller
public class MenuController {

    @GetMapping("/")
    public String home() {
        return "redirect:/menu";  // Redireciona para o menu

    }

    @GetMapping("/menu")
    public String showMenu() {
        return "mainMenu";
    }

    @PostMapping("/addUrl")
    public String indexURL(@RequestParam("url") String url, Model model) {
        System.out.println("[PlaceHolder] A adicionar URL: " + url);
        model.addAttribute("mensagem", "URL indexada com sucesso: " + url);
        model.addAttribute("tipo", "sucesso");
        return "mainMenu";
    }

    @PostMapping("/searchTerms")
    public String searchTerms(@RequestParam("termos") String termos, Model model) {

        List<String> searchTerms = List.of(termos.split(" "));

        // Fake results
        List<String> resultados = List.of("Resultado 1", "Resultado 2", "Resultado 3");

        // Call the AI analysis directly here
        String analysis = callGeminiAnalysis(termos);

        // Put everything into the model
        model.addAttribute("mensagem", "Pesquisa realizada com sucesso para: " + termos);
        model.addAttribute("tipo", "sucesso");
        model.addAttribute("resultados", resultados);
        model.addAttribute("terms", searchTerms);
        model.addAttribute("analise", analysis);

        return "resultTerms";
    }

    public String callGeminiAnalysis(String search){
        try{
            String promtBuilder = String.format("This are the search terms: %s \nGenerate a short, clear, 4–5 sentence contextual analysis for the user. Answer in portuguese",search);


            String apiKey = readApiKey();
            Client client = Client.builder()
                    .apiKey(apiKey)
                    .build();

            GenerateContentResponse response =
                    client.models.generateContent(
                            "gemini-2.5-flash",
                            promtBuilder,
                            null);

            System.out.println(response.text());

            return response.text();

        }catch (Exception e){
            System.out.println("Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }

    }

    @PostMapping("/searchInlinks")
    public String searchInlinks(@RequestParam("link") String link, Model model) {
        System.out.println("[PlaceHolder] A pesquisar por inlinks de " + link);
        model.addAttribute("mensagem", "Pesquisa realizada com sucesso para: " + link);
        model.addAttribute("tipo", "sucesso");
        //Fazer a call para o gateway aqui (mºetodo searchInlinks)
        // Aqui 'e para retornar a pagina dos resultados de pesquisa de inlinks
        return "mainMenu";
    }

    private String readApiKey(){
        try{
            Properties props = new Properties();
            props.load(getClass().getClassLoader().getResourceAsStream("config.properties"));
            return props.getProperty("geminiAPIKey");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
