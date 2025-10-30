SRC_DIR = src/main/java
CLASSES_DIR = target/classes
LIB_DIR = target/lib

SOURCES = $(shell find $(SRC_DIR) -name "*.java")
CLASSES = $(patsubst $(SRC_DIR)%,$(CLASSES_DIR)%,$(SOURCES:.java=.class))

LOCAL_IP = $(shell hostname -I | cut -d' ' -f1)

all: $(CLASSES) $(LIB_DIR)

$(LIB_DIR):
	mvn install

$(CLASSES_DIR)/%.class: $(SRC_DIR)/%.java
	mkdir -p $(dir $@)
	mvn compile -Dsource=$<

compile:
	mvn clean compile

package:
	mvn clean package

clean:
	rm -rf $(CLASSES_DIR)
	mvn clean

clean_db:
	rm -rf *.db

setup_rmi:
	@echo "Setting up rmiregistry"
	pkill rmiregistry || true
	CLASSPATH=$(CLASSES_DIR) rmiregistry 1099 &
	@echo "RMI registry started on port 1099"

run_gateway: all
	java -cp $(CLASSES_DIR):$(LIB_DIR)/* -Djava.rmi.server.hostname=$(LOCAL_IP) GatewayServer

run_barrel: all
	java -cp $(CLASSES_DIR):$(LIB_DIR)/* -Djava.rmi.server.hostname=$(LOCAL_IP) BarrelServer

run_barrel1: all
	java -cp $(CLASSES_DIR):$(LIB_DIR)/* -Djava.rmi.server.hostname=$(LOCAL_IP) BarrelServer

run_downloader: all
	java -cp $(CLASSES_DIR):$(LIB_DIR)/* DownloaderServer

run_client: all
	java -cp $(CLASSES_DIR):$(LIB_DIR)/* Cliente

help:
	@echo "Comandos disponíveis:"
	@echo "  make compile        - Compila o projeto"
	@echo "  make package        - Cria o package completo"
	@echo "  make clean          - Remove ficheiros compilados"
	@echo "  make clean_db       - Remove bases de dados"
	@echo "  make setup_rmi      - Inicia RMI registry"
	@echo ""
	@echo "Executar componentes:"
	@echo "  make run_gateway    - Inicia Gateway Server"
	@echo "  make run_barrel     - Inicia Barrel Server 1"
	@echo "  make run_barrel1    - Inicia Barrel Server 2"
	@echo "  make run_downloader - Inicia Downloader Server"
	@echo "  make run_client     - Inicia Cliente"

.PHONY: all compile package clean clean_db setup_rmi run_gateway run_barrel run_barrel1 run_downloader run_client help
