package dev.webfx.cli.util.textfile;

import dev.webfx.cli.util.splitfiles.SplitFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TextFileReaderWriter {

    public static String readInputTextFile(Path path) {
        return readTextFile(path, true);
    }

    public static String readOutputTextFile(Path path) {
        return readTextFile(path, false);
    }

    public static String readCliTextFile(Path path) {
        return readTextFile(path, false);
    }

    public static void incrementReadInputFilesCount() {
        TextFileThreadTransaction transaction = TextFileThreadTransaction.get();
        if (transaction != null)
            transaction.readInputFilesCount++;
    }

    private static String readTextFile(Path path, boolean input) {
        try {
            String content = SplitFiles.uncheckedReadTextFile(path);
            if (input) {
                incrementReadInputFilesCount();
                /*
                FileSystem fileSystem = path.getFileSystem();
                boolean isInsideJar = fileSystem.provider().getScheme().equals("jar");
                System.out.println("Reading " + TextFileThreadTransaction.get().readInputFilesCount + ") " + (isInsideJar ? "JAR " + fileSystem + "!" : "") + path);
                */
            }
            return content;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void writeTextFileIfNewOrModified(String content, Path path) {
        writeTextFile(content, path, true, false);
    }

    public static void writeTextFile(String content, Path path) {
        writeTextFile(content, path, false);
    }

    public static void writeTextFile(String content, Path path, boolean skipIfUnmodified) {
        writeTextFile(content, path, skipIfUnmodified, false);
    }

    public static void writeTextFile(String content, Path path, boolean onlyIfNewOrModified, boolean silent) {
        try (TextFileThreadTransaction transaction = TextFileThreadTransaction.open()) {
            transaction.addOperation(new TextFileOperation(path, content, onlyIfNewOrModified, silent));
            transaction.commit(); // Executed now if not embed in a wider transaction, or later on commit of the wider transaction
        }
    }

    public static void deleteTextFile(Path path) {
        writeTextFile(null, path, false);
    }

    public static boolean fileExists(Path path) {
        TextFileThreadTransaction transaction = TextFileThreadTransaction.get();
        if (transaction != null) {
            if (transaction.isFileOnWrite(path))
                return true;
            if (transaction.isFileOnDelete(path))
                return false;
        }
        return Files.exists(path);
    }

    public static void deleteFolder(Path folderPath) {
        try {
            if (Files.exists(folderPath))
                SplitFiles.walk(folderPath).forEachRemaining(path -> {
                    if (Files.isRegularFile(path))
                        deleteTextFile(path);
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
