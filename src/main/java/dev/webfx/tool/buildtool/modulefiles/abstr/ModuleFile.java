package dev.webfx.tool.buildtool.modulefiles.abstr;

import dev.webfx.tool.buildtool.Module;
import dev.webfx.tool.buildtool.ProjectModule;

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
