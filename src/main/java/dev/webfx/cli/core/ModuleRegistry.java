package dev.webfx.cli.core;

import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.exceptions.UnresolvedException;
import dev.webfx.cli.modulefiles.ResWebFxModuleFile;
import dev.webfx.cli.specific.SpecificFiles;
import dev.webfx.cli.specific.SpecificModules;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
final public class ModuleRegistry {

    // The workspace directory is the parent directory on the developer machine that contains all project repositories
    // (the working project, and eventually other projects in development the working project is depending on).
    private final Path workspaceDirectory;

    /*******************************************************************************************************************
     *                                               Constructor                                                       *
     ******************************************************************************************************************/

    public ModuleRegistry(Path workspaceDirectory) {
        this.workspaceDirectory = workspaceDirectory;
    }


    /*******************************************************************************************************************
     *                          Module registration mechanism (includes an import mechanism)                           *
     ******************************************************************************************************************/

    private final List<ProjectModule> registeredProjectModules = new ArrayList<>(); // those processed by the registration stream
    private final List<LibraryModule> registeredLibraryModules = new ArrayList<>(); // those processed by the registration stream
    private final Map<String /* module name */, DevProjectModule> devProjectModulesNameMap = new HashMap<>();
    private final Map<String /* module name */, M2ProjectModule> m2ProjectModulesNameMap = new HashMap<>();
    private final Map<String /* module name */, LibraryModule> libraryModulesNameMap = new HashMap<>();
    private final Map<Path, DevProjectModule> devProjectModulesPathMap = new HashMap<>();

    private final ArrayDeque<ReusableStream<ProjectModule>> listOfRootModuleAndChildrenToRegister = new ArrayDeque<>();
    private final ArrayDeque<M2RootModule> rootThirdPartyLibrariesWithPossibleTransitiveLibrariesToRegister = new ArrayDeque<>();
    private final ReusableStream<ProjectModule> projectModuleRegistrationResumableStream = ReusableStream.resumeFromIterator(new Iterator<>() {
        ReusableStream<ProjectModule> rootModuleAndChildrenToRegisterResumableStream;
        ProjectModule nextProjectModuleToRegister;

        @Override
        public boolean hasNext() {
            return getOrIncrementNextProjectModuleToRegister() != null;
        }

        @Override
        public ProjectModule next() {
            ProjectModule next = getOrIncrementNextProjectModuleToRegister();
            if (next != null) // No need to register (it's already done on creation), but we add the module to registeredProjectModules
                registeredProjectModules.add(next);
            nextProjectModuleToRegister = null; // To force incrementation on next getOrIncrementNextProjectModuleToRegister() call
            return next;
        }

        private ProjectModule getOrIncrementNextProjectModuleToRegister() {
            while (nextProjectModuleToRegister == null) {
                if (rootModuleAndChildrenToRegisterResumableStream == null) { // Indicates that we need to go to the next module and children stream
                    // But what if there is no more?
                    while (listOfRootModuleAndChildrenToRegister.isEmpty() && lastImportedProjectModuleIndex < registeredProjectModules.size() - 1) {
                        // Let's try to import more libraries
                        importProjectModuleRequiredLibraries(registeredProjectModules.get(++lastImportedProjectModuleIndex));
                    }
                    // Now let's try to pool the next module and children stream
                    rootModuleAndChildrenToRegisterResumableStream = listOfRootModuleAndChildrenToRegister.poll();
                    // If we reached the end,
                    if (rootModuleAndChildrenToRegisterResumableStream == null) {
                        // Now that all webfx modules and third-party libraries (declared in webfx.xml) have been
                        // registered, if we still haven't found what we are looking for (ex: a package), the last
                        // chance is to search in the third-party transitive libraries.
                        if (rootThirdPartyLibrariesWithPossibleTransitiveLibrariesToRegister.isEmpty())
                            break; // we exit with next = null if there are no more transitive libraries to register
                        else {
                            // Otherwise, we import the transitive libraries of the next root third-party library
                            rootThirdPartyLibrariesWithPossibleTransitiveLibrariesToRegister.poll().getTransitiveLibraries().forEach(ModuleRegistry.this::importLibrary);
                            // And continue the loop
                            continue;
                        }
                    }
                    // We got the next stream :) We need to call the resumable operator, so we can take one element after the other
                    rootModuleAndChildrenToRegisterResumableStream = rootModuleAndChildrenToRegisterResumableStream.resumable();
                }
                // Taking the next children from the current stream
                nextProjectModuleToRegister = rootModuleAndChildrenToRegisterResumableStream.findFirst().orElse(null);
                if (nextProjectModuleToRegister == null) // null means that the stream has no more children,
                    rootModuleAndChildrenToRegisterResumableStream = null; // This reset is to go to the next module and children stream on next loop
            }
            return nextProjectModuleToRegister;
        }
    });
    private final ReusableStream<ProjectModule> projectModuleRegistrationStream =
            replayProcessingResume(projectModuleRegistrationResumableStream, registeredProjectModules);
    // Stream for project modules registration
    private final ReusableStream<Module> moduleRegistrationResumableStream =
            projectModuleRegistrationResumableStream
                    .map(Module.class::cast)
                    .concat(registeredLibraryModules);
    private final ReusableStream<Module> moduleRegistrationStream =
            projectModuleRegistrationStream
                    .map(Module.class::cast)
                    .concat(registeredLibraryModules);

