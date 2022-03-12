package dev.webfx.buildtool.sourcegenerators;

import dev.webfx.buildtool.LocalProjectModule;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bruno Salmon
 */
final class GwtEmbedResourcesBundleSourceGenerator {

    static void generateGwtClientBundleSource(LocalProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module EmbedResourcesBundle super source for GWT");
        StringBuilder resourceDeclaration = new StringBuilder();
        StringBuilder resourceRegistration = new StringBuilder();
        AtomicInteger resourceNumber = new AtomicInteger();
        LocalProjectModule.filterLocalProjectModules(module.getThisAndTransitiveModules())
                .flatMap(LocalProjectModule::getEmbedResources)
                .stream().sorted()
                .forEach(r -> {
                    String resourceMethodName = "r" + resourceNumber.incrementAndGet();
                    resourceDeclaration
                            .append("    @Source(\"").append(r).append("\")\n")
                            .append("    TextResource ").append(resourceMethodName).append("();\n\n");
                    resourceRegistration
                            .append("            registerResource(\"").append(r).append("\", R.").append(resourceMethodName).append("());\n");
                });
        String packageName = getPackageName(module);
        String source = resourceNumber.get() == 0 ? null // if no resource, setting the source to null so writeTextFile() will actually delete the file if exists
                : ResourceTextFileReader.readTemplate("EmbedResourcesBundle.java")
                        .replace("${package}", packageName)
                        .replace("${resourceDeclaration}", resourceDeclaration)
                        .replace("${resourceRegistration}", resourceRegistration);
        TextFileReaderWriter.writeTextFileIfNewOrModified(source, getJavaFilePath(module));
    }

    static String getPackageName(LocalProjectModule module) {
        return module.getJavaModuleFile().getJavaModuleName() + ".embed";
    }

    static Path getJavaFilePath(LocalProjectModule module) {
        return module.getResourcesDirectory().resolve("super").resolve(getPackageName(module).replaceAll("\\.", "/")).resolve("EmbedResourcesBundle.java");
    }

    static String getProviderClassName(LocalProjectModule module) {
        return getPackageName(module)+ ".EmbedResourcesBundle$ProvidedGwtResourceBundle";
    }
}
