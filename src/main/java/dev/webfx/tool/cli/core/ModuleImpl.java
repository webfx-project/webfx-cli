package dev.webfx.tool.cli.core;

/**
 * @author Bruno Salmon
 */
class ModuleImpl implements Module {

    private final String name;
    protected String groupId;
    protected String artifactId;
    protected String version;
    protected String type;

    ModuleImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleImpl)) return false;

        ModuleImpl module = (ModuleImpl) o;

        return name.equals(module.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
