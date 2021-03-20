package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
public final class RootModule extends ProjectModule {

    private final Map<String, Module> libraryModules = new HashMap<>();
    private final Map<String /* package name */, List<Module>> javaPackagesModules = new HashMap<>();
    private final ReusableStream<ProjectModule> packageModuleSearchScopeResume;
    private final ReusableStream<Collection<Module>> cyclicDependencyLoopsCache;

    /***********************
     ***** Constructor *****
     ***********************/

    private final ReusableStream<ProjectModule> libraryProjectModules;

    public RootModule(String rootDirectory, String... libraryModulesHomePaths) {
        this(Path.of(rootDirectory), Arrays.stream(libraryModulesHomePaths).map(Path::of).toArray(Path[]::new));
    }

    public RootModule(Path rootDirectory, Path... libraryModulesHomePaths) {
        super(rootDirectory);
        libraryProjectModules =
                ReusableStream.of(libraryModulesHomePaths)
                .map(p -> new ProjectModule(p, this))
                .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                .cache();
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
    ReusableStream<ProjectModule> getProjectModuleSearchScope() {
        return ReusableStream.concat(
                getChildrenModulesInDepth(),
                libraryProjectModules);
    }

    /********************************
     ***** Registration methods *****
     ********************************/

    public void registerLibraryModule(LibraryModule module) {
        libraryModules.put(module.getName(), module);
        for (String javaPackage : module.getJavaPackages())
            registerJavaPackageModule(javaPackage, module);
    }

    private void registerJavaPackageModule(String javaPackage, Module module) {
        List<Module> lm = javaPackagesModules.get(javaPackage);
        if (lm != null && !lm.contains(module)) {
            Module m = lm.get(0);
            warning(module + " and " + m + " share the same package " + javaPackage);
            // Should always return, the exception is a hack to replace m = webfx-kit-gwt with module = webfx-kit-peers-extracontrols (they share the same package dev.webfx.extras.cell.collator.grid)
            //if (!(m instanceof ProjectModule) || ((ProjectModule) m).getTarget().isPlatformSupported(Platform.JRE))
            //    return;
        }
        if (lm == null)
            javaPackagesModules.put(javaPackage, lm = new ArrayList<>(1));
        lm.add(module);
    }

    void registerJavaPackagesProjectModule(ProjectModule module) {
        module.registerLibraryModules();
        module.getDeclaredJavaPackages().forEach(javaPackage -> registerJavaPackageModule(javaPackage, module));
    }

    Module getJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        Module module = getJavaPackageModuleNow(packageToSearch, sourceModule, true);
        if (module != null)
            return module;
        packageModuleSearchScopeResume.takeWhile(m -> getJavaPackageModuleNow(packageToSearch, sourceModule, true) == null).forEach(this::registerJavaPackagesProjectModule);
        return getJavaPackageModuleNow(packageToSearch, sourceModule, false);
    }

    private Module getJavaPackageModuleNow(String packageToSearch, ProjectModule sourceModule, boolean canReturnNull) {
        List<Module> lm = javaPackagesModules.get(packageToSearch);
        Module module = lm == null ? null : lm.stream().filter(m -> isSuitableModule(m, sourceModule))
                .findFirst()
                .orElse(null);
        if (module == null) { // Module not found :-(
            // Last chance: the package was actually in the source package! (ex: webfx-kit-extracontrols-registry-spi
            if (sourceModule.getDeclaredJavaPackages().anyMatch(p -> p.equals(packageToSearch)))
                module = sourceModule;
            else if (!canReturnNull) // Otherwise raising an exception (unless returning null is permitted)
                throw new UnresolvedException("Unknown module for package " + packageToSearch + " (requested by " + sourceModule + ")");
        }
        return module;
    }

    private boolean isSuitableModule(Module m, ProjectModule sourceModule) {
        if (!(m instanceof ProjectModule))
            return true;
        ProjectModule pm = (ProjectModule) m;
        // First case: only executable source modules should include implementing interface modules (others should include the interface module instead)
        if (pm.isImplementingInterface() && !sourceModule.isExecutable()) {
            // Exception is however made for non executable source modules that implements a provider
            // Ex: webfx-kit-extracontrols-registry-javafx can include webfx-kit-extracontrols-registry-spi (which implements webfx-kit-extracontrols-registry)
            boolean exception = sourceModule.getProvidedJavaServices().anyMatch(s -> pm.getDeclaredJavaClasses().anyMatch(c -> c.getClassName().equals(s)));
            if (!exception)
                return false;
        }
        // Second not permitted case:
        // Ex: webfx-kit-extracontrols-registry-javafx should not include webfx-kit-extracontrols-registry (but webfx-kit-extracontrols-registry-spi instead)
        if (pm.isInterface()) {
            if (sourceModule.getName().startsWith(pm.getName()))
                return false;
        }
        return true;
    }

    public Module findModule(String name) {
        Module module = libraryModules.get(name);
        if (module == null) {
            module = javaPackagesModules.values().stream().flatMap(Collection::stream).filter(m -> m.getName().equals(name)).findFirst().orElseGet(() -> findProjectModule(name, true));
            if (module == null)
                module = getOrCreateThirdPartyModule(name);
        }
        return module;
    }

    Module getOrCreateThirdPartyModule(String artifactId) {
        Module module = getThirdPartyModule(artifactId);
        if (module == null)
            module = createThirdPartyModule(artifactId);
        return module;
    }

    Module getThirdPartyModule(String artifactId) {
        return libraryModules.get(artifactId);
    }

    private Module createThirdPartyModule(String artifactId) {
        Module module = Module.create(artifactId);
        libraryModules.put(artifactId, module);
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
