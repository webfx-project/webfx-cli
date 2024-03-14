package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.ModuleDependency;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.sort.TopologicalSort;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.platform.ast.AST;
import dev.webfx.platform.ast.ReadOnlyAstArray;
import dev.webfx.platform.ast.ReadOnlyAstObject;
import dev.webfx.platform.ast.json.Json;
import dev.webfx.platform.conf.Config;
import dev.webfx.platform.conf.ConfigParser;
import dev.webfx.platform.conf.SourcesConfig;
import dev.webfx.platform.conf.impl.ConfigMerger;
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

    private final static PathMatcher AST_FILE_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.{properties,json}");

    private final static Map<Path, Config> configCache = new HashMap<>(); // We assume the CLI exits after the update commande, so no need to clear that cache
    private final static Map<Path, Config> i18nCache = new HashMap<>(); // We assume the CLI exits after the update commande, so no need to clear that cache

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

            // Conf Initialisation
            ConfigMerge confMerge = new ConfigMerge();
            // I18n Initialisation
            Map<String, ConfigMerge> i18nMerges = new HashMap<>();

            sortedModules.forEach(m -> {
                if (m instanceof ProjectModule) {
                    ProjectModule pm = (ProjectModule) m;
                    if (pm.hasMainWebFxSourceDirectory()) {
                        // Conf collection
                        Path webfxConfDirectory = pm.getMainWebFxSourceDirectory().resolve("conf/");
                        if (Files.isDirectory(webfxConfDirectory)) {
                            ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxConfDirectory))
                                    .filter(AST_FILE_MATCHER::matches)
                                    .forEach(path -> {
                                        Config config = configCache.get(path);
                                        if (config == null) {
                                            String fileContent = TextFileReaderWriter.readTextFile(path);
                                            config = ConfigParser.parseConfigFile(fileContent, path.toAbsolutePath().toString());
                                            configCache.put(path, config);
                                        }
                                        if (!config.isEmpty()) {
                                            confMerge.moduleConfigs.add(new Pair<>(pm, config));
                                            confMerge.moduleConfigsContainsArrays |= hasArray(config);
                                        }
                                    });
                        }
                        // I8n collection
                        Path webfxI18nDirectory = pm.getMainWebFxSourceDirectory().resolve("i18n/");
                        if (Files.isDirectory(webfxI18nDirectory)) {
                            ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxI18nDirectory))
                                    .filter(AST_FILE_MATCHER::matches)
                                    .forEach(path -> {
                                        Config i18nObject = i18nCache.get(path);
                                        if (i18nObject == null) {
                                            String fileContent = TextFileReaderWriter.readTextFile(path);
                                            i18nObject = ConfigParser.parseConfigFile(fileContent, path.getFileName().toString());
                                            i18nCache.put(path, i18nObject);
                                        }
                                        ReadOnlyAstArray languages = i18nObject.keys();
                                        for (int i = 0; i < languages.size(); i++) {
                                            String language = languages.getString(i);
                                            ConfigMerge languageMerge = i18nMerges.get(language);
                                            if (languageMerge == null)
                                                i18nMerges.put(language, languageMerge = new ConfigMerge());
                                            Config dictionaryConfig = i18nObject.childConfigAt(language);
                                            languageMerge.moduleConfigs.add(new Pair<>(pm, dictionaryConfig));
                                            languageMerge.moduleConfigsContainsArrays |= hasArray(dictionaryConfig);
                                        }
                                    });
                        }
                    }
                }
            });

            // Conf merge
            Path propertiesPath = module.getMainResourcesDirectory().resolve(SourcesConfig.PROPERTIES_SRC_ROOT_CONF_RESOURCE_FILE_PATH);
            Path jsonPath = module.getMainResourcesDirectory().resolve(SourcesConfig.JSON_SRC_ROOT_CONF_RESOURCE_FILE_PATH);
            mergeConfigs(confMerge, propertiesPath, jsonPath);

            // I18n merge
            i18nMerges.forEach((language, languageMerge) -> {
                Path i18nPropertiesPath = module.getMainResourcesDirectory().resolve("dev/webfx/stack/i18n/" + language + ".properties");
                Path i18nJsonPath = module.getMainResourcesDirectory().resolve("dev/webfx/stack/i18n/" + language + ".json");
                mergeConfigs(languageMerge, i18nPropertiesPath, i18nJsonPath);
            });
        }
    }

    private static void mergeConfigs(ConfigMerge configMerge, Path propertiesPath, Path jsonPath) {
        Path selectedPath = null;
        if (!configMerge.moduleConfigs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String[] lastModuleHeader = { null };
            if (!configMerge.moduleConfigsContainsArrays) {
                selectedPath = propertiesPath;
                for (Pair<ProjectModule, Config> moduleConfig : configMerge.moduleConfigs) {
                    appendAstObjectToProperties(null, moduleConfig.get2(), sb, lastModuleHeader, moduleConfig.get1().getName());
                }
            } else {
                selectedPath = jsonPath;
                Config config = ConfigMerger.mergeConfigs(configMerge.moduleConfigs.stream().map(Pair::get2).toArray(Config[]::new));
                sb.append(Json.formatNode(config));
            }
            if (sb.length() == 0)
                selectedPath = null; // In order to delete the conf files (see below)
            else
                TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), selectedPath);
        }
        if (selectedPath != propertiesPath)
            TextFileReaderWriter.deleteTextFile(propertiesPath);
        if (selectedPath != jsonPath)
            TextFileReaderWriter.deleteTextFile(jsonPath);
    }

    private static boolean hasArray(ReadOnlyAstObject astObject) {
        for (Object key : astObject.keys()) {
            Object value = astObject.get(key.toString());
            if (AST.isArray(value))
                return true;
            if (AST.isObject(value) && hasArray((ReadOnlyAstObject) value))
                return true;
        }
        return false;
    }

    private static void appendAstObjectToProperties(String prefix, ReadOnlyAstObject config, StringBuilder sb, String[] lastModuleHeader, String moduleName) {
        ReadOnlyAstArray keys = config.keys();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.getString(i);
            Object o = config.get(key);
            String newPrefix = prefix == null ? key : prefix + '.' + key;
            if (AST.isObject(o))
                appendAstObjectToProperties(newPrefix, (ReadOnlyAstObject) o, sb, lastModuleHeader, moduleName);
            else {
                if (sb.length() == 0)
                    sb.append("# File managed by WebFX (DO NOT EDIT MANUALLY)\n");
                if (!moduleName.equals(lastModuleHeader[0])) {
                    sb.append("\n# From ").append(moduleName).append('\n');
                    lastModuleHeader[0] = moduleName;
                }
                String propertiesKey = newPrefix.replace(":", "\\:").replace("=", "\\=");
                if (sb.toString().contains("\n" + propertiesKey + " = "))
                    sb.append('#');
                sb.append(propertiesKey).append(" = ");
                String propertiesValue = o.toString().replace("\n", "\\n");
                if (!propertiesValue.startsWith(" ") && !propertiesValue.endsWith(" ")) {
                    sb.append(propertiesValue);
                } else {
                    sb.append(propertiesValue.replace(" ", "\\u0020"));
                }
                sb.append(('\n'));
            }
        }
    }

    private static class ConfigMerge {
        private final List<Pair<ProjectModule, Config>> moduleConfigs = new ArrayList<>();
        private boolean moduleConfigsContainsArrays;
    }
}