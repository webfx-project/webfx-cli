package dev.webfx.tool.cli.commands;

import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "watch", description = "Enter watch mode.")
public final class Watch extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
    }
}