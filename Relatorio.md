# Googol - Projeto de Sistemas Distribuídos

## 1. Arquitetura de Software

### 1.1 Visão Geral
- Gateway (Gateway)
- Barrels (Replicação + Armazenamento)
- Downloaders (Web Scraping)
- Cliente (Interface)

### 1.2 Estrutura de Objetos
#### Barrel (BarrelServer.java)
- **Responsabilidade**: Armazenamento e indexação
- **Comunicação**: RMI + Multicast entre Barrels
- **Persistência**: MapDB + Bloom filters
- Ver Javadoc detalhado em `Barrel.java`


Barrel é o componente que possui as estruturas de armazenamenteo:
- **MapDB (Persistent Storage)**: Base de dados embebida que armazena o índice invertido (palavra → lista de URLs) e metadados das páginas. Persiste dados em disco para recuperação após crashes.
- **Bloom Filter**: Estrutura probabilística para verificação rápida de URLs já indexados, reduzindo acessos desnecessários ao MapDB e melhorando performance.
- **URL Queue**: Fila de URLs pendentes para processamento pelos Downloaders. Gerida com sincronização thread-safe.
- **Sequence Number Maps**: Mapas que rastreiam os números de sequência esperados de cada Gateway e Downloader, garantindo confiabilidade no multicast.
      

#### Downloader (DownloaderServer.java)
- **Responsabilidade**: Web scraping
- **Comunicação**: RMI com Barrels (multicast para enviar dados e round robin para receber dados)
- **Confiabilidade**: Reliable Multicast
- Ver Javadoc detalhado em `Downloader.java`


#### Gateway (gatewayServer.java)
- **Responsabilidade**: Atuar como mediador entre os pedidos do cliente e as funçoes dos barrels
- **Comunicação**: RMI com barrels (Round- Robin)
- Ver Javadoc detalhado em `cliente.java`


#### Cliente (cliente.java)
- **Responsabilidade**: gerir interação com o user
- **Comunicação**: RMI com Gateway
- **Confiabilidade**: Sistema de retry para conexao com gateway
- Ver Javadoc detalhado em `cliente.java`

## 2. Replicação e Multicast Fiável

### 2.1 Algoritmo de Multicast
**Tipo**: Nack-based Reliable Multicast
**Implementação**: Ver `Barrel.receiveMessage()` e `Downloader.reSendMessages()`

#### Fluxo:
1. Downloader envia mensagem com `seqNumber`
2. Barrel verifica sequência esperada
3. Se falta mensagem (seqNumber esperado < seqNumber ecebido ) solicita re-envio via `reSendMessages()`
4. Downloader mantém `historyBuffer` para recuperação

Sequence numbers dos gateways e downaloders são independentes entre si. 

Caso um downaloder crashe, ao reiniciar este irá começar a enviar mensagens com sequence number 0, logo pede aos barrels para reiniciarem o seu sequence number associado a esse downaloder, através do método `resetSeqNumbers()`.


### 2.2 Sincronização entre Barrels
Um barrel assim que inicia contacta os outros barrels ativos para sincronizar o seu estado, através do método, que copia os dados dos outros barrels para o novo barrel. Caso não existam barrels ativos, o barrel lê os seus próprios ficheiros.

### 3. Componente 
É dado acesso a um ficheiro config.txt a todos os componentes com o nome do objeto remoto, o ip e o porto do servidor rmi de todos os integrantes do sistema, a partir dele sempre que uma componente precisa de se comunicar com outra é o ficheiro é lido e as informações tratadas de forma a ser possivel os comandos rmi como lookup e rebind etc..

### 3.3 Failover
#### Mecanismo:
1. **Detecção**: `RemoteException` em chamadas RMI
2. **Reconexão**: `connectToBarrel()` com retry
3. **Notificação**: Callback `notifyBarrelUp()` quando Barrel volta
4. **Sincronização**: `resetSeqNumbers()` para alinhar estado

**Código-chave**:
- `Downloader.connectToBarrel()` - Gestão de conexões
- `Downloader.disconnectBarrel()` - Detecção de falhas
- `Barrel.notifyDownloadersUp()` - Broadcast de disponibilidade

## 4. Distribuição de Tarefas
Para a distribuição de tarefas foi seguida a sugestão 1 do enunciado, com algumas excepcoes para colaboração mútua

## 5. Testes Realizados

| Teste                                         | Resultado esperado                                                   | Resultado | Observações                                                                                                            |
|-----------------------------------------------|----------------------------------------------------------------------|-----------|------------------------------------------------------------------------------------------------------------------------|
| Crash de Barrel                               | Reconexão automática funciona                                        | PASS      |                                                                                                                        |
| Reliable multicast (downaloder)               | Mesnagens perdidas são recuperadas                                   | PASS      | Ordem de receção não garantida, pois não foi defenido como necessário                                                  |
| Reliable multicast (gateway)                  | Mesnagens perdidas são recuperadas                                   | PASS      | Ordem de receção não garantida, pois não foi defenido como necessário                                                  |
| Persistência com MapDB                        | Dados mantidos após reinício                                         | PASS      | Dados apenas são armazenados durante shutdown (redundancia de multiplos barrels permite reduzir este reduzir overhead) |
| Barrel recupera estado consistente após crash | Após barrel iniciar possuir a mesma informação que barrels já ativos | PASS      |                                                                                                                        |
| Crash do Downloader                           | Reconexão automática funciona                                        | PASS      |                                                                                                                        |



## 7. Como Executar
Para a execução do programa é necessário ter:
- O Java instalado (versão 11 ou superior)
- Maven para construção do projeto
  - Projeto usa maven para a gestão de dependências e build, logo não ;e necessário ter as dependencias pré instaladas, mas apenas o maeven.
- Make para facilitar o build e execução
- O makefil possui todos os comandos necessários para a compilação e execução do projeto. A ordem de inicialização dos componentes, não é relevante pois todos possuem mecanismos de reconexão automática.

