package dev.webfx.tool.buildtool.cli;

import dev.webfx.tool.buildtool.cli.Keywords.UnderKeyword;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * @author Bruno Salmon
 */
@Command(name = "move", description = "Move module(s) or package(s) under another module.",
        subcommands = {
                Move.WfxMoveModule.class,
                Move.WfxMovePackage.class,
        }
)
final class Move extends CommonSubcommand {

    @Command(name = "module", description = "Move a module under another module.")
    static class WfxMoveModule extends CommonSubcommand implements Runnable {

        @Parameters(description = "Name of the module to move.")
        private String name;

        @Parameters(paramLabel = "under")
        private UnderKeyword under;

        @Parameters(description="Name of the new parent module.")
        private String parent;

        @Override
        public void run() {

        }
    }

    @Command(name = "package", description = "Move a package under another module.")
    static class WfxMovePackage extends CommonSubcommand implements Runnable {

        @Parameters(description = "Name of the module to move.")
        private String name;

        @Parameters(paramLabel = "under")
        private UnderKeyword under;

        @Parameters(description="Name of the new parent module.")
        private String parent;

        @Override
        public void run() {

        }
    }

}
