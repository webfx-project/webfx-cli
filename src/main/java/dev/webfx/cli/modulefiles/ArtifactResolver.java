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
        return getArtifactId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getArtifactId(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (ModuleRegistry.isJdkModule(module) || isJdkEmulationModule(moduleName) && !(isForGwt && isExecutable))
            return null; // No external dependency is required
        if (isForGwt && isExecutable) {
            switch (moduleName) {
                case "elemental2-core":
                case "elemental2-dom":
                    return null; // Already included by default
                case "gwt-user":
                    return "gwt-dev";
            }
        }
        boolean mustBeJavaFxEmul = isForGwt || isRegistry;
        switch (moduleName) {
            //case "gwt-charts":
            case "jsinterop-base":
            case "jsinterop-annotations":
                return null;
            case "webfx-kit-javafxbase-emul":
                return mustBeJavaFxEmul ? moduleName : "javafx-base";
            case "webfx-kit-javafxgraphics-emul":
                return mustBeJavaFxEmul ? moduleName : "javafx-graphics";
            case "webfx-kit-javafxcontrols-emul":
                return mustBeJavaFxEmul ? moduleName : "javafx-controls";
            case "webfx-kit-javafxmedia-emul":
                return mustBeJavaFxEmul ? moduleName : "javafx-media";
            case "webfx-kit-javafxweb-emul":
                return mustBeJavaFxEmul ? moduleName : "javafx-web";
            case "javafx-base":
                return mustBeJavaFxEmul ? "webfx-kit-javafxbase-emul" : moduleName;
            case "javafx-graphics":
                return mustBeJavaFxEmul ? "webfx-kit-javafxgraphics-emul" : moduleName;
            case "javafx-controls":
                return mustBeJavaFxEmul ? "webfx-kit-javafxcontrols-emul" : moduleName;
            case "javafx-media":
                return mustBeJavaFxEmul ? "webfx-kit-javafxmedia-emul" : moduleName;
            case "javafx-web":
                return mustBeJavaFxEmul ? "webfx-kit-javafxweb-emul" : moduleName;
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
        return getGroupId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getGroupId(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
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
        return getVersion(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
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
        return getScope(moduleGroup, buildInfo.isForGwt, buildInfo.isForOpenJfx, buildInfo.isExecutable, buildInfo.isRegistry);
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
        // Setting scope to "provided" for interface modules and optional dependencies
        if (module instanceof ProjectModule && ((ProjectModule) module).isInterface() || moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
            return "provided";
        if (!isForGwt && !isForOpenJfx && !isExecutable && !isRegistry)
            switch (module.getName()) {
                case "javafx-base":
                case "javafx-graphics":
                case "javafx-controls":
                case "javafx-media":
                case "javafx-web":
                case "webfx-kit-javafxbase-emul":
                case "webfx-kit-javafxgraphics-emul":
                case "webfx-kit-javafxcontrols-emul":
                case "webfx-kit-javafxmedia-emul":
                case "webfx-kit-javafxweb-emul":
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

    private static boolean isJdkEmulationModule(String moduleName) {
        switch (moduleName) {
            case "java-nio-emul":
                return true;
        }
        return false;
    }
}
