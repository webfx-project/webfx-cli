package dev.webfx.buildtool;

import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Element;

/**
 * @author Bruno Salmon
 */
public final class ImportedRootModule extends ImportedProjectModule implements RootModule {

    private final ModuleRegistry moduleRegistry;
    private final ReusableStream<ProjectModule> packageModuleSearchScopeResume =
            ReusableStream.create(this::getProjectModuleSearchScope) // Using deferred creation because the module registry constructor may not be completed yet
            .resume();

    public ImportedRootModule(String name, ModuleRegistry moduleRegistry) {
        super(name, null);
        this.moduleRegistry = moduleRegistry;
    }

    public ImportedRootModule(Element projectElement, ModuleRegistry moduleRegistry) {
        super(projectElement, null);
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
