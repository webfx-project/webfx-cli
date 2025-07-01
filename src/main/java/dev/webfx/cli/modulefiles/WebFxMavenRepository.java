package dev.webfx.cli.modulefiles;

/**
 * @author Bruno Salmon
 */
public final class WebFxMavenRepository {

    public static final String ID = "webfx-snapshots";
    public static final boolean SNAPSHOT = true;
    public static final boolean RELEASE = !SNAPSHOT;
    public static final String URL = "https://central.sonatype.com/repository/maven-snapshots/";

}
