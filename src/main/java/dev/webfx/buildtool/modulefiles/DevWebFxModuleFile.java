package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;
import dev.webfx.buildtool.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.*;

/**
 * @author Bruno Salmon
 */
public final class DevWebFxModuleFile extends DevXmlModuleFileImpl implements WebFxModuleFile {

    private final static String EXPORT_SNAPSHOT_TAG = "export-snapshot";
    private final static String EXPORT_SECTION_COMMENT = "\n" +
            "\n" +
            "     ******************************************************************************************************************* \n" +
            "     ******************************* Section managed by WebFX (DO NOT EDIT MANUALLY) *********************************** \n" +
            "     ******************************************************************************************************************* \n" +
            "\n" +
            "     <export-snapshot> allows a much faster import of this WebFX library into another project. It's a self-contained\n" +
            "     image of this and children modules. All information required for the import of this library is present in this\n" +
            "     single file. The export snapshot is optional, and a WebFX library that doesn't generate it can still be imported\n" +
            "     into another project, but WebFX will then need to download all individual webfx.xml files for every children\n" +
            "     modules, together with their pom and sources. Knowing that each download requires a maven invocation that takes\n" +
            "     at least 3s (sometimes 10s or more), the export snapshot brings a significant performance improvement in the\n" +
            "     import process.\n" +
            "\n" +
            "     ";


