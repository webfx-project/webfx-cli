package dev.webfx.cli.commands;

import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.DevRootModule;
import dev.webfx.cli.core.TargetTag;
import dev.webfx.cli.modulefiles.DevMavenPomModuleFile;
import dev.webfx.cli.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.cli.sourcegenerators.TeaVMEmbedResourcesBundleSourceGenerator;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
/**
 * @author Bruno Salmon
 */
@Command(name = "create", description = "Create WebFX module(s).",
subcommands = {
        Create.Project.class,
        Create.Application.class,
        Create.Module.class,
})
public final class Create extends CommonSubcommand {

    static abstract class CreateSubCommand extends CommonSubcommand implements Callable<Void> {

        @Option(names = {"-p", "--project"}, arity = "0..1", fallbackValue = "!", description = "Create as a separate new project.")
        String project;

        private DevProjectModule createModule(String name, boolean aggregate) {
            CommandWorkspace workspace = getWorkspace();
            Path projectDirectoryPath = project != null ? workspace.getWorkspaceDirectoryPath().resolve(project) : workspace.getProjectDirectoryPath();
            Path modulePath = projectDirectoryPath.resolve(name);
            DevProjectModule module = workspace.getModuleRegistry().getOrCreateDevProjectModule(modulePath);
            module.getMavenModuleFile().setAggregate(aggregate);
            DevMavenPomModuleFile parentDevMavenModuleFile = getParentDevMavenModuleFile(module);
            if (parentDevMavenModuleFile != null)
                parentDevMavenModuleFile.addModule(module);
            return module;
        }

        DevProjectModule createAggregateModule(String name, boolean writePom) {
            DevProjectModule module = createModule(name, true);
            if (writePom)
                module.getMavenModuleFile().writeFile();
            return module;
        }

        DevProjectModule createSourceModule(String name, String templateFileName, String fullClassName, boolean executable) throws IOException {
            DevProjectModule module = createModule(name, false);
            Path modulePath = module.getHomeDirectory();
            Path sourcePath = modulePath.resolve("src/main/java");
            Path resourcesPath = modulePath.resolve("src/main/resources");
            Path testPath = modulePath.resolve("src/test/java");
            Files.createDirectories(sourcePath);
            Files.createDirectories(resourcesPath);
            Files.createDirectories(testPath);
            WebFxModuleFile webFxModuleFile = module.getWebFxModuleFile();
            if (templateFileName != null && fullClassName != null) {
                int p = fullClassName.lastIndexOf('.');
                String packageName = fullClassName.substring(0, p);
                String className = fullClassName.substring(p + 1);
                Path packagePath = sourcePath.resolve(packageName.replace('.', '/'));
                Path javaFilePath = packagePath.resolve(className + ".java");
                String template = ResourceTextFileReader.readTemplate(templateFileName)
                        .replace("${package}", packageName)
                        .replace("${class}", className);
                if (!Files.exists(javaFilePath))
                    TextFileReaderWriter.writeTextFile(template, javaFilePath);
                if (template.contains("javafx.application.Application"))
                    webFxModuleFile.addProvider("javafx.application.Application", fullClassName);
            }
            webFxModuleFile.setExecutable(executable);
            if (executable && module.getBuildInfo().isForTeaVm) {
                webFxModuleFile.addProvider("org.teavm.classlib.ResourceSupplier", TeaVMEmbedResourcesBundleSourceGenerator.getProviderClassName(module));
                webFxModuleFile.addProvider("dev.webfx.platform.resource.spi.impl.web.WebResourceBundle", TeaVMEmbedResourcesBundleSourceGenerator.getProviderClassName(module));
            }
            webFxModuleFile.writeFile();
            return module;
        }
    }

    @Command(name = "project", description = "Create a new project.")
    static class Project extends CreateSubCommand {

        @Option(names = {"-i", "--inline"}, description = "Inline the WebFX parent pom instead of referencing it.")
        private boolean inline;

