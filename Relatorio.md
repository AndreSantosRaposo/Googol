# Googol - Projeto de Sistemas Distribuídos

## 1. Arquitetura de Software

### 1.1 Visão Geral
- **Gateway**: Ponto de entrada para consultas e indexação, expõe interface RMI
- **Barrels**: Replicação e armazenamento distribuído do índice
- **Downloaders**: Web scraping e extração de conteúdo
- **Cliente**: Interface de linha de comandos para interação

### 1.2 Estrutura de Objetos

#### Barrel (BarrelServer.java)
- **Responsabilidade**: Armazenamento e indexação distribuída
- **Comunicação**: RMI + Multicast entre Barrels para replicação
- **Persistência**: MapDB + Bloom filters
- Ver Javadoc detalhado em `Barrel.java`

Barrel é o componente que possui as estruturas de armazenamento:
- **MapDB (Persistent Storage)**: Base de dados embebida que armazena o índice invertido (palavra → lista de URLs) e metadados das páginas. Persiste dados em disco para recuperação após crashes.
- **Bloom Filter**: Estrutura probabilística para verificação rápida de URLs já indexados, reduzindo acessos desnecessários ao MapDB e melhorando performance.
- **URL Queue**: Fila de URLs pendentes para processamento pelos Downloaders. Gerida com sincronização thread-safe.
- **Sequence Number Maps**: Mapas que rastreiam os números de sequência esperados de cada Gateway e Downloader, garantindo confiabilidade no multicast.

#### Downloader (DownloaderServer.java)
- **Responsabilidade**: Web scraping e extração de conteúdo (título, texto, links)
- **Comunicação**:
  - RMI com Barrels via multicast para enviar dados indexados
  - Round-robin para obter URLs da fila
- **Confiabilidade**: Reliable Multicast com buffer de histórico
- Ver Javadoc detalhado em `Downloader.java`

Downloader possui as seguintes estruturas:
- **History Buffer**: Buffer circular que mantém as últimas N mensagens enviadas, permitindo re-envio em caso de perda
- **Barrel Connection Pool**: Conjunto de conexões RMI para os Barrels ativos, com gestão de failover
- **Current Barrel Index**: Índice para round-robin na obtenção de URLs

<<<<<<< HEAD
#### Gateway (GatewayServer.java)
- **Responsabilidade**: Ponto de entrada único para clientes, coordenação de consultas
- **Comunicação**:
  - RMI Server para receber pedidos de clientes
  - RMI Client para comunicar com Barrels (consultas e indexação)
  - Multicast para enviar URLs de administração aos Barrels
- **Funcionalidades**:
  - `search(String query)`: Pesquisa distribuída por todos os Barrels ativos
  - `indexUrl(String url)`: Adiciona URL à fila de trabalho
  - `getSystemStats()`: Estatísticas agregadas do sistema
- Ver Javadoc detalhado em `Gateway.java`

Gateway possui as seguintes estruturas:
- **Barrel Connection Registry**: Lista de Barrels ativos para distribuição de carga
- **Sequence Number Counter**: Contador para reliable multicast com Barrels
- **History Buffer**: Buffer de mensagens para recuperação

#### Cliente (Cliente.java)
- **Responsabilidade**: Interface de linha de comandos para interação com o sistema
- **Comunicação**: RMI Client conectado ao Gateway
- **Funcionalidades**:
  - Pesquisa de termos
  - Indexação de URLs
  - Visualização de estatísticas do sistema
  - Listagem de páginas com determinada palavra

## 2. Replicação e Multicast Fiável

### 2.1 Algoritmo de Multicast
**Tipo**: NACK-based Reliable Multicast  
**Implementação**: Ver `Barrel.receiveMessage()` e `Downloader.reSendMessages()`

#### Fluxo:
1. Downloader/Gateway envia mensagem com `seqNumber` para todos os Barrels via multicast
2. Barrel verifica sequência esperada (`expectedSeqNumber`)
3. Se falta mensagem (expectedSeqNumber < seqNumber recebido):
  - Barrel solicita re-envio via `reSendMessages(from, to, senderName)`
  - Downloader/Gateway recupera mensagens do `historyBuffer`
  - Re-envia apenas as mensagens em falta
4. Barrel processa mensagem e incrementa `expectedSeqNumber`

**Características importantes:**
- Sequence numbers dos Gateways e Downloaders são **independentes** entre si
- Cada Barrel mantém mapa separado: `gatewaySeqNumbers` e `downloaderSeqNumbers`
- Ordem de receção **não é garantida** entre mensagens

