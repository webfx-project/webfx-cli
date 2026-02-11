package dev.webfx.cli.commands;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.DevRootModule;
import dev.webfx.cli.core.ProjectModuleImpl;
import dev.webfx.cli.core.TargetTag;
import dev.webfx.cli.exceptions.CliException;
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

    static abstract class CreateSubCommand extends FollowedByUpdateSubCommand implements Callable<Void> {

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

        DevProjectModule createSourceModule(String name, String templateFileName, String fullQualifiedApplicationClassName, boolean executable) throws IOException {
            DevProjectModule module = createModule(name, false);
            Path modulePath = module.getHomeDirectory();
            Path sourcePath = modulePath.resolve("src/main/java");
            Path resourcesPath = modulePath.resolve("src/main/resources");
            Path testPath = modulePath.resolve("src/test/java");
            Files.createDirectories(sourcePath);
            Files.createDirectories(resourcesPath);
            Files.createDirectories(testPath);
            WebFxModuleFile webFxModuleFile = module.getWebFxModuleFile();
            if (templateFileName != null && fullQualifiedApplicationClassName != null) {
                int p = fullQualifiedApplicationClassName.lastIndexOf('.');
                String packageName = fullQualifiedApplicationClassName.substring(0, p);
                String className = fullQualifiedApplicationClassName.substring(p + 1);
                Path packagePath = sourcePath.resolve(packageName.replace('.', '/'));
                Path javaFilePath = packagePath.resolve(className + ".java");
                String template = ResourceTextFileReader.readTemplate(templateFileName)
                    .replace("${package}", packageName)
                    .replace("${class}", className);
                if (!Files.exists(javaFilePath))
                    TextFileReaderWriter.writeTextFile(template, javaFilePath);
                if (template.contains("javafx.application.Application"))
                    webFxModuleFile.addProvider("javafx.application.Application", fullQualifiedApplicationClassName);
            }
            webFxModuleFile.setExecutable(executable);
            if (executable && module.getBuildInfo().isForTeaVm) {
                String providerClassName = TeaVMEmbedResourcesBundleSourceGenerator.getProviderClassName(module);
                webFxModuleFile.addProvider("org.teavm.classlib.ResourceSupplier", providerClassName);
                webFxModuleFile.addProvider("dev.webfx.platform.resource.spi.impl.web.WebResourceBundle", providerClassName);
            }
            webFxModuleFile.writeFile();
            return module;
        }

    }

    @Command(name = "project", description = "Create a new project.")
    static class Project extends CreateSubCommand {

        //@Option(names = {"-i", "--inline"}, description = "Inline the WebFX parent pom instead of referencing it.")
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
            runUpdateIfNotSkipped();
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
            if (aggregate)
                createAggregateModule(name, false);
            else if (moduleClassName != null)
                createSourceModule(name, ResourceTextFileReader.readTemplate("Class.javat"), moduleClassName, false);
            else {
                String possibleApplicationModuleName = ProjectModuleImpl.getPossibleApplicationModuleName(name);
                dev.webfx.cli.core.Module possibleApplicationModule = getWorkspace().getWorkingDevProjectModule().searchRegisteredModule(possibleApplicationModuleName);
                boolean executable = possibleApplicationModule != null;
                createSourceModule(name, null, null, executable);
            }
            runUpdateIfNotSkipped();
            return null;
        }
    }

    private static DevMavenPomModuleFile getParentDevMavenModuleFile(DevProjectModule module) {
        MavenPomModuleFile mavenModuleFile = module.getParentModule().getMavenModuleFile();
        if (mavenModuleFile instanceof DevMavenPomModuleFile)
            return (DevMavenPomModuleFile) mavenModuleFile;
        return null;
    }

    @Command(name = "application", description = "Create modules for a new WebFX application.")
    static class Application extends CreateSubCommand {

        @Parameters(paramLabel = "module", description = "The application module name.")
        private String applicationModuleName;

        private String fullQualifiedApplicationClassName;

        @Override
        public Void call() throws Exception {
            CommandWorkspace workspace = getWorkspace();
            if (workspace.getTopRootModule() == null) { // happens when the project has not been initialized through the Init command
                // We provide a default dummy initialization to reduce effort when playing with WebFX for the first time
                String groupId = "org.example";
                String artifactId = workspace.getProjectDirectoryPath().getFileName().toString();
                String version = "1.0.0";
                if (artifactId.equals(applicationModuleName))
                    artifactId = applicationModuleName.equals("webfx-example") ? "webfx-project" : "webfx-example";
                Init.execute(groupId + ":" + artifactId + ":" + version, true, workspace);
            }
            String applicationName = decideApplicationClassName();
            String packageName = decidePackageName();
            fullQualifiedApplicationClassName = packageName + "." + applicationName;
            validateParameters();
            createTagApplicationModule(null);
            createTagApplicationModule(TargetTag.OPENJFX);
            createTagApplicationModule(TargetTag.GWT);
            createTagApplicationModule(TargetTag.GLUON);
            createTagApplicationModule(TargetTag.TEAVM, TargetTag.JS);
            createTagApplicationModule(TargetTag.TEAVM, TargetTag.WASM);
            runUpdateIfNotSkipped();
            return null;
        }

        private String decidePackageName() {
            DevProjectModule topRootModule = getWorkspace().getTopRootModule();
            String groupId = topRootModule.getGroupId();
            String packageName = groupId;
            String modulePackageName = applicationModuleName.replace('-', '.');
            if (modulePackageName.equals(groupId) || modulePackageName.startsWith(groupId + ".")) {
                packageName = modulePackageName;
            } else {
                int lastDot = groupId.lastIndexOf('.');
                String lastGroupIdToken = lastDot == -1 ? groupId : groupId.substring(lastDot + 1);
                if (modulePackageName.equals(lastGroupIdToken))
                    packageName = groupId;
                else if (modulePackageName.startsWith(lastGroupIdToken + "."))
                    packageName += "." + modulePackageName.substring(lastGroupIdToken.length() + 1);
                else
                    packageName += "." + modulePackageName;
            }
            return packageName;
        }

        private String decideApplicationClassName() {
            return toPascalCase(applicationModuleName)
                .replace("Webfx", "WebFX")
                .replace("WebFXapp", "WebFXApp");
        }

        private static String toPascalCase(String s) {
            StringBuilder sb = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : s.toCharArray()) {
                if (!Character.isLetterOrDigit(c)) {
                    capitalizeNext = true;
                } else {
                    if (capitalizeNext) {
                        sb.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        sb.append(c);
                    }
                }
            }
            return sb.toString();
        }

        private void validateParameters() {
            if (fullQualifiedApplicationClassName != null && (!SourceVersion.isName(fullQualifiedApplicationClassName) || fullQualifiedApplicationClassName.contains("$")))
                throw new CliException("'" + fullQualifiedApplicationClassName + "' is not a valid java class name");
        }
        private DevProjectModule createTagApplicationModule(TargetTag targetTag) throws IOException {
            return createTagApplicationModule(targetTag, null);
        }

        private DevProjectModule createTagApplicationModule(TargetTag targetTag, TargetTag langTag) throws IOException {
            if (targetTag == null)
                return createSourceModule(applicationModuleName, "JavaFxHelloWorldApplication.javat", fullQualifiedApplicationClassName, false);
            return createSourceModule(applicationModuleName + "-" + targetTag.name().toLowerCase() + (langTag == null ? ""  : "-" + langTag.name().toLowerCase()), null, null, true);
        }
    }
}
