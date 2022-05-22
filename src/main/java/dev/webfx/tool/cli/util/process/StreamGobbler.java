package dev.webfx.tool.cli.util.process;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * @author Bruno Salmon
 */
class StreamGobbler implements Runnable {
    private final InputStream inputStream;
    private final Consumer<String> outputLineConsumer;

    private boolean completed;

    public StreamGobbler(InputStream inputStream, Consumer<String> outputLineConsumer) {
        this.inputStream = inputStream;
        this.outputLineConsumer = outputLineConsumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines()
                .forEach(outputLineConsumer);
        completed = true;
    }

    public boolean isCompleted() {
        return completed;
    }
}