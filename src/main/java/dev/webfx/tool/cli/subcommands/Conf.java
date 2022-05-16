package dev.webfx.tool.cli.subcommands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "conf", description = "Configure module(s) (webfx.xml files).",
        subcommands = {
                Conf.Get.class,
                Conf.Set.class,
                Conf.Add.class,
                Conf.Remove.class,
                Conf.Show.class,
                Conf.Bump.class,
        }
)
public final class Conf extends CommonSubcommand {

    @Command(name = "get", description = "Get the value of a configuration property.")
    static class Get extends CommonSubcommand implements Runnable {

        @Override
        public void run() {

        }
    }

    @Command(name = "set", description = "Set the value of a configuration property.")
    static class Set extends CommonSubcommand implements Runnable {

        @Override
        public void run() {

        }
    }

    @Command(name = "add", description = "Add a configuration property.")
    static class Add extends CommonSubcommand implements Runnable {

        @Override
        public void run() {

        }
    }

    @Command(name = "remove", description = "Remove a configuration property.")
    static class Remove extends CommonSubcommand implements Runnable {

        @Override
        public void run() {

        }
    }

    @Command(name = "bump", description = "Change the version of a library.")
    static final class Bump extends CommonSubcommand implements Runnable {

        @CommandLine.Parameters(description = "Library identified by groupId:artifactId.")
        private String library;

        @CommandLine.Parameters(paramLabel = "to")
        private Keywords.ToKeyword to;

        @CommandLine.Parameters(description = "New version of the library.")
        private String version;


        @Override
        public void run() {

        }
    }

    @Command(name="show", description = "Show modules, packages, libraries, etc...",
    subcommands = {
            Show.Module.class,
            Show.Package.class,
            Show.Library.class,
    })
    static final class Show extends CommonSubcommand {

        @Command(name="module", description = "Show modules.")
        static class Module extends CommonSubcommand implements Runnable {
            @Override
            public void run() {
                log("Module");
            }
        }

        @Command(name="package", description = "Show packages.")
        static class Package extends CommonSubcommand implements Runnable {
            @Override
            public void run() {
                log("Package");
            }
        }

        @Command(name="library", description = "Show libraries.")
        static class Library extends CommonSubcommand implements Runnable {
            @Override
            public void run() {
                log("Library");
            }
        }
    }
}
