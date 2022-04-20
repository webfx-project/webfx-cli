package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.DevProjectModule;
import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.ModuleDependency;
import dev.webfx.buildtool.ProjectModule;
import dev.webfx.buildtool.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.buildtool.util.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public final class DevWebFxModuleFile extends DevXmlModuleFileImpl implements WebFxModuleFile {

    private final static String EXPORT_SNAPSHOT_TAG = "export-snapshot";

    public DevWebFxModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"));
    }

    @Override
    public boolean updateDocument(Document document) {
        Node exportNode = lookupNode(EXPORT_SNAPSHOT_TAG);
        if (!generatesExportSnapshot()) {
            if (exportNode != null)
                XmlUtil.removeNode(exportNode);
            return false;
        }
        if (exportNode != null)
            XmlUtil.removeChildren(exportNode);
        else
            appendIndentNode(exportNode = document.createElement(EXPORT_SNAPSHOT_TAG), true);
        exportNode.appendChild(document.createComment(" Node managed by WebFX (DO NOT EDIT MANUALLY) "));
        final Node finalExportNode = exportNode;
        getProjectModule().getChildrenModulesInDepth()
                .forEach(m -> exportChildModule(m, finalExportNode));
        return true;
    }

    private void exportChildModule(ProjectModule childModule, Node exportNode) {
        Document childDocument = childModule.getWebFxModuleFile().getDocument();
        if (childDocument != null) {
            Element childProjectElement = (Element) exportNode.getOwnerDocument().importNode(childDocument.getDocumentElement(), true);
            childProjectElement.setAttribute("name", childModule.getName());
            Node sourcePackagesNode = XmlUtil.lookupNode(childProjectElement, "exported-packages/source-packages");
            if (sourcePackagesNode != null) {
                childModule.getExportedJavaPackages().forEach(p -> XmlUtil.appendTextElement(sourcePackagesNode.getParentNode(), "package", p));
                XmlUtil.removeNode(sourcePackagesNode);
            }
            Node modulesNode = XmlUtil.lookupNode(childProjectElement, "modules");
            if (modulesNode != null) {
                XmlUtil.removeChildren(modulesNode);
                childModule.getChildrenModules().forEach(m -> XmlUtil.appendTextElement(modulesNode, "module", m.getName()));
            }
            Node usedBySourceModulesNode = XmlUtil.lookupNode(childProjectElement, "dependencies/used-by-source-modules");
            if (usedBySourceModulesNode != null) {
                childModule.getDetectedByCodeAnalyzerSourceDependencies()
                        .map(ModuleDependency::getDestinationModule)
                        .map(Module::getName)
                        .sorted()
                        .forEach(m -> XmlUtil.appendTextElement(usedBySourceModulesNode, "source-module", m));
            }
            XmlUtil.appendIndentNode(childProjectElement, exportNode, true);
        }
    }
}
