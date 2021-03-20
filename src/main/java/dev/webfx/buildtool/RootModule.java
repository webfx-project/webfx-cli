package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
public final class RootModule extends ProjectModule {

    private final ModuleRegistry moduleRegistry;
    private final ReusableStream<ProjectModule> packageModuleSearchScopeResume;
    private final ReusableStream<Collection<Module>> cyclicDependencyLoopsCache;

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

    /********************************
     ***** Registration methods *****
     ********************************/

    public void registerLibraryModule(LibraryModule module) {
        moduleRegistry.registerLibraryModule(module);
    }

    void registerJavaPackagesProjectModule(ProjectModule module) {
        moduleRegistry.registerJavaPackagesProjectModule(module);
    }

    Module getJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        Module module = moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true);
        if (module != null)
            return module;
        packageModuleSearchScopeResume.takeWhile(m -> moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, true) == null).forEach(this::registerJavaPackagesProjectModule);
        return moduleRegistry.getJavaPackageModuleNow(packageToSearch, sourceModule, false);
    }

    public Module findOrCreateModule(String name) {
        Module module = findModule(name, true);
        if (module == null)
            module = moduleRegistry.createThirdPartyModule(name);
        return module;
    }

    public Module findModule(String name, boolean silent) {
        Module module = moduleRegistry.findModule(name);
        if (module == null) {
            module = findProjectModule(name, true);
            if (module == null) {
                module = moduleRegistry.getThirdPartyModule(name);
                if (module == null && !silent)
                    throw new UnresolvedException("Unknown module " + name);
            }
        }
        return module;
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