    public ReusableStream<ProjectModule> getProjectModuleRegistrationResumableStream() {
        return projectModuleRegistrationResumableStream;
    }

    public ReusableStream<ProjectModule> getProjectModuleRegistrationStream() {
        return projectModuleRegistrationStream;
    }

    public ReusableStream<Module> getModuleRegistrationResumableStream() {
        return moduleRegistrationResumableStream;
    }

    public ReusableStream<Module> getModuleRegistrationStream() {
        return moduleRegistrationStream;
    }

    public Module getRegisteredModuleOrLibrary(String name) {
        Module module = getRegisteredModuleOrLibraryWithExportedPackages(name);
        if (module == null)
            module = getRegisteredLibrary(name);
        return module;
    }

    LibraryModule getRegisteredLibrary(String name) {
        LibraryModule module = JDK_MODULES.get(name);
        if (module == null)
            module = libraryModulesNameMap.get(name);
        return module;
    }

    public Module getRegisteredModuleOrLibraryWithExportedPackages(String name) {
        Module module = getRegisteredProjectModule(name);
        if (module == null)
            module = packagesModulesNameMap.values().stream().flatMap(Collection::stream).filter(m -> m.getName().equals(name)).findFirst().orElse(null);
        return module;
    }

    public ProjectModule getRegisteredProjectModule(String name) {
        ProjectModule module = getRegisteredDevProjectModule(name);
        if (module == null)
            module = getRegisteredM2ProjectModule(name);
        return module;
    }

    DevProjectModule getRegisteredDevProjectModule(String name) {
        return devProjectModulesNameMap.get(name);
    }

    DevProjectModule getRegisteredDevProjectModule(Path projectDirectory) {
        return devProjectModulesPathMap.get(projectDirectory);
    }

    public DevProjectModule getOrCreateDevProjectModule(Path projectDirectory) {
        DevProjectModule module = getRegisteredDevProjectModule(projectDirectory);
        if (module == null) {
            if (projectDirectory.equals(workspaceDirectory) || !projectDirectory.startsWith(workspaceDirectory))
                throw new CliException("projectDirectory (" + projectDirectory + ") must be under workspace directory (" + workspaceDirectory + ")");
            Path parentDirectory = projectDirectory.getParent();
            DevProjectModule parentModule = parentDirectory.equals(workspaceDirectory) ? null : getOrCreateDevProjectModule(parentDirectory);
            module = createDevProjectModule(projectDirectory, parentModule);
        }
        return module;
    }

    DevProjectModule getOrCreateDevProjectModule(Path projectDirectory, DevProjectModule parentModule) {
        DevProjectModule module = getRegisteredDevProjectModule(projectDirectory);
        if (module == null)
            module = createDevProjectModule(projectDirectory, parentModule);
        return module;
    }

    private DevProjectModule createDevProjectModule(Path projectDirectory, DevProjectModule parentModule) {
        return registerDevProjectModule(
                parentModule != null && projectDirectory.startsWith(parentModule.getHomeDirectory()) ?
                        new DevProjectModule(projectDirectory, parentModule) :
                        new DevRootModule(projectDirectory, this)
        );
    }

    private DevProjectModule registerDevProjectModule(DevProjectModule module) {
        //System.out.println("Registering dev project " + module);
        devProjectModulesNameMap.put(module.getName(), module);
        devProjectModulesPathMap.put(module.getHomeDirectory(), module);
        if (module instanceof RootModule)
            addRootAndChildrenModulesInRegistrationStreamInput((RootModule) module);
        return module;
    }

