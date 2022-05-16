package dev.webfx.tool.cli.core;

import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Bruno Salmon
 */
public final class DevRootModule extends DevProjectModule implements RootModule {

    private final ModuleRegistry moduleRegistry;
    private boolean inlineWebFxParent;

    public DevRootModule(Path homeDirectory, ModuleRegistry moduleRegistry) {
        super(homeDirectory, null);
        this.moduleRegistry = moduleRegistry;
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

    /*****************************
     ***** Analyzing streams *****
     *****************************/

    private final ReusableStream<Collection<Module>> cyclicDependencyLoopsCache =
            ReusableStream.create(this::getThisAndChildrenModulesInDepth) // Using deferred creation because the module registry constructor may not be completed yet
                    .flatMap(DevRootModule::analyzeCyclicDependenciesLoops)
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
