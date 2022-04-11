package dev.webfx.buildtool.util.textfile;

import dev.webfx.buildtool.Logger;
import dev.webfx.buildtool.util.splitfiles.SplitFiles;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class TextFileReaderWriter {

    public static String readTextFile(Path path) {
        try {
            return SplitFiles.uncheckedReadTextFile(path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void writeTextFileIfNewOrModified(String content, Path path) {
        writeTextFileIfNewOrModified(content, readTextFile(path), path);
    }

    public static void writeTextFileIfNewOrModified(String newContent, String oldContent, Path path) {
        if (newContent == null && Files.exists(path) || !areTextFileContentsIdentical(newContent, oldContent))
            writeTextFile(newContent, path);
    }

    private static boolean areTextFileContentsIdentical(String content1, String content2) {
        return Objects.equals(removeLR(content1), removeLR(content2));
    }

    private static String removeLR(String content) {
        return content == null ? null : content.replaceAll("\r", "");
    }

    public static void writeTextFile(String content, Path path) {
        try (TextFileThreadTransaction transaction = TextFileThreadTransaction.open()) {
            transaction.addOperation(new TextFileOperation(path, content));
            transaction.commit(); // Executed now if not embed in a wider transaction, or later on commit of the wider transaction
        }
    }

    static void commit(TextFileOperation op) {
        try {
            if (op.content == null) // Delete file
                Files.deleteIfExists(op.path);
            else { // Write file
                boolean exists = Files.exists(op.path);
                if (!exists)
                    Files.createDirectories(op.path.getParent()); // Creating all necessary directories
                BufferedWriter writer = Files.newBufferedWriter(op.path, StandardCharsets.UTF_8);
                writer.write(op.content);
                writer.flush();
                writer.close();
                Logger.log((exists ? "Updated " :  "Created " ) + op.path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