#### Recuperação de Crashes
Caso um Downloader crashe e reinicie:
1. Começa a enviar mensagens com `seqNumber = 0`
2. Pede aos Barrels para reiniciarem o seu sequence number via `resetSeqNumbers()`
3. Barrels reiniciam contador específico daquele remetente

### 2.2 Sincronização entre Barrels
Um Barrel ao iniciar:
1. Contacta outros Barrels ativos via RMI para sincronizar estado
2. Copia dados através do método:
3. Caso não existam Barrels ativos, lê os seus próprios ficheiros persistidos (MapDB)


## 3. Componente RPC/RMI

### 3.2 Métodos Remotos Detalhados

#### GatewayInterface
- **`List<PageInfo> search(String query)`**
  - Distribuição: Consulta todos os Barrels em paralelo
  - Agregação: Combina resultados de múltiplos Barrels
  - Failover: Ignora Barrels inacessíveis (continua com os disponíveis)

- **`void addUrl(String url)`**
  - Valida URL (formato e protocolo)
  - Envia via multicast para todos os Barrels com sequence number
  - Adiciona URL à fila de processamento

- **`SystemStats getSystemStats()`**
  - Recolhe métricas de cada Barrel (total URLs, palavras indexadas)
  - Agrega estatísticas globais do sistema

- **`List<String> searchInlinks(String url)`**
  - Retorna lista de URLs que apontam para o URL especificado
  - Consulta adjacency list nos Barrels

- **`void reSendURL(int missingSeqNumber, BarrelIndex receiver)`**
  - Callback: Barrel solicita URL perdido
  - Gateway reenvia URL específico do history buffer

#### BarrelIndex
- **`void receiveMessage(int seqNumber, PageInfo page, List<String> urls, String nome, String ip, Integer port)`**
  - Verifica sequence number esperado
  - Se falta mensagem → solicita re-envio via callback
  - Atualiza índice invertido, adjacency list e Bloom filter
  - Thread-safe com sincronização em estruturas partilhadas

- **`String getUrlFromQueue()`**
  - Retorna URL da fila (round-robin entre Downloaders)
  - Remove URL da fila após entrega
  - Retorna `null` se fila vazia

- **`void resetSeqNumbers(String senderName)`**
  - Reinicia contador de sequence number para o remetente especificado
  - Usado após crash/reinício de Downloader ou Gateway

- **`List<PageInfo> searchPages(List<String> terms)`**
  - Pesquisa usando índice invertido
  - Retorna interseção de URLs que contêm todos os termos
  - Ordena resultados por número de incoming links (PageRank-like)

- **`boolean addUrlToQueue(String url, int seqNumber, String nome, String ip, Integer port)`**
  - Adiciona URL à fila com validação de sequence number
  - Verifica duplicados via Bloom filter
  - Solicita mensagens perdidas se houver gap nos sequence numbers

- **`List<String> getInLinks(String url)`**
  - Retorna lista de URLs que apontam para o URL especificado
  - Consulta adjacency list (grafo de links)

- **Métodos de Sincronização** (para novos Barrels):
  - `ConcurrentMap<String, Integer> getExpectedSeqNumber()`
  - `ConcurrentMap<String, Set<Integer>> getReceivedSeqNumbers()`
  - `ConcurrentMap<String, PageInfo> getPagesInfoMap()`
  - `ConcurrentMap<String, Set<String>> getAdjacencyListMap()`
  - `ConcurrentMap<String, Set<String>> getInvertedIndexMap()`
  - `byte[] getBloomFilterBytes()`

#### DownloaderIndex
- **`void notifyBarrelUp(String barrelName)`**
  - Callback: Barrel notifica Downloader quando fica disponível
  - Downloader tenta reconectar ao Barrel
  - Sincroniza sequence numbers

- **`void reSendMessages(int seqNumber, BarrelIndex requestingBarrel)`**
  - Callback: Barrel solicita mensagem perdida
  - Downloader recupera do `historyBuffer` (estrutura circular)
  - Re-envia apenas a mensagem com o sequence number solicitado

### 3.3 Failover

#### Mecanismo de Detecção e Recuperação:
1. **Detecção de Falha**:
  - `RemoteException` em chamadas RMI indica componente inacessível
  - Timeout configurável (default: 5 segundos)

2. **Reconexão (Downloader → Barrel)**:
  - Método `connectToBarrel()` tenta reconectar periodicamente
  - Retry com backoff exponencial (1s, 2s, 4s, ...)
  - Máximo de 10 tentativas antes de desistir

3. **Notificação Proativa (Barrel → Downloader)**:
  - Barrel chama callback `notifyBarrelUp()` ao iniciar
  - Downloader adiciona Barrel de volta ao pool ativo

