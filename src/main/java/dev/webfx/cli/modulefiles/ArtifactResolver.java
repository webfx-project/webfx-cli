package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Bruno Salmon
 */
public final class ArtifactResolver {

    public static String getGAV(Module module) {
        return getGroupId(module) + ":" + getArtifactId(module) + ":" + getVersion(module);
    }

    public static String getGV(Module module) {
        return getGroupId(module) + ":" + getVersion(module);
    }

    public static String getArtifactId(Module module) {
        BuildInfo buildInfo = BuildInfoThreadLocal.getBuildInfo();
        if (buildInfo == null && module instanceof ProjectModuleImpl)
            buildInfo = ((ProjectModuleImpl) module).getBuildInfo();
        if (buildInfo != null)
            return getArtifactId(module, buildInfo);
        return getArtifactId(module, false, false, false);
    }

    static String getArtifactId(Module module, BuildInfo buildInfo) {
        return getArtifactId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.requiresEmul);
    }

    static String getArtifactId(Module module, boolean isForGwt, boolean isExecutable, boolean requiresEmul) {
        String moduleName = module.getName();
        if (ModuleRegistry.isJdkModule(module) || SpecificModules.isJdkEmulationModule(moduleName) && !(isForGwt && isExecutable))
            return null; // No external dependency is required
        if (isForGwt && isExecutable) {
            switch (moduleName) {
                case SpecificModules.ELEMENTAL_2_CORE:
                case SpecificModules.ELEMENTAL_2_DOM:
                    return null; // Already included by default
                case SpecificModules.GWT_USER:
                    return SpecificModules.GWT_DEV;
            }
        }
        boolean mustBeJavaFxEmul = isForGwt || requiresEmul;
        switch (moduleName) {
            //case "gwt-charts":
            case SpecificModules.JSINTEROP_BASE:
            case SpecificModules.JSINTEROP_ANNOTATIONS:
                return null;
            case SpecificModules.WEBFX_KIT_JAVAFXBASE_EMUL:
                return mustBeJavaFxEmul ? moduleName : SpecificModules.JAVAFX_BASE;
            case SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_EMUL:
                return mustBeJavaFxEmul ? moduleName : SpecificModules.JAVAFX_GRAPHICS;
            case SpecificModules.WEBFX_KIT_JAVAFXCONTROLS_EMUL:
                return mustBeJavaFxEmul ? moduleName : SpecificModules.JAVAFX_CONTROLS;
            case SpecificModules.WEBFX_KIT_JAVAFXMEDIA_EMUL:
                return mustBeJavaFxEmul ? moduleName : SpecificModules.JAVAFX_MEDIA;
            case SpecificModules.WEBFX_KIT_JAVAFXWEB_EMUL:
                return mustBeJavaFxEmul ? moduleName : SpecificModules.JAVAFX_WEB;
            case SpecificModules.WEBFX_KIT_JAVAFXFXML_EMUL:
                return mustBeJavaFxEmul ? moduleName : SpecificModules.JAVAFX_FXML;
            case SpecificModules.JAVAFX_BASE:
                return mustBeJavaFxEmul ? SpecificModules.WEBFX_KIT_JAVAFXBASE_EMUL : moduleName;
            case SpecificModules.JAVAFX_GRAPHICS:
                return mustBeJavaFxEmul ? SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_EMUL : moduleName;
            case SpecificModules.JAVAFX_CONTROLS:
                return mustBeJavaFxEmul ? SpecificModules.WEBFX_KIT_JAVAFXCONTROLS_EMUL : moduleName;
            case SpecificModules.JAVAFX_MEDIA:
                return mustBeJavaFxEmul ? SpecificModules.WEBFX_KIT_JAVAFXMEDIA_EMUL : moduleName;
            case SpecificModules.JAVAFX_WEB:
                return mustBeJavaFxEmul ? SpecificModules.WEBFX_KIT_JAVAFXWEB_EMUL : moduleName;
            case SpecificModules.JAVAFX_FXML:
                return mustBeJavaFxEmul ? SpecificModules.WEBFX_KIT_JAVAFXFXML_EMUL : moduleName;
        }
        String artifactId = module.getArtifactId();
        if (artifactId != null)
            return artifactId;
        return moduleName;
    }

    public static String getGroupId(Module module) {
        BuildInfo buildInfo = BuildInfoThreadLocal.getBuildInfo();
        if (buildInfo == null && module instanceof ProjectModuleImpl)
            buildInfo = ((ProjectModuleImpl) module).getBuildInfo();
        if (buildInfo != null)
            return getGroupId(module, buildInfo);
        return getGroupId(module, false, false, false);
    }

    static String getGroupId(Module module, BuildInfo buildInfo) {
        return getGroupId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.requiresEmul);
    }

    static String getGroupId(Module module, boolean isForGwt, boolean isExecutable, boolean requiresEmul) {
        String moduleName = module.getName();
        boolean isJavaFxModule = moduleName.startsWith("javafx-");
        boolean isJavaFxEmulModule = RootModule.isJavaFxEmulModule(moduleName);
        boolean mustBeJavaFxEmulModule = isForGwt || requiresEmul;
        if (isJavaFxModule || isJavaFxEmulModule) {
            RootModule rootModule = null;
            if (module instanceof ProjectModule)
                rootModule = ((ProjectModule) module).getRootModule();
            else {
                BuildInfo buildInfo = BuildInfoThreadLocal.getBuildInfo();
                if (buildInfo != null)
                    rootModule = buildInfo.projectModule.getRootModule();
            }
            if (rootModule != null)
                module = rootModule.searchRegisteredModule(getArtifactId(module, isForGwt, isExecutable, requiresEmul), false);
            else if (isJavaFxEmulModule || mustBeJavaFxEmulModule)
                return "dev.webfx"; // hardcoded because we don't have access to the root module in this case
        }
        return module.getGroupId();
    }

    public static String getVersion(Module module) {
        BuildInfo buildInfo = BuildInfoThreadLocal.getBuildInfo();
        if (buildInfo == null && module instanceof ProjectModuleImpl)
            buildInfo = ((ProjectModuleImpl) module).getBuildInfo();
        if (buildInfo != null)
            return getVersion(module, buildInfo);
        return getVersion(module, false, false, false);
    }

    static String getVersion(Module module, BuildInfo buildInfo) {
        return getVersion(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.requiresEmul);
    }

    static String getVersion(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        boolean isJavaFxModule = moduleName.startsWith("javafx-");
        boolean isJavaFxEmulModule = RootModule.isJavaFxEmulModule(moduleName);
        boolean mustBeJavaFxEmulModule = isForGwt || isRegistry;
        if (isJavaFxModule || isJavaFxEmulModule) {
            RootModule rootModule = null;
            if (module instanceof ProjectModule)
                rootModule = ((ProjectModule) module).getRootModule();
            else {
                BuildInfo buildInfo = BuildInfoThreadLocal.getBuildInfo();
                if (buildInfo != null)
                    rootModule = buildInfo.projectModule.getRootModule();
            }
            if (rootModule != null)
                module = rootModule.searchRegisteredModule(getArtifactId(module, isForGwt, isExecutable, isRegistry), false);
            else if (isJavaFxEmulModule || mustBeJavaFxEmulModule)
                return "${webfx.version}"; // hardcoded because we don't have access to the root module in this case
        }
        return module.getVersion();
    }

    static String getType(Module module) {
        String type = module.getType();
        if (type == null && (module instanceof ProjectModule) && ((ProjectModule) module).isAggregate())
            type = "pom";
        return type;
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, BuildInfo buildInfo) {
        return getScope(moduleGroup, buildInfo.isForGwt, buildInfo.isForOpenJfx, buildInfo.isExecutable, buildInfo.requiresEmul);
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, boolean isForGwt, boolean isForOpenJfx, boolean isExecutable, boolean isRegistry) {
        // We take the scope of the first dependency ("default" as temporary value if not present).
        // Note: if a module has both a source and plugin dependency, it's important that the source dependency goes
        // first otherwise the compiler won't be able to compile the code if the scope is just set to "runtime".
        String scope = moduleGroup.getValue().stream().map(ModuleDependency::getScope)
                .map(s -> s == null ? "default" : s)
                .findFirst().orElse(null);
        if (scope != null && !"default".equals(scope)) // Returning the scope only if explicit in the first dependency
            return scope;
        Module module = moduleGroup.getKey();
        // Setting scope to "provided" for optional dependencies and interface modules
        if (module instanceof ProjectModule) {
            // Optional dependencies
            if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                return "provided";
            // An interface module should have scope "provided" in general (as it will be replaced by another module
            // implementing it in the end), but an exception is that when the implementing module is itself! (which
            // can happen for interface modules providing a default implementation).
            ProjectModule projectModule = ((ProjectModule) module);
            if (projectModule.isInterface() // yes, it's an interface module
                    // So we also check that it's not an implicit provider (which indicates that it was chosen as the implementing module)
                    && moduleGroup.getValue().stream().map(ModuleDependency::getType).noneMatch(type -> type == ModuleDependency.Type.IMPLICIT_PROVIDER))
                return "provided";
        }
        if (!isForGwt && !isForOpenJfx && !isExecutable && !isRegistry)
            switch (module.getName()) {
                case SpecificModules.JAVAFX_BASE:
                case SpecificModules.JAVAFX_GRAPHICS:
                case SpecificModules.JAVAFX_CONTROLS:
                case SpecificModules.JAVAFX_MEDIA:
                case SpecificModules.JAVAFX_WEB:
                case SpecificModules.JAVAFX_FXML:
                case SpecificModules.WEBFX_KIT_JAVAFXBASE_EMUL:
                case SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_EMUL:
                case SpecificModules.WEBFX_KIT_JAVAFXCONTROLS_EMUL:
                case SpecificModules.WEBFX_KIT_JAVAFXMEDIA_EMUL:
                case SpecificModules.WEBFX_KIT_JAVAFXWEB_EMUL:
                case SpecificModules.WEBFX_KIT_JAVAFXFXML_EMUL:
                    return "provided";
            }
        return null;
    }

    static String getClassifier(Map.Entry<Module, List<ModuleDependency>> moduleGroup, BuildInfo buildInfo) {
        return getClassifier(moduleGroup, buildInfo.isForGwt, buildInfo.isExecutable);
    }

    static String getClassifier(Map.Entry<Module, List<ModuleDependency>> moduleGroup, boolean isForGwt, boolean isExecutable) {
        String classifier = moduleGroup.getValue().stream().map(ModuleDependency::getClassifier).filter(Objects::nonNull).findAny().orElse(null);
        if (classifier != null)
            return classifier;
        if (isForGwt && isExecutable) {
            String moduleName = moduleGroup.getKey().getName();
            if (!moduleName.startsWith("gwt-") && !moduleName.startsWith("elemental2-") && !moduleName.equals("java-nio-emul") && !moduleName.equals("org.jresearch.gwt.time.tzdb"))
                return moduleName.endsWith("-emul-gwt") ? "shaded-sources" : "sources";
        }
        return null;
    }

}
