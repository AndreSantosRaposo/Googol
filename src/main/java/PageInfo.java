import java.io.Serializable;
import java.util.*;

public class PageInfo implements Serializable {
    private String title;
    private String url;
    private List<String> words;
    private String smallText;
    public PageInfo(String title, String url, List<String> words, String smallText) {
        this.title = title;
        this.url = url;
        this.words = words;
        this.smallText = smallText;
    }
    public String getTitle() {
        return title;
    }
    public String getUrl() {
        return url;
    }
    public List<String> getWords() {
        return words;
    }
    public String getSmallText() {
        return smallText;
    }
}
