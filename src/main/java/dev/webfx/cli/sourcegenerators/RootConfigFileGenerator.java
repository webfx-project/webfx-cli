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
import dev.webfx.platform.util.keyobject.ReadOnlyIndexedArray;
import dev.webfx.platform.util.keyobject.ReadOnlyKeyObject;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;

/**
 * @author Bruno Salmon
 */
public final class RootConfigFileGenerator {

    private final static PathMatcher PROPERTIES_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.properties");

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

            StringBuilder sb = new StringBuilder();
            sortedModules.forEach(m -> {
                if (m instanceof ProjectModule) {
                    ProjectModule pm = (ProjectModule) m;
                    boolean[] appendedHeader = { false };
                    pm.getWebFxModuleFile().getConfigurationVariables().forEach(mp -> {
                        if (!appendedHeader[0]) {
                            sb.append("\n# From ").append(pm.getName()).append('\n');
                            appendedHeader[0] = true;
                        }
                        sb.append(mp.getPropertyName()).append(" = ").append(mp.getPropertyValue()).append('\n');
                    });
                    if (pm.hasMainWebFxSourceDirectory()) {
                        Path webfxConfDirectory = pm.getMainWebFxSourceDirectory().resolve("conf/");
                        if (Files.isDirectory(webfxConfDirectory)) {
                            ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxConfDirectory))
                                    .filter(PROPERTIES_FILE_MATCHER::matches)
                                    .forEach(path -> {
                                        Config config = configCache.get(path);
                                        if (config == null) {
                                            String fileContent = TextFileReaderWriter.readTextFile(path);
                                            config = ConfigParser.parseFileConfig(fileContent, path.toAbsolutePath().toString());
                                            configCache.put(path, config);
                                        }
                                        appendKeyObject(null, config, sb, appendedHeader, pm.getName());
                                    });
                        }
                    }
                }
            });

            Path sourcesRootConfigResourcePath = module.getMainResourcesDirectory().resolve(SourcesConfig.SRC_ROOT_CONF_RESOURCE_FILE_PATH);
            if (sb.length() == 0)
                TextFileReaderWriter.deleteTextFile(sourcesRootConfigResourcePath);
            else
                TextFileReaderWriter.writeTextFileIfNewOrModified(
                        "# File managed by WebFX (DO NOT EDIT MANUALLY)\n" + sb
                        , sourcesRootConfigResourcePath);
        }
    }

    private static void appendKeyObject(String prefix, ReadOnlyKeyObject config, StringBuilder sb, boolean[] appendedHeader, String moduleName) {
        ReadOnlyIndexedArray keys = config.keys();
        for (int i = 0; i < keys.size(); i++) {
            if (!appendedHeader[0]) {
                sb.append("\n# From ").append(moduleName).append('\n');
                appendedHeader[0] = true;
            }
            String key = keys.getString(i);
            Object o = config.get(key);
            String newPrefix = prefix == null ? key : prefix + '.' + key;
            if (o instanceof ReadOnlyKeyObject)
                appendKeyObject(newPrefix, (ReadOnlyKeyObject) o, sb, appendedHeader, moduleName);
            else {
                if (sb.toString().contains("\n" + newPrefix + " = "))
                    sb.append('#');
                sb.append(newPrefix).append(" = ").append(o).append(('\n'));
            }
        }
    }

}


