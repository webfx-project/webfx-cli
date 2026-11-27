package dev.webfx.cli.specific;

/**
 * @author Bruno Salmon
 */
public interface SpecificPackages {

    String JAVA_NIO = "java.nio";
    // java.nio packages emulated by gwt-nio (java-nio-emul) for J2CL
    String[] JAVA_NIO_EMULATED_PACKAGES = { JAVA_NIO, "java.nio.charset", "java.nio.file", "java.nio.file.attribute" };
    String JAVA_LANG_REF = "java.lang.ref";
    String JAVA_TEXT = "java.text";
    String JAVA_TIME = "java.time";
    String JAVA_UTIL_REGEX = "java.util.regex";
    String NETSCAPE_JAVASCRIPT = "netscape.javascript";
}
