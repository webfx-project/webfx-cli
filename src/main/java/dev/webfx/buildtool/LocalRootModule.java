package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Bruno Salmon
 */
public final class LocalRootModule extends LocalProjectModule implements RootModule {

    private ModuleRegistry moduleRegistry;
    private boolean inlineWebFxParent;
    private final ReusableStream<ProjectModule> packageModuleSearchScopeResume;

    public LocalRootModule(Path homeDirectory, ModuleRegistry moduleRegistry) {
        super(homeDirectory, null);
        this.moduleRegistry = moduleRegistry;
        packageModuleSearchScopeResume =
                ReusableStream.create(this::getProjectModuleSearchScope) // Using deferred creation because the module registry constructor may not be completed yet
                        .resume();
    }

    @Override
    public boolean isInlineWebFxParent() {
        return inlineWebFxParent;
    }

    @Override
    public void setInlineWebFxParent(boolean inlineWebFxParent) {
        this.inlineWebFxParent = inlineWebFxParent;
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    public ReusableStream<ProjectModule> getPackageModuleSearchScopeResume() {
        return packageModuleSearchScopeResume;
    }

    public void exportModules(Document document) {
        // Making sure all project modules and libraries are registered
        searchProjectModuleWithinSearchScopeAndRegisterLibrariesAndPackagesUntil(pm -> false);
        Element rootElement = document.createElement("exported-modules");
        document.appendChild(rootElement);
        getModuleRegistry().getAllRegisteredModules()
                .forEach(m -> {
                    if (m instanceof ProjectModule) {
                        // Getting the WebFx module file (that manages the webfx.xml file)
                        ProjectModule pm = (ProjectModule) m;
                        // If it's an aggregate module and that the webfx.xml file doesn't exist, we take the maven module file instead
                        LocalProjectModule lpm = pm instanceof LocalProjectModule ? (LocalProjectModule) pm : null;
                        boolean maven = lpm != null && lpm.isAggregate() && !lpm.getWebFxModuleFile().getModuleFile().exists();
                        XmlModuleFile xmlModuleFile = maven ? pm.getMavenModuleFile() : pm.getWebFxModuleFile();
                        // Creating a xml copy of the project element (which is the document element)
                        Element projectElement = (Element) document.importNode(xmlModuleFile.getOrCreateModuleElement(), true);
                        projectElement.removeAttribute("xsi");
                        projectElement.removeAttribute("xsi:schemaLocation");
                        projectElement.removeAttribute("xmlns:xsi");
                        projectElement.removeAttribute("xmlns");
                        // Setting the module name as xml "name" attribute
                        projectElement.setAttribute("name", pm.getName());
                        if (pm.getParentModule() != null)
                            projectElement.setAttribute("parent", pm.getParentModule().getName());
                        else {
                            projectElement.setAttribute("groupId", pm.getGroupId());
                            projectElement.setAttribute("artifactId", pm.getArtifactId());
                            projectElement.setAttribute("version", pm.getVersion());
                        }
                        if (maven)
                            projectElement.setAttribute("maven", "true");
                        else if (XmlUtil.lookupNode(projectElement, "modules") == null) {
                            Element modulesElement = document.createElement("modules");
                            projectElement.appendChild(modulesElement);
                            pm.getMavenModuleFile().getChildrenModuleNames().forEach(name -> XmlUtil.appendTextElement(modulesElement, "/module", name));
                        }
/*
                        // Replacing the exported sources package with their actual computed values
                        Node sourcePackagesNode = XmlUtil.lookupNode(projectElement, "exported-packages/source-packages");
                        if (sourcePackagesNode != null) {
                            Node parentNode = sourcePackagesNode.getParentNode();
                            parentNode.removeChild(sourcePackagesNode);
                            pm.getExportedJavaPackages().forEach(p -> xmlModuleFile.appendTextNode(parentNode, "/package", p));
                        }
                        // Replacing the used by source modules with their actual computed values
                        Node usedBySourceModulesNode = XmlUtil.lookupNode(projectElement, "dependencies/used-by-source-modules");
                        if (usedBySourceModulesNode != null)
                            pm.getDiscoveredByCodeAnalyzerSourceDependencies().forEach(d -> xmlModuleFile.appendTextNode(usedBySourceModulesNode, "/source-module", d.getDestinationModule().getName()));
                        // Appending the result into the group node
                        rootElement.appendChild(projectElement);
                    } else if (m instanceof LibraryModule) {
                        rootElement.appendChild(document.importNode(((LibraryModule) m).getModuleNode(), true));
                    }
                });
    }


    /*****************************
     ***** Analyzing streams *****
     *****************************/

    private final ReusableStream<Collection<Module>> cyclicDependencyLoopsCache =
            ReusableStream.create(this::getThisAndChildrenModulesInDepth) // Using deferred creation because the module registry constructor may not be completed yet
                    .flatMap(LocalRootModule::analyzeCyclicDependenciesLoops)
                    .distinct()
                    .cache();


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

    private static List<Module> extendModuleCollection(Collection<Module> parentPath, Module module) {
        List<Module> newCollection = new ArrayList<>(parentPath);
        newCollection.add(module);
        return newCollection;
    }


    Collection<Module> getModulesInCyclicDependenciesLoop(Module m1, Module m2) {
        return analyzeCyclicDependenciesLoops()
                .filter(loop -> loop.contains(m1) && loop.contains(m2))
                .findFirst()
                .orElse(null);
    }

}
