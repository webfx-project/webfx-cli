package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author Bruno Salmon
 */
public final class RootModule extends ProjectModule {

    private final ModuleRegistry moduleRegistry;
    private final ReusableStream<ProjectModule> packageModuleSearchScopeResume;
    private final ReusableStream<Collection<Module>> cyclicDependencyLoopsCache;
    private boolean inlineWebfxParent;

    /***********************
     ***** Constructor *****
     ***********************/

    RootModule(Path rootDirectory, ModuleRegistry moduleRegistry) {
        super(rootDirectory.toAbsolutePath(), null);
        this.moduleRegistry = moduleRegistry;
        packageModuleSearchScopeResume =
                getProjectModuleSearchScope()
                        .resume();
        cyclicDependencyLoopsCache =
                getThisAndChildrenModulesInDepth()
                        .flatMap(RootModule::analyzeCyclicDependenciesLoops)
                        .distinct()
                        .cache();
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    ReusableStream<ProjectModule> getProjectModuleSearchScope() {
        return ReusableStream.concat(
                getThisAndChildrenModulesInDepth(),
                moduleRegistry.getLibraryProjectModules());
    }

    public boolean isInlineWebfxParent() {
        return inlineWebfxParent;
    }

    public void setInlineWebfxParent(boolean inlineWebfxParent) {
        this.inlineWebfxParent = inlineWebfxParent;
    }

    /********************************
     ***** Registration methods *****
     ********************************/

    public void registerLibraryModule(LibraryModule module) {
        moduleRegistry.registerLibraryModule(module);
    }

    void registerLibrariesAndJavaPackagesOfProjectModule(ProjectModule module) {
        moduleRegistry.registerLibrariesAndJavaPackagesOfProjectModule(module);
    }

    private Module findLibraryOrModuleOrAlreadyRegistered(String name) {
        return moduleRegistry.findLibraryOrModuleOrAlreadyRegistered(name);
    }

    Module getJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        Module module = moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true);
        if (module != null)
            return module;
        searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(m -> moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true) != null);
        return moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, false);
    }

    public Module findModule(String name) {
        return findModule(name, false);
    }

    public Module findModule(String name, boolean silent) {
        // Maybe the module is already registered? (the fastest case)
        Module module = findLibraryOrModuleOrAlreadyRegistered(name);
        if (module == null) { // If not yet registered,
            // Let's continue searching it within the search scope but first without registering the declared libraries and packages (first pass - quicker than second pass)
            module = searchProjectModuleWithinSearchScopeWithoutRegisteringLibrariesAndPackages(name, true);
            if (module == null) { // If not found,
                // Let's do that search again (quick to redo with cache) but with the registering process (second pass)
                searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(m -> moduleRegistry.findLibraryAlreadyRegistered(name) != null);
                // Let's finally check if that search was fruitful
                module = findLibraryOrModuleOrAlreadyRegistered(name);
                if (module == null && !silent) // If still not found, we just don't know this module!
                    throw new UnresolvedException("Unknown module " + name);
            }
        }
        return module;
    }

    private void searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(Predicate<? super ProjectModule> untilPredicate) {
        Predicate<? super ProjectModule> whilePredicate = untilPredicate.negate();
        packageModuleSearchScopeResume.takeWhile(whilePredicate)
                .forEach(this::registerLibrariesAndJavaPackagesOfProjectModule);
    }

    /*****************************
     ***** Analyzing streams *****
     *****************************/

    static Collection<Collection<Module>> analyzeDependenciesPathsBetween(Module sourceModule, Module destinationModule) {
        return analyzeDependenciesPathsBetween(new ArrayList<>(), sourceModule, destinationModule);
    }

    private static Collection<Collection<Module>> analyzeDependenciesPathsBetween(Collection<Module> parentPath, Module sourceModule, Module destinationModule) {
        Collection<Collection<Module>> paths = new ArrayList<>();
        if (!parentPath.contains(sourceModule)) { // Skipping cyclic dependencies
            Collection<Module> extendedPath = extendModuleCollection(parentPath, sourceModule);
            if (destinationModule == sourceModule)
                paths.add(extendedPath);
            else if (sourceModule instanceof ProjectModule)
                ((ProjectModule) sourceModule).getDirectModules()
                        .map(depModule -> analyzeDependenciesPathsBetween(extendedPath, depModule, destinationModule))
                        .forEach(paths::addAll);
        }
        return paths;
    }

    ReusableStream<Collection<Module>> analyzeCyclicDependenciesLoops() {
        return cyclicDependencyLoopsCache;
    }

    static List<Collection<Module>> analyzeCyclicDependenciesLoops(Module module) {
        return analyzeCyclicDependenciesLoops(new ArrayList<>(), module);
    }

    private static List<Collection<Module>> analyzeCyclicDependenciesLoops(List<Module> parentPath, Module module) {
        List<Collection<Module>> paths = new ArrayList<>();
        int index = parentPath.indexOf(module);
        if (index != -1) { // Cyclic dependency found
            List<Module> cyclicPath = new ArrayList<>();
            while (index < parentPath.size())
                cyclicPath.add(parentPath.get(index++));
            cyclicPath.add(module);
            paths.add(cyclicPath);
        } else if (module instanceof ProjectModule) {
            List<Module> extendedPath = extendModuleCollection(parentPath, module);
            ((ProjectModule) module).getDirectModules()
                    .map(depModule -> analyzeCyclicDependenciesLoops(extendedPath, depModule))
                    .forEach(paths::addAll);
        }
        return paths;
    }

    Collection<Module> getModulesInCyclicDependenciesLoop(Module m1, Module m2) {
        return analyzeCyclicDependenciesLoops()
                .filter(loop -> loop.contains(m1) && loop.contains(m2))
                .findFirst()
                .orElse(null);
    }

    ProjectModule findModuleDeclaringJavaService(String javaService) {
        return getThisAndChildrenModulesInDepth()
                .filter(m -> m.declaresJavaService(javaService))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unable to find " + javaService + " service declaration module"))
                ;
    }

    ReusableStream<ProjectModule> findModulesProvidingJavaService(String javaService) {
        return getThisAndChildrenModulesInDepth()
                .filter(m -> m.providesJavaService(javaService))
                ;
    }

    ProjectModule findBestMatchModuleProvidingJavaService(String javaService, TargetTag... tags) {
        return findBestMatchModuleProvidingJavaService(javaService, new Target(tags));
    }

    ProjectModule findBestMatchModuleProvidingJavaService(String javaService, Target requestedTarget) {
        return findBestMatchModuleProvidingJavaService(getThisAndChildrenModulesInDepth(), javaService, requestedTarget);
    }

    static ProjectModule findBestMatchModuleProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, TargetTag... tags) {
        return findBestMatchModuleProvidingJavaService(implementationScope, javaService, new Target(tags));
    }

    static ProjectModule findBestMatchModuleProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, Target requestedTarget) {
        return findModulesProvidingJavaService(implementationScope, javaService, requestedTarget, true).iterator().next();
    }

    public static ReusableStream<ProjectModule> findModulesProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, Target requestedTarget, boolean keepBestOnly) {
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

    private static List<Module> extendModuleCollection(Collection<Module> parentPath, Module module) {
        List<Module> newCollection = new ArrayList<>(parentPath);
        newCollection.add(module);
        return newCollection;
    }


    public static boolean isJavaFxEmulModule(Module module) {
        return isJavaFxEmulModule(module.getName());
    }

    public static boolean isJavaFxEmulModule(String moduleName) {
        return moduleName.startsWith("webfx-kit-javafx") && moduleName.endsWith("-emul");
    }
}
