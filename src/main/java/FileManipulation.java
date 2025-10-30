import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Arrays;

public class FileManipulation {

    /**
     * Lê todo o conteúdo de um arquivo e retorna como lista de strings
     */
    public static List<String> readFile(String filename) throws FileNotFoundException {
        List<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(filename))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim(); // Fazendo trim por segurança
                if (!line.isEmpty()) { // Ignorar linhas vazias
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * Divide uma linha específica do arquivo usando um separador
     *
     * @param filename  Nome do arquivo
     * @param line      Número da linha (começando em 0)
     * @param separator Separador para dividir a linha (padrão: ";")
     * @return Lista com as partes da linha dividida
     */
    public static List<String> lineSplitter(String filename, Integer line, String separator) throws FileNotFoundException {
        List<String> lines = readFile(filename);

        if (line < 0 || line >= lines.size()) {
            throw new IllegalArgumentException("Linha " + line + " não existe. O arquivo tem " + lines.size() + " linhas.");
        }

        String[] parts = lines.get(line).split(separator);

        List<String> splited = new ArrayList<>();
        for (String part : parts) {
            splited.add(part.trim());
        }
        System.out.println(splited);

        return splited;
    }

}