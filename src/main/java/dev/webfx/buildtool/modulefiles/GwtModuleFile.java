package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.ModuleDependency;
import dev.webfx.buildtool.ProjectModule;
import dev.webfx.buildtool.util.textfile.ResourceTextFileReader;
import dev.webfx.buildtool.util.xml.XmlUtil;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
public final class GwtModuleFile extends XmlModuleFile {

    public GwtModuleFile(ProjectModule module) {
        super(module, false);
    }

    @Override
    Path getModuleFilePath() {
        return resolveFromModuleHomeDirectory("src/main/module.gwt.xml");
    }

    @Override
    Document createInitialDocument() {
        return XmlUtil.parseXmlString(ResourceTextFileReader.readTemplate("module.gwt.xml"));
    }

    @Override
    void updateDocument(Document document) {
        document.getDocumentElement().setAttribute("rename-to", getModule().getName().replaceAll("-", "_"));
        Node moduleSourceCommentNode = lookupNode("/module//comment()[2]");
        Node moduleSourceEndNode = moduleSourceCommentNode.getNextSibling();
        // In the GWT module file, we need to list all transitive dependencies (not only direct dependencies) so that GWT can find all the sources required by the application
        getProjectModule().getTransitiveDependencies()
                .stream().collect(Collectors.groupingBy(ModuleDependency::getDestinationModule)).entrySet()
                .stream().sorted(Map.Entry.comparingByKey())
                .forEach(moduleGroup -> {
                    Module module = moduleGroup.getKey();
                    // Ignoring emulated modules for 2 reasons:
                    // 1) they are destined to the super source, not the source (so they don't need to be listed here)
                    // 2) these modules have been shaded so the original source packages would start with emul (which would be incorrect) if they were listed here
                    if (module.getName().startsWith("webfx-platform-gwt-emul-"))
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
                    nodeAppenderIfNotOnlyComment.accept(document.createComment(createModuleSectionLine(module.getName())));
                    moduleGroup.getValue()
                            .stream().sorted(Comparator.comparing(ModuleDependency::getSourceModule)) // Sorting by source module name instead of default (destination module name)
                            .forEach(dep -> nodeAppenderIfNotOnlyComment.accept(document.createComment(" used by " + dep.getSourceModule() + " (" + dep.getType() + ") ")));
                    String gwtModuleName = getGwtModuleName(module);
                    if (gwtModuleName != null) {
                        Element inherits = document.createElement("inherits");
                        inherits.setAttribute("name", gwtModuleName);
                        nodeAppenderIfNotOnlyComment.accept(inherits);
                    }
                    if (module instanceof ProjectModule) {
                        ProjectModule projectModule = (ProjectModule) module;
                        projectModule.getDeclaredJavaPackages()
                                .stream().sorted()
                                .forEach(p -> {
                                    Element sourceElement = document.createElement("source");
                                    sourceElement.setAttribute("path", p.replaceAll("\\.", "/"));
                                    nodeAppenderIfNotOnlyComment.accept(sourceElement);
                                });
                        projectModule.getResourcePackages()
                                .stream().sorted()
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
                        projectModule.getSystemProperties()
                                .stream().sorted()
                                .forEach(p -> {
                                    Element propertyElement = document.createElement("define-configuration-property");
                                    propertyElement.setAttribute("name", p);
                                    propertyElement.setAttribute("is_multi_valued", "false");
                                    nodeAppenderIfNotOnlyComment.accept(propertyElement);
                                });
                    }
                });
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
