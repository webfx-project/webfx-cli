package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.commands.UpdateTasks;
import dev.webfx.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public class TeaVMFilesGenerator {

    public static void generateTeaVMFiles(DevProjectModule module, UpdateTasks tasks) {
        if (tasks.indexHtml) {
            tasks.indexHtmlStopWatch.on();
            if (module.getWebHtmlFile().writeFile())
                tasks.indexHtmlCount++;
            tasks.indexHtmlStopWatch.off();
        }
        if (tasks.embedResource) {
            tasks.embedResourceStopWatch.on();
            if (TeaVMEmbedResourcesBundleSourceGenerator.generateTeaVMEmbedResourceBundleSource(module))
                tasks.embedResourceCount++;
            tasks.embedResourceStopWatch.off();
        }
    }

}
