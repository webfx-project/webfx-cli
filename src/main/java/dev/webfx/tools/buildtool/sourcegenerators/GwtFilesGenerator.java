package dev.webfx.tools.buildtool.sourcegenerators;

import dev.webfx.tools.buildtool.ProjectModule;

/**
 * @author Bruno Salmon
 */
public final class GwtFilesGenerator {

    public static void generateGwtFiles(ProjectModule module) {
        GwtEmbedResourcesBundleSourceGenerator.generateGwtClientBundleSource(module);
        GwtServiceLoaderSuperSourceGenerator.generateServiceLoaderSuperSource(module);
        GwtArraySuperSourceGenerator.generateArraySuperSource(module);
        module.getGwtModuleFile().writeFile();
        module.getGwtHtmlFile().writeFile();
    }

}
