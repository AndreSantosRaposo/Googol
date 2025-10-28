import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class DownloaderTest {

    @Test
    void reSendMessages_reenvia_para_barrel_correto() throws Exception {
        BarrelIndex barrelMock = mock(BarrelIndex.class);

        String nome = "down-A";
        Downloader d = new Downloader(nome, List.of(barrelMock));

        PageInfo page = new PageInfo("T", "https://x", List.of("a","b"), "s");
        List<String> urls = List.of("https://l1", "https://l2");
        HistoryMessage hm = new HistoryMessage(page, urls);

        int seq = 7;
        d.addToHistory(seq, hm);

        d.reSendMessages(seq, barrelMock);

        verify(barrelMock, times(1))
                .receiveMessage(eq(seq), eq(page), eq(urls), eq(nome));
        verifyNoMoreInteractions(barrelMock);
    }
}