package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.ModuleDependency;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.sort.TopologicalSort;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.platform.conf.Config;
import dev.webfx.platform.conf.ConfigParser;
import dev.webfx.platform.conf.SourcesConfig;
import dev.webfx.platform.conf.impl.ConfigMerger;
import dev.webfx.platform.util.keyobject.ReadOnlyIndexedArray;
import dev.webfx.platform.util.keyobject.ReadOnlyKeyObject;
import dev.webfx.platform.util.keyobject.formatter.AstFormatter;
import dev.webfx.platform.util.tuples.Pair;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class RootConfigFileGenerator {

    private final static PathMatcher PROPERTIES_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.{properties,json}");

    private final static Map<Path, Config> configCache = new HashMap<>(); // We assume the CLI exits after the update commande, so no need to clear that cache

    public static void generateExecutableModuleConfigurationResourceFile(DevProjectModule module) {
        if (module.isExecutable()) {
            // We will collect here all configurations from all transitive modules and merge them into a single
            // configuration file. The order is important when doing that merge, because configuration values can be
            // overridden. In that case, the actual final value to consider among the different modules defining that
            // same configuration value is the one defined in the top-most module in the dependency graph (the executable
            // module being at the very top of that graph).

            // Creating the dependency graph of the transitive modules (i.e. list of dependencies for each module)
            Map<Module, List<Module>> dependencyGraph =
                    ModuleDependency.createDependencyGraph(module.getMainJavaSourceRootAnalyzer().getTransitiveDependencies());
            // We sort these transitive modules in the order explained above (most dependent modules first, starting
            // with the executable module). Configuration values will be considered only once in the merge, i.e. the
            // first time they will appear in that order, and the consequent occurrences will be commented in the merged
            // configuration file.
            List<Module> sortedModules = TopologicalSort.sortDesc(dependencyGraph);

            List<Pair<ProjectModule, Config>> moduleConfigs = new ArrayList<>();
            boolean[] moduleConfigsContainsArrays = { false };

            sortedModules.forEach(m -> {
                if (m instanceof ProjectModule) {
                    ProjectModule pm = (ProjectModule) m;
                    if (pm.hasMainWebFxSourceDirectory()) {
                        Path webfxConfDirectory = pm.getMainWebFxSourceDirectory().resolve("conf/");
                        if (Files.isDirectory(webfxConfDirectory)) {
                            ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxConfDirectory))
                                    .filter(PROPERTIES_FILE_MATCHER::matches)
                                    .forEach(path -> {
                                        Config config = configCache.get(path);
                                        if (config == null) {
                                            String fileContent = TextFileReaderWriter.readTextFile(path);
                                            config = ConfigParser.parseConfigFile(fileContent, path.toAbsolutePath().toString());
                                            configCache.put(path, config);
                                        }
                                        if (!config.isEmpty()) {
                                            moduleConfigs.add(new Pair<>(pm, config));
                                            moduleConfigsContainsArrays[0] |= hasArray(config);
                                        }
                                    });
                        }
                    }
                }
            });

            Path propertiesPath = module.getMainResourcesDirectory().resolve(SourcesConfig.PROPERTIES_SRC_ROOT_CONF_RESOURCE_FILE_PATH);
            Path jsonPath = module.getMainResourcesDirectory().resolve(SourcesConfig.JSON_SRC_ROOT_CONF_RESOURCE_FILE_PATH);
            Path selectedPath = null;
            if (!moduleConfigs.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                String[] lastModuleHeader = { null };
                if (!moduleConfigsContainsArrays[0]) {
                    selectedPath = propertiesPath;
                    sb.append("# File managed by WebFX (DO NOT EDIT MANUALLY)\n");
                    for (Pair<ProjectModule, Config> moduleConfig : moduleConfigs) {
                        appendAstObjectToProperies(null, moduleConfig.get2(), sb, lastModuleHeader, moduleConfig.get1().getName());
                    }
                } else {
                    selectedPath = jsonPath;
                    Config config = ConfigMerger.mergeConfigs(moduleConfigs.stream().map(Pair::get2).toArray(Config[]::new));
                    sb.append(AstFormatter.formatObject(config, "json"));
                }
                TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), selectedPath);
            }
            if (selectedPath != propertiesPath)
                TextFileReaderWriter.deleteTextFile(propertiesPath);
            if (selectedPath != jsonPath)
                TextFileReaderWriter.deleteTextFile(jsonPath);
        }
    }

    private static boolean hasArray(ReadOnlyKeyObject config) {
        for (Object key : config.keys()) {
            Object value = config.get(key.toString());
            if (value instanceof ReadOnlyIndexedArray)
                return true;
            if (value instanceof ReadOnlyKeyObject)
                return hasArray((ReadOnlyKeyObject) value);
        }
        return false;
    }

    private static void appendAstObjectToProperies(String prefix, ReadOnlyKeyObject config, StringBuilder sb, String[] lastModuleHeader, String moduleName) {
        ReadOnlyIndexedArray keys = config.keys();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.getString(i);
            Object o = config.get(key);
            String newPrefix = prefix == null ? key : prefix + '.' + key;
            if (o instanceof ReadOnlyKeyObject)
                appendAstObjectToProperies(newPrefix, (ReadOnlyKeyObject) o, sb, lastModuleHeader, moduleName);
            else {
                if (!moduleName.equals(lastModuleHeader[0])) {
                    sb.append("\n# From ").append(moduleName).append('\n');
                    lastModuleHeader[0] = moduleName;
                }
                if (sb.toString().contains("\n" + newPrefix + " = "))
                    sb.append('#');
                sb.append(newPrefix).append(" = ").append(o).append(('\n'));
            }
        }
    }
}