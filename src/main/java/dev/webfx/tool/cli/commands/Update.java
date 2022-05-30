package dev.webfx.tool.cli.commands;

import dev.webfx.tool.cli.core.*;
import dev.webfx.tool.cli.sourcegenerators.GluonFilesGenerator;
import dev.webfx.tool.cli.sourcegenerators.GwtFilesGenerator;
import dev.webfx.tool.cli.sourcegenerators.JavaFilesGenerator;
import dev.webfx.tool.cli.util.textfile.TextFileThreadTransaction;
import dev.webfx.lib.reusablestream.ReusableStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * @author Bruno Salmon
 */
@Command(name = "update", description = "Update build files from webfx.xml files.")
public final class Update extends CommonSubcommand implements Runnable {

    @Option(names={"-o", "--only",}, arity = "1..*", description = "Run only the specified update tasks *.")
    String[] only;

    @Option(names={"-s", "--skip",}, arity = "1..*", description = "Skip the specified update tasks *.")
    String[] skip;

    private Boolean
            webfxXml,
            mavenPom,
            moduleInfoJava,
            metaInfServices,
            indexHtml,
            gwtXml,
            gwtSuperSources,
            gwtServiceLoader,
            gwtResourceBundles;

    private static final String[] TASK_WORDS = {
            "pom.xml",
            "module-info.java",
            "meta-inf/services",
            "index.html",
            "gwt.xml",
            "gwt-super-sources",
            "gwt-service-loader",
            "gwt-resource-bundles",
    };

    private static final char[] TASK_LETTERS = {
            'p', // mavenPom
            'j', // moduleInfoJava
            'm', // metaInfServices
            'h', // indexHtml
            'g', // gwtXml
            's', // gwtSuperSources
            'l', // gwtServiceLoader
            'b', // gwtResourceBundles
            'w', // webfx.xml
    };

    private void processTaskFlags(String[] flags, boolean value) {
        if (flags != null)
            for (String flag : flags)
                processTaskFlag(flag, value);
    }

    private void processTaskFlag(String flag, boolean value) {
        for (int taskIndex = 0; taskIndex < TASK_WORDS.length; taskIndex++)
            if (flag.equalsIgnoreCase(TASK_WORDS[taskIndex])) {
                enableTask(taskIndex, value);
                return;
            }
        if (!processTaskLetters(flag, value))
            throw new UnresolvedException("Unrecognized task " + flag);
    }

    private boolean processTaskLetters(String flag, boolean value) {
        for (int taskIndex = 0; taskIndex < TASK_WORDS.length; taskIndex++)
            if (flag.charAt(0) == TASK_LETTERS[taskIndex]) {
                enableTask(taskIndex, value);
                if (flag.length() > 1)
                    return processTaskLetters(flag.substring(1), value);
                return true;
            }
        return false;
    }

    private boolean enableTask(int taskIndex, boolean value) {
        switch (taskIndex) {
            case 0: return mavenPom = value;
            case 1: return moduleInfoJava = value;
            case 2: return metaInfServices = value;
            case 3: return indexHtml = value;
            case 4: return gwtXml = value;
            case 5: return gwtSuperSources = value;
            case 6: return gwtServiceLoader = value;
            case 7: return gwtResourceBundles = value;
            case 8: return webfxXml = value;
        }
        return false;
    }

    @Override
    public void run() {
        setUpLogger();

        for (int i = 0; i < TASK_LETTERS.length; i++)
            enableTask(i, only == null);
        processTaskFlags(only, true);
        processTaskFlags(skip, false);

        try (TextFileThreadTransaction transaction = TextFileThreadTransaction.open()) {

            DevProjectModule workingModule = getWorkingDevProjectModule();

            // Update webfx.xml if the working file is a root file
            getWorkingAndChildrenModules(workingModule)
                    .filter(RootModule.class::isInstance)
                    .forEach(m -> m.getWebFxModuleFile().updateAndWrite());

            // Generating or updating Maven module files (pom.xml)
            if (mavenPom)
                getWorkingAndChildrenModulesInDepth(workingModule)
                        .forEach(m -> m.getMavenModuleFile().updateAndWrite());

            // Generating files for Java modules (module-info.java and META-INF/services)
            if (moduleInfoJava || metaInfServices)
                getWorkingAndChildrenModulesInDepth(workingModule)
                        .filter(DevProjectModule::hasSourceDirectory)
                        .filter(DevProjectModule::hasJavaSourceDirectory)
                        .filter(m -> m.getTarget().isPlatformSupported(Platform.JRE))
                        .forEach(JavaFilesGenerator::generateJavaFiles);

            if (gwtXml || indexHtml || gwtSuperSources || gwtServiceLoader || gwtResourceBundles)
                // Generate files for executable GWT modules (module.gwt.xml, index.html, super sources, service loader, resource bundle)
                getWorkingAndChildrenModulesInDepth(workingModule)
                        .filter(m -> m.isExecutable(Platform.GWT))
                        .forEach(GwtFilesGenerator::generateGwtFiles);

            // Generate files for executable Gluon modules (graalvm_config/reflection.json)
            getWorkingAndChildrenModulesInDepth(workingModule)
                    .filter(m -> m.isExecutable(Platform.JRE))
                    .filter(m -> m.getTarget().hasTag(TargetTag.GLUON))
                    .forEach(GluonFilesGenerator::generateGraalVmReflectionJson);

            int operationsCount = transaction.operationsCount();
            transaction.commit(); // Write files generated by previous operation if no exception have been raised
            if (operationsCount == 0)
                log("All files are up-to-date");
            else
                log(operationsCount + " files updated");
        }
    }

    private static ReusableStream<DevProjectModule> getWorkingAndChildrenModules(DevProjectModule workingModule) {
        return workingModule
                .getThisAndChildrenModules()
                .filter(DevProjectModule.class::isInstance)
                .map(DevProjectModule.class::cast);
    }

    private static ReusableStream<DevProjectModule> getWorkingAndChildrenModulesInDepth(DevProjectModule workingModule) {
        return workingModule
                .getThisAndChildrenModulesInDepth()
                .filter(DevProjectModule.class::isInstance)
                .map(DevProjectModule.class::cast);
    }
}
