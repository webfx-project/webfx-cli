package dev.webfx.tool.cli.sourcegenerators;

import dev.webfx.tool.cli.core.DevProjectModule;
import dev.webfx.tool.cli.core.ProjectModule;
import dev.webfx.tool.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.tool.cli.util.textfile.TextFileReaderWriter;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bruno Salmon
 */
final class GwtEmbedResourcesBundleSourceGenerator {

    static void generateGwtClientBundleSource(DevProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module EmbedResourcesBundle super source for GWT");
        StringBuilder resourceDeclaration = new StringBuilder();
        StringBuilder resourceRegistration = new StringBuilder();
        AtomicInteger resourceNumber = new AtomicInteger();
        ProjectModule.filterProjectModules(module.getThisAndTransitiveModules())
                .flatMap(ProjectModule::getEmbedResources)
                .sorted()
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
                : ResourceTextFileReader.readTemplate("EmbedResourcesBundle.javat")
                        .replace("${package}", packageName)
                        .replace("${resourceDeclaration}", resourceDeclaration)
                        .replace("${resourceRegistration}", resourceRegistration);
        TextFileReaderWriter.writeTextFileIfNewOrModified(source, getJavaFilePath(module));
    }

    static String getPackageName(DevProjectModule module) {
        return module.getJavaModuleFile().getJavaModuleName() + ".embed";
    }

    static Path getJavaFilePath(DevProjectModule module) {
        return module.getResourcesDirectory().resolve("super").resolve(getPackageName(module).replaceAll("\\.", "/")).resolve("EmbedResourcesBundle.java");
    }

    static String getProviderClassName(DevProjectModule module) {
        return getPackageName(module)+ ".EmbedResourcesBundle$ProvidedGwtResourceBundle";
    }
}
