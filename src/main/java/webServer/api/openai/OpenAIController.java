package webServer.api.openai;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

@RestController
public class OpenAIController {

    @GetMapping("/analysis")
    public void getOpenAIAnalysis(@RequestParam(name = "search") List<String> search, Model model){
        try{
            System.out.println(search);


            StringBuilder promtBuilder = new StringBuilder("This are the search terms: ");
            for(String term:search){
                promtBuilder.append(term).append(", ");
            }
            promtBuilder.append("\nGenerate a short, clear, 4–5 sentence contextual analysis.");

            String apiKey = readApiKey();
            Client client = Client.builder()
                    .apiKey(apiKey)
                    .build();

            GenerateContentResponse response =
                    client.models.generateContent(
                            "gemini-2.5-flash",
                            promtBuilder.toString(),
                            null);

            System.out.println(response.text());

            model.addAttribute("analise",response.text());



        }catch (Exception e){
            System.out.println("Error: " + e.getMessage());
        }
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
