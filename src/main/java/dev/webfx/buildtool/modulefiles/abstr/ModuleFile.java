package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.ProjectModule;

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
