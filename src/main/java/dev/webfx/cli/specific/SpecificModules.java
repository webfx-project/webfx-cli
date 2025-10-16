package dev.webfx.cli.specific;

import dev.webfx.cli.core.ModuleRegistry;
import dev.webfx.platform.util.Arrays;

/**
 * @author Bruno Salmon
 */
public interface SpecificModules {

    String JAVAFX_BASE = "javafx-base";
    String JAVAFX_GRAPHICS = "javafx-graphics";
    String JAVAFX_CONTROLS = "javafx-controls";
    String JAVAFX_MEDIA = "javafx-media";
    String JAVAFX_WEB = "javafx-web";
    String JAVAFX_FXML = "javafx-fxml";
    String WEBFX_PARENT = "webfx-parent";
    String WEBFX_KIT_JAVAFXBASE_EMUL = "webfx-kit-javafxbase-emul";
    String WEBFX_KIT_JAVAFXGRAPHICS_EMUL = "webfx-kit-javafxgraphics-emul";
    String WEBFX_KIT_JAVAFXCONTROLS_EMUL = "webfx-kit-javafxcontrols-emul";
    String WEBFX_KIT_JAVAFXMEDIA_EMUL = "webfx-kit-javafxmedia-emul";
    String WEBFX_KIT_JAVAFXWEB_EMUL = "webfx-kit-javafxweb-emul";
    String WEBFX_KIT_JAVAFXFXML_EMUL = "webfx-kit-javafxfxml-emul";
    String WEBFX_KIT_JAVAFXGRAPHICS_ELEMENTAL2 = "webfx-kit-javafxgraphics-elemental2";
    String WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL = "webfx-kit-javafxgraphics-fat-j2cl";
    String WEBFX_KIT_JAVAFXGRAPHICS_OPENJFX = "webfx-kit-javafxgraphics-openjfx";
    String WEBFX_KIT_JAVAFXWEB_REGISTRY = "webfx-kit-javafxweb-registry";
    String WEBFX_KIT_JAVAFXWEB_ENGINEPEER = "webfx-kit-javafxweb-enginepeer";
    String WEBFX_KIT_JAVAFXMEDIA_GLUON = "webfx-kit-javafxmedia-gluon";
    String WEBFX_KIT_LAUNCHER = "webfx-kit-launcher";
    String WEBFX_PLATFORM_BOOT_JAVA = "webfx-platform-boot-java";
    String WEBFX_PLATFORM_JAVABASE_EMUL_GWT = "webfx-platform-javabase-emul-gwt";
    String WEBFX_PLATFORM_JAVABASE_EMUL_J2CL = "webfx-platform-javabase-emul-j2cl";
    String WEBFX_PLATFORM_JAVATIME_EMUL_J2CL = "webfx-platform-javatime-emul-j2cl";
    String WEBFX_EXTRAS_VISUAL_GRID_PEERS = "webfx-extras-visual-grid-peers";
    String JAVA_NIO_EMUL = "java-nio-emul";
    String GWT_USER = "gwt-user";
    String GWT_DEV = "gwt-dev";
    String GWT_TIME = "gwt-time";
    String ORG_JRESEARCH_GWT_TIME_TZDB = "org.jresearch.gwt.time.tzdb";
    String J2CL_ANNOTATIONS = "j2cl-annotations";
    String J2CL_PROCESSORS = "j2cl-processors";
    String SLFJ_API = "slf4j.api";

    static boolean isJavafxModule(String moduleName) {
        return javafxModuleToEmulModule(moduleName) != moduleName;
    }

    static String emulModuleToJavafxModule(String moduleName) {
        return switch (moduleName) {
            case WEBFX_KIT_JAVAFXBASE_EMUL -> JAVAFX_BASE;
            case WEBFX_KIT_JAVAFXGRAPHICS_EMUL -> JAVAFX_GRAPHICS;
            case WEBFX_KIT_JAVAFXCONTROLS_EMUL -> JAVAFX_CONTROLS;
            case WEBFX_KIT_JAVAFXMEDIA_EMUL -> JAVAFX_MEDIA;
            case WEBFX_KIT_JAVAFXWEB_EMUL -> JAVAFX_WEB;
            case WEBFX_KIT_JAVAFXFXML_EMUL -> JAVAFX_FXML;
            default -> moduleName;
        };
    }

