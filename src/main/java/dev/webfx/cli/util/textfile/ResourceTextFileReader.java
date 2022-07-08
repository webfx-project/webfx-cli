package dev.webfx.cli.util.textfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class ResourceTextFileReader {

    public static String readTemplate(String templateName) {
        return uncheckedReadResourceTextFile("dev/webfx/cli/templates/" + templateName);
    }

    public static String uncheckedReadResourceTextFile(String fileName) {
        try {
            return readResourceTextFile(fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String readResourceTextFile(String fileName) throws IOException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null)
                return null;
            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

}
