package dev.webfx.cli.commands;

import dev.webfx.cli.core.CliException;
import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.DevRootModule;
import dev.webfx.cli.core.ProjectModule;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.cli.util.textfile.TextFileThreadTransaction;
import dev.webfx.lib.reusablestream.ReusableStream;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Bruno Salmon
 */
@Command(name = "rename", description = "Rename module(s) - (experimental).",
        subcommands = {
                Rename.Module.class,
        })
public final class Rename extends CommonSubcommand {

    @Command(name = "module", description = "Rename a module")
    static class Module extends CommonSubcommand implements Runnable {

        @CommandLine.Parameters(description = "Original name of the module.")
        private String moduleName;

        @CommandLine.Parameters(description = "New name of the module.")
        private String moduleNewName;

        @Override
        public void run() {
            setUpLogger();
            execute(moduleName, moduleNewName, getWorkspace());
        }

        static void execute(String moduleName, String moduleNewName, CommandWorkspace workspace) {
            boolean wildcard = moduleName.contains("*") || moduleName.contains("%");
            if (!wildcard)
                executeNoWildcard(moduleName, moduleNewName, workspace);
            else {
                Pattern pattern = Pattern.compile(moduleName.replace("*", "(.*)").replace("%", "(.*)"));
                DevProjectModule workingModule = workspace.getWorkingDevProjectModule();
                ReusableStream<DevProjectModule> matchingModules =
                        workingModule.getThisAndChildrenModulesInDepth()
                                .map(DevProjectModule.class::cast)
                                .filter(m -> m.getRootModule() != m && pattern.matcher(m.getName()).matches())
                                .cache();
                if (matchingModules.count() == 0) // count() forces to load everything in the cache, so we capture all original names before renaming them
                    throw new CliException("Can't find any module matching " + moduleName + " under " + workingModule);
                matchingModules.forEach(m -> {
                    String name = m.getName();
                    Matcher matcher = pattern.matcher(name);
                    String newName = !matcher.matches() ? moduleNewName : moduleNewName.replace("*", matcher.group(1)).replace("%", matcher.group(1));
                    log("Renaming " + name + " to " + newName);
                    executeNoWildcard(name, newName, new CommandWorkspace(workspace));
                });
            }
        }

        static void executeNoWildcard(String moduleName, String moduleNewName, CommandWorkspace workspace) {
            DevProjectModule module = (DevProjectModule) workspace.getWorkingDevProjectModule().searchRegisteredModule(moduleName, false);
            DevRootModule rootModule = module.getRootModule();
            ReusableStream<DevProjectModule> childrenModulesInDepth =
                    rootModule.getThisAndChildrenModulesInDepth()
                            .map(DevProjectModule.class::cast);
            try (TextFileThreadTransaction transaction = TextFileThreadTransaction.open()) {
                childrenModulesInDepth
                        .filter(m ->
                                // Dependent modules
                                !m.getMainJavaSourceRootAnalyzer().getUnfilteredDirectDependencies().filter(dep -> dep.getDestinationModule() == module).isEmpty()
                                        // Implementing modules
                                        || m.implementsModule(module)
                        )
                        .forEach(m -> replaceModuleNameInWebFxAndPom(m, moduleName, moduleNewName));
                ProjectModule parentModule = module.getParentModule();
                if (parentModule instanceof DevProjectModule)
                    replaceModuleNameInWebFxAndPom(((DevProjectModule) parentModule), moduleName, moduleNewName);
                transaction.commit();
                Path homeDirectory = module.getHomeDirectory();
                if (homeDirectory.endsWith(module.getName()))
                    homeDirectory.toFile().renameTo(homeDirectory.getParent().resolve(moduleNewName).toFile());
                Update.executeUpdateTasks(new CommandWorkspace(workspace).getWorkingDevProjectModule().getRootModule(), new Update.UpdateTasks());
                transaction.commit();
            }
        }

        private static void replaceModuleNameInWebFxAndPom(DevProjectModule module, String moduleName, String moduleNewName) {
            replaceModuleNameInXmlFile(module.getWebFxModuleFile().getModuleFilePath(), moduleName, moduleNewName);
            replaceModuleNameInXmlFile(module.getMavenModuleFile().getModuleFilePath(), moduleName, moduleNewName);
        }

        private static void replaceModuleNameInXmlFile(Path xmlPath, String moduleName, String moduleNewName) {
            String xmlContent = TextFileReaderWriter.readTextFile(xmlPath);
            if (xmlContent != null)
                TextFileReaderWriter.writeTextFile(xmlContent.replace(">" + moduleName + "</", ">" + moduleNewName + "</"), xmlPath);
        }
    }
}
