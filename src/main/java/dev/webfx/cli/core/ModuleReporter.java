package dev.webfx.cli.core;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
public final class ModuleReporter {

    private final Module module;
    private final DevProjectModule projectModule;
    private final DevRootModule rootModule;

    public ModuleReporter(Module module) {
        this.module = module;
        projectModule = module instanceof DevProjectModule ? (DevProjectModule) module : null;
        rootModule = projectModule != null ? projectModule.getRootModule() : null;
    }

    /***************************
     ***** Listing methods *****
     ***************************/

    public void listJavaClasses() {
        listIterableElements("Listing " + projectModule + " module java classes",
                projectModule.getJavaSourceFiles()
        );
    }

    public void listDirectDependencies() {
        listIterableElements("Listing " + projectModule + " module direct dependencies",
                projectModule.getDirectDependencies().map(ModuleDependency::getDestinationModule)
        );
    }

    public void listChildrenModulesInDepth() {
        listIterableElements("Listing " + projectModule + " children modules (in depth)",
                projectModule.getChildrenModulesInDepth()
        );
    }

    public void listThisAndChildrenModulesInDepthWithTheirDirectDependencies() {
        listIterableElements("Listing " + projectModule + " and children modules (in depth) with their direct dependencies",
                projectModule.getThisAndChildrenModulesInDepth(),
                ModuleReporter::logModuleWithDirectDependencies
        );
    }

    public void listThisAndChildrenModulesInDepthTransitiveDependencies() {
        listIterableElements("Listing " + projectModule + " and children modules (in depth) transitive dependencies",
                projectModule.getTransitiveModules()
        );
    }

    public void listOrAndChildrenModulesInDepthDirectlyDependingOn(String moduleArtifactId) {
        listIterableElements("Listing " + projectModule + " or children modules (in depth) directly depending on " + moduleArtifactId,
                projectModule.getThisOrChildrenModulesInDepthDirectlyDependingOn(moduleArtifactId)
        );
    }

    public void listJavaClassesDependingOn(String destinationModule) {
        listIterableElements("Listing " + projectModule + " module java classes depending on " + destinationModule,
                projectModule.getJavaFilesDependingOn(destinationModule)
                , jc -> logJavaClassWithPackagesDependingOn(jc, destinationModule)
        );
    }


    /***************************
     ***** Listing methods *****
     ***************************/

    //// Listing methods that are just forwarders to the target project module

    public ModuleReporter newModuleAnalyzer(String moduleArtifactId) {
        return new ModuleReporter(rootModule.searchRegisteredModule(moduleArtifactId));
    }

    public void listProjectModuleJavaClasses(String moduleArtifactId) {
        newModuleAnalyzer(moduleArtifactId).listJavaClasses();
    }

    public void listProjectModuleJavaClassesDependingOn(String moduleArtifactId, String destinationModule) {
        newModuleAnalyzer(moduleArtifactId).listJavaClassesDependingOn(destinationModule);
    }

    public void listProjectModuleDirectDependencies(String moduleArtifactId) {
        newModuleAnalyzer(moduleArtifactId).listDirectDependencies();
    }

    public void listInDepthTransitiveDependencies(String moduleArtifactId) {
        newModuleAnalyzer(moduleArtifactId).listThisAndChildrenModulesInDepthTransitiveDependencies();
    }

    public void listDependenciesPathsBetween(String sourceModule, String destinationModule) {
        listDependenciesPathsBetween(rootModule.searchRegisteredModule(sourceModule), rootModule.searchRegisteredModule(destinationModule));
    }

    public void listDependenciesPathsBetween(Module sourceModule, Module destinationModule) {
        listIterableElements("Listing dependency paths between " + sourceModule + " and " + destinationModule,
                rootModule.analyzeDependenciesPathsBetween(sourceModule, destinationModule)
        );
    }

    public void listCyclicDependenciesPaths() {
        listIterableElements("Listing cyclic dependency paths",
                rootModule.analyzeCyclicDependenciesLoops()
        );
    }

    /***************************
     ***** Logging methods *****
     ***************************/

    private static void logModuleWithDirectDependencies(ProjectModule module) {
        log(module + " direct dependencies: " + module.getDirectModules()
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
