package dev.webfx.cli.modulefiles.abstr;

public interface GavApi {

    String getGroupId();

    String getArtifactId();

    String getVersion();

    default boolean isSnapshotVersion() {
        String version = getVersion();
        return version != null && version.endsWith("-SNAPSHOT");
    }

}