    static boolean isJavafxEmulModule(String moduleName) {
        //return moduleName.startsWith("webfx-kit-javafx") && moduleName.endsWith("-emul");
        return emulModuleToJavafxModule(moduleName) != moduleName;
    }

    static String javafxModuleToEmulModule(String moduleName) {
        return switch (moduleName) {
            case JAVAFX_BASE -> WEBFX_KIT_JAVAFXBASE_EMUL;
            case JAVAFX_GRAPHICS -> WEBFX_KIT_JAVAFXGRAPHICS_EMUL;
            case JAVAFX_CONTROLS -> WEBFX_KIT_JAVAFXCONTROLS_EMUL;
            case JAVAFX_MEDIA -> WEBFX_KIT_JAVAFXMEDIA_EMUL;
            case JAVAFX_WEB -> WEBFX_KIT_JAVAFXWEB_EMUL;
            case JAVAFX_FXML -> WEBFX_KIT_JAVAFXFXML_EMUL;
            default -> moduleName;
        };
    }

    static boolean isJavaFxMediaModule(String moduleName) {
        return moduleName.equals(JAVAFX_MEDIA) || moduleName.startsWith("webfx-kit-javafxmedia");
    }

    static boolean isJavaFxWebModule(String moduleName) {
        return moduleName.equals(JAVAFX_WEB) || moduleName.startsWith("webfx-kit-javafxweb");
    }

    static boolean isJavaFxFxmlModule(String moduleName) {
        return moduleName.equals(JAVAFX_FXML) || moduleName.startsWith("webfx-kit-javafxfxml");
    }

    String[] WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL_MODULES = {
        WEBFX_KIT_JAVAFXGRAPHICS_ELEMENTAL2,
            WEBFX_KIT_LAUNCHER,
            WEBFX_KIT_JAVAFXGRAPHICS_EMUL,
            "webfx-kit-javafxgraphics-peers",
            "webfx-kit-javafxgraphics-peers-base",
            "webfx-kit-javafxgraphics-peers-elemental2",
            "webfx-kit-javafxgraphics-registry-elemental2",
            "webfx-kit-util"
    };

    static boolean isModulePartOfWebfxKitJavaFxGraphicsFatJ2cl(String moduleName) {
        return Arrays.contains(WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL_MODULES, moduleName);
    }

    static boolean isRegistryModule(String moduleName) {
        return moduleName.contains("-registry-") || moduleName.endsWith("-registry");
    }

    static boolean isPluginModule(String moduleName) {
        return moduleName.contains("-plugin-") || moduleName.endsWith("-plugin");
    }

    static boolean isJsIntertopModule(String moduleName) {
        return moduleName.startsWith("jsinterop-");
    }

    static boolean isElemental2Module(String moduleName) {
        return moduleName.startsWith("elemental2-");
    }

    static boolean isPolyfillCompatModule(String moduleName) {
        return moduleName.equals("webfx-platform-polyfillcompat");
    }

    static boolean isJdkModule(String moduleName) {
        return ModuleRegistry.isJdkModule(moduleName);
    }

    static boolean skipJavaModuleInfo(String moduleName) {
        // We don't generate module-info.java for webfx-kit-javafxweb-registry because this generates JavaDoc errors (due to 'requires webfx.kit.javafxweb.enginepeer.emul' <- and no module-info.java for emul modules)
        return moduleName.equals(WEBFX_KIT_JAVAFXWEB_REGISTRY)
               // webfx-kit-javafxweb-enginepeer dependencies are emulation modules (with no module-info.java)
               || moduleName.equals(WEBFX_KIT_JAVAFXWEB_ENGINEPEER);
    }

}
