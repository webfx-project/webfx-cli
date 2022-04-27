package dev.webfx.tool.buildtool.sourcegenerators;

import dev.webfx.tool.buildtool.DevProjectModule;
import dev.webfx.tool.buildtool.ProjectModule;
import dev.webfx.tool.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.tool.buildtool.util.textfile.TextFileReaderWriter;

/**
 * @author Bruno Salmon
 */
final class GwtArraySuperSourceGenerator {

    static void generateArraySuperSource(DevProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module java.lang.reflect.Array.java super source for GWT");
        StringBuilder sb = new StringBuilder();
        ProjectModule.filterProjectModules(module.getTransitiveModules())
                .flatMap(m -> m.getWebFxModuleFile().getArrayNewInstanceClasses())
                .distinct()
                .sorted()
                .forEach(className -> sb.append("            case \"").append(className).append("\": return new ").append(className).append("[length];\n"));
        TextFileReaderWriter.writeTextFileIfNewOrModified(
                ResourceTextFileReader.readTemplate("Array.java")
                        .replace("${generatedCasesCode}", sb),
                module.getResourcesDirectory().resolve("super/java/lang/reflect/Array.java"));
    }
}
