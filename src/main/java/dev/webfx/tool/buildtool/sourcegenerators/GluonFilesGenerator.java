package dev.webfx.tool.buildtool.sourcegenerators;

import dev.webfx.tool.buildtool.DevProjectModule;
import dev.webfx.tool.buildtool.ProjectModule;
import dev.webfx.tool.buildtool.util.textfile.TextFileReaderWriter;

/**
 * @author Bruno Salmon
 */
public final class GluonFilesGenerator {

    static void generateServiceLoaderSuperSource(DevProjectModule module) {
        module.getProvidedJavaServices()
                .forEach(service -> {
                    StringBuilder sb = new StringBuilder();
                    module.getProvidedJavaServiceImplementations(service, false)
                            .forEach(providerClassName ->
                                    sb.append(providerClassName).append('\n')
                            );
                    TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), module.getResourcesDirectory().resolve("META-INF/services/" + service));
                });
    }

    public static void generateGraalVmReflectionJson(DevProjectModule gluonModule) {
        StringBuilder sb = new StringBuilder();
        ProjectModule.filterProjectModules(gluonModule.getTransitiveModules())
                .forEach(module -> {
                    String json = module.getWebFxModuleFile().getGraalVmReflectionJson();
                    if (json != null) {
                        json = removeLineFeeds(json, true);
                        if (json.startsWith("["))
                            json = json.substring(1);
                        if (json.endsWith("]"))
                            json = json.substring(0, json.length() - 1);
                        json = removeLineFeeds(json, false);
                        if (!json.isEmpty()) {
                            if (sb.length() > 0)
                                sb.append(",");
                            sb.append(json);
                        }
                    }
                });
        TextFileReaderWriter.writeTextFileIfNewOrModified(sb.length() == 0 ? null : "[\n" + sb + "\n]", gluonModule.getHomeDirectory().resolve("src/main/graalvm_conf/reflection.json"));
    }

    private static String removeLineFeeds(String json, boolean includeWhiteSpaces) {
        while (!json.isEmpty() && (json.startsWith("\n") || includeWhiteSpaces && Character.isWhitespace(json.charAt(0))))
            json = json.substring(1);
        while (!json.isEmpty() && (json.endsWith("\n") || includeWhiteSpaces && Character.isWhitespace(json.charAt(json.length() - 1))))
            json = json.substring(0, json.length() - 1);
        return json;
    }

}