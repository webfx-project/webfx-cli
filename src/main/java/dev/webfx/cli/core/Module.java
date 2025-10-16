package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.ArtifactResolver;
import dev.webfx.cli.modulefiles.abstr.GavApi;
import dev.webfx.cli.specific.SpecificModules;

/**
 * @author Bruno Salmon
 */
public interface Module extends GavApi, Comparable<Module> {

    String getName();

    String getType();

    boolean isJavaBaseEmulationModule();

    // Comparison function used to sort modules dependencies in the Maven pom files
    @Override
    default int compareTo(Module m) {
        // Note: ArtifactResolver will resolve the artifactId differently depending on the context (which is
        // synthesised by BuildInfo). It will search first in BuildInfoThreadLocal. If not found, it will take it from
        // the module if it's a project module, otherwise it will take BuildInfo default values.
        String thisArtifactId = ArtifactResolver.getArtifactId(this);
        if (thisArtifactId == null)
            thisArtifactId = getName();
        String mArtifactId = ArtifactResolver.getArtifactId(m);
        if (mArtifactId == null)
            mArtifactId = m.getName();

        return compareModuleNames(thisArtifactId, mArtifactId);
    }

    static int compareModuleNames(String thisArtifactId, String mArtifactId) {
        if (thisArtifactId.equals(SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL))
            thisArtifactId = "webfx-kit-util-after";
        if (mArtifactId.equals(SpecificModules.WEBFX_KIT_JAVAFXGRAPHICS_FAT_J2CL))
            mArtifactId = "webfx-kit-util-after";

        // Moving JavaFX emulation modules on top (before JavaFX itself even if scope is just provided), so that the
        // specific emulation API can eventually be used in the peer java code
        boolean thisEmul = SpecificModules.isJavafxEmulModule(thisArtifactId);
        boolean mEmul = SpecificModules.isJavafxEmulModule(mArtifactId);
        if (thisEmul != mEmul)
            return thisEmul ? -1 : 1;
        // This (temporary) rule is just for GridCollator (which has a different implementation in gwt/j2cl so must be listed first)
        if (thisArtifactId.startsWith(SpecificModules.WEBFX_EXTRAS_VISUAL_GRID_PEERS) && mArtifactId.startsWith(SpecificModules.WEBFX_EXTRAS_VISUAL_GRID_PEERS)) {
            if (thisArtifactId.endsWith("-elemental2"))
                return -1;
            if (mArtifactId.endsWith("-elemental2"))
                return 1;
        }
        // Everything else is sorted in alphabetic order
        return thisArtifactId.compareTo(mArtifactId);
    }
}
