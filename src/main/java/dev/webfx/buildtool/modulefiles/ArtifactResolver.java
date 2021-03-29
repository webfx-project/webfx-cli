package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Bruno Salmon
 */
final class ArtifactResolver {

    static String getArtifactId(Module module) {
        if (module instanceof ProjectModule)
            return getArtifactId(module, ((ProjectModule) module).getBuildInfo());
        return getArtifactId(module, false, false, false);
    }

    static String getArtifactId(ProjectModule module) {
        return getArtifactId(module, module.getBuildInfo());
    }

    static String getArtifactId(Module module, BuildInfo buildInfo) {
        return getArtifactId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getArtifactId(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (moduleName.equals("java-nio-emul") && isForGwt && isExecutable)
            return "gwt-nio";
        if (moduleName.startsWith("java-") || moduleName.startsWith("jdk-"))
            return null;
        if (module instanceof LibraryModule) {
            String artifactId = ((LibraryModule) module).getArtifactId();
            if (artifactId != null)
                return artifactId;
        }
        switch (moduleName) {
            //case "gwt-charts":
            case "jsinterop-base":
            case "jsinterop-annotations":
                return null;
            case "webfx-kit-javafxbase-emul":
                return isForGwt || isRegistry ? moduleName : "javafx-base";
            case "webfx-kit-javafxgraphics-emul":
                return isForGwt || isRegistry ? moduleName : "javafx-graphics";
            case "webfx-kit-javafxcontrols-emul":
                return isForGwt || isRegistry ? moduleName : "javafx-controls";
            case "webfx-kit-javafxmedia-emul":
                return isForGwt || isRegistry ? moduleName : "javafx-media";
        }
        if (isRegistry && "javafx-graphics".equals(moduleName))
            return "webfx-kit-javafxgraphics-emul";
        if (isForGwt && isExecutable) {
            switch (moduleName) {
                case "elemental2-core":
                case "elemental2-dom":
                case "javafx-base":
                case "javafx-graphics":
                case "javafx-controls":
                case "javafx-media":
                    return null;
                case "gwt-user":
                    return "gwt-dev";
            }
        }
        return moduleName;
    }

    static String getGroupId(ProjectModule module) {
        return getGroupId(module, module.getBuildInfo());
    }

    static String getGroupId(Module module, BuildInfo buildInfo) {
        return getGroupId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getGroupId(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (module instanceof ProjectModule && (moduleName.startsWith("javafx-") || !isForGwt && !isRegistry && RootModule.isJavaFxEmulModule(moduleName)))
            module = ((ProjectModule) module).getRootModule().findModule(getArtifactId(module, isForGwt, isExecutable, isRegistry), false);
        if (module instanceof LibraryModule) {
            String groupId = ((LibraryModule) module).getGroupId();
            if (groupId != null)
                return groupId;
        } else if (module instanceof ProjectModule) {
            String groupId = ((ProjectModule) module).getGroupIdOrParent();
            if (groupId != null)
                return groupId;
        }
        if (moduleName.startsWith("gwt-"))
            return "com.google.gwt";
        if (moduleName.startsWith("webfx-"))
            return "${webfx.groupId}";
        if (moduleName.startsWith("mongoose-"))
            return "${mongoose.groupId}";
        return "???";
    }

    static String getVersion(ProjectModule module) {
        return getVersion(module, module.getBuildInfo());
    }

    static String getVersion(Module module, BuildInfo buildInfo) {
        return getVersion(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getVersion(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (module instanceof ProjectModule && (moduleName.startsWith("javafx-") || !isForGwt && !isRegistry && RootModule.isJavaFxEmulModule(moduleName)))
            module = ((ProjectModule) module).getRootModule().findModule(getArtifactId(module, isForGwt, isExecutable, isRegistry), false);
        if (module instanceof LibraryModule) {
            String version = ((LibraryModule) module).getVersion();
            if (version != null)
                return version;
        } else if (module instanceof ProjectModule) {
            String version = ((ProjectModule) module).getVersionOrParent();
            if (version != null)
                return version;
        }
        if (moduleName.startsWith("webfx-"))
            return "${webfx.version}";
        if (moduleName.startsWith("mongoose-"))
            return "${mongoose.version}";
        return null; // Managed by root pom
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, BuildInfo buildInfo) {
        return getScope(moduleGroup, buildInfo.isForGwt, buildInfo.isForJavaFx, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, boolean isForGwt, boolean isForJavaFx, boolean isExecutable, boolean isRegistry) {
        String scope = moduleGroup.getValue().stream().map(ModuleDependency::getScope).filter(Objects::nonNull).findAny().orElse(null);
        if (scope != null)
            return scope;
        Module module = moduleGroup.getKey();
        // Setting scope to "provided" for interface modules and optional dependencies
        if (module instanceof ProjectModule && ((ProjectModule) module).isInterface() || moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
            return "provided";
        if (!isForGwt && !isForJavaFx && !isExecutable && !isRegistry)
            switch (module.getName()) {
                case "javafx-base":
                case "webfx-kit-javafxbase-emul":
                case "javafx-graphics":
                case "webfx-kit-javafxgraphics-emul":
                case "javafx-controls":
                case "webfx-kit-javafxcontrols-emul":
                case "javafx-media":
                case "webfx-kit-javafxmedia-emul":
                    return "provided";
                case "slf4j-api":
                    return "runtime";
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
                return moduleName.contains("-gwt-emul-") ? "shaded-sources" : "sources";
        }
        return null;
    }
}
