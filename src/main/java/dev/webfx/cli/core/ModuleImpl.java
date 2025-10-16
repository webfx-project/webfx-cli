package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
class ModuleImpl implements Module {

    protected String name;
    protected String groupId;
    protected String artifactId;
    protected String version;
    protected String type;
    protected boolean javaBaseEmulationModule;

    ModuleImpl(String name) {
        this.name = name;
        javaBaseEmulationModule = getName().contains("javabase-emul");
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

    public boolean isJavaBaseEmulationModule() {
        return javaBaseEmulationModule;
    }

    public void setJavaBaseEmulationModule(boolean javaBaseEmulationModule) {
        this.javaBaseEmulationModule = javaBaseEmulationModule;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleImpl module)) return false;

        return name.equals(module.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
