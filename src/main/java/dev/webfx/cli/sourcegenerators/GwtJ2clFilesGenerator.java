package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public final class GwtJ2clFilesGenerator {

    public static void generateGwtJ2clFiles(DevProjectModule module) {
        module.getGwtHtmlFile().writeFile();
        if (module.getBuildInfo().isForJ2cl) {
            J2clEmbedResourcesBundleSourceGenerator.generateJ2clClientBundleSource(module);
            GwtJ2clEntryPointSourceGenerator.generateJ2clEntryPointSource(module);
        } else { // GWT
            GwtEmbedResourcesBundleSourceGenerator.generateGwtClientBundleSource(module);
            GwtJ2clEntryPointSourceGenerator.generateGwtEntryPointSource(module);
            module.getGwtModuleFile().writeFile();
        }
    }

}
