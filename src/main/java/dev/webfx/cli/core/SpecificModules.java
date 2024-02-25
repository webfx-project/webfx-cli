package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public class SpecificModules {

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
    public static final String WEBFX_KIT_GWT = "webfx-kit-gwt";
    public static final String WEBFX_KIT_J2CL = "webfx-kit-j2cl";
    public static final String WEBFX_KIT_OPENJFX = "webfx-kit-openjfx";
    public static final String WEBFX_KIT_JAVAFXWEB_REGISTRY = "webfx-kit-javafxweb-registry";
    public static final String WEBFX_KIT_JAVAFXWEB_ENGINEPEER = "webfx-kit-javafxweb-enginepeer";
    public static final String WEBFX_KIT_JAVAFXMEDIA_GLUON = "webfx-kit-javafxmedia-gluon";
    public static final String WEBFX_PLATFORM_BOOT_JAVA = "webfx-platform-boot-java";
    public static final String WEBFX_PLATFORM_JAVABASE_EMUL_GWT = "webfx-platform-javabase-emul-gwt";
    public static final String WEBFX_PLATFORM_JAVABASE_EMUL_J2CL = "webfx-platform-javabase-emul-j2cl";
    public static final String WEBFX_EXTRAS_VISUAL_GRID_PEERS = "webfx-extras-visual-grid-peers";
    public static final String JAVA_NIO_EMUL = "java-nio-emul";
    public static final String GWT_USER = "gwt-user";
    public static final String GWT_DEV = "gwt-dev";
    public static final String GWT_TIME = "gwt-time";
    public static final String ORG_JRESEARCH_GWT_TIME_TZDB = "org.jresearch.gwt.time.tzdb";
    public static final String J2CL_TIME = "j2cl-time";
    public static final String J2CL_ANNOTATIONS = "j2cl-annotations";
    public static final String J2CL_PROCESSORS = "j2cl-processors";
    public static final String SLFJ_API = "slf4j.api";

    public static boolean isJavafxModule(String moduleName) {
        return javafxModuleToEmulModule(moduleName) != moduleName;
    }

    public static String emulModuleToJavafxModule(String moduleName) {
        switch (moduleName) {
            case WEBFX_KIT_JAVAFXBASE_EMUL:
                return JAVAFX_BASE;
            case WEBFX_KIT_JAVAFXGRAPHICS_EMUL:
                return JAVAFX_GRAPHICS;
            case WEBFX_KIT_JAVAFXCONTROLS_EMUL:
                return JAVAFX_CONTROLS;
            case WEBFX_KIT_JAVAFXMEDIA_EMUL:
                return JAVAFX_MEDIA;
            case WEBFX_KIT_JAVAFXWEB_EMUL:
                return JAVAFX_WEB;
            case WEBFX_KIT_JAVAFXFXML_EMUL:
                return JAVAFX_FXML;
            default:
                return moduleName;
        }
    }

    public static boolean isJavafxEmulModule(String moduleName) {
        //return moduleName.startsWith("webfx-kit-javafx") && moduleName.endsWith("-emul");
        return emulModuleToJavafxModule(moduleName) != moduleName;
    }

    public static String javafxModuleToEmulModule(String moduleName) {
        switch (moduleName) {
            case JAVAFX_BASE:
                return WEBFX_KIT_JAVAFXBASE_EMUL;
            case JAVAFX_GRAPHICS:
                return WEBFX_KIT_JAVAFXGRAPHICS_EMUL;
            case JAVAFX_CONTROLS:
                return WEBFX_KIT_JAVAFXCONTROLS_EMUL;
            case JAVAFX_MEDIA:
                return WEBFX_KIT_JAVAFXMEDIA_EMUL;
            case JAVAFX_WEB:
                return WEBFX_KIT_JAVAFXWEB_EMUL;
            case JAVAFX_FXML:
                return WEBFX_KIT_JAVAFXFXML_EMUL;
            default:
                return moduleName;
        }
    }

    public static boolean isJavaFxMediaModule(String moduleName) {
        return moduleName.equals(JAVAFX_MEDIA) || moduleName.startsWith("webfx-kit-javafxmedia");
    }

    public static boolean isJavaFxWebModule(String moduleName) {
        return moduleName.equals(JAVAFX_WEB) || moduleName.startsWith("webfx-kit-javafxweb");
    }

    public static boolean isJavaFxFxmlModule(String moduleName) {
        return moduleName.equals(JAVAFX_FXML) || moduleName.startsWith("webfx-kit-javafxfxml");
    }

    public static boolean isModuleIntegratedToWebfxKitJ2cl(String moduleName) {
        switch (moduleName) {
            case WEBFX_KIT_GWT:
            case "webfx-kit-launcher":
            case WEBFX_KIT_JAVAFXGRAPHICS_EMUL:
            case "webfx-kit-javafxgraphics-peers":
            case "webfx-kit-javafxgraphics-peers-base":
            case "webfx-kit-javafxgraphics-peers-gwt":
            case "webfx-kit-javafxgraphics-registry-gwt":
            case "webfx-kit-util":
                return true;
            default:
                return false;
        }
    }

    public static boolean isRegistryModule(String moduleName) {
        return moduleName.contains("-registry-") || moduleName.endsWith("-registry");
    }

    public static boolean isJsIntertopModule(String moduleName) {
        return moduleName.startsWith("jsinterop-");
    }

    public static boolean isElemental2Module(String moduleName) {
        return moduleName.startsWith("elemental2-");
    }

    public static boolean isJdkModule(String moduleName) {
        return ModuleRegistry.isJdkModule(moduleName);
    }

    public static boolean skipJavaModuleInfo(String moduleName) {
        // We don't generate module-info.java for webfx-kit-javafxweb-registry because this generates JavaDoc errors (due to 'requires webfx.kit.javafxweb.enginepeer.emul' <- and no module-info.java for emul modules)
        return moduleName.equals(WEBFX_KIT_JAVAFXWEB_REGISTRY)
                // webfx-kit-javafxweb-enginepeer dependencies are emulation modules (with no module-info.java)
                || moduleName.equals(WEBFX_KIT_JAVAFXWEB_ENGINEPEER);
    }

}
