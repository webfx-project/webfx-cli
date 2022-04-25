package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.*;
import dev.webfx.buildtool.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.*;

import java.util.*;

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
            removeNodeAndPreviousCommentsOrBlankTexts(exportNode);
            XmlUtil.removeNode(exportNode);
        } else
            exportNode = document.createElement(EXPORT_SNAPSHOT_TAG);
        if (!generatesExportSnapshot())
            return exportNodeWasPresent;
        // Exporting this and children modules in depth
        DevProjectModule projectModule = getProjectModule();
        final Node finalExportNode = exportNode;
        projectModule.getThisAndChildrenModulesInDepth()
                .forEach(pm -> exportChildModuleProject(pm, finalExportNode));
        // Adding usage to resolve if-uses-java-package and if-uses-java-class directives without downloading the sources
        ReusableStream<ProjectModule> usageCoverage = projectModule.getDirectivesUsageCoverage();
        // First pass: searching all the if-uses-java-package and if-java-classes directives and collecting the packages or classes that require to find the usage
        Set<String> packagesListedInDirectives = new HashSet<>(); // To be populated
        Set<String> classesListedInDirectives = new HashSet<>(); // To be populated
        usageCoverage
                .forEach(pm -> collectJavaPackagesAndClassesListedInDirectives(pm, packagesListedInDirectives, classesListedInDirectives));
        //System.out.println("packagesListedInDirectives: " + packagesListedInDirectives);
        //System.out.println("classesListedInDirectives: " + classesListedInDirectives);
        // Third pass: finding usage
        Element usagesElement = document.createElement("usages");
        computeAndPopulateUsagesOfJavaPackagesAndClasses(usagesElement, usageCoverage,
                convertSetToSortedList(packagesListedInDirectives),
                convertSetToSortedList(classesListedInDirectives));
        if (usagesElement.hasChildNodes())
            XmlUtil.appendIndentNode(usagesElement, exportNode, true);
        appendIndentNode(document.createComment(EXPORT_SECTION_COMMENT), true);
        appendIndentNode(exportNode, true);
        return true;
    }

    private static <T extends Comparable<? super T>> List<T> convertSetToSortedList(Set<T> set) {
        List<T> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
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
                removeNodeAndPreviousCommentsOrBlankTexts(XmlUtil.lookupNode(childProjectElement, tag));
            // Replacing the <modules/> section with the effective modules (so the import doesn't need to download the pom)
            Node modulesNode = XmlUtil.lookupNode(childProjectElement, "modules");
            if (modulesNode != null) {
                XmlUtil.removeChildren(modulesNode);
                childModule.getChildrenModules().forEach(m -> XmlUtil.appendElementWithTextContent(modulesNode, "module", m.getName()));
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
            // Adding a snapshot of the detected used by sources modules (so the import doesn't need to download the sources).
            if (childModule.hasJavaSourceDirectory()) {
                Node detectedUsedBySourceModulesNode = XmlUtil.appendIndentNode(document.createElement("used-by-source-modules"), childProjectElement, true);
                childModule.getDetectedByCodeAnalyzerSourceDependencies()
                        .map(ModuleDependency::getDestinationModule)
                        .map(Module::getName)
                        .sorted()
                        .forEach(m -> XmlUtil.appendElementWithTextContent(detectedUsedBySourceModulesNode, "module", m));
            }
            // Adding a snapshot of the used required java services
            childModule.getUsedRequiredJavaServices().forEach(js -> XmlUtil.appendElementWithTextContentIfNotAlreadyExists(childProjectElement, "used-services/required-service", js, true));
            // Adding a snapshot of the used optional java services
            childModule.getUsedOptionalJavaServices().forEach(js -> XmlUtil.appendElementWithTextContentIfNotAlreadyExists(childProjectElement, "used-services/optional-service", js, true));
            XmlUtil.appendIndentNode(childProjectElement, exportNode, true);
        }
    }

    private static void collectJavaPackagesAndClassesListedInDirectives(ProjectModule pm, Set<String> packagesListedInDirectives /* to populate */, Set<String> classesListedInDirectives /* to populate */) {
        Element moduleElement = pm.getWebFxModuleFile().getModuleElement();
        collectJavaPackagesOrClassesListedInDirectives(moduleElement, packagesListedInDirectives, true);
        collectJavaPackagesOrClassesListedInDirectives(moduleElement, classesListedInDirectives, false);
    }

    private static void collectJavaPackagesOrClassesListedInDirectives(Element moduleElement, Set<String> packagesOrClassesListedInDirectives, boolean packages) {
        if (moduleElement != null) {
            // Collecting elements with matching text content
            packagesOrClassesListedInDirectives.addAll(
                    XmlUtil.nodeListToTextContentList(
                            XmlUtil.lookupNodeList(moduleElement, packages ? "//if-uses-java-package" : "//if-uses-java-class")
                    )
            );
            // Collecting element with matching attributes
            packagesOrClassesListedInDirectives.addAll(
                    XmlUtil.nodeListToAttributeValueList(
                            XmlUtil.lookupNodeList(moduleElement, packages ? "//*[@if-uses-java-package]" : "//*[@if-uses-java-class]")
                            , packages ? "if-uses-java-package" : "if-uses-java-class"
                    )
            );
        }
    }

    private static void computeAndPopulateUsagesOfJavaPackagesAndClasses(Element usagesElement, ReusableStream<ProjectModule> searchScope, List<String> packagesListedInDirectives, List<String> classesListedInDirectives) {
        computeAndPopulateUsagesOfJavaPackagesOrClasses(usagesElement, searchScope, packagesListedInDirectives, true);
        computeAndPopulateUsagesOfJavaPackagesOrClasses(usagesElement, searchScope, classesListedInDirectives, false);
    }

    private static void computeAndPopulateUsagesOfJavaPackagesOrClasses(Element usagesElement, ReusableStream<ProjectModule> searchScope, List<String> packagesOrClassesListedInDirectives, boolean packages) {
        packagesOrClassesListedInDirectives
                .forEach(packageOrClassToFindUsage -> {
                    ReusableStream<ProjectModule> modulesUsingJavaPackagesOrClasses = searchScope
                            //.flatMap(ProjectModule::getThisAndTransitiveModules) // already done, no?
                            .filter(ProjectModule.class::isInstance)
                            .map(ProjectModule.class::cast)
                            .distinct()
                            .filter(m -> usesJavaPackageOrClass(m, packageOrClassToFindUsage, packages))
                            .sorted();
                    Element packageElement = XmlUtil.appendElementWithAttributeIfNotAlreadyExists(usagesElement, packages ? "java-package" : "java-class", "name", packageOrClassToFindUsage, true);
                    modulesUsingJavaPackagesOrClasses
                            .forEach(pm -> XmlUtil.appendElementWithTextContent(packageElement, "module", pm.getName()));
                });
    }

    private static boolean usesJavaPackageOrClass(ProjectModule pm, String packageOrClassToFindUsage, boolean isPackage) {
        return isPackage ? pm.usesJavaPackage(packageOrClassToFindUsage) : pm.usesJavaClass(packageOrClassToFindUsage);
    }

    private static void removeNodeAndPreviousCommentsOrBlankTexts(Node node) {
        if (node != null)
            while (true) {
                Node previousSibling = node.getPreviousSibling();
                if (previousSibling instanceof Comment ||
                        previousSibling instanceof Text && previousSibling.getTextContent().isBlank())
                    XmlUtil.removeNode(previousSibling);
                else {
                    XmlUtil.removeNode(node);
                    break;
                }
            }
    }

}
