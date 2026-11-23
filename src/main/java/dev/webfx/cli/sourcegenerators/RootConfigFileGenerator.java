package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.stopwatch.StopWatch;
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

    public static boolean generateExecutableModuleConfigurationResourceFile(DevProjectModule module, boolean canUseCache, StopWatch mergePrepStopWatch) {
        if (!module.isExecutable())
            return false;
        // We will collect here all configurations from all transitive modules and merge them into a single
        // configuration file. The order is important when doing that merge, because configuration values can be
        // overridden. In that case, the actual final value to consider among the different modules defining that
        // same configuration value is the one defined in the top-most module in the dependency graph (the executable
        // module being at the very top of that graph).

        // Conf Initialisation
        ConfigMerge confMerge = new ConfigMerge();

        Map<String, Path> moduleWebFxPaths = module.collectThisAndTransitiveWebFXPaths(canUseCache, false, mergePrepStopWatch);

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
                                    // Note: config might be null in some case (ex: empty yaml file or with comments only)
                                    CONFIG_CACHE.put(path, config);
                                }
                                if (config != null && !config.isEmpty()) {
                                    confMerge.moduleConfigs.add(new Pair<>(moduleName, config));
                                    confMerge.moduleConfigsContainsArrays |= AstUtil.hasArray(config);
                                }
                            });
                }
        });

        // Conf merge
        Path mainResourcesDirectory = module.getMainResourcesDirectory();

        // The generated file used to live directly in dev.webfx.platform.conf, i.e. in the very package that declares
        // SourcesConfig, so every executable module declared that package too and split it on the module path (boot
        // layer creation refused with LayerInstantiationException). It now lives in the .exe sub-package, mirroring
        // dev.webfx.platform.meta.exe.
        //
        // Deleting the file at its former location matters because leaving it there would keep the split package
        // alive, so the move would deliver nothing, and because it would be a frozen copy of the configuration as it
        // stood on the day of the move, which a platform older than the .exe location would go on reading. Emptying
        // the old folder of regular files is enough: a directory holding only the exe/ sub-directory declares no
        // package.
        //
        // A FULL `webfx update` would already remove it without this, by virtually deleting dev/webfx under every
        // executable's resources and garbage collecting whatever is not regenerated (Update.executeUpdateTasks). That
        // sweep is skipped when only some tasks are requested, which is exactly when a move would otherwise be left
        // half-done, so the deletion is stated here rather than relied upon there.
        TextFileReaderWriter.deleteTextFile(mainResourcesDirectory.resolve(SourcesConfig.PROPERTIES_SRC_ROOT_CONF_RESOURCE_FILE_PATH));
        TextFileReaderWriter.deleteTextFile(mainResourcesDirectory.resolve(SourcesConfig.JSON_SRC_ROOT_CONF_RESOURCE_FILE_PATH));

        Path propertiesPath = mainResourcesDirectory.resolve(SourcesConfig.PROPERTIES_SRC_ROOT_CONF_EXE_RESOURCE_FILE_PATH);
        Path jsonPath = mainResourcesDirectory.resolve(SourcesConfig.JSON_SRC_ROOT_CONF_EXE_RESOURCE_FILE_PATH);
        return confMerge.mergeConfigs(propertiesPath, jsonPath);
    }
}