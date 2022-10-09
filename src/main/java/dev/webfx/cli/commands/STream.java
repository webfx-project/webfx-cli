package dev.webfx.cli.commands;

import dev.webfx.cli.WebFX;
import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.*;
import dev.webfx.cli.modulefiles.ArtifactResolver;
import dev.webfx.lib.reusablestream.ReusableStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
@Command(name = "stream", description = "Return modules, packages, dependencies, etc....",
    subcommands = {
        STream.Modules.class,
        STream.JavaFiles.class,
        STream.Dependencies.class
    }
)
// Using uppercase for second letter to avoid confusion with java Stream class
public final class STream extends CommonSubcommand {

    static abstract class StreamCommand extends CommonSubcommand implements Runnable {

        @Option(names= "distinct", description = "Return only distinct elements.")
        boolean distinct;

        @Option(names= "sorted", description = "Sort the stream.")
        boolean sorted;

        @Option(names= "limit", description = "Set a limit to the stream.")
        Long limit;

        @Option(names= "count", description = "Count the stream.")
        boolean count;

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
            if (count)
                log(stream.count());
            else if (forEach == null)
                stream.forEach(CommonCommand::log);
            else
                stream.forEach(o -> {
                    String s = forEach.replace("{o}", o.toString());
                    WebFX.executeCommand(s.split(" "));
                });
        }

