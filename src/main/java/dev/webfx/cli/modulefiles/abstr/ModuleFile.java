package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.ProjectModule;

/**
 * @author Bruno Salmon
 */
public interface ModuleFile {

    boolean fileExists();

    Module getModule();

    default ProjectModule getProjectModule() {
        return (ProjectModule) getModule();
    }

}
