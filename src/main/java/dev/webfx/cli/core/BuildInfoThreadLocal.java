package dev.webfx.cli.core;

/**
 * Usage:
 *
 * try (BuildInfoThreadLocal closable = BuildInfoThreadLocal.open(buildInfo)) {
 *      ...
 *      Any call to BuildInfoThreadLocal.getBuildInfo() - including in external methods - will return buildInfo
 *      until then end of this try block
 *      ...
 * }
 *
 * @author Bruno Salmon
 */
public final class BuildInfoThreadLocal implements AutoCloseable {

    private static final ThreadLocal<BuildInfo> THREAD_LOCAL = new ThreadLocal<>();

    private final BuildInfo previousBuildInfo = THREAD_LOCAL.get();

    private BuildInfoThreadLocal(BuildInfo threadLocalBuildInfo) {
        THREAD_LOCAL.set(threadLocalBuildInfo);
    }

    public static BuildInfoThreadLocal open(BuildInfo buildInfo) {
        return buildInfo == null ? null : new BuildInfoThreadLocal(buildInfo);
    }

    public static BuildInfo getBuildInfo() {
        return THREAD_LOCAL.get();
    }

    @Override
    public void close() { // Automatically called on try block exit (unless open() returned null)
        THREAD_LOCAL.set(previousBuildInfo);
    }

}
