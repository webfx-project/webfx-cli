package dev.webfx.buildtool;

import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Bruno Salmon
 */
final public class ModuleRegistry {

    private final Path workspaceDirectory;
    private final Map<String /* library name */, LibraryModule> libraryModules = new HashMap<>();
    private final Map<Path, LocalProjectModule> localProjectModules = new HashMap<>();
    private final Map<String /* package name */, List<Module>> packagesModules = new HashMap<>();
    private final ReusableStream<ProjectModule> importedProjectModules;

    /***********************
     ***** Constructor *****
     ***********************/

    public ModuleRegistry(Path workspaceDirectory, String webFxExportFilePath) {
        this(workspaceDirectory, webFxExportFilePath == null ? null : Path.of(webFxExportFilePath));
    }

    public ModuleRegistry(Path workspaceDirectory, Path webFxExportFilePath) {
        this.workspaceDirectory = workspaceDirectory;
        if (webFxExportFilePath == null)
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
                                    "webfx-extras-flexbox",
                                    "webfx-extras-materialdesign",
                                    "webfx-extras-webtext",
                                    "webfx-extras-visual",
                                    "webfx-extras-visual-charts",
                                    "webfx-extras-visual-grid",
                                    "webfx-extras-cell",
                                    "webfx-stack-platform",
                                    "webfx-framework"
                            )
                            .map(p -> getOrCreateLocalProjectModule(workspaceDirectory.resolve(p), null))
                            .flatMap(ProjectModule::getThisAndChildrenModulesInDepth)
                            //.filter(m -> !m.getHomeDirectory().startsWith(rootDirectory))
                            .cache();
        else {
            Document document = XmlUtil.parseXmlFile(webFxExportFilePath.toFile());
            XmlUtil.nodeListToReusableStream(XmlUtil.lookupNodeList(document, "//library"), LibraryModule::new)
                    .forEach(this::registerLibraryModule);
            NodeList nodeList = XmlUtil.lookupNodeList(document, "exported-modules//project");
            Map<String, Element> moduleElementsToImport = new HashMap<>();
            Map<String, ProjectModule> importedModules = new HashMap<>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element element = (Element) nodeList.item(i);
                moduleElementsToImport.put(XmlUtil.getAttributeValue(element, "name"), element);
            }
            importModules(moduleElementsToImport, importedModules);
            importedProjectModules = ReusableStream.fromIterable(importedModules.values());
        }
    }

    private void importModules(Map<String, Element> moduleElementsToImport, Map<String, ProjectModule> importedModules) {
        List<String> moduleNames = new ArrayList<>(moduleElementsToImport.keySet());
        for (String moduleName : moduleNames)
            importModule(moduleName, moduleElementsToImport, importedModules);
    }

    private ProjectModule importModule(String moduleName, Map<String, Element> moduleElementsToImport, Map<String, ProjectModule> importedModules) {
        ProjectModule projectModule = importedModules.get(moduleName);
        if (projectModule == null) {
            Element moduleElement = moduleElementsToImport.remove(moduleName);
            if (moduleElement != null) {
                String parentModuleName = XmlUtil.getAttributeValue(moduleElement, "parent");
                if (parentModuleName == null)
                    importedModules.put(moduleName, projectModule = new ImportedRootModule(moduleElement, this));
                else {
                    ProjectModule parentModule = importModule(parentModuleName, moduleElementsToImport, importedModules);
                    if (parentModule == null)
                        parentModule = new ImportedRootModule(parentModuleName, this);
                    importedModules.put(moduleName, projectModule = new ImportedProjectModule(moduleElement, parentModule));
                }
            }
        }
        return projectModule;
    }

    ReusableStream<ProjectModule> getImportedProjectModules() {
        return importedProjectModules;
    }

    ReusableStream<Module> getAllRegisteredModules() {
        return ReusableStream.<Module>empty().concat(
                importedProjectModules,
                localProjectModules.values(),
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
            LocalProjectModule projectModule = module instanceof LocalProjectModule ? (LocalProjectModule) module : m instanceof LocalProjectModule ? (LocalProjectModule) m : null;
            LocalRootModule workingModule = projectModule != null ? projectModule.getRootModule() : null;
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
        if (pm.isImplementingInterface() && !sourceModule.isExecutable() && pm instanceof LocalProjectModule) {
            LocalProjectModule lpm = (LocalProjectModule) pm;
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

    public LocalProjectModule getOrCreateLocalProjectModule(Path projectDirectory) {
        LocalProjectModule module = getLocalProjectModule(projectDirectory);
        if (module != null)
            return module;
        if (!projectDirectory.equals(workspaceDirectory) && projectDirectory.startsWith(workspaceDirectory)) {
            Path parentDirectory = projectDirectory.getParent();
            LocalProjectModule projectModule = parentDirectory.equals(workspaceDirectory) ? null : getOrCreateLocalProjectModule(parentDirectory);
            return createLocalProjectModule(projectDirectory, projectModule);
        }
        throw new BuildException("projectDirectory (" + projectDirectory + ") must be under workspace directory (" + workspaceDirectory + ")");
    }

    LocalProjectModule getOrCreateLocalProjectModule(Path projectDirectory, LocalProjectModule parentModule) {
        LocalProjectModule module = getLocalProjectModule(projectDirectory);
        if (module == null)
            module = createLocalProjectModule(projectDirectory, parentModule);
        return module;
    }

    LocalProjectModule getLocalProjectModule(Path projectDirectory) {
        return localProjectModules.get(projectDirectory);
    }

    private LocalProjectModule createLocalProjectModule(Path projectDirectory, LocalProjectModule parentModule) {
        LocalProjectModule module =
                parentModule != null && projectDirectory.startsWith(parentModule.getHomeDirectory()) ?
                        new LocalProjectModule(projectDirectory, parentModule) :
                        new LocalRootModule(projectDirectory, this);
        localProjectModules.put(projectDirectory, module);
        return module;
    }
}
