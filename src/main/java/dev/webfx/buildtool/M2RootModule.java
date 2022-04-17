package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public final class M2RootModule extends M2ProjectModule implements RootModule {

    private final ModuleRegistry moduleRegistry;

    public M2RootModule(Module descriptor, ModuleRegistry moduleRegistry) {
        super(descriptor, null);
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    public boolean isInlineWebFxParent() {
        return false;
    }

    @Override
    public void setInlineWebFxParent(boolean inlineWebFxParent) {
    }

}
