import java.io.Serializable;
import java.util.List;

public class HistoryMessage implements Serializable {
    private final PageInfo page;
    private final List<String> urls;

    public HistoryMessage(PageInfo page, List<String> urls) {
        this.page = page;
        this.urls = urls;
    }

    public PageInfo getPage() {
        return page;
    }

    public List<String> getUrls() {
        return urls;
    }


}
