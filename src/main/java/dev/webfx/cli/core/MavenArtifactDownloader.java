package dev.webfx.cli.core;

public interface MavenArtifactDownloader {

    boolean downloadArtifact(String groupId, String artifactId, String version, String classifier);

}
