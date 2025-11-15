package webServer.api.hackernews;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URL;



@RestController
public class HackerNewsController {
    private static final Logger logger = LoggerFactory.getLogger(HackerNewsController.class);

    @GetMapping("/topstories")
    private void hackerRankTopStories(@RequestParam List<String> search) {
        try{
            URL urlTopStories = URI.create("https://hacker-news.firebaseio.com/v0/topstories.json").toURL();
            HttpURLConnection connection = (HttpURLConnection) urlTopStories.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SD-Googol");

            if (connection.getResponseCode() >= 300) {
                debug(connection);
            }

            InputStream is = connection.getInputStream();
            // Convert InputStream to String
            String json = new String(is.readAllBytes());
            // Convert JSON string to int[]
            ObjectMapper mapper = new ObjectMapper();
            int[] ids = mapper.readValue(json, int[].class);

            connection.disconnect();

            logger.info("Successfully retrieved top stories with " + ids.length + " IDs.");
            System.out.println("Successfully retrieved top stories with " + ids.length + " IDs.");

            for(int id : ids) {
                System.out.println("Story ID: " + id);
            }

            //================================ FAER PARTE DE VERIFICAR SE TEM PALAVRAS ================================//



        }catch (MalformedURLException e){
            logger.error("Malformed URL Exception: " + e.getMessage());
        }catch (Exception e){
            logger.error("General Exception: " + e.getMessage());
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
