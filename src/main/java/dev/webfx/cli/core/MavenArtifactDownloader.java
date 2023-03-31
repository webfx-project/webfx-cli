package dev.webfx.cli.core;

public interface MavenArtifactDownloader {

    void downloadArtifact(String groupId, String artifactId, String version, String classifier);

}
