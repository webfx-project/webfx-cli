package dev.webfx.cli.sourcegenerators;

import dev.webfx.cli.commands.UpdateTasks;
import dev.webfx.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public final class GwtJ2clFilesGenerator {

    public static void generateGwtJ2clFiles(DevProjectModule module, UpdateTasks tasks) {
        if (tasks.indexHtml) { // Common to GWT & J2CL
            tasks.indexHtmlStopWatch.on();
            if (module.getWebHtmlFile().writeFile())
                tasks.indexHtmlCount++;
            tasks.indexHtmlStopWatch.off();
        }
        if (module.getBuildInfo().isForJ2cl) { // J2CL only
            if (tasks.embedResource) {
                tasks.embedResourceStopWatch.on();
                if (J2clEmbedResourcesBundleSourceGenerator.generateJ2clClientBundleSource(module))
                    tasks.embedResourceCount++;
                tasks.embedResourceStopWatch.off();
            }
            if (tasks.entryPoint) {
                tasks.entryPointStopWatch.on();
                GwtJ2clEntryPointSourceGenerator.generateJ2clEntryPointSource(module);
                tasks.entryPointCount++;
                tasks.entryPointStopWatch.off();
            }
        } else { // GWT only
            if (tasks.embedResource) {
                tasks.embedResourceStopWatch.on();
                if (GwtEmbedResourcesBundleSourceGenerator.generateGwtClientBundleSource(module))
                    tasks.embedResourceCount++;
                tasks.embedResourceStopWatch.off();
            }
            if (tasks.entryPoint) {
                tasks.entryPointStopWatch.on();
                GwtJ2clEntryPointSourceGenerator.generateGwtEntryPointSource(module);
                tasks.entryPointCount++;
                tasks.entryPointStopWatch.off();
            }
            if (tasks.gwtXml) {
                tasks.gwtXmlStopWatch.on();
                if (module.getGwtModuleFile().writeFile())
                    tasks.gwtXmlCount++;
                tasks.gwtXmlStopWatch.off();
            }
            if (tasks.callbacks) {
                tasks.callbacksStopWatch.on();
                if (GwtWebToJavaCallbacksGenerator.generateWebToJavaCallbacksSuperSource(module))
                    tasks.callbacksCount++;
                tasks.callbacksStopWatch.off();
            }
        }
    }

}
