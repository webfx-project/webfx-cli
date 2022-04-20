package dev.webfx.buildtool;

/**
 * @author Bruno Salmon
 */
public final class M2RootModule extends M2ProjectModule implements RootModule {

    private final ModuleRegistry moduleRegistry;
    private final LibraryModule libraryModule;

    public M2RootModule(LibraryModule libraryModule, ModuleRegistry moduleRegistry) {
        super(libraryModule, null);
        this.moduleRegistry = moduleRegistry;
        this.libraryModule = libraryModule;
    }

    @Override
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    public boolean isWebFxModuleFileExpected() {
        return libraryModule.isWebFx();
    }

    @Override
    public boolean isInlineWebFxParent() {
        return false; // Never called
    }

    @Override
    public void setInlineWebFxParent(boolean inlineWebFxParent) {
        // Never called
    }

}
