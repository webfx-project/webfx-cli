package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.*;
import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

 /**
 * @author Bruno Salmon
 */
public class LocalMavenPomModuleFile extends LocalXmlModuleFileImpl implements MavenPomModuleFile {

    private Boolean aggregate;

    public LocalMavenPomModuleFile(LocalProjectModule module) {
        super(module, module.getHomeDirectory().resolve("pom.xml"), true);
    }

    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }

    public boolean isAggregate() {
        return aggregate != null ? aggregate : lookupNode("modules") != null;
    }

    @Override
    public Document createInitialDocument() {
        LocalProjectModule projectModule = getProjectModule();
        boolean isRootModule = projectModule instanceof RootModule;
        BuildInfo buildInfo = projectModule.getBuildInfo();
        String templateFileName = isRootModule ? (((RootModule) projectModule).isInlineWebFxParent() ? "pom_root_inline.xml" : "pom_root.xml") : isAggregate() ? "pom_aggregate.xml" :  !buildInfo.isExecutable ? "pom_not_executable.xml" : buildInfo.isForGwt ? "pom_gwt_executable.xml" : buildInfo.isForGluon ? "pom_gluon_executable.xml" : "pom_javafx_executable.xml";
        String template = ResourceTextFileReader.readTemplate(templateFileName)
                .replace("${groupId}",    ArtifactResolver.getGroupId(projectModule))
                .replace("${artifactId}", ArtifactResolver.getArtifactId(projectModule))
                .replace("${version}",    ArtifactResolver.getVersion(projectModule))
                ;
        if (!isRootModule) {
            ProjectModule parentModule = projectModule.getParentModule();
            template = template
                    .replace("${parent.groupId}",    ArtifactResolver.getGroupId(parentModule))
                    .replace("${parent.artifactId}", ArtifactResolver.getArtifactId(parentModule))
                    .replace("${parent.version}",    ArtifactResolver.getVersion(parentModule))
            ;
        }
        return XmlUtil.parseXmlString(template);
    }

    @Override
    public void updateDocument(Document document) {
        if (isAggregate())
            return;
        Node dependenciesNode = lookupOrCreateNode("dependencies");
        clearNodeChildren(dependenciesNode);
        dependenciesNode.appendChild(document.createTextNode(" "));
        dependenciesNode.appendChild(document.createComment(" Dependencies managed by WebFX (DO NOT EDIT MANUALLY) "));
        dependenciesNode.appendChild(document.createTextNode("\n\n    "));
        LocalProjectModule module = getProjectModule();
        BuildInfo buildInfo = module.getBuildInfo();
        ReusableStream<ModuleDependency> dependencies = buildInfo.isForGwt && buildInfo.isExecutable ? module.getTransitiveDependencies() :
                ReusableStream.concat(
                        module.getDirectDependencies(),
                        module.getTransitiveDependencies().filter(dep -> dep.getType() == ModuleDependency.Type.IMPLICIT_PROVIDER)
                ).distinct();
        Set<String> gas = new HashSet<>(); // set of groupId:artifactId listed so far in the pom dependencies - used for duplicate removal below
        dependencies
                .stream().collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                .stream().sorted(Map.Entry.comparingByKey())
                .forEach(moduleGroup -> {
                    Module destinationModule = moduleGroup.getKey();
                    String artifactId = ArtifactResolver.getArtifactId(destinationModule, buildInfo);
                    if (artifactId != null) {
                        String groupId = ArtifactResolver.getGroupId(destinationModule, buildInfo);
                        // Destination modules are already unique but maybe some are actually resolved to the same groupId:artifactId
                        String ga = groupId + ":" + artifactId;
                        if (!gas.contains(ga)) { // Checking uniqueness to avoid malformed pom
                            gas.add(ga);
                            Node groupNode = appendTextNode(dependenciesNode, "/dependency/groupId", groupId);
                            Node dependencyNode = groupNode.getParentNode();
                            appendTextNode(dependencyNode, "/artifactId", artifactId);
                            String version = ArtifactResolver.getVersion(destinationModule, buildInfo);
                            if (version != null)
                                appendTextNode(dependencyNode, "/version", version);
                            String type = ArtifactResolver.getType(destinationModule);
                            if (type != null)
                                appendTextNode(dependencyNode, "/type", type);
                            String scope = ArtifactResolver.getScope(moduleGroup, buildInfo);
                            String classifier = ArtifactResolver.getClassifier(moduleGroup, buildInfo);
                            // Adding scope if provided, except if scope="runtime" and classifier="sources" (this would prevent GWT to access the source)
                            if (scope != null && !("runtime".equals(scope) && "sources".equals(classifier)))
                                appendTextNode(dependencyNode, "/scope", scope);
                            if (classifier != null)
                                appendTextNode(dependencyNode, "/classifier", classifier);
                            if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                                appendTextNode(dependencyNode, "/optional", "true");
                            dependenciesNode.appendChild(document.createTextNode("\n    "));
                        }
                    }
                });
    }

    public void addModule(Module module) {
        String artifactId = ArtifactResolver.getArtifactId(module);
        appendTextNodeIfNotAlreadyExists("modules/module", artifactId);
    }

}