4. **Sincronização Pós-Reconexão**:
  - `resetSeqNumbers()` alinha estado após crash
  - Downloader re-envia mensagens desde última confirmada

**Código-chave**:
- `Downloader.connectToBarrel()` - Gestão de conexões com retry
- `Downloader.disconnectBarrel()` - Detecção de falhas e remoção do pool
- `Barrel.notifyDownloadersUp()` - Broadcast de disponibilidade após inicialização
- `Gateway.rebindBarrel()` - Reconexão do Gateway aos Barrels

## 5. Integração com Serviços REST

### 5.1 Visão Geral
O sistema integra-se com APIs REST externas:
- **Hacker News API**: Indexação automática de top stories relacionadas com os termos de pesquisa
- **Google Gemini API**: Análise contextualizada dos resultados usando IA generativa

### 5.2 Hacker News Integration

#### Endpoints Utilizados
- `https://hacker-news.firebaseio.com/v0/topstories.json` - Lista de IDs das top stories
- `https://hacker-news.firebaseio.com/v0/item/{id}.json` - Detalhes de cada story

#### Fluxo de Indexação
1. Cliente solicita indexação via interface web
2. WebServer obtém lista de top stories (HTTP GET)
3. Para cada story, verifica se o texto contém termos da pesquisa
4. URLs relevantes são enviados ao Gateway via RMI para indexação
5. Downloaders processam URLs e enviam conteúdo aos Barrels

### 5.3 Google Gemini API (Análise Contextualizada)

#### Implementação`

**API Utilizada**: Google Generative AI - Gemini 2.5 Flash  
**Endpoint**: `https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent`

#### Fluxo de Análise
1. Após pesquisa bem-sucedida, WebServer invoca `callGeminiAnalysis(termos)`
2. Prompt construído com termos de pesquisa: `These are the search terms: {termos}. Generate a clear 4–5 sentence contextual analysis in Portuguese`


## 6. WebSocket
Para a página de estatísticas (/stats), foi implementado um mecanismo de Server Push para garantir que os dados (Top 10 pesquisas e métricas dos Barrels) são atualizados em todos os clientes conectados sem necessidade de refresh.

### **StatsNotifierService:**

-Serviço injetado no Spring que detém o SimpMessagingTemplate.

-Sempre que ocorre uma ação que altera o estado do sistema (ex: nova pesquisa ou indexação de URL), este serviço invoca o gateway.getSystemStats() via RMI.

-O objeto SystemStats resultante é serializado para JSON e enviado para o tópico /topic/stats.

## 7. Distribuição de Tarefas
### 7.1 Meta 1
Para a distribuição de tarefas foi seguida a sugestão 1 do enunciado, com algumas excepcoes para colaboração mútua
### 7.2 Meta 2
**Paulo Vilar:**
  
1-Implemtnação de WebSocket 

2- Implementação de Comunicação RMI

**André Raposo:**

1-Integração de REST WebServices 

2-Arquitetura geral do projeto e endpoints 
  
## 8. Testes Realizados

| Teste                                         | Resultado esperado                                                   | Resultado | Observações                                                                                                            |
|-----------------------------------------------|----------------------------------------------------------------------|-----------|------------------------------------------------------------------------------------------------------------------------|
| Crash de Barrel                               | Reconexão automática funciona                                        | PASS      |                                                                                                                        |
| Reliable multicast (downaloder)               | Mesnagens perdidas são recuperadas                                   | PASS      | Ordem de receção não garantida, pois não foi defenido como necessário                                                  |
| Reliable multicast (gateway)                  | Mesnagens perdidas são recuperadas                                   | PASS      | Ordem de receção não garantida, pois não foi defenido como necessário                                                  |
| Persistência com MapDB                        | Dados mantidos após reinício                                         | PASS      | Dados apenas são armazenados durante shutdown (redundancia de multiplos barrels permite reduzir este reduzir overhead) |
| Barrel recupera estado consistente após crash | Após barrel iniciar possuir a mesma informação que barrels já ativos | PASS      |                                                                                                                        |
| Crash do Downloader                           | Reconexão automática funciona                                        | PASS      |                                                                                                                        |
| Crash da webapp                               | Reconexão automática                                                 | PASS      |                                                                                                                        |



## 9. Como Executar
Para a execução do programa é necessário ter:
- O Java instalado (versão 11 ou superior)
- Maven para construção do projeto
  - Projeto usa maven para a gestão de dependências e build, logo não ;e necessário ter as dependencias pré instaladas, mas apenas o maeven.
- Make para facilitar o build e execução
- O makefil possui todos os comandos necessários para a compilação e execução do projeto. A ordem de inicialização dos componentes, não é relevante pois todos possuem mecanismos de reconexão automática.

