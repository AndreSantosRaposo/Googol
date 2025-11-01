# makefile
MVN = mvn
JAVA = java
DEPDIR = target/dependency
JARDIR = target

UNAME := $(shell uname 2>/dev/null || echo Windows)
ifeq ($(UNAME),Windows)
  CP_DEFAULT = target\classes;target\dependency\*
else
  CP_DEFAULT = target/classes:target/dependency/*
endif
CP ?= $(CP_DEFAULT)

.PHONY: all build copy-deps clean run-barrel run-downloader run-gateway run-barrel-mvn run-downloader-mvn run-gateway-mvn

all: build

build:
	$(MVN) -DskipTests package

copy-deps:
	$(MVN) dependency:copy-dependencies -DoutputDirectory=$(DEPDIR)

clean:
	$(MVN) clean
	-rmdir /s /q $(DEPDIR) 2>nul || rm -rf $(DEPDIR) 2> /dev/null

# Run via mvn exec (mais simples)
run-barrel-mvn:
	$(MVN) exec:java -Dexec.mainClass="BarrelServer"

run-downloader-mvn:
	$(MVN) exec:java -Dexec.mainClass="DownloaderServer"

run-gateway-mvn:
	$(MVN) exec:java -Dexec.mainClass="GatewayServer"

# Run directly with java (usa CP; garantir que target/dependency existe)
run-barrel: copy-deps
	$(JAVA) -cp "$(CP)" BarrelServer

run-downloader: copy-deps
	$(JAVA) -cp "$(CP)" DownloaderServer

run-gateway: copy-deps
	$(JAVA) -cp "$(CP)" GatewayServer
