package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.*;
import dev.webfx.cli.modulefiles.abstr.DevModuleFileImpl;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class DevJavaModuleInfoFile extends DevModuleFileImpl {

    public DevJavaModuleInfoFile(DevProjectModule module) {
        super(module, module.getMainJavaSourceDirectory().resolve("module-info.java"));
    }

    public String getJavaModuleName() {
        return getJavaModuleName(getModule());
    }

    @Override
    public void writeFile() {
        DevProjectModule module = getProjectModule();
        if (module.getWebFxModuleFile().skipJavaModuleInfoUpdate())
            return;
        StringBuilder sb = new StringBuilder("// File managed by WebFX (DO NOT EDIT MANUALLY)\n\nmodule ").append(getJavaModuleName()).append(" {\n");
        processSection(sb, "Direct dependencies modules", "requires",
                ReusableStream.fromIterable(
                        module.getMainJavaSourceRootAnalyzer().getDirectDependencies()
                        // Modules with "runtime", "test" or "verify" scope must not have a "requires" clause (since they are invisible for the source module).
                        // Exception is made however for JDK modules (since they are always visible) and may be needed (ex: java.sql for Vert.x)
                        .filter(d -> (!"runtime".equals(d.getScope()) && !"test".equals(d.getScope()) && !"verify".equals(d.getScope())) || ModuleRegistry.isJdkModule(d.getDestinationModule()))
                        // Grouping by destination module
                        .collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                )
                .map(this::getJavaModuleNameWithStaticOrTransitivePrefixIfApplicable)
                .filter(Objects::nonNull)
                .distinct()
        );

        processSection(sb, "Exported packages", "exports",
                module.getExportedJavaPackages()
        );
        processSection(sb, "Resources packages", "opens",
                module.getResourcePackages()
        );
        processSection(sb, "Meta Resource package", "opens",
                module.getMetaResourcePackage()
        );
        processSection(sb, "Used services", "uses",
                module.getMainJavaSourceRootAnalyzer().getUsedJavaServices()
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
            tokens.sorted(Comparator.comparing(DevJavaModuleInfoFile::moduleNameOnly)) // ignoring prefixes such as static or transitive
                    .forEach(p -> sb.append("    ").append(keyword).append(' ').append(p).append(";\n"));
        }
    }

    private static String moduleNameOnly(String moduleNameWithPossiblePrefix) {
        moduleNameWithPossiblePrefix = moduleNameWithPossiblePrefix.trim();
        int lastSpaceIndex = moduleNameWithPossiblePrefix.lastIndexOf(' ');
        return lastSpaceIndex < 0 ? moduleNameWithPossiblePrefix : moduleNameWithPossiblePrefix.substring(lastSpaceIndex + 1);
    }

    private String getJavaModuleNameWithStaticOrTransitivePrefixIfApplicable(Map.Entry<Module, List<ModuleDependency>> moduleGroup) {
        String javaModuleName = getJavaModuleName(moduleGroup.getKey());
        if (javaModuleName.equals("slf4j.api") ||
                javaModuleName.equals(getJavaModuleName())) // May happen with modules implementing an interface module
            return null;
        if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isTransitive))
            javaModuleName = "transitive " + javaModuleName;
        if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
            javaModuleName = "static " + javaModuleName;
        return javaModuleName;
    }

    private static String getJavaModuleName(Module module) {
        // Some WebFX modules are emulating Java(FX) modules, so they must have the same Java module name (as the user
        // application code is expecting)
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
            case "webfx-kit-javafxmedia-gluon":
                return "javafx.media";
            case "webfx-kit-javafxweb-emul":
                return "javafx.web";
            case "webfx-kit-javafxfxml-emul":
                return "javafx.fxml";
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
