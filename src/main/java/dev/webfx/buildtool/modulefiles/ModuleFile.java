package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.ProjectModule;

/**
 * @author Bruno Salmon
 */
interface ModuleFile {

    Module getModule();

    default ProjectModule getProjectModule() {
        return (ProjectModule) getModule();
    }

}
