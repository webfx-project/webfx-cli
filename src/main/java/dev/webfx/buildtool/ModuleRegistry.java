package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.ResWebFxModuleFile;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
final public class ModuleRegistry {

    private final static Map<String /* module name */, LibraryModule> JDK_MODULES = new HashMap<>();
    static {
        // Registering JDK modules (packages registration will be done in the non-static constructor)
        new ResWebFxModuleFile("dev/webfx/buildtool/jdk/webfx.xml").getLibraryModules().forEach(m -> JDK_MODULES.put(m.getName(), m));
    }

    public static boolean isJdkModule(Module module) {
        return isJdkModule(module.getName());
    }

    public static boolean isJdkModule(String name) {
        return JDK_MODULES.containsKey(name);
    }

    private final Path workspaceDirectory;
    private final Map<String /* module name */, LibraryModule> libraryModules = new HashMap<>();
    private final Map<String /* module name */, M2ProjectModule> m2ProjectModules = new HashMap<>();
    private final List<M2RootModule> m2RootModules = new ArrayList<>();
    private final Map<Path, DevProjectModule> devProjectModules = new HashMap<>();
    private final Map<String /* package name */, List<Module>> packagesModules = new HashMap<>();
    private final ReusableStream<ProjectModule> importedProjectModules;

    /***********************
     ***** Constructor *****
     ***********************/

    public ModuleRegistry(Path workspaceDirectory) {
        this.workspaceDirectory = workspaceDirectory;
        // Registering JDK packages
        JDK_MODULES.values().forEach(this::registerLibraryExportedPackages);
        // Registering imported modules
        importedProjectModules =
                ReusableStream.fromIterable(() -> new Iterator<M2RootModule>() {
                    int nextIndex = 0;
                            @Override
                            public boolean hasNext() {
                                return nextIndex < m2RootModules.size();
                            }

                            @Override
                            public M2RootModule next() {
                                return m2RootModules.get(nextIndex++);
                            }
                        })
                        .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                        //.flatMap(ProjectModule::getThisAndTransitiveModules)
                        //.filter(m -> m instanceof ProjectModule)
                        //.map(m -> (ProjectModule) m)
                        //.cache()
        ;
    }

    ReusableStream<ProjectModule> getImportedProjectModules() {
        return importedProjectModules;
    }

    /********************************
     ***** Registration methods *****
     ********************************/

    public void registerLibraryModule(LibraryModule module) {
        if (module.isMavenLibrary()) {
            if (findModuleAlreadyRegistered(module.getName()) == null)
                registerM2ProjectModule(new M2RootModule(module, this));
        } else {
            libraryModules.put(module.getName(), module);
            registerLibraryExportedPackages(module);
        }
    }

    private void registerLibraryExportedPackages(LibraryModule module) {
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
            // Exception is however made for non-executable source modules that implement a provider
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

    public Module findLibraryOrModuleAlreadyRegistered(String name) {
        Module module = findLibraryAlreadyRegistered(name);
        if (module == null)
            module = packagesModules.values().stream().flatMap(Collection::stream).filter(m -> m.getName().equals(name)).findFirst().orElse(null);
        return module;
    }

    LibraryModule findLibraryAlreadyRegistered(String name) {
        LibraryModule module = JDK_MODULES.get(name);
        if (module == null)
            module = libraryModules.get(name);
        return module;
    }

    public Module findModuleAlreadyRegistered(String name) {
        Module module = getM2ProjectModule(name);
        if (module == null)
            module = packagesModules.values().stream().flatMap(Collection::stream).filter(m -> m.getName().equals(name)).findFirst().orElse(null);
        return module;
    }

    DevProjectModule getDevProjectModule(Path projectDirectory) {
        return devProjectModules.get(projectDirectory);
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

    private DevProjectModule createDevProjectModule(Path projectDirectory, DevProjectModule parentModule) {
        DevProjectModule module =
                parentModule != null && projectDirectory.startsWith(parentModule.getHomeDirectory()) ?
                        new DevProjectModule(projectDirectory, parentModule) :
                        new DevRootModule(projectDirectory, this);
        devProjectModules.put(projectDirectory, module);
        return module;
    }

    M2ProjectModule getM2ProjectModule(String name) {
        return m2ProjectModules.get(name);
    }

    public M2ProjectModule getOrCreateM2ProjectModule(String name, M2ProjectModule parentModule) {
        M2ProjectModule module = getM2ProjectModule(name);
        if (module == null)
            module = createM2ProjectModule(name, parentModule);
        return module;
    }

    public M2ProjectModule createM2ProjectModule(String name, M2ProjectModule parentModule) {
        return registerM2ProjectModule(new M2ProjectModule(name, parentModule));
    }

    private M2ProjectModule registerM2ProjectModule(M2ProjectModule module) {
        m2ProjectModules.put(module.getName(), module);
        if (module instanceof M2RootModule)
            m2RootModules.add((M2RootModule) module);
        return module;
    }

}
