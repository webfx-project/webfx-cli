package dev.webfx.cli.util.javacode;

import dev.webfx.cli.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * @author Bruno Salmon
 */
public final class JavaCode {

    /*public static int filesCount;
    public static int locCount;*/

    private Supplier<Path> javaPathSupplier;
    private Path javaFilePath;
    private String textCode;

    public JavaCode(Supplier<Path> javaPathSupplier) {
        this.javaPathSupplier = javaPathSupplier;
    }

    public JavaCode(Path javaFilePath) {
        this.javaFilePath = javaFilePath;
    }

    public JavaCode(String textCode) {
        this.textCode = textCode;
    }

    public String getTextCode() {
        if (textCode == null) {
            if (javaFilePath == null)
                javaFilePath = javaPathSupplier.get();
            textCode = TextFileReaderWriter.readInputTextFile(javaFilePath);
            /*filesCount++;
            String[] lines = textCode.split("\r\n|\r|\n");
            int loc = lines.length + 1;
            System.out.println(loc + " for " + javaFilePath);
            locCount += loc;*/
        }
        return textCode;
    }
}
