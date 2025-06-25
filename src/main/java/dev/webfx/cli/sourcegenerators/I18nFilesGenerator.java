package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.util.hashlist.HashList;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.cli.util.stopwatch.StopWatch;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.platform.ast.ReadOnlyAstArray;
import dev.webfx.platform.ast.ReadOnlyAstObject;
import dev.webfx.platform.conf.Config;
import dev.webfx.platform.conf.ConfigParser;
import dev.webfx.platform.util.collection.Collections;
import dev.webfx.platform.util.tuples.Pair;

import javax.lang.model.SourceVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class I18nFilesGenerator {

    private final static Map<Path, Config> I18N_CACHE = new HashMap<>(); // We assume the CLI exits after the update command, so no need to clear that cache

    public static int generateExecutableModuleI18nResourceFiles(DevProjectModule module, boolean canUseCache, StopWatch mergePrepStopWatch) {
        if (!module.isExecutable())
            return 0;
        // We will collect here all configurations from all transitive modules and merge them into a single
        // configuration file. The order is important when doing that merge because configuration values can be
        // overridden. In that case, the actual final value to consider among the different modules defining that
        // same configuration value is the one defined in the top-most module of the dependency graph. The executable
        // module is indeed at the very top of that graph.

        // I18n Initialisation
        Map<String, ConfigMerge> i18nMerges = new HashMap<>();

        Map<String, Path> moduleWebFxPaths = module.collectThisAndTransitiveWebFXPaths(canUseCache, false, mergePrepStopWatch);

        moduleWebFxPaths.forEach((moduleName, webfxPath) -> collectI18nDictionaries(moduleName, webfxPath, i18nMerges));

        int[] filesCount = { 0 };
        // I18n merge
        i18nMerges.forEach((language, languageMerge) -> {
            Path i18nPropertiesPath = module.getMainResourcesDirectory().resolve("dev/webfx/stack/i18n/" + language + ".properties");
            Path i18nJsonPath = module.getMainResourcesDirectory().resolve("dev/webfx/stack/i18n/" + language + ".json");
            if (languageMerge.mergeConfigs(i18nPropertiesPath, i18nJsonPath))
                filesCount[0]++;
        });
        return filesCount[0];
    }

    public static boolean generateI18nModuleJavaKeys(DevProjectModule module) {
        String javaKeysClass = module.getWebFxModuleFile().getI18nJavaKeysClass();
        if (javaKeysClass == null || !module.hasMainWebFxSourceDirectory())
            return false;

        // I18n Initialisation
        Map<String, ConfigMerge> i18nMerges = new HashMap<>();

        collectI18nDictionaries(module.getName(), module.getMainWebFxSourceDirectory(), i18nMerges);

        HashList<String> keys = new HashList<>();
        i18nMerges.forEach((language, languageMerge) -> {
            languageMerge.moduleConfigs.forEach(pair -> pair.get2().keys().forEach(key -> keys.add(key.toString())));
        });

        if (keys.isEmpty())
            return false;

        StringBuilder sb = new StringBuilder();
        keys.forEach(key -> {
            if (sb.length() > 0)
                sb.append("\n");
            if (!SourceVersion.isName(key) || key.contains(".")) // Commenting keys that are not valid java names
                sb.append("//");
            sb.append("    String ").append(key).append(" = \"").append(key).append("\";");
        });

        Path javaFilePath = module.getMainJavaSourceDirectory().resolve(javaKeysClass.replace('.', '/') + ".java");
        int lastDotIndex = javaKeysClass.lastIndexOf('.');
        String content = ResourceTextFileReader.readTemplate("I18nKeys.javat")
                .replace("${package}", javaKeysClass.substring(0, lastDotIndex))
                .replace("${class}", javaKeysClass.substring(lastDotIndex + 1))
                .replace("${i18nKeysDeclaration}", sb);
        TextFileReaderWriter.writeTextFileIfNewOrModified(content, javaFilePath);

        // Generating a warning if some keys are not translated
        i18nMerges.forEach((language, languageMerge) -> {
            List<String> untranslatedKeys = Collections.filter(keys, key -> Collections.noneMatch(languageMerge.moduleConfigs, pair -> {
                Object value = pair.get2().get(key);
                if (value instanceof String)
                    return true;
                if (value instanceof ReadOnlyAstObject) {
                    ReadOnlyAstObject object = (ReadOnlyAstObject) value;
                    return object.has("text");
                }
                return false;
            }));
            if (!untranslatedKeys.isEmpty()) {
                Logger.warning("The following keys are not translated in the '" + language + "' dictionary for module " + module.getName() + ": " + untranslatedKeys);
            }
        });
        return true;
    }

    private static void collectI18nDictionaries(String moduleName, Path webfxPath, Map<String, ConfigMerge> i18nMerges) {
        Path webfxI18nDirectory = webfxPath.resolve("i18n/");
        if (Files.isDirectory(webfxI18nDirectory)) {
            ReusableStream.create(() -> SplitFiles.uncheckedWalk(webfxI18nDirectory))
                .filter(AstUtil.AST_FILE_MATCHER::matches)
                .forEach(path -> {
                    Config i18nObject = I18N_CACHE.get(path);
                    if (i18nObject == null) {
                        String fileContent = TextFileReaderWriter.readInputTextFile(path);
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
    }

}