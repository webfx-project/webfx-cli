package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface RootModule extends ProjectModule {

    ModuleRegistry getModuleRegistry();

    boolean isInlineWebFxParent();

    void setInlineWebFxParent(boolean inlineWebFxParent);

    default Module findModule(String name) {
        return findModule(name, false);
    }

    default ReusableStream<ProjectModule> getProjectModuleSearchScope() {
        // It's important that the search scope is 1) complete and 2) ordered (giving priority to children, etc...)
        ReusableStream<ProjectModule> scope1 = getThisAndChildrenModulesInDepth(); // already cached
        ReusableStream<ProjectModule> scope2 = getModuleRegistry().getImportedProjectModules();
        return ReusableStream.concat(
                scope1,
                scope2);
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
        return getModuleRegistry().findLibraryOrModuleAlreadyRegistered(name);
    }

    default Module getJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        ModuleRegistry moduleRegistry = getModuleRegistry();
        Module module = moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true);
        if (module == null)
            module = continueSearchingAndRegisteringUntilGetting(() -> moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true));
        if (module == null) // Fruitless search but silent so far, now raising an exception by doing a non-silent search
            module = moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, false);
        return module;
    }

    default Module findModule(String name, boolean silent) {
        // Maybe the module is already registered? (the fastest case)
        Module module = findLibraryOrModuleOrAlreadyRegistered(name);
        if (module == null) { // If not yet registered,
            // Let's continue searching it within the search scope but first without registering the declared libraries and packages (first pass - quicker than second pass)
            module = searchProjectModuleWithoutRegistering(name, true);
            if (module == null) { // If not found,
                // Let's do that search again (quick to redo with cache) but with the registering process (second pass)
                module = continueSearchingAndRegisteringUntilGetting(() -> getModuleRegistry().findLibraryAlreadyRegistered(name));
                if (module == null && !silent) // If the search is still fruitless, we just don't know this module!
                    throw new UnresolvedException("Unknown module " + name);
            }
        }
        return module;
    }

    default void continueSearchingAndRegisteringUntil(Predicate<? super ProjectModule> untilPredicate) {
        Predicate<? super ProjectModule> whilePredicate = untilPredicate.negate();
        getProjectModuleSearchScopeResume().takeWhile(whilePredicate)
                .forEach(this::registerLibrariesAndJavaPackagesOfProjectModule);
    }

    default <T> T continueSearchingAndRegisteringUntilGetting(Supplier<T> getter) {
        T result = getter.get();
        if (result == null) {
            Object[] resultHolder = new Object[1];
            continueSearchingAndRegisteringUntil(p -> (resultHolder[0] = getter.get()) != null);
            result = (T) resultHolder[0];
        }
        return result;
    }

    ReusableStream<ProjectModule> getProjectModuleSearchScopeResume();

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
