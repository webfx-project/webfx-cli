package dev.webfx.cli.util.textfile;

import dev.webfx.cli.WebFxCLI;
import dev.webfx.cli.core.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class ResourceTextFileReader {

    private static final Class<?> ROOT_CLASS = WebFxCLI.class; // Class located in dev.webfx.cli root package
    private static final String ROOT_RESOURCE_FOLDER = ROOT_CLASS.getPackageName().replace('.', '/') + '/'; // Should be "dev/webfx/cli/"
    private static final String TEMPLATE_RESOURCE_FOLDER = ROOT_RESOURCE_FOLDER + "templates/"; // Should be 'dev/webfx/cli/templates/'

    public static String readTemplate(String templateName) {
        return uncheckedReadResourceTextFile(TEMPLATE_RESOURCE_FOLDER + templateName);
    }

    public static String uncheckedReadResourceTextFile(String fileName) {
        try {
            return readResourceTextFile(fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String readResourceTextFile(String fileName) throws IOException {
        if (fileName.startsWith(ROOT_RESOURCE_FOLDER))
            fileName = fileName.substring(ROOT_RESOURCE_FOLDER.length());
        else if (fileName.startsWith("/" + ROOT_RESOURCE_FOLDER))
            fileName = fileName.substring(ROOT_RESOURCE_FOLDER.length() + 1);
        else {
            Logger.warning("Couldn't locate resource file " + fileName + " (all WebFX CLI resources should start with " + ROOT_RESOURCE_FOLDER + ")");
            return null;
        }
        try (InputStream is = ROOT_CLASS.getResourceAsStream(fileName)) {
            if (is == null) {
                Logger.warning("Unable to read resource file " + fileName);
                return null;
            }
            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

}
