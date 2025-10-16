package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bruno Salmon
 */
public final class TeaVMEmbedResourcesBundleSourceGenerator {

    private static final String GENERATED_PROVIDER_CLASS_NAME = "dev.webfx.platform.resource.teavm.TeaVMEmbedResourcesBundle";
    private static final String GENERATED_PROVIDER_JAVA_FILE = "dev/webfx/platform/resource/teavm/TeaVMEmbedResourcesBundle.java";


    static boolean generateTeaVMEmbedResourceBundleSource(DevProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module EmbedResourcesBundle super source for GWT");
        StringBuilder resourceDeclaration = new StringBuilder();
        ProjectModule.filterProjectModules(module.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules())
                .flatMap(ProjectModule::getEmbedResources)
                .sorted()
                .forEach(r -> resourceDeclaration.append("        \"").append(r).append("\",\n"));
        String source = ResourceTextFileReader.readTemplate("TeaVMEmbedResourcesBundle.javat")
                        .replace("${resourceDeclaration}",
                            // Removing the last comma and line feed
                            resourceDeclaration.substring(0, resourceDeclaration.length() - 2));
        TextFileReaderWriter.writeTextFileIfNewOrModified(source, getJavaFilePath(module));
        return true;
    }

    static Path getJavaFilePath(DevProjectModule module) {
        return module.getMainJavaSourceDirectory().resolve(GENERATED_PROVIDER_JAVA_FILE);
    }

    public static String getProviderClassName() {
        return GENERATED_PROVIDER_CLASS_NAME;
    }
}
