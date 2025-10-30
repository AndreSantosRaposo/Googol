# Makefile.ps1 - Build automation para Windows

$CLASSES_DIR = "target\classes"
$LIB_DIR = "target\lib"
$LOCAL_IP = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.InterfaceAlias -notlike "*Loopback*"} | Select-Object -First 1).IPAddress

function Compile {
    Write-Host "Compilando projeto..." -ForegroundColor Green
    mvn clean compile
}

function Package {
    Write-Host "Criando package..." -ForegroundColor Green
    mvn clean package
}

function Clean {
    Write-Host "Limpando ficheiros compilados..." -ForegroundColor Green
    if (Test-Path $CLASSES_DIR) { Remove-Item -Recurse -Force $CLASSES_DIR }
    mvn clean
}

function Clean-DB {
    Write-Host "Removendo bases de dados..." -ForegroundColor Green
    Remove-Item -Force *.db -ErrorAction SilentlyContinue
}

function Setup-RMI {
    Write-Host "Configurando RMI registry..." -ForegroundColor Green
    Stop-Process -Name "rmiregistry" -ErrorAction SilentlyContinue
    Start-Process rmiregistry -ArgumentList "1099" -NoNewWindow
    Write-Host "RMI registry iniciado na porta 1099" -ForegroundColor Cyan
}

function Run-Gateway {
    Compile
    Write-Host "Iniciando Gateway Server..." -ForegroundColor Green
    java -cp "$CLASSES_DIR;$LIB_DIR\*" -Djava.rmi.server.hostname=$LOCAL_IP GatewayServer
}

function Run-Barrel {
    Compile
    Write-Host "Iniciando Barrel Server 1..." -ForegroundColor Green
    java -cp "$CLASSES_DIR;$LIB_DIR\*" -Djava.rmi.server.hostname=$LOCAL_IP BarrelServer
}

function Run-Barrel1 {
    Compile
    Write-Host "Iniciando Barrel Server 2..." -ForegroundColor Green
    java -cp "$CLASSES_DIR;$LIB_DIR\*" -Djava.rmi.server.hostname=$LOCAL_IP BarrelServer
}

function Run-Downloader {
    Compile
    Write-Host "Iniciando Downloader Server..." -ForegroundColor Green
    java -cp "$CLASSES_DIR;$LIB_DIR\*" DownloaderServer
}

function Run-Client {
    Compile
    Write-Host "Iniciando Cliente..." -ForegroundColor Green
    java -cp "$CLASSES_DIR;$LIB_DIR\*" Cliente
}

function Show-Help {
    Write-Host "`nComandos disponíveis:" -ForegroundColor Yellow
    Write-Host "  .\Makefile.ps1 compile        - Compila o projeto" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 package        - Cria o package completo" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 clean          - Remove ficheiros compilados" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 clean_db       - Remove bases de dados" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 setup_rmi      - Inicia RMI registry" -ForegroundColor Cyan
    Write-Host "`nExecutar componentes:" -ForegroundColor Yellow
    Write-Host "  .\Makefile.ps1 gateway        - Inicia Gateway Server" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 barrel         - Inicia Barrel Server 1" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 barrel1        - Inicia Barrel Server 2" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 downloader     - Inicia Downloader Server" -ForegroundColor Cyan
    Write-Host "  .\Makefile.ps1 client         - Inicia Cliente" -ForegroundColor Cyan
}

# Main
switch ($args[0]) {
    "compile"     { Compile }
    "package"     { Package }
    "clean"       { Clean }
    "clean_db"    { Clean-DB }
    "setup_rmi"   { Setup-RMI }
    "gateway"     { Run-Gateway }
    "barrel"      { Run-Barrel }
    "barrel1"     { Run-Barrel1 }
    "downloader"  { Run-Downloader }
    "client"      { Run-Client }
    "help"        { Show-Help }
    default       { Show-Help }
}
