package webServer.webSock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import webServer.GatewayInterface;
import webServer.SystemStats;
import webServer.FileManipulation;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;


/**
 * Serviço responsável por notificar os clientes Web sobre atualizações nas estatísticas do sistema.
 * <p>
 * Esta classe atua como uma ponte entre o sistema distribuído (via RMI) e a interface Web (via WebSocket).
 * Ela mantém uma referência ao Gateway RMI e usa o {@link SimpMessagingTemplate} do Spring para
 * fazer "Server Push" dos dados para os clientes conectados.
 * </p>
 *
 * Funcionalidades principais:
 * <ul>
 * <li>Conexão e reconexão automática ao Gateway RMI.</li>
 * <li>Obtenção do objeto {@link SystemStats} atualizado.</li>
 * <li>Envio assíncrono de dados para o tópico "/topic/stats".</li>
 * </ul>
 */
@Service
public class StatsNotifierService {

    private final SimpMessagingTemplate template;
    private GatewayInterface gateway;

    private static final String CONFIG_FILE = "config.txt";
    private static final int GATEWAY_LINE = 1;

    // O Spring injeta o SimpMessagingTemplate (ferramenta de push)
    @Autowired
    public StatsNotifierService(SimpMessagingTemplate template) {
        this.template = template;
        connectToGateway();
    }

    /**
     * Tenta estabelecer (ou restabelecer) a referência RMI ao Gateway.
     */
    private void connectToGateway() {
        if (gateway != null) return;

        try {
            List<String> cfg = FileManipulation.lineSplitter(CONFIG_FILE, GATEWAY_LINE, ";");
            String name = cfg.get(0).trim();
            String ip = cfg.get(1).trim();
            int port = Integer.parseInt(cfg.get(2).trim());

            System.out.printf("[StatsNotifier] Attempting RMI connection to Gateway '%s' at %s:%d%n", name, ip, port);

            Registry registry = LocateRegistry.getRegistry(ip, port);
            gateway = (GatewayInterface) registry.lookup(name);

            System.out.println("[StatsNotifier] Connected to Gateway successfully!");

            // Se a conexão for bem-sucedida, envia o estado inicial UP
            sendPush();

        } catch (Exception e) {
            System.err.println("[StatsNotifier] Initial connection failed. Will attempt reconnect on next action: " + e.getMessage());
            gateway = null;
        }
    }

    /**
     * Obtém as estatísticas do sistema via RMI e envia para os clientes Web via WebSocket.
     * <p>
     * Este método é chamado sempre que uma ação relevante ocorre no sistema (ex: nova pesquisa).
     * </p>
     */
    private void sendPush() {
        if (gateway == null) {
            connectToGateway();
            if (gateway == null) return;
        }

        try {
            SystemStats currentStats = gateway.getSystemStats();
            System.out.println("--- DEBUG STATS NOTIFIER ---");

            // Imprimir Top 10 Searches:
            List<?> top10 = currentStats.getTop10Searches();
            System.out.println("TIPO DE DADOS TOP 10: " + top10.getClass().getName());
            System.out.println("VALORES TOP 10 BRUTOS: " + top10);

            // Imprimir um valor de Barrel para comparação:
            System.out.println("TESTE BARREL (OK): " + currentStats.getBarrelMetrics().keySet());
            System.out.println("--- DEBUG STATS NOTIFIER ---");
            // =========================================================
            // END DEBUG
            // =========
            // 3. SERVER PUSH: Envia o objeto JSON para o tópico do WebSocket.
            template.convertAndSend("/topic/stats", currentStats);
            System.out.println("[StatsNotifier] Event-driven stats update sent (PUSH).");

        } catch (RemoteException e) {
            // 4. Tratamento de Falha: Se o Gateway cair durante a chamada.
            System.err.println("[StatsNotifier] RMI call failed (RemoteException). Reconnecting...");
            gateway = null; // Anula a referência inválida
            connectToGateway(); // Tenta restabelecer a referência para a próxima ação.

        } catch (Exception e) {
            System.err.println("[StatsNotifier] General error during push: " + e.getMessage());
        }
    }

    /**
     * Gatilho público para forçar uma atualização imediata das estatísticas.
     * Deve ser chamado pelos Controllers quando ocorre uma ação relevante (ex: nova pesquisa).
     */

    public void sendImmediateStatsUpdate() {
        sendPush();
    }
}