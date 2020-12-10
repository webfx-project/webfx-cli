package webfx.tools.buildtool.modulefiles;

import org.w3c.dom.Element;
import webfx.tools.buildtool.Module;
import webfx.tools.buildtool.ModuleDependency;
import webfx.tools.buildtool.Platform;
import webfx.tools.buildtool.ProjectModule;
import webfx.tools.buildtool.util.textfile.TextFileReaderWriter;
import webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public class JavaModuleFile extends ModuleFile {

    public JavaModuleFile(ProjectModule module) {
        super(module);
    }

    @Override
    Path getModulePath() {
        return getModule().getJavaSourceDirectory().resolve("module-info.java");
    }

    public String getJavaModuleName() {
        return getJavaModuleName(getModule());
    }

    @Override
    void readFile() {
    }

    @Override
    public void writeFile() {
        ProjectModule module = getModule();
        StringBuilder sb = new StringBuilder("// Generated by WebFx\n\nmodule ").append(getJavaModuleName()).append(" {\n");
        processSection(sb, "Direct dependencies modules", "requires",
                ReusableStream.fromIterable(module.getDirectDependencies().collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet())
                        .map(this::getJavaModuleNameWithStaticPrefixIfApplicable)
                        .filter(Objects::nonNull)
                        .distinct()
        );

        processSection(sb, "Exported packages", "exports",
                module.getDeclaredJavaPackages()
        );
        processSection(sb, "Resources packages", "opens",
                module.getResourcePackages()
        );
        processSection(sb, "Used services", "uses",
                module.getUsedJavaServices()
        );
        ReusableStream<String> providedJavaServices = module.getProvidedJavaServices();
        if (module.getTarget().isPlatformSupported(Platform.JRE) && providedJavaServices.count() > 0) {
            sb.append("\n    // Provided services\n");
            providedJavaServices
                    .stream()
                    .sorted()
                    .forEach(s -> sb.append("    provides ").append(s).append(" with ").append(module.getProvidedJavaServiceImplementations(s, true).collect(Collectors.joining(", "))).append(";\n"));
        }
        sb.append("\n}");

        TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), getModulePath());
    }

    private static void processSection(StringBuilder sb, String sectionName, String keyword, ReusableStream<String> tokens) {
        if (tokens.count() > 0) {
            sb.append("\n    // ").append(sectionName).append('\n');
            tokens.stream()
                    .sorted()
                    .forEach(p -> sb.append("    ").append(keyword).append(' ').append(p).append(";\n"));
        }
    }

    private String getJavaModuleNameWithStaticPrefixIfApplicable(Map.Entry<Module, List<ModuleDependency>> moduleGroup) {
        String javaModuleName = getJavaModuleName(moduleGroup.getKey());
        if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
            return "static " + javaModuleName;
        if (javaModuleName.equals("slf4j.api") ||
                javaModuleName.equals(getJavaModuleName())) // May happen with modules implementing an interface module
            return null;
        return javaModuleName;
    }

    private static String getJavaModuleName(Module module) {
        String moduleName = module.getName();
        switch (moduleName) {
            case "webfx-kit-javafxbase-emul":
                return "javafx.base";
            case "webfx-kit-javafxcontrols-emul":
                return "javafx.controls";
            case "webfx-kit-javafxgraphics-emul":
                return "javafx.graphics";
            case "webfx-kit-javafxmedia-emul":
                return "javafx.media";
            default:
                if (module instanceof ProjectModule) {
                    ProjectModule projectModule = (ProjectModule) module;
                    Element documentElement = projectModule.getWebfxModuleFile().getDocument().getDocumentElement();
                    String abstractModule = documentElement == null ? null : documentElement.getAttribute("implements-module");
                    if (abstractModule != null && !abstractModule.equals(""))
                        moduleName = projectModule.getRootModule().findModule(abstractModule).getName();
                }
                return moduleName.replaceAll("[^a-zA-Z0-9]", ".");
        }
    }
}