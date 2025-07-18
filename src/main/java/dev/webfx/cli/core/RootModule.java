package dev.webfx.cli.core;

import dev.webfx.cli.util.sort.TopologicalSort;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface RootModule extends ProjectModule {

    ModuleRegistry getModuleRegistry();

    boolean isInlineWebFxParent();

    void setInlineWebFxParent(boolean inlineWebFxParent);

    /********************************
     ***** Registration methods *****
     ********************************/

    default Module searchJavaPackageModule(String packageToSearch, ProjectModule sourceModule) {
        return searchJavaPackageModule(packageToSearch, sourceModule, false);
    }


    default Module searchJavaPackageModule(String packageToSearch, ProjectModule sourceModule, boolean canReturnNull) {
        ModuleRegistry moduleRegistry = getModuleRegistry();
        // Trying a quick search
        Module module = moduleRegistry.getDeclaredJavaPackageModule(packageToSearch, sourceModule, true);
        if (module == null)
            searchDeclaredModule(m -> {
                //System.out.println(m);
                return moduleRegistry.getDeclaredJavaPackageModule(packageToSearch, sourceModule, true) != null;
            }, true);
        if (module == null && !canReturnNull) // Fruitless search but silent so far, now raising an exception by doing a non-silent search
            module = moduleRegistry.getDeclaredJavaPackageModule(packageToSearch, sourceModule, false);
        return module;
    }

    static ReusableStream<ProjectModule> findModulesProvidingJavaService(ReusableStream<ProjectModule> implementationScope, String javaService, ProjectModuleImpl targetModule, boolean keepBestOnly) {
        // Searching modules within the scope that are compatible with the requested target and that implement the service
        Target requestedTarget = targetModule.getTarget();
        ReusableStream<ProjectModule> modules = implementationScope
            .filter(m -> !m.isDeprecated())
            .filter(m -> !m.isPreview()) // TODO: add preview mode allowing preview modules
            .filter(m -> m.isCompatibleWithTarget(requestedTarget))
            .filter(m -> m.providesJavaService(javaService));
        // If we have several modules and need to keep the best only (ex: required service),
        if (keepBestOnly && modules.count() > 1) {
            // We collect the modules into a list to ease manipulation, and we will search for the best module in that list
            List<? extends ProjectModule> modulesList = modules.collect(Collectors.toList());
            // 1) First criterion = if explicitly listed as plugin-module in the target module or application module,
            // this is the main point of control for the developer to force a specific service implementation
            ReusableStream<Module> directPluginModules = targetModule.getWebFxModuleFile().getPluginModuleDependencies().map(ModuleDependency::getDestinationModule);
            ReusableStream<Module> directApplicationPluginModules = targetModule.getApplicationModule().getWebFxModuleFile().getPluginModuleDependencies().map(ModuleDependency::getDestinationModule);
            for (ProjectModule module : modulesList) {
                if (directPluginModules.anyMatch(m -> m.equals(module)) || directApplicationPluginModules.anyMatch(m -> m.equals(module))) {
                    return ReusableStream.of(module);
                }
            }
            // 2) Second criterion = the platform target. If one fits better than others for this criterion, we keep it
            int bestGrade = -1;
            boolean allSameGrade = true;
            ProjectModule bestGradeModule = null;
            for (ProjectModule pm : modulesList) {
                int grade = pm.gradeTargetMatch(requestedTarget);
                if (bestGradeModule != null && grade != bestGrade)
                    allSameGrade = false;
                if (grade > bestGrade) {
                    bestGrade = grade;
                    bestGradeModule = pm;
                }
            }
            if (!allSameGrade)
                return ReusableStream.of(bestGradeModule);
            // 3) Third criterion = dev modules. Modules implemented by developers are preferred over non-dev modules (such as webfx libraries)
            List<DevProjectModule> devModulesList = modulesList.stream()
                .filter(m -> m instanceof DevProjectModule)
                .map(DevProjectModule.class::cast)
                .collect(Collectors.toList());
            // If there is only one dev module, he is the preferred one and we return it
            if (devModulesList.size() == 1)
                return ReusableStream.of(devModulesList.get(0));
            // If there are several dev modules, we continue, but we get rid of possible other non-dev modules.
            if (!devModulesList.isEmpty())
                modulesList = devModulesList;
            // 4) Fourth criterion = the modules position in dependencies (we keep the one closest to the target module)
            // Creating the dependency graph of the transitive modules starting from the target module (probably executable module)
            Map<ProjectModule, List<ProjectModule>> dependencyGraph =
                // Note: calling get getTransitiveDependencies() at this point seems to freeze its transitive
                // dependencies before their final computation completion (so they remain incomplete and this
                // has big consequences afterward). Calling getTransitiveDependenciesWithoutImplicitProviders()
                // instead is working fine so far without creating subsequent problems.
                targetModule.getProjectModulesDependencyGraph(false);
            // Now we do a topological sort of all modules in the dependency graph
            List<ProjectModule> sortedModules = TopologicalSort.sortDesc(dependencyGraph);
            // Going back to our modules list, we sort it following the same order as the topological sort
            modulesList.sort(Comparator.comparingInt(pm -> {
                int indexOf = sortedModules.indexOf(pm);
                return indexOf != -1 ? indexOf : Integer.MAX_VALUE;
            }));
            // The first module in the list should be now the closest from the target module from the topological point of view
            modules = ReusableStream.of(modulesList.get(0));
        }
        return modules;
    }

}
