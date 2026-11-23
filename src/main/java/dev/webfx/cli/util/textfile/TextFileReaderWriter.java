package dev.webfx.cli.util.textfile;

import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.stopwatch.StopWatch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TextFileReaderWriter {

    public static final StopWatch FILE_READING_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    public static final StopWatch FILE_WRITING_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    public static final StopWatch FILE_DELETING_STOPWATCH = StopWatch.createSystemNanoStopWatch();


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
        String content = null;
        FILE_READING_STOPWATCH.on();
        try {
            content = SplitFiles.uncheckedReadTextFile(path);
            if (input) {
                incrementReadInputFilesCount();
                /*
                FileSystem fileSystem = path.getFileSystem();
                boolean isInsideJar = fileSystem.provider().getScheme().equals("jar");
                System.out.println("Reading " + TextFileThreadTransaction.get().readInputFilesCount + ") " + (isInsideJar ? "JAR " + fileSystem + "!" : "") + path);
                */
            }
        } catch (RuntimeException ignored) {
        } finally {
            FILE_READING_STOPWATCH.off();
        }
        return content;
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

    public static void writeTextFileNow(String content, Path path) throws IOException {
        try {
            FILE_WRITING_STOPWATCH.on();
            BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            writer.write(content);
            writer.flush();
            writer.close();
        } finally {
            FILE_WRITING_STOPWATCH.off();
        }
    }

    public static void deleteTextFile(Path path) {
        writeTextFile(null, path, false);
    }

    /**
     * The files that an update in progress will have written by the time its transaction commits, including those not
     * on disk yet.
     *
     * <p>Generated files are written through a transaction that commits at the very END of the update, so for most of
     * a run the filesystem shows the state BEFORE it: a file about to be written is absent, and one about to be
     * deleted is still there. Anything that decides something from the shape of a module's resources therefore has to
     * ask the transaction as well, or it reads a state that will not exist by the time the run finishes — which is
     * exactly what {@link #listFiles} and {@link #folderContainsFile} are for.
     */
    static Collection<Path> getPendingWrittenFiles() {
        TextFileThreadTransaction transaction = TextFileThreadTransaction.get();
        if (transaction == null)
            return Collections.emptyList();
        Set<Path> toWritePaths = new HashSet<>();
        transaction.collectToWritePaths(toWritePaths);
        return toWritePaths;
    }

    /**
     * The files that folder will hold once the update in progress has committed, down to maxDepth — i.e. what is on
     * disk, minus what is about to be deleted, plus what is about to be written.
     *
     * <p>Use this rather than walking the folder whenever the answer feeds something that is itself generated, such as
     * a module-info's opens or a GWT resource bundle's entries. A plain walk during a run that MOVES a generated
     * resource reports its old location and not its new one, and a module-info that opens a package the same run
     * empties is refused outright by the module system (InvalidModuleDescriptorException), compiling cleanly first
     * because the emptied directory is still there.
     */
    public static List<Path> listFiles(Path folder, int maxDepth) {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            SplitFiles.uncheckedWalk(folder, maxDepth, FileVisitOption.FOLLOW_LINKS).forEachRemaining(path -> {
                if (!Files.isDirectory(path) && fileExists(path)) // fileExists() drops those about to be deleted
                    files.add(path);
            });
        }
        for (Path pendingWrittenFile : getPendingWrittenFiles()) {
            if (pendingWrittenFile.startsWith(folder) && !files.contains(pendingWrittenFile)
                    && folder.relativize(pendingWrittenFile).getNameCount() <= maxDepth)
                files.add(pendingWrittenFile);
        }
        return files;
    }

    /**
     * Whether folder will hold at least one file once the update in progress has committed. Note that this is also the
     * module system's rule for whether a directory declares a package: a directory holding only sub-directories
     * declares none.
     */
    public static boolean folderContainsFile(Path folder) {
        return !listFiles(folder, Integer.MAX_VALUE).isEmpty();
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

    static boolean deleteFile(Path path) throws IOException {
        try {
            FILE_DELETING_STOPWATCH.on();
            boolean deleted = Files.deleteIfExists(path);
            if (!deleted)
                FILE_DELETING_STOPWATCH.decRunCount();
            return deleted;
        } finally {
            FILE_DELETING_STOPWATCH.off();
        }
    }

}
