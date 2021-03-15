package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public final class ModuleProperty {

    private final String name;
    private final String value;

    public ModuleProperty(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