        @Parameters(paramLabel = "groupId", description = "GroupId of the project artifact.")
        private String groupId;

        @Parameters(paramLabel = "artifactId", description = "ArtifactId of the project artifact.")
        private String artifactId;

        @Parameters(paramLabel = "version", description = "Version of the project artifact.")
        private String version;

        @Override
        public Void call() {
            project = artifactId;
            DevRootModule module = (DevRootModule) createAggregateModule("", false);
            module.setGroupId(groupId);
            module.setArtifactId(artifactId);
            module.setVersion(version);
            module.setInlineWebFxParent(inline);
            module.getMavenModuleFile().writeFile();
            return null;
        }
    }

    @Command(name = "module", description = "Create a single generic module.")
    static class Module extends CreateSubCommand {

        @Parameters(paramLabel = "name", description = "Name of the new module.")
        private String name;

        @Option(names={"-c", "--class"}, description = "Fully qualified class name.")
        private String moduleClassName;

        @Option(names={"-a", "--aggregate"}, description = "Will create an aggregate pom.xml module.")
        private boolean aggregate;

        @Override
        public Void call() throws Exception {
            DevProjectModule module = aggregate ? createAggregateModule(name, true) : createSourceModule(name, ResourceTextFileReader.readTemplate("Class.javat"), moduleClassName, false);
            writeParentMavenModuleFile(module);
            return null;
        }
    }

    private static DevMavenPomModuleFile getParentDevMavenModuleFile(DevProjectModule module) {
        MavenPomModuleFile mavenModuleFile = module.getParentModule().getMavenModuleFile();
        if (mavenModuleFile instanceof DevMavenPomModuleFile)
            return (DevMavenPomModuleFile) mavenModuleFile;
        return null;
    }

    private static void writeParentMavenModuleFile(DevProjectModule module) {
        DevMavenPomModuleFile parentDevMavenModuleFile = getParentDevMavenModuleFile(module);
        if (parentDevMavenModuleFile != null)
            parentDevMavenModuleFile.writeFile();
    }

    @Command(name = "application", description = "Create modules for a new WebFX application.")
    static class Application extends CreateSubCommand {

        @Parameters(paramLabel = "class", arity = "0..1", description = "Fully qualified JavaFX Application class name.")
        private String javaFxApplication;

        @Option(names = {"--prefix"}, description = "Prefix of the application modules that will be created.")
        private String prefix;

        @Option(names = {"-w", "--helloWorld"}, description = "Use hello world code template.")
        private boolean helloWorld;

        @Override
        public Void call() throws Exception {
            validateParameters();
            if ("!".equals(project))
                project = prefix;
            if (prefix == null) {
                if (project != null)
                    prefix = project;
                else
                    prefix = getWorkspace().getModuleRegistry().getOrCreateDevProjectModule(getWorkspace().getProjectDirectoryPath()).getName();
            }
            DevProjectModule applicationModule = createTagApplicationModule(null);
            createTagApplicationModule(TargetTag.OPENJFX);
            createTagApplicationModule(TargetTag.GWT);
            createTagApplicationModule(TargetTag.GLUON);
            writeParentMavenModuleFile(applicationModule);
            new Update().run();
            return null;
        }

        private void validateParameters() {
            if (javaFxApplication != null && (!SourceVersion.isName(javaFxApplication) || javaFxApplication.contains("$")))
                throw new CliException("'" + javaFxApplication + "' is not a valid java class name");
        }

        private DevProjectModule createTagApplicationModule(TargetTag targetTag) throws IOException {
            if (targetTag == null)
                return createSourceModule(prefix + "-application", helloWorld ? "JavaFxHelloWorldApplication.javat" : "JavaFxApplication.javat", javaFxApplication, false);
            return createSourceModule(prefix + "-application-" + targetTag.name().toLowerCase(), null, null, true);
        }
    }
}
