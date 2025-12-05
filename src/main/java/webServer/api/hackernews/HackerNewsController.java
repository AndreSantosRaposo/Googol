package webServer.api.hackernews;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import webServer.BarrelIndex;
import webServer.FileManipulation;

import java.net.URI;
import java.net.URL;





@RestController
public class HackerNewsController {
    private static final Logger logger = LoggerFactory.getLogger(HackerNewsController.class);

    @GetMapping("/topstories")
    public List<String> topStories(@RequestParam(name = "search") List<String> search, Model model) {
        try{
            URL urlTopStories = URI.create("https://hacker-news.firebaseio.com/v0/topstories.json").toURL();
            String baseStoryUrl = "https://hacker-news.firebaseio.com/v0/item/%s.json";

            HttpURLConnection connection = (HttpURLConnection) urlTopStories.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SD-Googol");

            if (connection.getResponseCode() >= 300) {
                debug(connection);
            }

            InputStream is = connection.getInputStream();
            String json = new String(is.readAllBytes());
            ObjectMapper mapper = new ObjectMapper();
            int[] ids = mapper.readValue(json, int[].class);

            connection.disconnect();

            logger.info("Successfully retrieved top stories with " + ids.length + " IDs.");
            System.out.println("Successfully retrieved top stories with " + ids.length + " IDs.");

            List<String> linksToIndex = new ArrayList<>();

//            for(int id : ids) {
//                String storyURL = String.format(baseStoryUrl,id);
//                URL urlStory =  URI.create(storyURL).toURL();
//                connection = (HttpURLConnection) urlStory.openConnection();
//                if (connection.getResponseCode() >= 300) {
//                    debug(connection);
//                }
//                is = connection.getInputStream();
//                json = new String(is.readAllBytes());
//                HackerNewsItemRecord story = mapper.readValue(json, HackerNewsItemRecord.class);
//
//                boolean hasTerms = true;
//                for(String term:search){
//                    if(!hasTerms){
//                        break;
//                    }
//                    hasTerms = (story.text() != null && story.text().toLowerCase().contains(term.toLowerCase()));
//                }
//                if(hasTerms){
//                    linksToIndex.add(story.url());
//                }
//            }

            // Test only========================================================
            linksToIndex.add("https://kraa.io/about");
            linksToIndex.add("https://pbnj.sh/");
            linksToIndex.add("https://www.wired.com/story/age-verification-is-sweeping-the-us-activists-are-fighting-back/");



            // Indexar os links no Barrel
            indexLinksInBarrel(linksToIndex);

            return linksToIndex;

        }catch (MalformedURLException e){
            logger.error("Malformed URL Exception: " + e.getMessage());
        }catch (Exception e){
            logger.error("General Exception: " + e.getMessage());
        }

        return new ArrayList<>();
    }

    private void indexLinksInBarrel(List<String> links) {
        try {
            System.out.println("A adicionar n de links: " + links.size());
            List<String> barrel1Config = FileManipulation.lineSplitter("config.txt", 2, ";");
            List<String> barrel2Config = FileManipulation.lineSplitter("config.txt", 3, ";");

            if (barrel1Config.size() < 3 || barrel2Config.size() < 3) {
                logger.error("Barrel configuration is incomplete");
                return;
            }

            BarrelIndex barrel1 = null;
            BarrelIndex barrel2 = null;

            // Tentar conectar ao Barrel 1
            try {
                String barrel1Name = barrel1Config.get(0).trim();
                String barrel1Ip = barrel1Config.get(1).trim();
                int barrel1Port = Integer.parseInt(barrel1Config.get(2).trim());

                Registry registry1 = LocateRegistry.getRegistry(barrel1Ip, barrel1Port);
                barrel1 = (BarrelIndex) registry1.lookup(barrel1Name);
                logger.info("Connected to Barrel1");
            } catch (Exception e) {
                logger.warn("Failed to connect to Barrel1: " + e.getMessage());
            }

            // Tentar conectar ao Barrel 2
            try {
                String barrel2Name = barrel2Config.get(0).trim();
                String barrel2Ip = barrel2Config.get(1).trim();
                int barrel2Port = Integer.parseInt(barrel2Config.get(2).trim());

                Registry registry2 = LocateRegistry.getRegistry(barrel2Ip, barrel2Port);
                barrel2 = (BarrelIndex) registry2.lookup(barrel2Name);
                logger.info("Connected to Barrel2");
            } catch (Exception e) {
                logger.warn("Failed to connect to Barrel2: " + e.getMessage());
            }

            if (barrel1 == null && barrel2 == null) {
                logger.error("Could not connect to any Barrel");
                return;
            }

            // Adicionar links aos Barrels disponíveis
            for (String url : links) {
                if (url != null && !url.isEmpty()) {
                    if (barrel1 != null) {
                        try {
                            barrel1.addUrlToQueue(url);
                            logger.info("URL added to Barrel1: " + url);
                        } catch (Exception e) {
                            logger.error("Error adding to Barrel1: " + e.getMessage());
                        }
                    }

                    if (barrel2 != null) {
                        try {
                            barrel2.addUrlToQueue(url);
                            logger.info("URL added to Barrel2: " + url);
                        } catch (Exception e) {
                            logger.error("Error adding to Barrel2: " + e.getMessage());
                            // Continua com próxima URL
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error indexing links in Barrels: " + e.getMessage());
        }
    }




    private void debug(HttpURLConnection connection) throws IOException {
        // This function is used to debug the resulting code from HTTP connections.

        // Response code such as 404 or 500 will give you an idea of what is wrong.
        System.out.println("Response Code:" + connection.getResponseCode());

        // The HTTP headers returned from the server
        System.out.println("_____ HEADERS _____");
        for (String header : connection.getHeaderFields().keySet()) {
            System.out.println(header + ": " + connection.getHeaderField(header));
        }

        // If there is an error, the response body is available through the method
        // getErrorStream, instead of regular getInputStream.
        BufferedReader in = new BufferedReader(new InputStreamReader(
                connection.getErrorStream()));
        StringBuilder builder = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            builder.append(inputLine);
        in.close();
        System.out.println("Body: " + builder);
    }
}
