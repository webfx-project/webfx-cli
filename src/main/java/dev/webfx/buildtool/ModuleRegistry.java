package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
final public class ModuleRegistry {

    private final Path workspaceDirectory;
    private final Map<String, LibraryModule> libraryModules = new HashMap<>();
    private final Map<Path, ProjectModule> projectModules = new HashMap<>();
    private final Map<String /* package name */, List<Module>> javaPackagesModules = new HashMap<>();
    private final ReusableStream<ProjectModule> libraryProjectModules;

    /***********************
     ***** Constructor *****
     ***********************/

    public ModuleRegistry(String rootDirectory, String... libraryModulesHomePaths) {
        this(Path.of(rootDirectory), libraryModulesHomePaths);
    }

    public ModuleRegistry(Path rootDirectory, String... libraryModulesHomePaths) {
        this(rootDirectory, Arrays.stream(libraryModulesHomePaths).map(Path::of).toArray(Path[]::new));
    }

    public ModuleRegistry(Path workspaceDirectory, Path... libraryModulesHomePaths) {
        this.workspaceDirectory = workspaceDirectory;
        libraryProjectModules =
                ReusableStream.of(libraryModulesHomePaths)
                        .map(p -> getOrCreateProjectModule(workspaceDirectory.resolve(p), null))
                        .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                        //.filter(m -> !m.getHomeDirectory().startsWith(rootDirectory))
                        .cache();
    }

    ReusableStream<ProjectModule> getLibraryProjectModules() {
        return libraryProjectModules;
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
            String message = module + " and " + m + " share the same package " + javaPackage;
            ProjectModule projectModule = module instanceof ProjectModule ? (ProjectModule) module : m instanceof ProjectModule ? (ProjectModule) m : null;
            RootModule workingModule = projectModule != null ? projectModule.getRootModule() : null;
            if (workingModule != null && (workingModule.isModuleUnderRootHomeDirectory(module) || workingModule.isModuleUnderRootHomeDirectory(m)))
                Logger.warning(message);
            else // Something happening in a library (not in the developer code)
                Logger.verbose(message);
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

   Module getJavaPackageModuleNow(String packageToSearch, ProjectModule sourceModule, boolean canReturnNull) {
        List<Module> lm = javaPackagesModules.get(packageToSearch);
        Module module = lm == null ? null : lm.stream().filter(m -> isSuitableModule(m, sourceModule))
                .findFirst()
                .orElse(null);
        if (module == null) { // Module not found :-(
            // Last chance: the package was actually in the source package! (ex: webfx-kit-extracontrols-registry-spi
            if (sourceModule.getDeclaredJavaPackages().anyMatch(p -> p.equals(packageToSearch)))
                module = sourceModule;
            else if (!canReturnNull) // Otherwise raising an exception (unless returning null is permitted)
                throw new UnresolvedException("Unresolved module for package " + packageToSearch + " (used by " + sourceModule + ")");
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
            boolean exception = sourceModule.getProvidedJavaServices().anyMatch(s -> pm.getDeclaredJavaFiles().anyMatch(c -> c.getClassName().equals(s)));
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
        if (module == null)
            module = javaPackagesModules.values().stream().flatMap(Collection::stream).filter(m -> m.getName().equals(name)).findFirst().orElse(null);
        return module;
    }

    LibraryModule getLibraryModule(String artifactId) {
        return libraryModules.get(artifactId);
    }

    public ProjectModule getOrCreateProjectModule(Path projectDirectory) {
        ProjectModule module = getProjectModule(projectDirectory);
        if (module != null)
            return module;
        if (!projectDirectory.equals(workspaceDirectory) && projectDirectory.startsWith(workspaceDirectory)) {
            Path parentDirectory = projectDirectory.getParent();
            ProjectModule projectModule = parentDirectory.equals(workspaceDirectory) ? null : getOrCreateProjectModule(parentDirectory);
            return createProjectModule(projectDirectory, projectModule);
        }
        throw new BuildException("projectDirectory (" + projectDirectory + ") must be under workspace directory (" + workspaceDirectory + ")");
    }

    ProjectModule getOrCreateProjectModule(Path projectDirectory, ProjectModule parentModule) {
        ProjectModule module = getProjectModule(projectDirectory);
        if (module == null)
            module = createProjectModule(projectDirectory, parentModule);
        return module;
    }

    ProjectModule getProjectModule(Path projectDirectory) {
        return projectModules.get(projectDirectory);
    }

    private ProjectModule createProjectModule(Path projectDirectory, ProjectModule parentModule) {
        ProjectModule module =
                parentModule != null && projectDirectory.startsWith(parentModule.getHomeDirectory()) ?
                        new ProjectModule(projectDirectory, parentModule) :
                        new RootModule(projectDirectory, this);
        projectModules.put(projectDirectory, module);
        return module;
    }
}
