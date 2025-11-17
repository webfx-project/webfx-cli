package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.commands.UpdateTasks;
import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.TargetTag;

/**
 * @author Bruno Salmon
 */
public class TeaVMFilesGenerator {

    public static void generateTeaVMFiles(DevProjectModule module, UpdateTasks tasks) {
        // No index.html or embed resource for web workers
        if (module.getTarget().hasTag(TargetTag.WORKERTHREAD))
            return;

        if (tasks.indexHtml && !module.getTarget().hasTag(TargetTag.WORKERTHREAD)) {
            tasks.indexHtmlStopWatch.on();
            if (module.getIndexHtmlFile().writeFile())
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
