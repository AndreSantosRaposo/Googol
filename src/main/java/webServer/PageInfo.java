package webServer;
import java.io.Serializable;
import java.util.*;

/**
 * Immutable container for indexed web page information.
 *
 * Stores essential metadata about a web page:
 * <ul>
 *     <li>Page title</li>
 *     <li>Full URL</li>
 *     <li>List of words extracted from the page text</li>
 *     <li>Short text snippet (preview)</li>
 * </ul>
 *
 */
public class PageInfo implements Serializable {
    private final String title;
    private final String url;
    private final List<String> words;
    private final String smallText;

    /**
     * Constructs a new page information object.
     *
     * @param title     the page title
     * @param url       the full URL of the page
     * @param words     list of words extracted from the page text
     * @param smallText short preview text (typically first few sentences)
     */
    public PageInfo(String title, String url, List<String> words, String smallText) {
        this.title = title;
        this.url = url;
        this.words = words;
        this.smallText = smallText;
    }

    /**
     * Returns the page title.
     * @return the title of the web page
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the full URL of the page.
     * @return the page URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the list of words extracted from the page.
     * @return immutable list of words used for indexing
     */
    public List<String> getWords() {
        return words;
    }

    /**
     * Returns a short text snippet from the page.
     * @return preview text (typically first few sentences)
     */
    public String getSmallText() {
        return smallText;
    }
}
