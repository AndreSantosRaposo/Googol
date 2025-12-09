# Diretórios
CLASSES_DIR = target\classes
LIB_DIR = target\lib
CONFIGS_DIR = configs

# Detetar IP automaticamente
LOCAL_IP = $(shell powershell -Command "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$$_.IPAddress -notlike '127.*'}).IPAddress | Select-Object -First 1")

# Classpath para Windows (separador ;)
CP = $(CLASSES_DIR);$(LIB_DIR)\*

# Targets principais
.PHONY: all clean clean_db compile setup_rmi
.PHONY: run_barrel run_barrel1 run_barrel2 run_downloader1 run_downloader2
.PHONY: run_gateway run_client

all: compile

compile:
	mvn compile

clean:
	mvn clean
	if exist $(CLASSES_DIR) rmdir /s /q $(CLASSES_DIR)

clean_db:
	del /q *.db 2>nul || exit 0

setup_rmi:
	@echo Setting up rmiregistry
	@taskkill /f /im rmiregistry.exe 2>nul || exit 0
	@start /b cmd /c "set CLASSPATH=$(CLASSES_DIR) && rmiregistry 1099"
	@echo RMI registry started on port 1099

# Run components
run_barrel: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.BarrelServer

run_barrel1: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.BarrelServer

run_barrel2: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.BarrelServer

run_downloader1: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.DownloaderServer

run_downloader2: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.DownloaderServer

run_gateway: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.GatewayServer

run_client: compile
	java -cp "$(CP)" -Djava.rmi.server.hostname=$(LOCAL_IP) webServer.Cliente

run_webapp:
	mvn spring-boot:run