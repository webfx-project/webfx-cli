package dev.webfx.cli.util.textfile;

import dev.webfx.cli.core.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
public final class TextFileThreadTransaction implements AutoCloseable {

    private static final ThreadLocal<TextFileThreadTransaction> threadLocalTransactions = new ThreadLocal<>();

    private final TextFileThreadTransaction previousTransaction;
    private final List<TextFileOperation> operations = new ArrayList<>();
    private final Set<Path> toDeletePaths = new HashSet<>();
    private final Set<Path> deletedPaths = new HashSet<>();
    private final Set<Path> toWritePaths = new HashSet<>();
    public int readInputFilesCount;
    public int outputFilesCount; // = created + updated + unmodified
    public int createdOutputFilesCount;
    public int updatedOutputFilesCount;
    public int unmodifiedOutputFilesCount;
    public int deletedOutputFilesCount; // in addition to outputFilesCount
    private int executedOperationsCounts;

    public static TextFileThreadTransaction open() {
        threadLocalTransactions.set(new TextFileThreadTransaction(get()));
        return get();
    }

    @Override
    public void close() {
        threadLocalTransactions.set(previousTransaction);
    }

    static TextFileThreadTransaction get() {
        return threadLocalTransactions.get();
    }

    public TextFileThreadTransaction(TextFileThreadTransaction previousTransaction) {
        this.previousTransaction = previousTransaction;
    }

    void addOperation(TextFileOperation operation) {
        operations.add(operation);
        Path path = operation.path;
        if (operation.content == null) { // Delete operation
            toDeletePaths.add(path);
            // When a delete operation occurs, we remove any previous pending write operation on that same file
            toWritePaths.remove(path);
        } else { // Write operation (create or update)
            toWritePaths.add(path);
            // When a write operation occurs, we remove any previous pending delete operation on that same file
            toDeletePaths.remove(path);
            deletedPaths.remove(path);
        }
    }

    public int executedOperationsCount() {
        return executedOperationsCounts;
    }

    public void commit() {
        // Committing all operations
        operations.forEach(operation -> {
            // Either immediately if not embed in a previous transaction
            if (previousTransaction == null)
                commitOperation(operation); // executed now
            else { // Or just transferring it to the previous transaction
                previousTransaction.addOperation(operation); // will be executed on transaction commit
                // Also transferring already deleted paths (probably empty because the operation was not yet executed)
                previousTransaction.deletedPaths.addAll(deletedPaths);
            }
        });
        operations.clear();
        toWritePaths.clear();
        toDeletePaths.clear();
        deletedPaths.clear();
    }

    boolean isFileOnDelete(Path path) {
        return toDeletePaths.contains(path) || deletedPaths.contains(path);
    }

    boolean isFileOnWrite(Path path) {
        return toWritePaths.contains(path);
    }

    private void commitOperation(TextFileOperation op) {
        try {
            Path path = op.path;
            if (op.content != null) { // Write operation
                if (toWritePaths.contains(path)) { // Checking if the file is still to write (no subsequent delete operation occurs)
                    if (!op.silent)
                        outputFilesCount++;
                    boolean exists = Files.exists(path);
                    if (!exists) {
                        Files.createDirectories(path.getParent()); // Creating all necessary parent directories
                        if (!op.silent)
                            createdOutputFilesCount++;
                    } else if (op.onlyIfNewOrModified && areTextFileContentsIdentical(op.content, TextFileReaderWriter.readOutputTextFile(path))) {
                        if (!op.silent)
                            unmodifiedOutputFilesCount++;
                        return;
                    } else if (!op.silent)
                        updatedOutputFilesCount++;
                    BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
                    writer.write(op.content);
                    writer.flush();
                    writer.close();
                    toWritePaths.remove(path);
                    if (!op.silent) {
                        Logger.log((exists ? "Updated " : "Created ") + path);
                        executedOperationsCounts++;
                    }
                }
            } else { // Delete operation
                if (toDeletePaths.contains(path)) { // Checking if the file is still to delete (no subsequent write operation occurs)
                    if (Files.deleteIfExists(path)) {
                        deletedPaths.add(path);
                        if (!op.silent) {
                            Logger.log("Deleted " + path);
                            deletedOutputFilesCount++;
                            executedOperationsCounts++;
                        }
                    }
                    toDeletePaths.remove(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean areTextFileContentsIdentical(String content1, String content2) {
        return Objects.equals(removeLR(content1), removeLR(content2));
    }

    private static String removeLR(String content) {
        return content == null ? null : content.replaceAll("\r", "");
    }

}
