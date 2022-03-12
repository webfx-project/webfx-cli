package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.ImportedMavenPomModuleFile;
import dev.webfx.buildtool.modulefiles.ImportedWebFxModuleFile;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Element;

/**
 * @author Bruno Salmon
 */
public class ImportedProjectModule extends ProjectModuleImpl {

    private ImportedMavenPomModuleFile mavenPomModuleFile;
    private ImportedWebFxModuleFile webFxModuleFile;

    public ImportedProjectModule(String name, ProjectModule parentModule) {
        super(name, parentModule);
    }

    public ImportedProjectModule(Element projectElement, ProjectModule parentModule) {
        this(projectElement.getAttribute("name"), parentModule);
        if (XmlUtil.getBooleanAttributeValue(projectElement, "maven"))
            mavenPomModuleFile = new ImportedMavenPomModuleFile(this, projectElement);
        else {
            webFxModuleFile = new ImportedWebFxModuleFile(this, projectElement);
            groupId = XmlUtil.getAttributeValue(projectElement, "groupId");
            artifactId = XmlUtil.getAttributeValue(projectElement, "artifactId");
            version = XmlUtil.getAttributeValue(projectElement, "version");
        }
    }

    public ProjectModule getOrCreateChildProjectModule(String name) {
        // TODO optimise this with a map
        return getModuleRegistry().getImportedProjectModules().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public ImportedMavenPomModuleFile getMavenModuleFile() {
        return mavenPomModuleFile;
    }

    @Override
    public ImportedWebFxModuleFile getWebFxModuleFile() {
        return webFxModuleFile;
    }

    @Override
    public ReusableStream<String> getDeclaredJavaPackages() {
        return null;
    }

    @Override
    public ReusableStream<JavaFile> getDeclaredJavaFiles() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedJavaPackages() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedRequiredJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedOptionalJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<String> getDeclaredJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<Providers> getExecutableProviders() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getDirectDependencies() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getTransitiveDependencies() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getDiscoveredByCodeAnalyzerSourceDependencies() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getDirectDependenciesWithoutFinalExecutableResolutions() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getTransitiveDependenciesWithoutImplicitProviders() {
        return null;
    }
}
