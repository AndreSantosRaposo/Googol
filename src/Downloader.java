import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import java.util.*;


public class Downloader {
    public Downloader() {}

    public void scrapURL(String url) {
        List<String> words = new ArrayList<>();
        try{
            Document doc = Jsoup.connect(url).get();
            String pageTitle = doc.title();
            String doctext = doc.text();
            words = List.of(doctext.split(" "));
            String[] sentences = doctext.split("\\.");
            //Text snippet will idealy have 3 sentences.
            String textSnippet = String.join(".", Arrays.copyOfRange(sentences, 0, Math.min(3, sentences.length))) + ".";

            PageInfo PageInformation = new PageInfo(pageTitle,url,words,textSnippet);

            List<String> hrefs = doc.select("a[href]")
                    .stream()
                    .map(link -> link.attr("abs:href"))
                    .toList();

        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        Downloader downloader = new Downloader();
        downloader.scrapURL("https://www.google.com");
    }
}
