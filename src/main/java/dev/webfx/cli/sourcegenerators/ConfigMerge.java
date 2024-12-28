package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.Logger;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.platform.ast.AST;
import dev.webfx.platform.ast.ReadOnlyAstArray;
import dev.webfx.platform.ast.ReadOnlyAstObject;
import dev.webfx.platform.ast.json.Json;
import dev.webfx.platform.conf.Config;
import dev.webfx.platform.conf.impl.ConfigMerger;
import dev.webfx.platform.util.collection.Collections;
import dev.webfx.platform.util.collection.HashList;
import dev.webfx.platform.util.collection.ToStringOptions;
import dev.webfx.platform.util.tuples.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Bruno Salmon
 */
public final class ConfigMerge {

    final List<Pair<String /* module name */, Config>> moduleConfigs = new ArrayList<>();
    boolean moduleConfigsContainsArrays;

    private static final Map<String, List<String>> KEYS_MODULES = new HashMap<>();

    boolean mergeConfigs(Path propertiesPath, Path jsonPath) {
        //  Configuration values will be considered only once in the merge, i.e. the first time they will appear in that
        //  order, and the consequent occurrences will be commented in the merged configuration file.
        boolean generatedFiles = false;
        Path selectedPath = null;
        if (!moduleConfigs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String[] lastModuleHeader = { null };
            if (!moduleConfigsContainsArrays) {
                selectedPath = propertiesPath;
                for (Pair<String, Config> moduleConfig : moduleConfigs) {
                    appendAstObjectToProperties(null, moduleConfig.get2(), sb, lastModuleHeader, moduleConfig.get1());
                }
            } else {
                selectedPath = jsonPath;
                Config config = ConfigMerger.mergeConfigs(moduleConfigs.stream().map(Pair::get2).toArray(Config[]::new));
                sb.append(Json.formatNode(config));
            }
            if (sb.length() == 0)
                selectedPath = null; // In order to delete the conf files (see below)
            else {
                TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), selectedPath);
                generatedFiles = true;
                // Duplicate keys detection (accumulated among all merges) => logDuplicatedKeysWarnings() will display them
                for (Pair<String, Config> moduleConfig : moduleConfigs) {
                    String moduleName = moduleConfig.get1();
                    Config config = moduleConfig.get2();
                    config.keys().forEach(key -> {
                        String sKey = key.toString();
                        List<String> modules = KEYS_MODULES.computeIfAbsent(sKey, k -> new HashList<>());
                        modules.add(moduleName);
                    });
                }
            }
        }
        if (selectedPath != propertiesPath)
            TextFileReaderWriter.deleteTextFile(propertiesPath);
        if (selectedPath != jsonPath)
            TextFileReaderWriter.deleteTextFile(jsonPath);
        return generatedFiles;
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
                if (sb.toString().contains("\n" + propertiesKey + " = ")) {
                    sb.append('#');
                }
                sb.append(propertiesKey).append(" = ");
                String propertiesValue = o == null ? null : o.toString().replace("\n", "\\n");
                if (propertiesValue == null) {
                    Logger.warning("Null value for key " + propertiesKey + " in module " + moduleName);
                    sb.append("null");
                } else
                    if (!propertiesValue.startsWith(" ") && !propertiesValue.endsWith(" ")) {
                        sb.append(propertiesValue);
                    } else {
                        sb.append(propertiesValue.replace(" ", "\\u0020"));
                    }
                sb.append(('\n'));
            }
        }
    }

    public static void logDuplicatedKeysWarnings() {
        KEYS_MODULES.forEach((sKey, modules) -> {
            if (modules.size() > 1) {
                Logger.warning("Duplicated key " + sKey + " used by " + Collections.toString(modules, ToStringOptions.COMMA_SEPARATED_TO_STRING_OPTIONS));
            }
        });
    }

}
