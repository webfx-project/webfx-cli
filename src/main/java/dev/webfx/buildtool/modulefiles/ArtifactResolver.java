package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;

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
        if (module instanceof DevProjectModule)
            return getArtifactId((DevProjectModule) module);
        return getArtifactId(module, false, false, false);
    }

    static String getArtifactId(DevProjectModule module) {
        return getArtifactId(module, module.getBuildInfo());
    }

    static String getArtifactId(Module module, BuildInfo buildInfo) {
        return getArtifactId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getArtifactId(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (isJdkModule(moduleName) || isJdkEmulationModule(moduleName) && !(isForGwt && isExecutable))
            return null; // No external dependency is required
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
        String artifactId = module.getArtifactId();
        if (artifactId != null)
            return artifactId;
        return moduleName;
    }

    public static String getGroupId(Module module) {
        if (module instanceof DevProjectModule)
            return getGroupId((DevProjectModule) module);
        return getGroupId(module, false, false, false);
    }

    static String getGroupId(DevProjectModule module) {
        return getGroupId(module, module.getBuildInfo());
    }

    static String getGroupId(Module module, BuildInfo buildInfo) {
        return getGroupId(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getGroupId(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (module instanceof ProjectModule && (moduleName.startsWith("javafx-") || !isForGwt && !isRegistry && RootModule.isJavaFxEmulModule(moduleName)))
            module = ((ProjectModule) module).getRootModule().findModule(getArtifactId(module, isForGwt, isExecutable, isRegistry), false);
        String groupId = module.getGroupId();
        if (groupId != null)
            return groupId;
        return "null";
    }

    public static String getSafeVersion(Module module) {
        String version = getVersion(module);
        return version != null ? version : "null";
    }

    public static String getVersion(Module module) {
        if (module instanceof DevProjectModule)
            return getVersion((DevProjectModule) module);
        return getVersion(module, false, false, false);
    }

    static String getVersion(DevProjectModule module) {
        return getVersion(module, module.getBuildInfo());
    }

    static String getVersion(Module module, BuildInfo buildInfo) {
        return getVersion(module, buildInfo.isForGwt, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getVersion(Module module, boolean isForGwt, boolean isExecutable, boolean isRegistry) {
        String moduleName = module.getName();
        if (module instanceof ProjectModule && (moduleName.startsWith("javafx-") || !isForGwt && !isRegistry && RootModule.isJavaFxEmulModule(moduleName)))
            module = ((ProjectModule) module).getRootModule().findModule(getArtifactId(module, isForGwt, isExecutable, isRegistry), false);
        return module.getVersion();
    }

    static String getType(Module module) {
        return module.getType();
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, BuildInfo buildInfo) {
        return getScope(moduleGroup, buildInfo.isForGwt, buildInfo.isForOpenJfx, buildInfo.isExecutable, buildInfo.isRegistry);
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, boolean isForGwt, boolean isForOpenJfx, boolean isExecutable, boolean isRegistry) {
        String scope = moduleGroup.getValue().stream().map(ModuleDependency::getScope).filter(Objects::nonNull).findAny().orElse(null);
        if (scope != null)
            return scope;
        Module module = moduleGroup.getKey();
        // Setting scope to "provided" for interface modules and optional dependencies
        if (module instanceof ProjectModule && ((ProjectModule) module).isInterface() || moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
            return "provided";
        if (!isForGwt && !isForOpenJfx && !isExecutable && !isRegistry)
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

    static boolean isJdkModule(String moduleName) {
        switch (moduleName.replaceAll("-", ".")) {
            // List returned by java --list-modules
            case "java.base":
            case "java.compiler":
            case "java.datatransfer":
            case "java.desktop":
            case "java.instrument":
            case "java.logging":
            case "java.management":
            case "java.management.rmi":
            case "java.naming":
            case "java.net.http":
            case "java.prefs":
            case "java.rmi":
            case "java.scripting":
            case "java.se":
            case "java.security.jgss":
            case "java.security.sasl":
            case "java.smartcardio":
            case "java.sql":
            case "java.sql.rowset":
            case "java.transaction.xa":
            case "java.xml":
            case "java.xml.crypto":
            case "jdk.accessibility":
            case "jdk.aot":
            case "jdk.attach":
            case "jdk.charsets":
            case "jdk.compiler":
            case "jdk.crypto.cryptoki":
            case "jdk.crypto.ec":
            case "jdk.dynalink":
            case "jdk.editpad":
            case "jdk.hotspot.agent":
            case "jdk.httpserver":
            case "jdk.internal.ed":
            case "jdk.internal.jvmstat":
            case "jdk.internal.le":
            case "jdk.internal.opt":
            case "jdk.internal.vm.ci":
            case "jdk.internal.vm.compiler":
            case "jdk.internal.vm.compiler.management":
            case "jdk.jartool":
            case "jdk.javadoc":
            case "jdk.jcmd":
            case "jdk.jconsole":
            case "jdk.jdeps":
            case "jdk.jdi":
            case "jdk.jdwp.agent":
            case "jdk.jfr":
            case "jdk.jlink":
            case "jdk.jshell":
            case "jdk.jsobject":
            case "jdk.jstatd":
            case "jdk.localedata":
            case "jdk.management":
            case "jdk.management.agent":
            case "jdk.management.jfr":
            case "jdk.naming.dns":
            case "jdk.naming.ldap":
            case "jdk.naming.rmi":
            case "jdk.net":
            case "jdk.pack":
            case "jdk.rmic":
            case "jdk.scripting.nashorn":
            case "jdk.scripting.nashorn.shell":
            case "jdk.sctp":
            case "jdk.security.auth":
            case "jdk.security.jgss":
            case "jdk.unsupported":
            case "jdk.unsupported.desktop":
            case "jdk.xml.dom":
            case "jdk.zipfs":
                return true;
        }
        return false;
    }

    private static boolean isJdkEmulationModule(String moduleName) {
        switch (moduleName) {
            case "java-nio-emul":
                return true;
        }
        return false;
    }
}
