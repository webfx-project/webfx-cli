package dev.webfx.tool.cli.modulefiles.abstr;

import dev.webfx.tool.cli.core.Module;
import dev.webfx.tool.cli.core.ProjectModule;

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
