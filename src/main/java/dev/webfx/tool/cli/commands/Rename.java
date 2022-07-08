package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.core.CliException;
import dev.webfx.tool.cli.core.DevProjectModule;
import dev.webfx.tool.cli.core.DevRootModule;
import dev.webfx.tool.cli.core.ProjectModule;
import dev.webfx.tool.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.tool.cli.util.textfile.TextFileThreadTransaction;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
@Command(name = "rename", description = "Rename module(s).",
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
            DevRootModule rootModule = getWorkingDevProjectModule().getRootModule();
            ReusableStream<DevProjectModule> childrenModulesInDepth =
                    rootModule.getThisAndChildrenModulesInDepth()
                    .map(DevProjectModule.class::cast);
            DevProjectModule originalModule = childrenModulesInDepth
                    .filter(m -> moduleName.equals(m.getName()))
                    .findFirst().orElse(null);
            if (originalModule == null)
                throw new CliException("Can't find module " + moduleName + " in your project");
            setUpLogger();

            try (TextFileThreadTransaction transaction = TextFileThreadTransaction.open()) {
                childrenModulesInDepth
                        .filter(m ->
                                // Dependent modules
                                !m.getDirectModules().filter(dm -> dm == originalModule).isEmpty()
                                // Implementing modules
                                || m.implementsModule(originalModule)
                        )
                        .forEach(m -> replaceModuleNameInWebfxModule(m.getWebFxModuleFile().getModuleFilePath()));
                ProjectModule parentModule = originalModule.getParentModule();
                if (parentModule instanceof DevProjectModule)
                    replaceModuleNameInWebfxModule(((DevProjectModule)parentModule).getWebFxModuleFile().getModuleFilePath());
                transaction.commit();
                originalModule.rename(moduleNewName);
                Update.executeUpdateTasks(rootModule, new Update.UpdateTasks(true));
                transaction.commit();
                Path homeDirectory = originalModule.getHomeDirectory();
                if (homeDirectory.endsWith(moduleName))
                    homeDirectory.toFile().renameTo(homeDirectory.getParent().resolve(moduleNewName).toFile());
            }
        }

        private void replaceModuleNameInWebfxModule(Path webfxModulePath) {
            String xmlContent = TextFileReaderWriter.readTextFile(webfxModulePath)
                    .replace(">" + moduleName + "</", ">" + moduleNewName + "</");
            TextFileReaderWriter.writeTextFile(xmlContent, webfxModulePath);
        }
    }
}
