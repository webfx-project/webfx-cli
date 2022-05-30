package dev.webfx.tool.cli.util.os;

/**
 * @author Bruno Salmon
 */
public class OperatingSystem {

    public static String getOsName() {
        return System.getProperty("os.name");
    }

    public static OsFamily getOsFamily() {
        String name = getOsName().toLowerCase().replaceAll(" ", "");
        if (name.contains("windows"))
            return OsFamily.WINDOWS;
        if (name.contains("macos"))
            return OsFamily.MAC_OS;
        if (name.contains("linux"))
            return OsFamily.LINUX;
        return OsFamily.OTHER;
    }

    public static boolean isWindows() {
        return getOsFamily() == OsFamily.WINDOWS;
    }

    public static boolean isMacOs() {
        return getOsFamily() == OsFamily.MAC_OS;
    }

    public static boolean isLinux() {
        return getOsFamily() == OsFamily.LINUX;
    }

}
