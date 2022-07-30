package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.*;
import dev.webfx.cli.core.Module;
import dev.webfx.cli.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.cli.util.textfile.ResourceTextFileReader;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
public final class DevGwtModuleFile extends DevXmlModuleFileImpl {

    public DevGwtModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("src/main/module.gwt.xml"), false);
    }

    @Override
    public Document createInitialDocument() {
        return XmlUtil.parseXmlString(ResourceTextFileReader.readTemplate("module.gwt.xml"));
    }

    @Override
    public boolean updateDocument(Document document) {
        document.getDocumentElement().setAttribute("rename-to", getModule().getName().replaceAll("-", "_"));
        Node moduleSourceCommentNode = lookupNode("/module//comment()[2]");
        Node moduleSourceEndNode = moduleSourceCommentNode.getNextSibling();
        // In the GWT module file, we need to list all transitive dependencies (not only direct dependencies) so that GWT can find all the sources required by the application
        getProjectModule().getMainJavaSourceRootAnalyzer().getTransitiveDependencies()
                .collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                .stream().sorted(Map.Entry.comparingByKey())
                .forEach(moduleGroup -> {
                    Module module = moduleGroup.getKey();
                    String moduleName = module.getName();
                    // Ignoring emulated modules for 2 reasons:
                    // 1) they are destined to the super source, not the source (so they don't need to be listed here)
                    // 2) these modules have been shaded so the original source packages would start with emul (which would be incorrect) if they were listed here
                    if (moduleName.startsWith("webfx-platform-emul-") && moduleName.endsWith("-gwt"))
                        return;
                    Node parentNode = moduleSourceEndNode.getParentNode();
                    // Creating a node appender that inserts a node with the after a new line and indentation
                    String newIndentedLine = "\n    ";
                    Consumer<Node> nodeAppender = node -> {
                        parentNode.insertBefore(document.createTextNode(newIndentedLine), moduleSourceEndNode);
                        parentNode.insertBefore(node, moduleSourceEndNode);
                    };

                    // Creating a wrapper of the node appender that will drop all the unnecessary comments if there is
                    // finally no elements (such as source, resource or configuration property) for that module.
                    List<Comment> initialComments = new ArrayList<>();
                    Boolean[] hasOnlyCommentsSoFar = {true}; // wrapping the boolean in an array so the lambda expression can change its value
                    Consumer<Node> nodeAppenderIfNotOnlyComment = node -> {
                        if (hasOnlyCommentsSoFar[0]) {
                            if (node instanceof Comment) {
                                initialComments.add((Comment) node);
                                return;
                            }
                            // First node that is not a comment
                            initialComments.forEach(nodeAppender); // Writing back all initial comments
                            initialComments.clear(); // Ok to free the memory now
                            hasOnlyCommentsSoFar[0] = false; // Changing the boolean value
                            // Now that all comments are written, we can append the node
                        }
                        nodeAppender.accept(node);
                    };
                    nodeAppenderIfNotOnlyComment.accept(document.createComment(createModuleSectionLine(moduleName)));
                    /* Commented because this "Used by " section doesn't always generate the same content (ex: different result when executed on a single repository or on the contrib repository)
                    moduleGroup.getValue()
                            .stream().sorted(Comparator.comparing(ModuleDependency::getSourceModule)) // Sorting by source module name instead of default (destination module name)
                            .forEach(dep -> nodeAppenderIfNotOnlyComment.accept(document.createComment(" used by " + dep.getSourceModule() + " (" + dep.getType() + ") ")));
                    */
                    String gwtModuleName = getGwtModuleName(module);
                    if (gwtModuleName != null) {
                        Element inherits = document.createElement("inherits");
                        inherits.setAttribute("name", gwtModuleName);
                        nodeAppenderIfNotOnlyComment.accept(inherits);
                    }
                    ReusableStream<String> sourcePackages = ReusableStream.empty(), resourcePackages = sourcePackages, systemProperties = sourcePackages;
                    if (module instanceof ProjectModule) {
                        ProjectModule projectModule = (ProjectModule) module;
                        resourcePackages = projectModule.getResourcePackages();
                        sourcePackages = projectModule.getMainJavaSourceRootAnalyzer().getSourcePackages();
                        systemProperties = projectModule.getSystemProperties();
                    } else if (module instanceof LibraryModule && !ModuleRegistry.isJdkModule(module)) {
                        boolean isGwtLibrary = gwtModuleName != null && (
                                        gwtModuleName.startsWith("com.google.gwt") ||
                                        gwtModuleName.startsWith("org.gwtproject") ||
                                        gwtModuleName.startsWith("elemental2")) ||
                        moduleName.startsWith("jsinterop");
                        if (!isGwtLibrary) // No need to list the source packages for GWT libraries
                            sourcePackages = ((LibraryModule) module).getExportedPackages();
                    }
                    sourcePackages
                            .sorted()
                            .forEach(p -> {
                                Element sourceElement = document.createElement("source");
                                sourceElement.setAttribute("path", p.replaceAll("\\.", "/"));
                                nodeAppenderIfNotOnlyComment.accept(sourceElement);
                            });
                    resourcePackages
                            .sorted()
                            .forEach(p -> {
                                Element resourceElement = document.createElement("resource");
                                resourceElement.setAttribute("path", p.replaceAll("\\.", "/"));
                                nodeAppenderIfNotOnlyComment.accept(resourceElement);
                                Element publicElement = document.createElement("public");
                                publicElement.setAttribute("path", "");
                                publicElement.setAttribute("includes", p.replaceAll("\\.", "/") + "/");
                                nodeAppenderIfNotOnlyComment.accept(publicElement);
                            });
                    // Declaring the system properties to ask GWT to replace the System.getProperty() calls with the
                    // values at compile time.
                    // Note: these properties are set with the -setProperty GWT compiler argument when calling the
                    // GWT plugin in the root pom.xml
                    systemProperties
                            .sorted()
                            .forEach(p -> {
                                Element propertyElement = document.createElement("define-configuration-property");
                                propertyElement.setAttribute("name", p);
                                propertyElement.setAttribute("is_multi_valued", "false");
                                nodeAppenderIfNotOnlyComment.accept(propertyElement);
                            });
                });
        return true;
    }

    private static String createModuleSectionLine(String title) {
        int lineLength = Math.max(title.length(), 80);
        int left = (lineLength - title.length()) / 2;
        return Stream.generate(() -> "=").limit(left).collect(Collectors.joining())
                + "< " + title + " >" +
                Stream.generate(() -> "=").limit(lineLength - left - title.length()).collect(Collectors.joining());
    }

    private static String getGwtModuleName(Module module) {
        // TODO Move this hardcoded declaration into webfx.xml files (<gwt-module-name> tag)
        switch (module.getName()) {
            case "gwt-user": return "com.google.gwt.user.User";
            case "java-logging": return "com.google.gwt.logging.Logging";
            case "elemental2-core": return "elemental2.core.Core";
            case "elemental2-dom": return "elemental2.dom.Dom";
            case "elemental2-svg": return "elemental2.svg.Svg";
            case "gwt-charts": return "com.googlecode.gwt.charts.Charts";
            case "charba": return "org.pepstock.charba.Charba";
            case "java-nio-emul": return "org.gwtproject.nio.GwtNioSupport"; // gwt-nio
            default: return null;
        }
    }
}
