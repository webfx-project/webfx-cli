package dev.webfx.buildtool;

import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class Export {

    public static void main(String[] args) {
        try {
            export();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void export() throws Exception {
        String xmlContent = ResourceTextFileReader.readResourceTextFile("dev/webfx/buildtool/templates/webfx-export-mock.xml");
        TextFileReaderWriter.writeTextFile(xmlContent, Path.of("./webfx-export.xml"));
    }

}
