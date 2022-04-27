package dev.webfx.tool.buildtool;

import dev.webfx.lib.reusablestream.ReusableStream;

import java.util.Comparator;
import java.util.Objects;

public interface RootModule extends ProjectModule {

    ModuleRegistry getModuleRegistry();

    boolean isInlineWebFxParent();

    void setInlineWebFxParent(boolean inlineWebFxParent);

    /********************************
     ***** Registration methods *****
     ********************************/

    default Module searchJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        ModuleRegistry moduleRegistry = getModuleRegistry();
        // Trying a quick search
        Module module = moduleRegistry.getDeclaredJavaPackageModule(packageToSearch, sourceModule, true);
        if (module == null)
            searchDeclaredModule(m -> {
                //System.out.println(m);
                return moduleRegistry.getDeclaredJavaPackageModule(packageToSearch, sourceModule, true) != null;
            }, true);
        if (module == null) // Fruitless search but silent so far, now raising an exception by doing a non-silent search
            module = moduleRegistry.getDeclaredJavaPackageModule(packageToSearch, sourceModule, false);
        return module;
    }

    default ProjectModule findModuleDeclaringJavaService(String javaService) {
        return getThisAndChildrenModulesInDepth()
                .filter(m -> m.declaresJavaService(javaService))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find " + javaService + " service declaration module"))
                ;
    }

    default ReusableStream<ProjectModule> findModulesProvidingJavaService(String javaService) {
        return getThisAndChildrenModulesInDepth()
                .filter(m -> m.providesJavaService(javaService))
                ;
    }

    default ProjectModule findBestMatchModuleProvidingJavaService(String javaService, TargetTag... tags) {
        return findBestMatchModuleProvidingJavaService(javaService, new Target(tags));
    }

    default ProjectModule findBestMatchModuleProvidingJavaService(String javaService, Target requestedTarget) {
        return findBestMatchModuleProvidingJavaService(getThisAndChildrenModulesInDepth(), javaService, requestedTarget);
    }

    static ProjectModule findBestMatchModuleProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, TargetTag... tags) {
        return findBestMatchModuleProvidingJavaService(implementationScope, javaService, new Target(tags));
    }

    static ProjectModule findBestMatchModuleProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, Target requestedTarget) {
        return findModulesProvidingJavaService(implementationScope, javaService, requestedTarget, true).iterator().next();
    }

    static ReusableStream<ProjectModule> findModulesProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, Target requestedTarget, boolean keepBestOnly) {
        ReusableStream<ProjectModule> modules = implementationScope
                .filter(m -> m.isCompatibleWithTarget(requestedTarget))
                .filter(m -> m.providesJavaService(javaService));
        if (keepBestOnly)
            modules = ReusableStream.of(modules
                            .max(Comparator.comparingInt(m -> m.gradeTargetMatch(requestedTarget)))
                            .orElse(null)
                    //.orElseThrow(() -> new IllegalArgumentException("Unable to find " + javaService + " service implementation for requested target " + requestedTarget + " within " + implementationScope.collect(Collectors.toList())))
            ).filter(Objects::nonNull);
        return modules;
    }

    /**********************************
     ***** Static utility methods *****
     **********************************/


    static boolean isJavaFxEmulModule(Module module) {
        return isJavaFxEmulModule(module.getName());
    }

    static boolean isJavaFxEmulModule(String moduleName) {
        return moduleName.startsWith("webfx-kit-javafx") && moduleName.endsWith("-emul");
    }

}