    private void addRootAndChildrenModulesInRegistrationStreamInput(RootModule module) {
        listOfRootModuleAndChildrenToRegister.add(module.getThisAndChildrenModulesInDepth());
    }

    M2ProjectModule getRegisteredM2ProjectModule(String name) {
        return m2ProjectModulesNameMap.get(name);
    }

    public M2ProjectModule getOrCreateM2ProjectModule(String name, M2ProjectModule parentModule) {
        M2ProjectModule module = getRegisteredM2ProjectModule(name);
        if (module == null)
            module = createM2ProjectModule(name, parentModule);
        return module;
    }

    public M2ProjectModule createM2ProjectModule(String name, M2ProjectModule parentModule) {
        return registerM2ProjectModule(new M2ProjectModule(name, parentModule));
    }

    private M2ProjectModule registerM2ProjectModule(M2ProjectModule module) {
        //System.out.println("Registering M2 project " + module);
        m2ProjectModulesNameMap.put(module.getName(), module);
        if (module instanceof RootModule)
            addRootAndChildrenModulesInRegistrationStreamInput((RootModule) module);
        return module;
    }


    /*******************************************************************************************************************
     *                                           Module import mechanism                                               *
     ******************************************************************************************************************/

    private int lastImportedProjectModuleIndex = -1; // index of last imported project in registeredProjectModules
    private int lastDeclaredProjectModuleIndex = -1; // index of last declared project in registeredProjectModules

    void importProjectModuleRequiredLibraries(ProjectModule module) {
        module.getRequiredLibraryModules().forEach(this::importLibrary);
    }

    public void importLibrary(LibraryModule libraryModule) {
        String moduleName = libraryModule.getName();
        if (libraryModule.shouldBeDownloadedInM2()) {
            if (getRegisteredModuleOrLibraryWithExportedPackages(moduleName) == null)
                registerM2ProjectModule(new M2RootModule(libraryModule, this));
        } else if (getRegisteredLibrary(moduleName) == null) {
            //.out.println("Registering library " + moduleName);
            libraryModulesNameMap.put(moduleName, libraryModule);
            registeredLibraryModules.add(libraryModule);
        }
    }


    /*******************************************************************************************************************
     *                                        Module declaration mechanism                                             *
     ******************************************************************************************************************/

    private int lastDeclaredLibraryModuleIndex = -1; // index of last declared project in registeredLibraryModules
    private final List<Module> declaredModules = new ArrayList<>();

    private final Map<String /* package name */, List<Module>> packagesModulesNameMap = new HashMap<>();

    // Stream for project modules declaration
    private final ReusableStream<Module> moduleDeclarationResumableStream =
            ReusableStream.resumeFromIterator(new Iterator<>() {
                Module nextModuleToDeclare;

                @Override
                public boolean hasNext() {
                    return getOrIncrementNextProjectModuleToDeclare() != null;
                }

                @Override
                public Module next() {
                    Module next = getOrIncrementNextProjectModuleToDeclare();
                    if (next instanceof ProjectModule projectModule) {
                        declareProjectModulePackages(projectModule); // This also adds the module to declaredModules
                        if (next instanceof M2RootModule m2RootModule) { // Ex: M2Project for JUnit library declared as <library artifact="org.junit.jupiter:junit-jupiter:5.9.0"/>
                            // => We must register the transitive dependencies as libraries as well
                            LibraryModule libraryModule = m2RootModule.getLibraryModule();
                            if (libraryModule.isThirdParty() && libraryModule.getRootModule() == null)
                                rootThirdPartyLibrariesWithPossibleTransitiveLibrariesToRegister.add(m2RootModule);
                        }
                    } else if (next instanceof LibraryModule)
                        declareLibraryModulePackages((LibraryModule) next);
                    nextModuleToDeclare = null; // To force incrementation on next getOrIncrementNextProjectModuleToDeclare() call
                    return next;
                }

                private Module getOrIncrementNextProjectModuleToDeclare() {
                    while (nextModuleToDeclare == null) {
                        if (lastDeclaredLibraryModuleIndex < registeredLibraryModules.size() - 1)
                            nextModuleToDeclare = registeredLibraryModules.get(++lastDeclaredLibraryModuleIndex);
                        else if (lastDeclaredProjectModuleIndex < registeredProjectModules.size() - 1)
                            nextModuleToDeclare = registeredProjectModules.get(++lastDeclaredProjectModuleIndex);
                        else if (projectModuleRegistrationResumableStream.findFirst().orElse(null) == null
                                && lastDeclaredLibraryModuleIndex >= registeredLibraryModules.size() - 1
                                && lastDeclaredProjectModuleIndex >= registeredProjectModules.size() - 1)
                            break;
                    }
                    return nextModuleToDeclare;
                }
            });
    private final ReusableStream<Module> moduleDeclarationStream =
            replayProcessingResume(moduleDeclarationResumableStream, declaredModules);