    public DevWebFxModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"));
    }

    @Override
    public boolean updateDocument(Document document) {
        Node exportNode = lookupNode(EXPORT_SNAPSHOT_TAG);
        boolean exportNodeWasPresent = exportNode != null;
        if (exportNode != null) {
            XmlUtil.removeChildren(exportNode);
            removeNodeAndPreviousBlankText(exportNode, true);
            XmlUtil.removeNode(exportNode);
        } else
            exportNode = document.createElement(EXPORT_SNAPSHOT_TAG);
        if (!generatesExportSnapshot())
            return exportNodeWasPresent;
        final Node finalExportNode = exportNode;
        // Exporting this and children modules in depth
        DevProjectModule projectModule = getProjectModule();
        projectModule.getThisAndChildrenModulesInDepth()
                .forEach(pm -> exportChildModuleProject(pm, finalExportNode));
        // Adding usage to resolve if-uses-java-package and if-uses-java-class directives without downloading the sources
        ReusableStream<String>[] packagesToFindUsageHolder = new ReusableStream[1];
        ReusableStream<String>[] classesToFindUsageHolder = new ReusableStream[1];
        ReusableStream<ProjectModule> transitiveProjectModules = projectModule.getThisAndChildrenModulesInDepth()
                .flatMap(ProjectModule::getThisAndTransitiveModules)
                .filter(ProjectModule.class::isInstance)
                .map(ProjectModule.class::cast)
                .distinct()
                .cache();
        // First pass: collect
        transitiveProjectModules
                .forEach(pm -> collectJavaPackagesAndClassesToFindUsage(pm.getWebFxModuleFile().getModuleElement(), packagesToFindUsageHolder, classesToFindUsageHolder));
        // Second pass: record
        Element usagesElement = document.createElement("usages");
        findUsageOfJavaPackagesAndClasses(usagesElement, transitiveProjectModules, packagesToFindUsageHolder, classesToFindUsageHolder);
        if (usagesElement.hasChildNodes())
            XmlUtil.appendIndentNode(usagesElement, exportNode, true);
        appendIndentNode(document.createComment(EXPORT_SECTION_COMMENT), true);
        appendIndentNode(exportNode, true);
        return true;
    }

    private void exportChildModuleProject(ProjectModule childModule, Node exportNode) {
        Document childDocument = childModule.getWebFxModuleFile().getDocument();
        if (childDocument != null) {
            Document document = exportNode.getOwnerDocument();
            // Duplicating the xml element, so it can be copied into <export-snapshot/>
            Element childProjectElement = (Element) document.importNode(childDocument.getDocumentElement(), true);
            // Making the project name explicit (so the import knows what module we are talking about)
            childProjectElement.setAttribute("name", childModule.getName());
            // Removing tags that are not necessary for the import: <update-options>, <maven-pom-manual>
            String[] unnecessaryTags = {"update-options", "maven-pom-manual"};
            for (String tag : unnecessaryTags)
                removeNodeAndPreviousBlankText(XmlUtil.lookupNode(childProjectElement, tag), true);
            // Replacing the <modules/> section with the effective modules (so the import doesn't need to download the pom)
            Node modulesNode = XmlUtil.lookupNode(childProjectElement, "modules");
            if (modulesNode != null) {
                XmlUtil.removeChildren(modulesNode);
                childModule.getChildrenModules().forEach(m -> XmlUtil.appendElementWithTextContent(modulesNode, "module", m.getName()));
            }
            // Replacing the <used-by-source-modules/> directive with the detected source modules (so the import doesn't need to download the sources)
            Node usedBySourceModulesNode = XmlUtil.lookupNode(childProjectElement, "dependencies/used-by-source-modules");
            if (usedBySourceModulesNode != null) {
                childModule.getDetectedByCodeAnalyzerSourceDependencies()
                        .map(ModuleDependency::getDestinationModule)
                        .map(Module::getName)
                        .sorted()
                        .forEach(m -> XmlUtil.appendElementWithTextContent(usedBySourceModulesNode, "module", m));
            }
            // Trying to export the packages for the third-party libraries (so the import doesn't need to download their sources)
            DevProjectModule projectModule = getProjectModule();
            new ExportedWebFxModuleFile(projectModule, childProjectElement)
                    .getRequiredThirdPartyLibraryModules()
                    .filter(LibraryModule::shouldBeDownloadedInM2)
                    // Also excluding the snapshots because the exported packages may change in the future
                    .filter(libraryModule -> !libraryModule.getVersion().contains("SNAPSHOT"))
                    .forEach(libraryModule -> {
                        ProjectModule libraryProjectModule = projectModule.searchRegisteredProjectModule(libraryModule.getName(), true);
                        if (libraryProjectModule != null)
                            libraryProjectModule.getJavaSourcePackages()
                                    .forEach(p -> XmlUtil.appendElementWithTextContentIfNotAlreadyExists(libraryModule.getXmlNode(), "exported-packages/package", p, true));
                    });
            // Adding a snapshot of the source packages, because they must be listed in executable GWT modules, and also
            // because we want to be able to evaluate the <source-packages/> directive without having to download the sources
            childModule.getJavaSourcePackages().forEach(p -> XmlUtil.appendElementWithTextContentIfNotAlreadyExists(childProjectElement, "source-packages/package", p, true));
            // Adding a snapshot of the used required java services
            childModule.getUsedRequiredJavaServices().forEach(js -> XmlUtil.appendElementWithTextContentIfNotAlreadyExists(childProjectElement, "used-services/required-service", js, true));
            // Adding a snapshot of the used optional java services
            childModule.getUsedOptionalJavaServices().forEach(js -> XmlUtil.appendElementWithTextContentIfNotAlreadyExists(childProjectElement, "used-services/optional-service", js, true));
            XmlUtil.appendIndentNode(childProjectElement, exportNode, true);
        }
    }

    private static void collectJavaPackagesAndClassesToFindUsage(Element moduleElement, ReusableStream<String>[] packagesToFindUsageHolder, ReusableStream<String>[] classesToFindUsageHolder) {
        collectJavaPackagesOrClassesToFindUsage(moduleElement, packagesToFindUsageHolder, true);
        collectJavaPackagesOrClassesToFindUsage(moduleElement, classesToFindUsageHolder, false);
    }

    private static void collectJavaPackagesOrClassesToFindUsage(Element moduleElement, ReusableStream<String>[] packagesOrClassesToFindUsageHolder, boolean packages) {
        if (moduleElement != null) {
            // Searching elements with matching text content
            NodeList nodeList = XmlUtil.lookupNodeList(moduleElement, packages ? "//if-uses-java-package" : "//if-uses-java-class");
            if (nodeList.getLength() > 0)
                addJavaPackagesOrClassesToFindUsage(
                        XmlUtil.nodeListToTextContentReusableStream(nodeList)
                        , packagesOrClassesToFindUsageHolder);
            nodeList = XmlUtil.lookupNodeList(moduleElement, packages ? "//*[@if-uses-java-package]" : "//*[@if-uses-java-class]");
            if (nodeList.getLength() > 0)
                addJavaPackagesOrClassesToFindUsage(
                        XmlUtil.nodeListToAttributeValueReusableStream(nodeList, packages ? "if-uses-java-package" : "if-uses-java-class")
                        , packagesOrClassesToFindUsageHolder);
        }
    }

    private static void addJavaPackagesOrClassesToFindUsage(ReusableStream<String> packagesOrClasses, ReusableStream<String>[] packagesOrClassesToFindUsageHolder) {
        if (packagesOrClassesToFindUsageHolder[0] != null)
            packagesOrClasses = packagesOrClassesToFindUsageHolder[0].concat(packagesOrClasses);
        packagesOrClassesToFindUsageHolder[0] = packagesOrClasses;
    }

    private static void findUsageOfJavaPackagesAndClasses(Element usagesElement, ReusableStream<ProjectModule> transitiveProjectModules, ReusableStream<String>[] packagesToFindUsageHolder, ReusableStream<String>[] classesToFindUsageHolder) {
        findUsageOfJavaPackagesOrClasses(usagesElement, transitiveProjectModules, packagesToFindUsageHolder, true);
        findUsageOfJavaPackagesOrClasses(usagesElement, transitiveProjectModules, classesToFindUsageHolder, false);
    }

    private static void findUsageOfJavaPackagesOrClasses(Element usagesElement, ReusableStream<ProjectModule> transitiveProjectModules, ReusableStream<String>[] packagesOrClassesToFindUsageHolder, boolean packages) {
        if (packagesOrClassesToFindUsageHolder[0] != null)
            packagesOrClassesToFindUsageHolder[0].distinct().sorted().forEach(packageOrClassToFindUsage -> {
                ReusableStream<ProjectModule> modulesUsingJavaPackagesOrClasses = transitiveProjectModules
                        .filter(m -> packages ? m.usesJavaPackage(packageOrClassToFindUsage) : m.usesJavaClass(packageOrClassToFindUsage))
                        .distinct()
                        .sorted();
                if (!modulesUsingJavaPackagesOrClasses.isEmpty()) {
                    Element packageElement = XmlUtil.appendElementWithAttributeIfNotAlreadyExists(usagesElement, packages ? "java-package" : "java-class", "name", packageOrClassToFindUsage, true);
                    modulesUsingJavaPackagesOrClasses
                            .forEach(pm -> XmlUtil.appendElementWithTextContent(packageElement, "module", pm.getName()));
                }
            });
    }

        private static void removeNodeAndPreviousBlankText(Node node, boolean removeComments) {
        if (node != null)
            while (true) {
                Node previousSibling = node.getPreviousSibling();
                if (previousSibling instanceof Text && previousSibling.getTextContent().isBlank()
                        || removeComments && previousSibling instanceof Comment)
                    XmlUtil.removeNode(previousSibling);
                else {
                    XmlUtil.removeNode(node);
                    break;
                }
            }
    }
}
