package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.platform.ast.ReadOnlyAstArray;
import dev.webfx.platform.conf.Config;
import dev.webfx.platform.conf.ConfigParser;
import dev.webfx.platform.util.tuples.Pair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class I18nFilesGenerator {

    private final static Map<Path, Config> I18N_CACHE = new HashMap<>(); // We assume the CLI exits after the update commande, so no need to clear that cache

    public static void generateExecutableModuleI18nResourceFiles(DevProjectModule module, boolean canUseCache) {
        if (module.isExecutable()) {
            // We will collect here all configurations from all transitive modules and merge them into a single
            // configuration file. The order is important when doing that merge, because configuration values can be
            // overridden. In that case, the actual final value to consider among the different modules defining that
            // same configuration value is the one defined in the top-most module in the dependency graph (the executable
            // module being at the very top of that graph).

            // I18n Initialisation
            Map<String, ConfigMerge> i18nMerges = new HashMap<>();

            Map<String, Path> moduleWebFxPaths = module.collectThisAndTransitiveWebFXPaths(canUseCache);

            moduleWebFxPaths.forEach((moduleName, webfxPath) -> {
                // I8n collection
                Path webfxI18nDirectory = webfxPath.resolve("i18n/");
                if (Files.isDirectory(webfxI18nDirectory)) {
                    ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxI18nDirectory))
                            .filter(AstUtil.AST_FILE_MATCHER::matches)
                            .forEach(path -> {
                                Config i18nObject = I18N_CACHE.get(path);
                                if (i18nObject == null) {
                                    String fileContent = TextFileReaderWriter.readTextFile(path);
                                    i18nObject = ConfigParser.parseConfigFile(fileContent, path.getFileName().toString());
                                    I18N_CACHE.put(path, i18nObject);
                                }
                                ReadOnlyAstArray languages = i18nObject.keys();
                                for (int i = 0; i < languages.size(); i++) {
                                    String language = languages.getString(i);
                                    ConfigMerge languageMerge = i18nMerges.get(language);
                                    if (languageMerge == null)
                                        i18nMerges.put(language, languageMerge = new ConfigMerge());
                                    Config dictionaryConfig = i18nObject.childConfigAt(language);
                                    languageMerge.moduleConfigs.add(new Pair<>(moduleName, dictionaryConfig));
                                    languageMerge.moduleConfigsContainsArrays |= AstUtil.hasArray(dictionaryConfig);
                                }
                            });
                }
            });

            // I18n merge
            i18nMerges.forEach((language, languageMerge) -> {
                Path i18nPropertiesPath = module.getMainResourcesDirectory().resolve("dev/webfx/stack/i18n/" + language + ".properties");
                Path i18nJsonPath = module.getMainResourcesDirectory().resolve("dev/webfx/stack/i18n/" + language + ".json");
                languageMerge.mergeConfigs(i18nPropertiesPath, i18nJsonPath);
            });
        }
    }


}