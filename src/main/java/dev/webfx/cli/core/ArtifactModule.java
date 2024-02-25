package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public class ArtifactModule implements Module {

    private final String[] split;

    public ArtifactModule(String artifact) { // artifact = groupId:artifactId:version or groupId:artifactId:type:version
        split = artifact.split(":");
    }

    @Override
    public String getName() {
        return getArtifactId();
    }

    @Override
    public String getGroupId() {
        return getArtifactToken(0);
    }

    @Override
    public String getArtifactId() {
        return getArtifactToken(1);
    }

    @Override
    public String getVersion() {
        return getArtifactToken(split.length - 1);
    }

    @Override
    public String getType() {
        return split.length < 4 ? null : getArtifactToken(split.length - 2);
    }

    @Override
    public boolean isJavaBaseEmulationModule() {
        return false; // Can't tell at this point, but should be embedded in a LibraryModule if it is the case
    }

    private String getArtifactToken(int tokenIndex) {
        if (tokenIndex < split.length)
            return split[tokenIndex];
        return null;
    }

}
