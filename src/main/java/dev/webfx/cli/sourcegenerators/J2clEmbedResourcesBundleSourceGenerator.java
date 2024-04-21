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
final class J2clEmbedResourcesBundleSourceGenerator {

    static final String GENERATED_PROVIDER_CLASS_NAME = "dev.webfx.platform.resource.j2cl.J2clEmbedResourcesBundle$ProvidedJ2clResourceBundle";
    private static final String GENERATED_PROVIDER_JAVA_FILE = "dev/webfx/platform/resource/j2cl/J2clEmbedResourcesBundle.java";

    static boolean generateJ2clClientBundleSource(DevProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module EmbedResourcesBundle super source for GWT");
        StringBuilder resourceDeclaration = new StringBuilder();
        StringBuilder resourceRegistration = new StringBuilder();
        AtomicInteger resourceNumber = new AtomicInteger();
        ProjectModule.filterProjectModules(module.getMainJavaSourceRootAnalyzer().getThisAndTransitiveModules())
                .flatMap(ProjectModule::getEmbedResources)
                .sorted()
                .forEach(r -> {
                    int rn = resourceNumber.incrementAndGet();
                    if (rn > 1) {
                        resourceDeclaration.append("\n\n");
                        resourceRegistration.append("\n");
                    }
                    String resourceMethodName = "r" + rn;
                    resourceDeclaration
                            .append("    @Source(\"/").append(r).append("\")\n")
                            .append("    TextResource ").append(resourceMethodName).append("();");
                    resourceRegistration
                            .append("            registerResource(\"").append(r).append("\", () -> R.").append(resourceMethodName).append("().getText());");
                });
        String source = resourceNumber.get() == 0 ? null // if no resource, setting the source to null so writeTextFile() will actually delete the file if exists
                : ResourceTextFileReader.readTemplate("J2clEmbedResourcesBundle.javat")
                        .replace("${resourceDeclaration}", resourceDeclaration)
                        .replace("${resourceRegistration}", resourceRegistration);
        TextFileReaderWriter.writeTextFileIfNewOrModified(source, getJavaFilePath(module));
        return source != null;
    }

    static Path getJavaFilePath(DevProjectModule module) {
        return module.getMainResourcesDirectory().resolve(GENERATED_PROVIDER_JAVA_FILE);
    }

    static String getProviderClassName() {
        return GENERATED_PROVIDER_CLASS_NAME;
    }
}
