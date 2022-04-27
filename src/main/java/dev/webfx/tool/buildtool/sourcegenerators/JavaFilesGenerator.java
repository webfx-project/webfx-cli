package dev.webfx.tool.buildtool.sourcegenerators;

import dev.webfx.tool.buildtool.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public class JavaFilesGenerator {

    public static void generateJavaFiles(DevProjectModule module) {
        // Generating module-info.java for this module
        module.getJavaModuleFile().writeFile();
        // Generating META-INF/services/ files
        // Note: this is the old way of declaring services (new way is in module-info.java) but still required for GraalVM
        // => To be removed as soon as GraalVM supports the new wat of declaring services
        GluonFilesGenerator.generateServiceLoaderSuperSource(module);
    }
}
