package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
final public class ModuleRegistry {

    private final Path workspaceDirectory;
    private final Map<String /* library name */, LibraryModule> libraryModules = new HashMap<>();
    private final Map<Path, DevProjectModule> devProjectModules = new HashMap<>();
    private final Map<String /* package name */, List<Module>> packagesModules = new HashMap<>();
    private final ReusableStream<ProjectModule> importedProjectModules;

    /***********************
     ***** Constructor *****
     ***********************/

    public ModuleRegistry(Path workspaceDirectory) {
        this.workspaceDirectory = workspaceDirectory;
        importedProjectModules =
                ReusableStream.of(
                                "webfx",
                                "webfx-platform",
                                "webfx-lib-javacupruntime",
                                "webfx-lib-odometer",
                                "webfx-lib-enzo",
                                "webfx-lib-medusa",
                                "webfx-lib-reusablestream",
                                "webfx-extras",
                                "webfx-stack-platform",
                                "webfx-framework"
                        )
                        .map(p -> getOrCreateDevProjectModule(workspaceDirectory.resolve(p), null))
                        .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                        //.filter(m -> !m.getHomeDirectory().startsWith(rootDirectory))
                        .cache();
    }

    ReusableStream<ProjectModule> getImportedProjectModules() {
        return importedProjectModules;
    }

    ReusableStream<Module> getAllRegisteredModules() {
        return ReusableStream.<Module>empty().concat(
                importedProjectModules,
                devProjectModules.values(),
                libraryModules.values()
        ).distinct();
    }

    /********************************
     ***** Registration methods *****
     ********************************/

    public void registerLibraryModule(LibraryModule module) {
        libraryModules.put(module.getName(), module);
        for (String p : module.getExportedPackages())
            registerPackageModule(p, module);
    }

    private void registerPackageModule(String javaPackage, Module module) {
        List<Module> lm = packagesModules.get(javaPackage);
        if (lm != null && !lm.contains(module)) {
            Module m = lm.get(0);
            String message = module + " and " + m + " share the same package " + javaPackage;
            DevProjectModule projectModule = module instanceof DevProjectModule ? (DevProjectModule) module : m instanceof DevProjectModule ? (DevProjectModule) m : null;
            DevRootModule workingModule = projectModule != null ? projectModule.getRootModule() : null;
            if (workingModule != null && (workingModule.isModuleUnderRootHomeDirectory(module) || workingModule.isModuleUnderRootHomeDirectory(m)))
                Logger.warning(message);
            else // Something happening in a library (not in the developer code)
                Logger.verbose(message);
            // Should always return, the exception is a hack to replace m = webfx-kit-gwt with module = webfx-kit-peers-extracontrols (they share the same package dev.webfx.extras.cell.collator.grid)
            //if (!(m instanceof ProjectModule) || ((ProjectModule) m).getTarget().isPlatformSupported(Platform.JRE))
            //    return;
        }
        if (lm == null)
            packagesModules.put(javaPackage, lm = new ArrayList<>(1));
        lm.add(module);
    }

    void registerLibrariesAndJavaPackagesOfProjectModule(ProjectModule module) {
        module.registerLibraryModules();
        module.getDeclaredJavaPackages().forEach(p -> registerPackageModule(p, module));
        module.getWebFxModuleFile().getExplicitExportedPackages().forEach(p -> registerPackageModule(p, module));
    }

    Module getJavaPackageModuleNow(String packageToSearch, ProjectModule sourceModule, boolean canReturnNull) {
        List<Module> lm = packagesModules.get(packageToSearch);
        Module module = lm == null ? null : lm.stream().filter(m -> isSuitableModule(m, sourceModule))
                .findFirst()
                .orElse(null);
        if (module == null) { // Module not found :-(
            // Last chance: the package was actually in the source package! (ex: webfx-kit-extracontrols-registry-spi)
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
        if (pm.isImplementingInterface() && !sourceModule.isExecutable() && pm instanceof DevProjectModule) {
            DevProjectModule lpm = (DevProjectModule) pm;
            // Exception is however made for non executable source modules that implements a provider
            // Ex: webfx-kit-extracontrols-registry-javafx can include webfx-kit-extracontrols-registry-spi (which implements webfx-kit-extracontrols-registry)
            boolean exception = sourceModule.getProvidedJavaServices().anyMatch(s -> lpm.getDeclaredJavaFiles().anyMatch(c -> c.getClassName().equals(s)));
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

    public Module findLibraryOrModuleOrAlreadyRegistered(String name) {
        Module module = findLibraryAlreadyRegistered(name);
        if (module == null)
            module = packagesModules.values().stream().flatMap(Collection::stream).filter(m -> m.getName().equals(name)).findFirst().orElse(null);
        return module;
    }

    LibraryModule findLibraryAlreadyRegistered(String artifactId) {
        return libraryModules.get(artifactId);
    }

    public DevProjectModule getOrCreateDevProjectModule(Path projectDirectory) {
        DevProjectModule module = getDevProjectModule(projectDirectory);
        if (module != null)
            return module;
        if (!projectDirectory.equals(workspaceDirectory) && projectDirectory.startsWith(workspaceDirectory)) {
            Path parentDirectory = projectDirectory.getParent();
            DevProjectModule projectModule = parentDirectory.equals(workspaceDirectory) ? null : getOrCreateDevProjectModule(parentDirectory);
            return createDevProjectModule(projectDirectory, projectModule);
        }
        throw new BuildException("projectDirectory (" + projectDirectory + ") must be under workspace directory (" + workspaceDirectory + ")");
    }

    DevProjectModule getOrCreateDevProjectModule(Path projectDirectory, DevProjectModule parentModule) {
        DevProjectModule module = getDevProjectModule(projectDirectory);
        if (module == null)
            module = createDevProjectModule(projectDirectory, parentModule);
        return module;
    }

    DevProjectModule getDevProjectModule(Path projectDirectory) {
        return devProjectModules.get(projectDirectory);
    }

    private DevProjectModule createDevProjectModule(Path projectDirectory, DevProjectModule parentModule) {
        DevProjectModule module =
                parentModule != null && projectDirectory.startsWith(parentModule.getHomeDirectory()) ?
                        new DevProjectModule(projectDirectory, parentModule) :
                        new DevRootModule(projectDirectory, this);
        devProjectModules.put(projectDirectory, module);
        return module;
    }
}
