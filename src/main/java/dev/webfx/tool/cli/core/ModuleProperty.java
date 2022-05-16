package dev.webfx.tool.cli.core;

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

    public String getPropertyName() {
        return name;
    }

    public String getPropertyValue() {
        return value;
    }
}
