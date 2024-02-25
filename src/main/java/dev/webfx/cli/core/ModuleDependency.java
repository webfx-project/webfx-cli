package dev.webfx.cli.core;

import dev.webfx.lib.reusablestream.ReusableStream;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public final class ModuleDependency implements Comparable<ModuleDependency> {

    public enum Type {
        SOURCE,
        NOT_FOUND_SOURCE,
        RESOURCE,
        EMULATION,
        APPLICATION,
        PLUGIN,
        IMPLICIT_PROVIDER
    }

    private final Module sourceModule;
    private final Module destinationModule;
    private final Type type;
    private final boolean optional;
    private final boolean transitive;
    private final String scope;
    private final String classifier;
    private final List<Target> executableTargets;

    public ModuleDependency(Module sourceModule, Module destinationModule, Type type) {
        this(sourceModule, destinationModule, type, false, false, null, null, Collections.emptyList());
    }

    public ModuleDependency(Module sourceModule, Module destinationModule, Type type, boolean optional, boolean transitive, String scope, String classifier, List<Target> executableTargets) {
        this.sourceModule = sourceModule;
        this.destinationModule = destinationModule;
        this.type = type;
        this.optional = optional;
        this.transitive = transitive;
        this.scope = scope;
        this.classifier = classifier;
        this.executableTargets = executableTargets;
    }

    public Module getSourceModule() {
        return sourceModule;
    }

    public Module getDestinationModule() {
        return destinationModule;
    }

    public Type getType() {
        return type;
    }

    public boolean isOptional() {
        return optional;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public String getScope() {
        return scope;
    }

    public String getClassifier() {
        return classifier;
    }

    public List<Target> getExecutableTargets() {
        return executableTargets;
    }

    @Override
    public int compareTo(ModuleDependency dep) {
        // By default, we sort the dependencies by ordering the by the destination module name
        return destinationModule.getName().compareTo(dep.destinationModule.getName());
    }

    ReusableStream<ModuleDependency> collectThisAndTransitiveDependencies() {
        Set<ModuleDependency> dependencies = new LinkedHashSet<>();
        collectThisAndTransitiveDependencies(dependencies, sourceModule instanceof ProjectModule ? (ProjectModule) sourceModule : null);
        return ReusableStream.fromIterable(dependencies);
    }

    private void collectThisAndTransitiveDependencies(Collection<ModuleDependency> dependencies, ProjectModule targetModule) {
        if (dependencies.stream().noneMatch(d -> d.destinationModule == destinationModule)) { // Avoiding infinite recursion
            dependencies.add(this);
            // We don't include the JavaFX emulation modules (as they may just be finally mapped to simple JavaFX modules)
            String destinationModuleName = destinationModule.getName();
            if (SpecificModules.isJavafxEmulModule(destinationModuleName)
                    // Except for GWT executables (which use only WebFX modules and need them all)
                    && !targetModule.isExecutable(Platform.GWT)
                    // Same with J2CL (except for fat modules
                    && !(targetModule.isExecutable(Platform.J2CL) && !SpecificModules.isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(destinationModuleName) /*&& !SpecificModules.isModulePartOfWebfxExtrasVisualGridFatJ2cl(destinationModuleName)*/)
                    // Also except for Gluon modules using media => they need webfx-kit-javafxmedia-gluon + transitive dependencies such as webfx-platform-audio-gluon, Gluon Attach audio, etc...
                    && !destinationModuleName.equals(SpecificModules.WEBFX_KIT_JAVAFXMEDIA_GLUON))
                return;
            ProjectModule pm = destinationModule instanceof ProjectModule ? (ProjectModule) destinationModule : null;
            if (pm != null)
                pm.getMainJavaSourceRootAnalyzer().getDirectDependenciesWithoutFinalExecutableResolutions().forEach(dep ->
                        dep.collectThisAndTransitiveDependencies(dependencies, targetModule)
                );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleDependency)) return false;

        ModuleDependency that = (ModuleDependency) o;

        if (optional != that.optional) return false;
        if (!sourceModule.equals(that.sourceModule)) return false;
        if (!destinationModule.equals(that.destinationModule)) return false;
        if (type != that.type) return false;
        if (scope != null ? !scope.equals(that.scope) : that.scope != null) return false;
        return classifier != null ? classifier.equals(that.classifier) : that.classifier == null;

    }

    @Override
    public int hashCode() {
        int result = sourceModule.hashCode();
        result = 31 * result + destinationModule.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (optional ? 1 : 0);
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return sourceModule + " -> " + destinationModule + " (type = " + type + (optional ? ", optional" : "") + (scope == null ? "" : ", scope = " + scope) + (classifier == null ? "" : ", classifier = " + classifier) + (executableTargets == null ? "" : ", executableTarget = " + executableTargets) + ")";
    }

    public static ModuleDependency createDependency(Module srcModule, Module dstModule, Type type) {
        return new ModuleDependency(srcModule, dstModule, type);
    }

    public static ModuleDependency createSourceDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.SOURCE);
    }

    public static ModuleDependency createNotFoundSourceDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.NOT_FOUND_SOURCE);
    }

    public static ModuleDependency createResourceDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.RESOURCE);
    }

    public static ModuleDependency createEmulationDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.EMULATION);
    }

    public static ModuleDependency createApplicationDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.APPLICATION);
    }

    public static ModuleDependency createPluginDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.PLUGIN);
    }

    public static ModuleDependency createImplicitProviderDependency(Module srcModule, Module dstModule) {
        return createDependency(srcModule, dstModule, Type.IMPLICIT_PROVIDER);
    }

    public static Map<Module, List<Module>> createDependencyGraph(ReusableStream<ModuleDependency> dependencies) {
        return dependencies.collect(Collectors.groupingBy(ModuleDependency::getSourceModule,
                Collectors.mapping(ModuleDependency::getDestinationModule, Collectors.toList())));
    }
}
