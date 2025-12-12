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
        // Tenta conectar APENAS UMA VEZ na inicialização do serviço
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

            // Conexão via Registry (padrão de consistência do seu projeto)
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
     * TAREFA PRINCIPAL: Puxa o SystemStats do Gateway e envia via WebSocket (PUSH).
     * Chamado apenas por eventos externos.
     */
    private void sendPush() {
        // 1. Verificação de Tolerância a Falhas: Se a referência falhou, tenta reconectar.
        if (gateway == null) {
            connectToGateway();
            if (gateway == null) return;
        }

        try {
            // 2. Ação RMI: Puxa o estado atualizado do sistema.
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
            // END DEBUG TEMPORÁRIO
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



    public void sendImmediateStatsUpdate() {
        sendPush();
    }
}