package dev.webfx.buildtool.sourcegenerators;

import dev.webfx.buildtool.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public final class GwtFilesGenerator {

    public static void generateGwtFiles(DevProjectModule module) {
        GwtEmbedResourcesBundleSourceGenerator.generateGwtClientBundleSource(module);
        GwtServiceLoaderSuperSourceGenerator.generateServiceLoaderSuperSource(module);
        GwtArraySuperSourceGenerator.generateArraySuperSource(module);
        module.getGwtModuleFile().writeFile();
        module.getGwtHtmlFile().writeFile();
    }

}
