package dev.webfx.buildtool.sourcegenerators;

import dev.webfx.buildtool.LocalProjectModule;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;

/**
 * @author Bruno Salmon
 */
final class GwtArraySuperSourceGenerator {

    static void generateArraySuperSource(LocalProjectModule module) {
        //GwtFilesGenerator.logSection("Generating " + module.getName() + " module java.lang.reflect.Array.java super source for GWT");
        StringBuilder sb = new StringBuilder();
        LocalProjectModule.filterLocalProjectModules(module.getTransitiveModules())
                .flatMap(m -> m.getWebFxModuleFile().getArrayNewInstanceClasses())
                .distinct()
                .stream().sorted()
                .forEach(className -> sb.append("            case \"").append(className).append("\": return new ").append(className).append("[length];\n"));
        TextFileReaderWriter.writeTextFileIfNewOrModified(
                ResourceTextFileReader.readTemplate("Array.java")
                        .replace("${generatedCasesCode}", sb),
                module.getResourcesDirectory().resolve("super/java/lang/reflect/Array.java"));
    }
}
