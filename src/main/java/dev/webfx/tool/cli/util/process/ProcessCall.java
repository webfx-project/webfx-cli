package dev.webfx.tool.cli.util.process;

import dev.webfx.tool.cli.core.Logger;
import dev.webfx.tool.cli.util.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public class ProcessCall {

    private File workingDirectory;

    private String[] commandTokens;

    private String shellLogCommand;

    private boolean powershellCommand;

    private Predicate<String> logLineFilter;

    private Predicate<String> errorLineFilter;

    private final List<String> errorLines = new ArrayList<>();

    private Predicate<String> resultLineFilter;

    private String lastResultLine;

    private boolean logsCalling = true;

    private boolean logsCallDuration = true;

    private StreamGobbler streamGobbler;

    private int exitCode;

    private long callDurationMillis;

    public ProcessCall() {
    }

    public ProcessCall(String... commandTokens) {
        setCommandTokens(commandTokens);
    }

    public ProcessCall setCommand(String command) {
        shellLogCommand = command;
        return setCommandTokens(command.split(" "));
    }

    public ProcessCall setPowershellCommand(String command) {
        shellLogCommand = command;
        powershellCommand = true;
        return setCommandTokens("powershell", "-Command", command.replaceAll("\"", "\\\\\"")); // Replacing " with \" (otherwise double quotes will be removed)
    }

    public ProcessCall setBashCommand(String command) {
        shellLogCommand = command;
        return setCommandTokens("bash", "-c", command);
    }

    public ProcessCall setCmdCommand(String command) {
        shellLogCommand = command;
        return setCommandTokens("cmd", "/c", command);
    }

    public ProcessCall setCommandTokens(String... commandTokens) {
        this.commandTokens = commandTokens;
        return this;
    }

    public String[] getCommandTokens() {
        return commandTokens;
    }

    private String getShellLogCommand() {
        if (shellLogCommand == null)
            shellLogCommand = Arrays.stream(getCommandTokens()).map(ProcessCall::toShellLogCommandToken).collect(Collectors.joining(" "));
        return shellLogCommand;
    }

    public static String toShellLogCommandToken(String commandToken) {
        String shellLogCommandToken = commandToken;
        if (shellLogCommandToken.contains(" ")
                && !shellLogCommandToken.startsWith("\"")
                && !shellLogCommandToken.endsWith("\"")
                && !shellLogCommandToken.startsWith("'")
                && !shellLogCommandToken.endsWith("'")
        )
            if (!OperatingSystem.isWindows())
                shellLogCommandToken = shellLogCommandToken.replace(" ", "\\ ");
            else if (!shellLogCommandToken.contains("\""))
                shellLogCommandToken = "\"" + shellLogCommandToken + "\"";
            else
                shellLogCommandToken = "'" + shellLogCommandToken + "'";
        return shellLogCommandToken;
    }

    public ProcessCall setWorkingDirectory(Path workingDirectory) {
        return setWorkingDirectory(workingDirectory.toFile());
    }

    public ProcessCall setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public ProcessCall setLogLineFilter(Predicate<String> logLineFilter) {
        this.logLineFilter = logLineFilter;
        return this;
    }

    public ProcessCall setResultLineFilter(Predicate<String> resultLineFilter) {
        this.resultLineFilter = resultLineFilter;
        return this;
    }

    public ProcessCall setErrorLineFilter(Predicate<String> errorLineFilter) {
        this.errorLineFilter = errorLineFilter;
        return this;
    }

    public ProcessCall setLogsCall(boolean logsCalling, boolean logsCallDuration) {
        this.logsCalling = logsCalling;
        this.logsCallDuration = logsCallDuration;
        return this;
    }

    public ProcessCall executeAndWait() {
        executeAndConsume(line -> {
            boolean log = false;
            if (errorLineFilter != null && errorLineFilter.test(removeEscapeSequences(line))) {
                errorLines.add(line);
                log = true;
            }
            if (logLineFilter == null || logLineFilter.test(removeEscapeSequences(line)))
                log = true;
            if (resultLineFilter == null || resultLineFilter.test(removeEscapeSequences(line)))
                lastResultLine = removeEscapeSequences(line);
            if (log)
                Logger.log(line);
        });
        return this;
    }

    private static String removeEscapeSequences(String line) {
        return line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    public ProcessCall logCallCommand() {
        Logger.log((powershellCommand ? "PS " : "") + (workingDirectory == null ? "" : workingDirectory) + (OperatingSystem.isLinux() ? "$ " : OperatingSystem.isMacOs() ? " % " : "> ") + getShellLogCommand());
        return this;
    }

    public ProcessCall logCallDuration() {
        Logger.log("Call duration: " + callDurationMillis + " ms");
        return this;
    }

    public ProcessCall onLastResultLine(Consumer<String> lastResultLineConsumer) {
        waitForStreamGobblerCompleted();
        lastResultLineConsumer.accept(lastResultLine);
        return this;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getLastResultLine() {
        waitForStreamGobblerCompleted();
        return lastResultLine;
    }

    public List<String> getErrorLines() {
        waitForStreamGobblerCompleted();
        return errorLines;
    }

    public String getLastErrorLine() {
        return getErrorLines().isEmpty() ? null : errorLines.get(errorLines.size() - 1);
    }

    private void executeAndConsume(Consumer<String> outputLineConsumer) {
        if (logsCalling)
            logCallCommand();
        try {
            tryExecuteAndConsume(outputLineConsumer, getCommandTokens());
        } catch (Exception e) {
            // Sometimes it's because Windows doesn't find the program (like mvn), if it's not started from cmd
            if (OperatingSystem.isWindows() && !"cmd".equals(commandTokens[0]) && e.getMessage().contains("CreateProcess error=2"))
                try { // In that case, we try again through cmd
                    tryExecuteAndConsume(outputLineConsumer, "cmd", "/c", getShellLogCommand());
                    return; // If it works, we recovered, so we return without raising an exception
                } catch (Exception e2) { /* if it still doesn't work, we raise the original error */ }
            throw new RuntimeException(e);
        }
    }

    private void tryExecuteAndConsume(Consumer<String> outputLineConsumer, String... commandTokens) throws IOException, InterruptedException {
        long t0 = System.currentTimeMillis();
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(commandTokens)
                .directory(workingDirectory);
        // Using inherited i/o when no filter are required (which may display ANSI colors)
        // Note 1: it is necessary to use them to display "Do you want to continue? [Y/n]" on Linux bach
        // Note 2: this prevents the StreamGobbler working (no output lines)
        if (logLineFilter == null && resultLineFilter == null)
            processBuilder
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        streamGobbler = new StreamGobbler(process.getInputStream(), outputLineConsumer);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        exitCode = process.waitFor();
        callDurationMillis = System.currentTimeMillis() - t0;
        if (logsCallDuration)
            logCallDuration();
    }

    private void waitForStreamGobblerCompleted() {
        while (streamGobbler != null && !streamGobbler.isCompleted())
            try {
                synchronized (streamGobbler) {
                    streamGobbler.wait(1);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }

    public static int executeCommandTokens(String... commandTokens) {
        return new ProcessCall().setCommandTokens(commandTokens).executeAndWait().getExitCode();
    }

    public static int executeCmdCommand(String cmdCommand) {
        return new ProcessCall().setCmdCommand(cmdCommand).executeAndWait().getExitCode();
    }

}
