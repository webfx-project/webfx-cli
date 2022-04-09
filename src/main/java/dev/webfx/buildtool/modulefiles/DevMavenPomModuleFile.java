package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
public class DevMavenPomModuleFile extends DevXmlModuleFileImpl implements MavenPomModuleFile {

    private Boolean aggregate;

    public DevMavenPomModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("pom.xml"), true);
    }

    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }

    public boolean isAggregate() {
        return aggregate != null ? aggregate : lookupNode("modules") != null;
    }

    @Override
    public boolean recreateOnUpdateAndWrite() {
        return getWebFxMavenPomProjectNode() != null || getProjectModule().getWebFxModuleFile().isAggregate();
    }

    private Node getWebFxMavenPomProjectNode() {
        Node projectNode = null;
        Document webFxDocument = getProjectModule().getWebFxModuleFile().getDocument();
        if (webFxDocument != null) {
            Element webFxDocumentElement = webFxDocument.getDocumentElement();
            projectNode = XmlUtil.lookupNode(webFxDocumentElement, "maven-pom-project");
        }
        return projectNode;
    }

    @Override
    public Document createInitialDocument() {
        DevProjectModule projectModule = getProjectModule();
        boolean isRootModule = projectModule instanceof RootModule;
        BuildInfo buildInfo = projectModule.getBuildInfo();
        String templateFileName =
                isRootModule ? (((RootModule) projectModule).isInlineWebFxParent() ? "pom_root_inline.xml" : "pom_root.xml")
                        : isAggregate() ? "pom_aggregate.xml"
                        : !buildInfo.isExecutable ? "pom_not_executable.xml"
                        : buildInfo.isForGwt ? "pom_gwt_executable.xml"
                        : buildInfo.isForGluon ? "pom_gluon_executable.xml"
                        : "pom_javafx_executable.xml";
        String template = ResourceTextFileReader.readTemplate(templateFileName);
        Document document = XmlUtil.parseXmlString(template);
        Element documentElement = document.getDocumentElement();
        Node webFxMavenPomProjectNode = getWebFxMavenPomProjectNode();
        if (webFxMavenPomProjectNode != null) {
            XmlUtil.appendChildren(document.importNode(webFxMavenPomProjectNode, true), documentElement);
            XmlUtil.indentNode(documentElement, true);
        }
        return document;
    }

    @Override
    public boolean updateDocument(Document document) {
        DevProjectModule module = getProjectModule();
        if (module.isAggregate()) {
            appendTextNodeIfNotAlreadyExists("packaging", "pom", true);
            if (module.getWebFxModuleFile().isAggregate())
                module.getChildrenModules().forEach(this::addModule);
            else
                return false;
        } else {
            if (!module.hasSourceDirectory()) // Ex: webfx-parent, webfx-stack-parent
                return false;
            Node dependenciesNode = lookupOrCreateNode("dependencies");
            XmlUtil.removeChildren(dependenciesNode);
            if (!recreateOnUpdateAndWrite()) {
                dependenciesNode.appendChild(document.createTextNode(" "));
                dependenciesNode.appendChild(document.createComment(" Dependencies managed by WebFX (DO NOT EDIT MANUALLY) "));
            }
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
                                Node groupNode = XmlUtil.appendTextElement(dependenciesNode, "/dependency/groupId", groupId, true, false);
                                Node dependencyNode = groupNode.getParentNode();
                                XmlUtil.appendTextElement(dependencyNode, "/artifactId", artifactId);
                                String version = ArtifactResolver.getVersion(destinationModule, buildInfo);
                                if (version != null)
                                    XmlUtil.appendTextElement(dependencyNode, "/version", version);
                                String type = ArtifactResolver.getType(destinationModule);
                                if (type != null)
                                    XmlUtil.appendTextElement(dependencyNode, "/type", type);
                                String scope = ArtifactResolver.getScope(moduleGroup, buildInfo);
                                String classifier = ArtifactResolver.getClassifier(moduleGroup, buildInfo);
                                // Adding scope if provided, except if scope="runtime" and classifier="sources" (this would prevent GWT to access the source)
                                if (scope != null && !("runtime".equals(scope) && "sources".equals(classifier)))
                                    XmlUtil.appendTextElement(dependencyNode, "/scope", scope);
                                if (classifier != null)
                                    XmlUtil.appendTextElement(dependencyNode, "/classifier", classifier);
                                if (moduleGroup.getValue().stream().anyMatch(ModuleDependency::isOptional))
                                    XmlUtil.appendTextElement(dependencyNode, "/optional", "true");
                            }
                        }
                    });
            if (!gas.isEmpty() && dependenciesNode.getParentNode() == null)
                appendIndentNode(dependenciesNode, true);
        }
        Node artifactIdNode = lookupNode("artifactId");
        if (artifactIdNode == null)
            prependTextElement("artifactId", ArtifactResolver.getArtifactId(module), true);
        Node parentNode = lookupNode("parent");
        if (parentNode == null) {
            Module parentModule = module.getParentModule();
            if (parentModule == null && !module.getName().equals("webfx-parent"))
                parentModule = module.getRootModule().findModule("webfx-parent");
            if (parentModule != null) {
                parentNode = createAndPrependElement("parent", true);
                XmlUtil.appendTextElement(parentNode, "groupId", ArtifactResolver.getGroupId(parentModule));
                XmlUtil.appendTextElement(parentNode, "artifactId", ArtifactResolver.getArtifactId(parentModule));
                XmlUtil.appendTextElement(parentNode, "version", ArtifactResolver.getVersion(parentModule));
            }
        }
        Node modelVersionNode = lookupNode("modelVersion");
        if (modelVersionNode == null)
            prependTextElement("modelVersion", "4.0.0");
        return true;
    }

    public void addModule(Module module) {
        String artifactId = ArtifactResolver.getArtifactId(module);
        appendTextNodeIfNotAlreadyExists("modules/module", artifactId, true, false);
    }

}
