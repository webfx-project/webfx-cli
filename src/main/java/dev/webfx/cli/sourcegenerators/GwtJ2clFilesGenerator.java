package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public final class GwtJ2clFilesGenerator {

    public static void generateGwtJ2clFiles(DevProjectModule module, boolean gwtXml, boolean indexHtml, boolean entryPoint, boolean embedResource) {
        if (indexHtml)
            module.getGwtJ2clHtmlFile().writeFile();
        if (module.getBuildInfo().isForJ2cl) {
            if (embedResource)
                J2clEmbedResourcesBundleSourceGenerator.generateJ2clClientBundleSource(module);
            if (entryPoint)
                GwtJ2clEntryPointSourceGenerator.generateJ2clEntryPointSource(module);
        } else { // GWT
            if (embedResource)
                GwtEmbedResourcesBundleSourceGenerator.generateGwtClientBundleSource(module);
            if (entryPoint)
                GwtJ2clEntryPointSourceGenerator.generateGwtEntryPointSource(module);
            if (gwtXml)
                module.getGwtModuleFile().writeFile();
        }
    }

}
