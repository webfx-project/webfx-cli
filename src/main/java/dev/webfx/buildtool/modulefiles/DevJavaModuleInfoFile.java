package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.*;
import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.modulefiles.abstr.DevModuleFileImpl;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class DevJavaModuleInfoFile extends DevModuleFileImpl {

    public DevJavaModuleInfoFile(DevProjectModule module) {
        super(module, module.getJavaSourceDirectory().resolve("module-info.java"));
    }

    public String getJavaModuleName() {
        return getJavaModuleName(getModule());
    }

    @Override
    public void writeFile() {
        DevProjectModule module = getProjectModule();
        StringBuilder sb = new StringBuilder("// File managed by WebFX (DO NOT EDIT MANUALLY)\n\nmodule ").append(getJavaModuleName()).append(" {\n");
        processSection(sb, "Direct dependencies modules", "requires",
                ReusableStream.fromIterable(
                        module.getDirectDependencies()
                        // Modules with "runtime" scope must not have a "requires" clause (since they are invisible for the module).
                        // Exception is made however for JDK modules (since they are always visible) and may be needed (ex: java.sql for Vert.x)
                        .filter(d -> !"runtime".equals(d.getScope()) || ModuleRegistry.isJdkModule(d.getDestinationModule()))
                        // Grouping by destination module
                        .collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                )
                .map(this::getJavaModuleNameWithStaticPrefixIfApplicable)
                .filter(Objects::nonNull)
                .distinct()
        );

        processSection(sb, "Exported packages", "exports",
                module.getExportedJavaPackages()
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
                    .sorted()
                    .forEach(s -> sb.append("    provides ").append(s).append(" with ").append(module.getProvidedJavaServiceImplementations(s, true).collect(Collectors.joining(", "))).append(";\n"));
        }
        sb.append("\n}");

        TextFileReaderWriter.writeTextFileIfNewOrModified(sb.toString(), getModuleFilePath());
    }

    private static void processSection(StringBuilder sb, String sectionName, String keyword, ReusableStream<String> tokens) {
        if (tokens.count() > 0) {
            sb.append("\n    // ").append(sectionName).append('\n');
            tokens.sorted()
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
            case "java-nio-emul":
                return "java.base";
            case "webfx-kit-javafxbase-emul":
                return "javafx.base";
            case "webfx-kit-javafxcontrols-emul":
                return "javafx.controls";
            case "webfx-kit-javafxgraphics-emul":
                return "javafx.graphics";
            case "webfx-kit-javafxmedia-emul":
                return "javafx.media";
            default:
                if (module instanceof DevProjectModule) {
                    DevProjectModule projectModule = (DevProjectModule) module;
                    String abstractModule = projectModule.implementedInterfaces().findFirst().orElse(null);
                    if (abstractModule != null && !abstractModule.equals(""))
                        moduleName = projectModule.getRootModule().searchRegisteredModule(abstractModule).getName();
                }
                return moduleName.replaceAll("[^a-zA-Z0-9]", ".");
        }
    }
}
