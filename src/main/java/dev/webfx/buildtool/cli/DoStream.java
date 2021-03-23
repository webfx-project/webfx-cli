package dev.webfx.buildtool.cli;

import dev.webfx.buildtool.Logger;
import dev.webfx.buildtool.ModuleDependency;
import dev.webfx.buildtool.ProjectModule;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
@Command(name = "stream", description = "Return modules, packages, dependencies, etc....",
    subcommands = {
        DoStream.Modules.class,
        DoStream.Dependencies.class
    }
)
final class DoStream extends CommonSubcommand {

    static abstract class StreamCommand extends CommonSubcommand implements Runnable {

        @Option(names= "sorted", description = "Sort the stream.")
        boolean sorted;

        @Option(names= "limit", description = "Set a limit to the stream.")
        Long limit;

        @Option(names= "distinct", description = "Return only distinct elements.")
        boolean distinct;

        @Option(names= {"foreach", "forEach"}, description = "Return only distinct elements.")
        String forEach;

        @Override
        public void run() {
            setUpLogger();
            Stream<?> stream = computeStream();
            if (distinct)
                stream = stream.distinct();
            if (sorted)
                stream = stream.sorted();
            if (limit != null)
                stream = stream.limit(limit);
            if (forEach == null)
                stream.forEach(Logger::log);
            else
                stream.forEach(o -> {
                    String s = forEach.replace("{o}", o.toString());
                    WebFx.executeCommand(s.split(" "));
                });
        }

        abstract Stream<?> computeStream();
    }

    static abstract class ProjectModuleStreamCommand extends StreamCommand {

        @Option(names = {"-x", "--executable"}, negatable = true, description = "Filter executable modules.")
        Boolean executable;

        @Option(names = {"-i", "--interface"}, negatable = true, description = "Filter interface modules.")
        Boolean isInterface;

        @Option(names = {"-a", "--automatic"}, negatable = true, description = "Filter automatic modules.")
        Boolean automatic;

        @Option(names = {"-g", "--aggregate"}, negatable = true, description = "Filter automatic modules.")
        Boolean aggregate;

        @Option(names = {"-t", "--implementing"}, negatable = true, description = "Filter implementing modules.")
        Boolean implementing;

        @Option(names = {"--implements"}, arity = "1..*", description = "Filter modules implementing specific interface.")
        String[] implementingClasses;

        @Override
        Stream<ProjectModule> computeStream() {
            Stream<ProjectModule> stream = computeProjectModuleStream().stream();
            if (executable != null)
                stream = stream.filter(m -> m.isExecutable() == executable);
            if (isInterface != null)
                stream = stream.filter(m -> m.isInterface() == isInterface);
            if (automatic != null)
                stream = stream.filter(m -> m.isAutomatic() == automatic);
            if (aggregate != null)
                stream = stream.filter(m -> m.isAggregate() == aggregate);
            if (implementing != null)
                stream = stream.filter(m -> m.isImplementingInterface() == implementing);
            if (implementingClasses != null)
                stream = stream.filter(m -> m.implementedInterfaces().anyMatch(i -> Arrays.asList(implementingClasses).contains(i)));
            return stream;
        }

        abstract ReusableStream<ProjectModule> computeProjectModuleStream();

    }

    @Command(name = "modules", description = "Return modules.")
    static class Modules extends ProjectModuleStreamCommand {

        @Option(names = {"-p", "--parent"}, description = "Include the parent module.")
        boolean includeParent;

        @Option(names = {"-r", "--recursive"}, description = "Include sub children recursively.")
        boolean recursive;

        @Override
        ReusableStream<ProjectModule> computeProjectModuleStream() {
            ProjectModule projectModule = getWorkingProjectModule();
            if (!recursive)
                return includeParent ? projectModule.getThisAndChildrenModules() : projectModule.getChildrenModules();
            return includeParent ? projectModule.getThisAndChildrenModulesInDepth() : projectModule.getChildrenModulesInDepth();
        }
    }

    static abstract class DependencyStreamCommand extends StreamCommand {

        enum Map { to_src, to_dst, to_type }

        @Option(names = "map", description = "Map the dependency to something else.")
        Map map;

        @Override
        Stream<?> computeStream() {
            Stream<ModuleDependency> dependencyStream = computeDependencyStream().stream();
            if (map == null)
                return dependencyStream;
            return dependencyStream.map(dep -> { switch (map) {
                case to_src: return dep.getSourceModule();
                case to_dst: return dep.getDestinationModule();
                case to_type: return dep.getType();
                default: return dep;
            }});
        }

        abstract ReusableStream<ModuleDependency> computeDependencyStream();
    }

    @Command(name = "dependencies", description = "Return module dependencies.")
    static class Dependencies extends DependencyStreamCommand {

        @Override
        ReusableStream<ModuleDependency> computeDependencyStream() {
            return getWorkingProjectModule().getDirectDependencies();
        }
    }
}
