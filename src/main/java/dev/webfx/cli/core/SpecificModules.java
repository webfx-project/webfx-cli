package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public class SpecificModules {

    public static final String ELEMENTAL_2_CORE = "elemental2-core";
    public static final String ELEMENTAL_2_DOM = "elemental2-dom";
    public static final String GWT_USER = "gwt-user";
    public static final String GWT_DEV = "gwt-dev";
    public static final String JSINTEROP_BASE ="jsinterop-base";
    public static final String JSINTEROP_ANNOTATIONS ="jsinterop-annotations";

    public static final String JAVAFX_BASE = "javafx-base";
    public static final String JAVAFX_GRAPHICS = "javafx-graphics";
    public static final String JAVAFX_CONTROLS = "javafx-controls";
    public static final String JAVAFX_MEDIA = "javafx-media";
    public static final String JAVAFX_WEB = "javafx-web";
    public static final String JAVAFX_FXML = "javafx-fxml";
    public static final String WEBFX_KIT_JAVAFXBASE_EMUL = "webfx-kit-javafxbase-emul";
    public static final String WEBFX_KIT_JAVAFXGRAPHICS_EMUL = "webfx-kit-javafxgraphics-emul";
    public static final String WEBFX_KIT_JAVAFXCONTROLS_EMUL = "webfx-kit-javafxcontrols-emul";
    public static final String WEBFX_KIT_JAVAFXMEDIA_EMUL = "webfx-kit-javafxmedia-emul";
    public static final String WEBFX_KIT_JAVAFXWEB_EMUL = "webfx-kit-javafxweb-emul";
    public static final String WEBFX_KIT_JAVAFXFXML_EMUL = "webfx-kit-javafxfxml-emul";

    public static final String JAVA_NIO_EMUL = "java-nio-emul";
    public static final String GWT_TIME = "gwt-time";
    public static final String WEBFX_KIT_GWT = "webfx-kit-gwt";
    public static final String WEBFX_PLATFORM_JAVABASE_EMUL_GWT = "webfx-platform-javabase-emul-gwt";
    public static final String WEBFX_KIT_JAVAFXWEB_REGISTRY = "webfx-kit-javafxweb-registry";
    public static final String WEBFX_KIT_JAVAFXWEB_ENGINEPEER = "webfx-kit-javafxweb-enginepeer";
    public static final String WEBFX_PLATFORM_BOOT_JAVA = "webfx-platform-boot-java";
    public static final String WEBFX_KIT_OPENJFX = "webfx-kit-openjfx";
    public static final String WEBFX_KIT_JAVAFXMEDIA_GLUON = "webfx-kit-javafxmedia-gluon";

    public static boolean skipJavaModuleInfo(String moduleName) {
        // We don't generate module-info.java for webfx-kit-javafxweb-registry because this generates JavaDoc errors (due to 'requires webfx.kit.javafxweb.enginepeer.emul' <- and no module-info.java for emul modules)
        return moduleName.equals(WEBFX_KIT_JAVAFXWEB_REGISTRY)
                // webfx-kit-javafxweb-enginepeer dependencies are emulation modules (with no module-info.java)
                || moduleName.equals(WEBFX_KIT_JAVAFXWEB_ENGINEPEER);
    }

    public static boolean isMediaModule(String moduleName) {
        return /*moduleName.equals(JAVAFX_MEDIA) || */moduleName.startsWith("webfx-kit-javafxmedia");
    }

    public static boolean isWebModule(String moduleName) {
        return moduleName.equals(JAVAFX_WEB) || moduleName.startsWith("webfx-kit-javafxweb");
    }

    public static boolean isFxmlModule(String moduleName) {
        return moduleName.equals(JAVAFX_FXML) || moduleName.equals(SpecificModules.WEBFX_KIT_JAVAFXFXML_EMUL);
    }

    public static boolean isRegistryModule(String moduleName) {
        return moduleName.contains("-registry-") || moduleName.endsWith("-registry");
    }

    public static boolean isJdkEmulationModule(String moduleName) {
        switch (moduleName) {
            case JAVA_NIO_EMUL:
                return true;
        }
        return false;
    }
}
