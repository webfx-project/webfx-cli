package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.platform.conf.Config;
import dev.webfx.platform.conf.ConfigParser;
import dev.webfx.platform.conf.SourcesConfig;
import dev.webfx.platform.util.tuples.Pair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class RootConfigFileGenerator {

    private final static Map<Path, Config> CONFIG_CACHE = new HashMap<>(); // We assume the CLI exits after the update commande, so no need to clear that cache

    public static boolean generateExecutableModuleConfigurationResourceFile(DevProjectModule module, boolean canUseCache) {
        if (!module.isExecutable())
            return false;
        // We will collect here all configurations from all transitive modules and merge them into a single
        // configuration file. The order is important when doing that merge, because configuration values can be
        // overridden. In that case, the actual final value to consider among the different modules defining that
        // same configuration value is the one defined in the top-most module in the dependency graph (the executable
        // module being at the very top of that graph).

        // Conf Initialisation
        ConfigMerge confMerge = new ConfigMerge();

        Map<String, Path> moduleWebFxPaths = module.collectThisAndTransitiveWebFXPaths(canUseCache);

        moduleWebFxPaths.forEach((moduleName, webfxPath) -> {
            // Conf collection
                Path webfxConfDirectory = webfxPath.resolve("conf/");
                if (Files.isDirectory(webfxConfDirectory)) {
                    ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxConfDirectory))
                            .filter(AstUtil.AST_FILE_MATCHER::matches)
                            .forEach(path -> {
                                Config config = CONFIG_CACHE.get(path);
                                if (config == null) {
                                    String fileContent = TextFileReaderWriter.readInputTextFile(path);
                                    config = ConfigParser.parseConfigFile(fileContent, path.toAbsolutePath().toString());
                                    CONFIG_CACHE.put(path, config);
                                }
                                if (!config.isEmpty()) {
                                    confMerge.moduleConfigs.add(new Pair<>(moduleName, config));
                                    confMerge.moduleConfigsContainsArrays |= AstUtil.hasArray(config);
                                }
                            });
                }
        });

        // Conf merge
        Path propertiesPath = module.getMainResourcesDirectory().resolve(SourcesConfig.PROPERTIES_SRC_ROOT_CONF_RESOURCE_FILE_PATH);
        Path jsonPath = module.getMainResourcesDirectory().resolve(SourcesConfig.JSON_SRC_ROOT_CONF_RESOURCE_FILE_PATH);
        return confMerge.mergeConfigs(propertiesPath, jsonPath);
    }
}