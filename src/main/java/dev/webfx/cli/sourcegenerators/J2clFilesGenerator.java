package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.commands.UpdateTasks;
import dev.webfx.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public final class J2clFilesGenerator {

    public static void generateJ2clFiles(DevProjectModule module, UpdateTasks tasks) {
        if (tasks.indexHtml) {
            tasks.indexHtmlStopWatch.on();
            if (module.getIndexHtmlFile().writeFile())
                tasks.indexHtmlCount++;
            tasks.indexHtmlStopWatch.off();
        }
        if (tasks.embedResource) {
            tasks.embedResourceStopWatch.on();
            if (J2clEmbedResourcesBundleSourceGenerator.generateJ2clClientBundleSource(module))
                tasks.embedResourceCount++;
            tasks.embedResourceStopWatch.off();
        }
        if (tasks.entryPoint) {
            tasks.entryPointStopWatch.on();
            J2clEntryPointSourceGenerator.generateJ2clEntryPointSource(module);
            tasks.entryPointCount++;
            tasks.entryPointStopWatch.off();
        }
    }

}
