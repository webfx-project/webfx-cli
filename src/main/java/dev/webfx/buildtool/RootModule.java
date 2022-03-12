package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;

public interface RootModule extends ProjectModule {

    ModuleRegistry getModuleRegistry();

    boolean isInlineWebFxParent();

    void setInlineWebFxParent(boolean inlineWebFxParent);

    default Module findModule(String name) {
        return findModule(name, false);
    }

    default ReusableStream<ProjectModule> getProjectModuleSearchScope() {
        return ReusableStream.concat(
                getThisAndChildrenModulesInDepth(),
                getModuleRegistry().getImportedProjectModules());
    }

    /********************************
     ***** Registration methods *****
     ********************************/

    default void registerLibraryModule(LibraryModule module) {
        getModuleRegistry().registerLibraryModule(module);
    }

    default void registerLibrariesAndJavaPackagesOfProjectModule(ProjectModule module) {
        getModuleRegistry().registerLibrariesAndJavaPackagesOfProjectModule(module);
    }

    private Module findLibraryOrModuleOrAlreadyRegistered(String name) {
        return getModuleRegistry().findLibraryOrModuleOrAlreadyRegistered(name);
    }

    default Module getJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        ModuleRegistry moduleRegistry = getModuleRegistry();
        Module module = moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true);
        if (module != null)
            return module;
        searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(m -> moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true) != null);
        return moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, false);
    }

    default Module findModule(String name, boolean silent) {
        // Maybe the module is already registered? (the fastest case)
        Module module = findLibraryOrModuleOrAlreadyRegistered(name);
        if (module == null) { // If not yet registered,
            // Let's continue searching it within the search scope but first without registering the declared libraries and packages (first pass - quicker than second pass)
            module = searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages(name, true);
            if (module == null) { // If not found,
                // Let's do that search again (quick to redo with cache) but with the registering process (second pass)
                searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(m -> getModuleRegistry().findLibraryAlreadyRegistered(name) != null);
                // Let's finally check if that search was fruitful
                module = findLibraryOrModuleOrAlreadyRegistered(name);
                if (module == null && !silent) // If still not found, we just don't know this module!
                    throw new UnresolvedException("Unknown module " + name);
            }
        }
        return module;
    }

    default void searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(Predicate<? super ProjectModule> untilPredicate) {
        Predicate<? super ProjectModule> whilePredicate = untilPredicate.negate();
        getPackageModuleSearchScopeResume().takeWhile(whilePredicate)
                .forEach(this::registerLibrariesAndJavaPackagesOfProjectModule);
    }

    ReusableStream<ProjectModule> getPackageModuleSearchScopeResume();

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
