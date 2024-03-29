package dev.webfx.cli.core;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
final class ModuleReporter {

    private final Module module;
    private final DevProjectModule projectModule;
    private final DevRootModule rootModule;

    ModuleReporter(Module module) {
        this.module = module;
        projectModule = module instanceof DevProjectModule ? (DevProjectModule) module : null;
        rootModule = projectModule != null ? projectModule.getRootModule() : null;
    }

    /***************************
     ***** Listing methods *****
     ***************************/

    void listJavaClasses() {
        listIterableElements("Listing " + projectModule + " module java classes",
                projectModule.getMainJavaSourceRootAnalyzer().getSourceFiles()
        );
    }

    void listDirectDependencies() {
        listIterableElements("Listing " + projectModule + " module direct dependencies",
                projectModule.getMainJavaSourceRootAnalyzer().getDirectDependencies().map(ModuleDependency::getDestinationModule)
        );
    }

    void listChildrenModulesInDepth() {
        listIterableElements("Listing " + projectModule + " children modules (in depth)",
                projectModule.getChildrenModulesInDepth()
        );
    }

    void listThisAndChildrenModulesInDepthWithTheirDirectDependencies() {
        listIterableElements("Listing " + projectModule + " and children modules (in depth) with their direct dependencies",
                projectModule.getThisAndChildrenModulesInDepth(),
                ModuleReporter::logModuleWithDirectDependencies
        );
    }

    void listThisAndChildrenModulesInDepthTransitiveDependencies() {
        listIterableElements("Listing " + projectModule + " and children modules (in depth) transitive dependencies",
                projectModule.getMainJavaSourceRootAnalyzer().getTransitiveModules()
        );
    }

    void listOrAndChildrenModulesInDepthDirectlyDependingOn(String moduleArtifactId) {
        listIterableElements("Listing " + projectModule + " or children modules (in depth) directly depending on " + moduleArtifactId,
                projectModule.getMainJavaSourceRootAnalyzer().getThisOrChildrenModulesInDepthDirectlyDependingOn(moduleArtifactId)
        );
    }

    void listJavaClassesDependingOn(String destinationModule) {
        listIterableElements("Listing " + projectModule + " module java classes depending on " + destinationModule,
                projectModule.getJavaFilesDependingOn(destinationModule)
                , jc -> logJavaClassWithPackagesDependingOn(jc, destinationModule)
        );
    }


    /***************************
     ***** Listing methods *****
     ***************************/

    //// Listing methods that are just forwarders to the target project module

    ModuleReporter newModuleAnalyzer(String moduleArtifactId) {
        return new ModuleReporter(rootModule.searchRegisteredModule(moduleArtifactId));
    }

    void listProjectModuleJavaClasses(String moduleArtifactId) {
        newModuleAnalyzer(moduleArtifactId).listJavaClasses();
    }

    void listProjectModuleJavaClassesDependingOn(String moduleArtifactId, String destinationModule) {
        newModuleAnalyzer(moduleArtifactId).listJavaClassesDependingOn(destinationModule);
    }

    void listProjectModuleDirectDependencies(String moduleArtifactId) {
        newModuleAnalyzer(moduleArtifactId).listDirectDependencies();
    }

    void listInDepthTransitiveDependencies(String moduleArtifactId) {
        newModuleAnalyzer(moduleArtifactId).listThisAndChildrenModulesInDepthTransitiveDependencies();
    }

    void listDependenciesPathsBetween(String sourceModule, String destinationModule) {
        listDependenciesPathsBetween(rootModule.searchRegisteredModule(sourceModule), rootModule.searchRegisteredModule(destinationModule));
    }

    void listDependenciesPathsBetween(Module sourceModule, Module destinationModule) {
        listIterableElements("Listing dependency paths between " + sourceModule + " and " + destinationModule,
                rootModule.analyzeDependenciesPathsBetween(sourceModule, destinationModule)
        );
    }

    void listCyclicDependenciesPaths() {
        listIterableElements("Listing cyclic dependency paths",
                rootModule.analyzeCyclicDependenciesLoops()
        );
    }

    /***************************
     ***** Logging methods *****
     ***************************/

    private static void logModuleWithDirectDependencies(ProjectModule module) {
        log(module + " direct dependencies: " + module.getMainJavaSourceRootAnalyzer().getDirectModules()
                .collect(Collectors.toList()));
    }

    private static void logJavaClassWithPackagesDependingOn(JavaFile jc, String destinationModule) {
        RootModule rootModule = jc.getProjectModule().getRootModule();
        log(jc + " through packages " +
                jc.getUsedJavaPackages()
                        .filter(p -> destinationModule.equals(rootModule.searchJavaPackageModule(p, jc.getProjectModule()).getName()))
                        .distinct()
                        .collect(Collectors.toList()));
    }

    /**********************************
     ***** Static utility methods *****
     **********************************/

    private static <T> void listIterableElements(String section, Iterable<T> iterable) {
        listIterableElements(section, iterable, ModuleReporter::log);
    }

    private static <T> void listIterableElements(String section, Iterable<T> iterable, Consumer<? super T> elementLogger) {
        logSection(section);
        iterable.forEach(elementLogger);
    }

    private static void logSection(String section) {
        String middle = "***** " + section + " *****";
        String starsLine = Stream.generate(() -> "*").limit(middle.length()).collect(Collectors.joining());
        log("");
        log(starsLine);
        log(middle);
        log(starsLine);
    }

    private static void log(Object message) {
        Logger.log(message.toString());
    }
}
