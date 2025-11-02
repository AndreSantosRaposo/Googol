import java.io.Serializable;
import java.util.List;

/**
 * Immutable message container stored in the Downloader's history buffer.
 *
 * Each HistoryMessage represents a parsed web page and its extracted links,
 * associated with a sequence number. This allows the Downloader to re-send lost messages
 * to Barrels upon request.
 *
 * Thread-safety: This class is immutable and thread-safe.
 */
public class HistoryMessage implements Serializable {
    /**
     * Information about the indexed web page (title, URL, words, snippet).
     */
    private final PageInfo page;

    /**
     * List of absolute URLs extracted from the page.
     */
    private final List<String> urls;

    /**
     * createsa new history message with page information and extracted links.
     *
     * @param page the page information (title, URL, words, snippet)
     * @param urls the list of URLs found on this page
     */
    public HistoryMessage(PageInfo page, List<String> urls) {
        this.page = page;
        this.urls = urls;
    }

    /**
     * Returns the page information.
     * @return the {@link PageInfo} object containing page metadata
     */
    public PageInfo getPage() {
        return page;
    }

    /**
     * Retns the list of URLs extracted from the page.
     * @return immutable list of absolute URLs
     */
    public List<String> getUrls() {
        return urls;
    }
}
