package dev.webfx.cli.util.textfile;

import dev.webfx.cli.core.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private int executedOperationsCounts;

    public static TextFileThreadTransaction open() {
        threadLocalTransactions.set(new TextFileThreadTransaction(get()));
        return get();
    }

    static TextFileThreadTransaction get() {
        return threadLocalTransactions.get();
    }

    public TextFileThreadTransaction(TextFileThreadTransaction previousTransaction) {
        this.previousTransaction = previousTransaction;
    }

    void addOperation(TextFileOperation operation) {
        operations.add(operation);
        if (operation.content == null)
            toDeletePaths.add(operation.path);
        else
            toWritePaths.add(operation.path);
    }

    public int executedOperationsCount() {
        return executedOperationsCounts;
    }

    public void commit() {
        if (previousTransaction == null)
            operations.forEach(this::commitOperation);
        else {
            previousTransaction.operations.addAll(operations);
            previousTransaction.toWritePaths.addAll(toWritePaths);
            previousTransaction.toDeletePaths.addAll(toDeletePaths);
            previousTransaction.deletedPaths.addAll(deletedPaths);
        }
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
            if (op.content != null) { // Write file
                boolean exists = Files.exists(op.path);
                if (!exists) {
                    Files.createDirectories(op.path.getParent()); // Creating all necessary directories
                    if (deletedPaths.contains(op.path)) {
                        deletedPaths.remove(op.path);
                        executedOperationsCounts--;
                    }
                }
                BufferedWriter writer = Files.newBufferedWriter(op.path, StandardCharsets.UTF_8);
                writer.write(op.content);
                writer.flush();
                writer.close();
                toWritePaths.remove(op.path);
                if (!op.silent) {
                    Logger.log((exists ? "Updated " : "Created ") + op.path);
                    executedOperationsCounts++;
                }
            } else { // Delete file
                if (Files.deleteIfExists(op.path)) {
                    deletedPaths.add(op.path);
                    if (!op.silent) {
                        Logger.log("Deleted " + op.path);
                        executedOperationsCounts++;
                    }
                }
                toDeletePaths.remove(op.path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        threadLocalTransactions.set(previousTransaction);
    }

}