    public ReusableStream<ProjectModule> getProjectModuleDeclarationResume() {
        return ProjectModule.filterProjectModules(moduleDeclarationResumableStream);
    }

    public ReusableStream<ProjectModule> getProjectModuleDeclarationStream() {
        return ProjectModule.filterProjectModules(moduleDeclarationStream);
    }

    public ReusableStream<Module> getModuleDeclarationResumableStream() {
        return moduleDeclarationResumableStream;
    }

    public ReusableStream<Module> getModuleDeclarationStream() {
        return moduleDeclarationStream;
    }

    private void declarePackageBelongsToModule(String packageName, Module module) {
        List<Module> lm = packagesModulesNameMap.get(packageName);
        if (lm == null) // First time we declare this package
            packagesModulesNameMap.put(packageName, lm = new ArrayList<>(1));
        else if (lm.contains(module)) // Already declared and with the same module (shouldn't arrive)
            return; // We just skip (but why this double declaration with the same package and same module?)
        else {
            Module m = lm.get(0);
            String message = module + " and " + m + " share the same package " + packageName;
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
        lm.add(module);
    }

    void declareProjectModulePackages(ProjectModule module) {
        //System.out.println("Declaring packages for project module " + module);
        module.getMainJavaSourceRootAnalyzer().getSourcePackages().forEach(p -> declarePackageBelongsToModule(p, module));
        module.getWebFxModuleFile().getExplicitExportedPackages().forEach(p -> declarePackageBelongsToModule(p, module));
        declaredModules.add(module);
    }

    void declareLibraryModulePackages(LibraryModule module) {
        //System.out.println("Declaring packages for library " + module);
        module.getExportedPackages().forEach(p -> declarePackageBelongsToModule(p, module));
        declaredModules.add(module);
    }

    Module getDeclaredJavaPackageModule(String packageName, ProjectModule sourceModule, boolean canReturnNull) {
        // First, we check if the package is not in the source package! (ex: webfx-extras-controls-registry-spi)
        if (sourceModule.getMainJavaSourceRootAnalyzer().getSourcePackages().anyMatch(p -> p.equals(packageName)))
            return sourceModule;
        List<Module> lm = packagesModulesNameMap.get(packageName);
        Module module = lm == null ? null : lm.stream()
                .filter(m -> isSuitableModule(m, sourceModule))
                .findFirst()
                .orElse(null);
        if (module == null) { // Module isn't found :-(
            if (!canReturnNull) { // Otherwise, raising an exception (unless returning null is permitted)
                if (lm == null || lm.isEmpty())
                    throw new UnresolvedException("Unresolved module for package " + packageName + " (used by " + sourceModule + ")");
                StringBuilder sb = new StringBuilder("Unsuitable module for package " + packageName + " (used by " + sourceModule + ")");
                lm.forEach(m -> sb.append("\n").append(m.getName()).append(" declares this package, but it is not suitable because ").append(getUnsuitableModuleReason(m, sourceModule)));
                throw new UnresolvedException(sb.toString());
            }
        }
        return module;
    }

    public boolean isSuitableModule(Module m, ProjectModule sourceModule) {
        return getUnsuitableModuleReason(m, sourceModule) == null;
    }

    private String getUnsuitableModuleReason(Module m, ProjectModule sourceModule) {
        String moduleName = m.getName();
        String sourceModuleName = sourceModule.getName();
        if (m.isJavaBaseEmulationModule() && moduleName.contains("j2cl") && !moduleName.contains("gwt") && sourceModule.isExecutable(Platform.GWT)) {
            return "J2CL module (not suitable for GWT)";
        }
        if (!(m instanceof ProjectModule pm))
            return null;
        //
        if (pm.getTarget().hasTag(TargetTag.POLYFILL))
            return "the elemental2 polyfill module is meant to be used only in the TeaVM plugin configuration in webfx-parent, project modules should refer to the actual elemental2 modules";
        if (pm.isDeprecated()) {
            return "this module is deprecated";
        }
        // A preview module cannot be used unless the source module is also a preview module
        if (pm.isPreview() && !sourceModule.isPreview()) {
            return "this module is a preview";
        }
        // First case: only executable source modules should include implementing interface modules (others should include the interface module instead)
        if (pm.isImplementingInterface() && !sourceModule.isExecutable()) {
            // Exception is, however, made for non-executable source modules that implement a provider.
            // Ex: webfx-extras-controls-registry-openjfx can include webfx-extras-controls-registry-spi (which implements webfx-extras-controls-registry)
            boolean exception = !sourceModule.getProvidedJavaServices().isEmpty();
            //.anyMatch(service -> pm.getJavaSourceFiles().anyMatch(c -> c.getClassName().equals(service)));
            if (!exception)
                return "it implements an interface module (" + pm.implementedInterfaces().findFirst().orElse(null) + "), and only executable modules should use it (" + sourceModuleName + " is not an executable module)";
        }
        // Second not permitted case:
        // Ex: webfx-extras-controls-registry-openjfx should not include webfx-extras-controls-registry (but webfx-extras-controls-registry-spi instead)
        if (pm.isInterface()) {
            if (sourceModuleName.startsWith(moduleName))
                return "it should not be included in " + sourceModuleName;
        }
        if (sourceModuleName.contains("workerthread") && pm.getTarget().hasTag(TargetTag.ELEMENTAL2)) {
            return "elemental2 cannot be used in web worker threads as the DOM is inaccessible"; // Ex: use webfx-platform-console-java instead of webfx-platform-console-elemental2
        }
        return null;
    }


    /*******************************************************************************************************************
     *                                  JDK modules registration & declaration                                         *
     ******************************************************************************************************************/

    private final static Map<String /* module name */, LibraryModule> JDK_MODULES = new HashMap<>();

    // Registering JDK modules (packages registration will be done in the non-static constructor)
    static {
        // Reading the webfx.xml resource file listing all modules of the JDK
        new ResWebFxModuleFile(SpecificFiles.RES_JDK_WEBFX_XML)
                // All modules are declared like third-party libraries
                .getRequiredThirdPartyLibraryModules()
                // Storing the module name together with the associated library
                .forEach(m -> JDK_MODULES.put(m.getName(), m));
    }

    // Declaring JDK packages on this module registry instance
    {
        JDK_MODULES.values().forEach(this::declareJdkModulePackages);
    }

    private void declareJdkModulePackages(LibraryModule jdkModule) {
        jdkModule.getExportedPackages().forEach(p -> declarePackageBelongsToModule(p, jdkModule));
    }

    public static boolean isJdkModule(String name) {
        return JDK_MODULES.containsKey(name);
    }


    /*******************************************************************************************************************
     *                                           Export snapshot usages                                                *
     ===================================================================================================================
     The purpose of this section is to collect and register all java packages and classes usages listed in the
     <export-snapshot> section of all webfx.xml files related to the working project, and then provide a method that
     will use this information to tell if a module is using a package or a class. The goal here is to compute that usage
     quickly, without having to download the sources of WebFX libraries when importing them - because downloading sources
     of many modules can take a lot of time - at least 3 s per module. Please note that this feature is designed to work
     only for the execution of the directives if-uses-java-package and if-uses-java-class. Because the usages listed in
     export snapshots are restricted to only the packages and classes known to be used by such directives.
     ******************************************************************************************************************/

    // On the fly export snapshots registration stream
    private final ReusableStream<M2ProjectModule> m2ProjectModulesWithExportSnapshotsResume =
            // We start with polling all project modules from the registration stream
            projectModuleRegistrationStream
                    // We keep m2 projects only
                    .filter(M2ProjectModule.class::isInstance).map(M2ProjectModule.class::cast)
                    // We keep only those with an export snapshot (at this point, all modules listed in the export snapshot are included)
                    .filter(m2 -> m2.getM2WebFxModuleFile().isExported())
                    // We keep only one module per export snapshot (the one that generated the snapshot - which is the first project listed in <export-snapshot>
                    .filter(m2 -> m2.getName().equals(m2.getM2WebFxModuleFile().lookupExportedSnapshotFirstProjectName()))
                    // Registering the export snapshot usages on the fly while pulling this stream
                    .map(this::registerExportSnapshotUsages)
                    // This stream is for an on-the-fly registration, so no need to repeat and process the same modules
                    .resumable();

    private final Map<String /* package or class name */, List<SnapshotUsages>> registeredSnapshotUsages = new HashMap<>();

    private M2ProjectModule registerExportSnapshotUsages(M2ProjectModule m2) {
        m2.getM2WebFxModuleFile().javaPackagesFromExportSnapshotUsage()
                .forEach(javaPackageName -> {
                    List<SnapshotUsages> usages = registeredSnapshotUsages.computeIfAbsent(javaPackageName, k -> new ArrayList<>());
                    usages.add(new SnapshotUsages(m2, m2.getWebFxModuleFile().modulesUsingJavaPackageFromExportSnapshot(javaPackageName).collect(Collectors.toList())));
                });
        m2.getM2WebFxModuleFile().javaClassesFromExportSnapshotUsage()
                .forEach(javaClassName -> {
                    List<SnapshotUsages> usages = registeredSnapshotUsages.computeIfAbsent(javaClassName, k -> new ArrayList<>());
                    usages.add(new SnapshotUsages(m2, m2.getWebFxModuleFile().modulesUsingJavaClassFromExportSnapshot(javaClassName).collect(Collectors.toList())));
                });
        return m2;
    }

    public Boolean doExportSnapshotsTellIfModuleIsUsingPackageOrClass(ProjectModule module, String packageOrClass) {
        // Getting the usages registered so far for this package or class from the export snapshots
        List<SnapshotUsages> packageOrClassSnapshotUsages = registeredSnapshotUsages.get(packageOrClass);
        // If no usage has been registered so far, we pull the stream until we eventually get at least one
        if (packageOrClassSnapshotUsages == null) {
            m2ProjectModulesWithExportSnapshotsResume.takeWhile(m2 -> registeredSnapshotUsages.get(packageOrClass) == null).count();
            // If after pulling the whole stream we still haven't any, this means that no export snapshots mention that
            // usage. Therefore, we return null to say that we don't know if the modules use this package or class from
            // the export snapshots.
            packageOrClassSnapshotUsages = registeredSnapshotUsages.get(packageOrClass);
            if (packageOrClassSnapshotUsages == null)
                return null;
        }
        // If we have some usages reported from the export snapshots, we investigate them
        for (int i = 0, n = packageOrClassSnapshotUsages.size(); i < n; i++) {
            SnapshotUsages snapshotUsage = packageOrClassSnapshotUsages.get(i);
            // If the snapshot usage can tell if the module uses or not the package or class, we return that result
            Boolean moduleUsing = snapshotUsage.isModuleUsing(module); // TODO: check why it's taking lots of time
            if (moduleUsing != null)
                return moduleUsing;
            // Otherwise, once we have requested all usages registered so far, we continue pulling the stream
            if (i == n - 1) { // we reached the last one
                final List<SnapshotUsages> finalUsages = packageOrClassSnapshotUsages;
                int finalSize = n;
                // we pull the stream until we get new usages
                m2ProjectModulesWithExportSnapshotsResume.takeWhile(m2 -> finalUsages.size() == finalSize).count();
                // we integrate these new usages in the loop
                n = packageOrClassSnapshotUsages.size();
            }
        }
        // If no usage could tell if the module is using this package or class after pulling the whole stream,
        return null; // we return null to say we don't know from the export snapshots
    }

    private record SnapshotUsages(M2ProjectModule m2ProjectModule, List<String> usedByModules) {

        public Boolean isModuleUsing(ProjectModule module) {
                // First quick check: if the present module is listed in any already computed usage for that class or package, we return true
                if (usedByModules.contains(module.getName()))
                    return true;
                // At this stage we know that this module was never listed in any usage of this class or package computed
                // so far, but we need to check if these computed usages considered that module or not in this computation.
                // If any of them did, we can return false, because it means that that existing usage already checked
                // that this module wasn't using that class or package.
                if (m2ProjectModule.getDirectivesUsageCoverage().anyMatch(pm -> pm == module))
                    return false;
                return null;
            }
        }


    /*******************************************************************************************************************
     *                                           Static utility methods                                                *
     ******************************************************************************************************************/

    private static <T> ReusableStream<T> replayProcessingResume(ReusableStream<T> processingResume, List<? extends T> processedList) {
        return ReusableStream.fromIterable(() -> new Iterator<>() {

            int lastIndex = -1;
            T next;

            @Override
            public boolean hasNext() {
                return getOrIncrementNext() != null;
            }

            @Override
            public T next() {
                T next = getOrIncrementNext();
                this.next = null;
                return next;
            }

            private T getOrIncrementNext() {
                while (next == null) {
                    if (lastIndex < processedList.size() - 1)
                        next = processedList.get(++lastIndex);
                    else if (processingResume.findFirst().orElse(null) == null)
                        break;
                }
                return next;
            }
        });
    }
}
