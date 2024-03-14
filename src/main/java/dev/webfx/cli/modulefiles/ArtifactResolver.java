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

    private static final String POM = "pom";
    private static final String PROVIDED = "provided";
    private static final String RUNTIME = "runtime";
    private static final String DEFAULT = "default";
    private static final String SOURCES = "sources";
    private static final String SHADED_SOURCES = "shaded-sources";

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
        return getArtifactId(module, false, false, false, false, false);
    }

    static String getArtifactId(Module module, BuildInfo buildInfo) {
        return getArtifactId(module, buildInfo.isForGwt, buildInfo.isForJ2cl, buildInfo.isExecutable, buildInfo.isJ2clCompilable, buildInfo.requiresJavafxEmul);
    }

    static String getArtifactId(Module module, boolean isForGwt, boolean isForJ2cl, boolean isExecutable, boolean isJ2clCompilable, boolean requiresJavafxEmul) {
        String moduleName = module.getName();
        // Emulated JDK modules needs to be listed in executable GWT modules, and also in any module that may be
        // compiled by J2CL (as provided). Otherwise, (ex: for Java modules) no external dependency is required.
        if (!(isForGwt && isExecutable || isJ2clCompilable) && module.isJavaBaseEmulationModule())
            return null;
        if ((isForGwt || isForJ2cl) && isExecutable) {
            if (SpecificModules.isElemental2Module(moduleName) || SpecificModules.isJsIntertopModule(moduleName))
                return null; // Already included by default
            if (moduleName.equals(SpecificModules.GWT_USER)) {
                return isForJ2cl ? null : SpecificModules.GWT_DEV;
            }
            if (isForJ2cl && requiresJavafxEmul && SpecificModules.isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(moduleName))
                return SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL;
            if (isForGwt && moduleName.equals(SpecificModules.J2CL_TIME))
                return null;
        }
        // No external dependency required for other JDK modules
        if (SpecificModules.isJdkModule(moduleName))
            return null;
        if (SpecificModules.isJavafxModule(moduleName))
            return requiresJavafxEmul ? SpecificModules.javafxModuleToEmulModule(moduleName) : moduleName;
        if (SpecificModules.isJavafxEmulModule(moduleName))
            return requiresJavafxEmul ? moduleName : SpecificModules.emulModuleToJavafxModule(moduleName);
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
        return getGroupId(module, false, false, false, false, false);
    }

    static String getGroupId(Module module, BuildInfo buildInfo) {
        return getGroupId(module, buildInfo.isForGwt, buildInfo.isForJ2cl, buildInfo.isExecutable, buildInfo.isJ2clCompilable, buildInfo.requiresJavafxEmul);
    }

    static String getGroupId(Module module, boolean isForGwt, boolean isForJ2cl, boolean isExecutable, boolean isJ2clCompilable, boolean requiresJavafxEmul) {
        String moduleName = module.getName();
        boolean isJavaFxModule = SpecificModules.isJavafxModule(moduleName);
        boolean isJavaFxEmulModule = SpecificModules.isJavafxEmulModule(moduleName);
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
                module = rootModule.searchRegisteredModule(getArtifactId(module, isForGwt, isForJ2cl, isExecutable, isJ2clCompilable, requiresJavafxEmul), false);
            else if (isJavaFxEmulModule || requiresJavafxEmul)
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
        return getVersion(module, false, false, false, false, false);
    }

    static String getVersion(Module module, BuildInfo buildInfo) {
        return getVersion(module, buildInfo.isForGwt, buildInfo.isForJ2cl, buildInfo.isExecutable, buildInfo.isJ2clCompilable, buildInfo.requiresJavafxEmul);
    }

    static String getVersion(Module module, boolean isForGwt, boolean isForJ2cl, boolean isExecutable, boolean isJ2clCompilable, boolean requiresJavafxEmul) {
        String moduleName = module.getName();
        boolean isJavaFxModule = moduleName.startsWith("javafx-");
        boolean isJavaFxEmulModule = SpecificModules.isJavafxEmulModule(moduleName);
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
                module = rootModule.searchRegisteredModule(getArtifactId(module, isForGwt, isForJ2cl, isExecutable, isJ2clCompilable, requiresJavafxEmul), false);
            else if (isJavaFxEmulModule || requiresJavafxEmul)
                return "${webfx.version}"; // hardcoded because we don't have access to the root module in this case
        }
        return module.getVersion();
    }

    static String getType(Module module) {
        String type = module.getType();
        if (type == null && (module instanceof ProjectModule) && ((ProjectModule) module).isAggregate())
            type = POM;
        return type;
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, BuildInfo buildInfo) {
        return getScope(moduleGroup, buildInfo.isForGwt, buildInfo.isForOpenJfx, buildInfo.isExecutable, buildInfo.isJ2clCompilable, buildInfo.requiresJavafxEmul);
    }

    static String getScope(Map.Entry<Module, List<ModuleDependency>> moduleGroup, boolean isForGwt, boolean isForOpenJfx, boolean isExecutable, boolean isJ2clCompilable, boolean requiresJavafxEmul) {
        // We take the scope of the first dependency ("default" as temporary value if not present).
        // Note: if a module has both a source and plugin dependency, it's important that the source dependency goes
        // first otherwise the compiler won't be able to compile the code if the scope is just set to "runtime".
        String scope = moduleGroup.getValue().stream().map(ModuleDependency::getScope)
                .map(s -> s == null ? DEFAULT : s)
                .findFirst().orElse(null);
        if (scope != null && !DEFAULT.equals(scope)) // Returning the scope only if explicit in the first dependency
            return scope;
        Module module = moduleGroup.getKey();
        // Setting scope to "provided" for optional dependencies and interface modules
        if (module instanceof ProjectModule) {
            // Optional dependencies
            if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                return PROVIDED;
            // An interface module should have scope "provided" in general (as it will be replaced by another module
            // implementing it in the end), but an exception is that when the implementing module is itself! (which
            // can happen for interface modules providing a default implementation).
            ProjectModule projectModule = ((ProjectModule) module);
            if (projectModule.isInterface() // yes, it's an interface module
                    // So we also check that it's not an implicit provider (which indicates that it was chosen as the implementing module)
                    && moduleGroup.getValue().stream().map(ModuleDependency::getType).noneMatch(type -> type == ModuleDependency.Type.IMPLICIT_PROVIDER))
                return PROVIDED;
        }
        String moduleName = module.getName();
        if (SpecificModules.J2CL_PROCESSORS.equals(moduleName))
            return PROVIDED; // because J2CL processors don't need to be compiled by J2CL
        if (!isExecutable && isJ2clCompilable && SpecificModules.JAVA_NIO_EMUL.equals(moduleName))
            return PROVIDED; // because other environments than J2CL actually don't need it
        if (!isExecutable && isJ2clCompilable && module.isJavaBaseEmulationModule())
            return RUNTIME; // because other environments than J2CL actually don't need it
        if (!isExecutable && SpecificModules.WEBFX_PLATFORM_JAVABASE_EMUL_J2CL.equals(moduleName))
            return RUNTIME;
        if (!isExecutable && SpecificModules.J2CL_TIME.equals(moduleName))
            return RUNTIME;
        if (!isForGwt && !isForOpenJfx && !isExecutable && !requiresJavafxEmul && (SpecificModules.isJavafxModule(moduleName) || SpecificModules.isJavafxEmulModule(moduleName)))
            return PROVIDED;
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
            if (!moduleName.startsWith("gwt-") && !SpecificModules.isElemental2Module(moduleName) && !moduleName.equals(SpecificModules.JAVA_NIO_EMUL) && !moduleName.equals(SpecificModules.ORG_JRESEARCH_GWT_TIME_TZDB))
                return moduleName.endsWith("-emul-gwt") ? SHADED_SOURCES : SOURCES;
        }
        return null;
    }

}
