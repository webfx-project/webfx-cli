package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
public final class M2RootModule extends M2ProjectModule implements RootModule {

    private final ModuleRegistry moduleRegistry;
    private final ReusableStream<ProjectModule> packageModuleSearchScopeResume =
            ReusableStream.create(this::getProjectModuleSearchScope) // Using deferred creation because the module registry constructor may not be completed yet
            .resume();

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

    @Override
    public ReusableStream<ProjectModule> getPackageModuleSearchScopeResume() {
        return packageModuleSearchScopeResume;
    }
}
