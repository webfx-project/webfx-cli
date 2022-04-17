package dev.webfx.buildtool.util.textfile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bruno Salmon
 */
public final class TextFileThreadTransaction implements AutoCloseable {

    private static final ThreadLocal<TextFileThreadTransaction> threadLocalTransactions = new ThreadLocal<>();

    private final TextFileThreadTransaction previousTransaction;
    private final List<TextFileOperation> operations = new ArrayList<>();

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
    }

    public int operationsCount() {
        return operations.size();
    }

    public void commit() {
        if (previousTransaction == null)
            operations.forEach(TextFileReaderWriter::commit);
        else
            previousTransaction.operations.addAll(operations);
        operations.clear();
    }

    @Override
    public void close() {
        threadLocalTransactions.set(previousTransaction);
    }

}
