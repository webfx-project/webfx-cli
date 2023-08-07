package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;

import java.util.Objects;

/**
 * @author Bruno Salmon
 */
public final class GluonFilesGenerator {

    public static void generateGraalVmReflectionJson(DevProjectModule gluonModule) {
        StringBuilder sb = new StringBuilder();
        ProjectModule.filterProjectModules(gluonModule.getMainJavaSourceRootAnalyzer().getTransitiveModules())
                .map(module -> module.getWebFxModuleFile().getGraalVmReflectionJson())
                .filter(Objects::nonNull)
                .distinct()
                .forEach(json -> { // Json String (expecting a Json array [ ... ])
                    // Removing the possible first white spaces
                    json = json.replaceAll("\\n\\s+\\n", "\n");
                    // Removing the brackets, because all arrays are merged into 1
                    int openBracketIndex = json.indexOf('[');
                    int closeBracketIndex = json.lastIndexOf(']');
                    if (openBracketIndex > 0 && closeBracketIndex > 0) {
                        json = json.substring(openBracketIndex + 1, closeBracketIndex);
                        // Also removing left indentation (extra indentation can come from webfx.xml export)
                        json = json.replace("\n" + " ".repeat(openBracketIndex), "\n");
                    }
                    // Removing the possible last white spaces
                    json = json.replaceAll("(?m)\\n\\s+$", "\n");
                    if (!json.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(",");
                        sb.append(json);
                    }
                });
        TextFileReaderWriter.writeTextFileIfNewOrModified(sb.length() == 0 ? null : "[\n" + sb + "\n]", gluonModule.getHomeDirectory().resolve("src/main/graalvm_conf/reflection.json"));
    }

}