        abstract Stream<?> computeStream();
    }

    static abstract class ProjectModuleStreamCommand extends StreamCommand {

        @Option(names = {"-x", "--executable"}, negatable = true, description = "Filter executable modules.")
        Boolean executable;

        @Option(names = {"-i", "--interface"}, negatable = true, description = "Filter interface modules.")
        Boolean isInterface;

        @Option(names = {"-a", "--auto-inject"}, negatable = true, description = "Filter modules with auto injection conditions.")
        Boolean autoInject;

        @Option(names = {"-g", "--aggregate"}, negatable = true, description = "Filter aggregate modules.")
        Boolean aggregate;

        @Option(names = {"-l", "--leaf"}, negatable = true, description = "Filter leaf modules.")
        Boolean leaf;

        @Option(names = {"-t", "--implementing"}, negatable = true, description = "Filter implementing modules.")
        Boolean implementing;

        @Option(names = {"--implements"}, arity = "1..*", description = "Filter modules implementing specific interface.")
        String[] implementingClasses;

        enum FlatMap {java_files, java_classes, code_lines, declared_packages, used_packages, used_services, implemented_services, service_providers, direct_dependencies, transitive_dependencies, libraries}

        @Option(names = {"flatmap"}, description = "Flat map the modules into java files, etc...")
        FlatMap flatMap;

        enum Map { to_src, to_dst, to_type, to_gav, to_gv }

        @Option(names = "map", description = "Map the objects to something else.")
        Map map;

        @Override
        Stream<?> computeStream() {
            Stream<ProjectModule> projectModuleStream = computeProjectModuleStream();
            Stream<ModuleDependency> moduleDependencyStream = null;
            Stream<? extends Module> moduleStream = projectModuleStream;
            Stream<?> stream = projectModuleStream;
            if (flatMap != null) {
                moduleStream = null;
                switch (flatMap) {
                    case java_files:
                        stream = projectModuleStream.flatMap(m -> m.getJavaSourceFiles().stream());
                        break;
                    case java_classes:
                        stream = projectModuleStream.flatMap(m -> m.getJavaSourceFiles().stream()).map(JavaFile::getClassName);
                        break;
                    case code_lines:
                        stream = projectModuleStream.flatMap(m -> m.getJavaSourceFiles().stream()).flatMap(f -> Arrays.stream(f.getJavaCode().getTextCode().split("\n")));
                        break;
                    case declared_packages:
                        stream = projectModuleStream.flatMap(m -> m.getJavaSourcePackages().stream());
                        break;
                    case used_packages:
                        stream = projectModuleStream.flatMap(m -> m.getUsedJavaPackages().stream());
                        break;
                    case used_services:
                        stream = projectModuleStream.flatMap(m -> m.getUsedJavaServices().stream());
                        break;
                    case implemented_services:
                        stream = projectModuleStream.flatMap(m -> m.getProvidedJavaServices().map(s -> new Providers(s, ReusableStream.of(m))) .stream());
                        break;
                    case service_providers:
                        stream = projectModuleStream.flatMap(m -> m.getExecutableProviders().stream()).filter(p -> p.getProviderClassNames().stream().findFirst().isPresent());
                        break;
                    case direct_dependencies:
                        stream = moduleDependencyStream = projectModuleStream.flatMap(m -> m.getDirectDependencies().stream());
                        break;
                    case transitive_dependencies:
                        stream = moduleDependencyStream = projectModuleStream.flatMap(m -> m.getTransitiveDependencies().stream());
                        break;
                    case libraries:
                        stream = moduleStream = projectModuleStream.flatMap(m -> m.getRequiredLibraryModules().stream());
                        break;
                }
            }
            if (map != null) {
                switch (map) {
                    case to_dst:
                    case to_src:
                    case to_type:
                        if (moduleDependencyStream != null)
                            stream = moduleDependencyStream.map(dep -> { switch (map) {
                                case to_src: return dep.getSourceModule();
                                case to_dst: return dep.getDestinationModule();
                                case to_type: return dep.getType();
                                default: return dep;
                            }});
                        break;
                    case to_gav:
                        if (moduleStream != null)
                            stream = moduleStream.map(ArtifactResolver::getGAV);
                        break;
                    case to_gv:
                        if (moduleStream != null)
                            stream = moduleStream.map(ArtifactResolver::getGV);
                        break;
                }
            }
            return stream;
        }

        Stream<ProjectModule> computeProjectModuleStream() {
            Stream<ProjectModule> stream = computeProjectModuleBaseStream().stream();
            if (executable != null)
                stream = stream.filter(m -> m.isExecutable() == executable);
            if (isInterface != null)
                stream = stream.filter(m -> m.isInterface() == isInterface);
            if (autoInject != null)
                stream = stream.filter(m -> m.hasAutoInjectionConditions() == autoInject);
            if (aggregate != null)
                stream = stream.filter(m -> m.isAggregate() == aggregate);
            if (leaf != null)
                stream = stream.filter(m -> m.isAggregate() != leaf);
            if (implementing != null)
                stream = stream.filter(m -> m.isImplementingInterface() == implementing);
            if (implementingClasses != null)
                stream = stream.filter(m -> m.implementedInterfaces().anyMatch(i -> Arrays.asList(implementingClasses).contains(i)));
            return stream;
        }

        abstract ReusableStream<ProjectModule> computeProjectModuleBaseStream();

    }

    @Command(name = "modules", description = "Return modules.")
    static class Modules extends ProjectModuleStreamCommand {

        @Option(names = {"-p", "--parent"}, description = "Include the parent module.")
        boolean includeParent;

        @Option(names = {"-r", "--recursive"}, description = "Include sub children recursively.")
        boolean recursive;

        @Override
        ReusableStream<ProjectModule> computeProjectModuleBaseStream() {
            if (getClass() != Modules.class || flatMap != null)
                includeParent = recursive = true;
            ProjectModule projectModule = getWorkspace().getWorkingProjectModule();
            if (!recursive)
                return includeParent ? projectModule.getThisAndChildrenModules() : projectModule.getChildrenModules();
            return includeParent ? projectModule.getThisAndChildrenModulesInDepth() : projectModule.getChildrenModulesInDepth();
        }
    }

    @Command(name = "java_files", description = "Return module java files.")
    static class JavaFiles extends Modules {

        @Override
        Stream<?> computeStream() {
            flatMap = FlatMap.java_files;
            return super.computeStream();
        }
    }

    @Command(name = "dependencies", description = "Return module dependencies.")
    static class Dependencies extends Modules {

        @Option(names = {"--transitive"}, description = "Include transitive dependencies.")
        boolean transitive;
        @Override
        Stream<?> computeStream() {
            flatMap = transitive ? FlatMap.transitive_dependencies : FlatMap.direct_dependencies;
            return super.computeStream();
        }
    }
}
