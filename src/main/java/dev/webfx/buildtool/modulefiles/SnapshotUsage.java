package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.ProjectModule;

import java.util.List;

/**
 * @author Bruno Salmon
 */
public final class SnapshotUsage {
    final String packageOrClassName;
    final M2ProjectModule m2ProjectModule;
    final List<String> usedByModules;

    public SnapshotUsage(String packageOrClassName, M2ProjectModule m2ProjectModule, List<String> usedByModules) {
        this.packageOrClassName = packageOrClassName;
        this.m2ProjectModule = m2ProjectModule;
        this.usedByModules = usedByModules;
    }

    public Boolean isModuleUsing(ProjectModule module) {
        // First quick check: if the present module is listed in any already computed usage for that class or package, we return true
        if (usedByModules.contains(module.getName()))
            return true;
        // At this stage we know that this module was never listed in any usage of this class or package computed
        // so far, but we need to check if these computed usages considered that module or not in this computation.
        // If any of them did, we can return false, because it means that that existing usage already checked
        // that this module wasn't using that class or package.
        if (m2ProjectModule.getDirectivesUsageCoverage().anyMatch(pm -> pm == module))
            return false;
        return null;
    }
}
