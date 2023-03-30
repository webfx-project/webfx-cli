package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.ArtifactResolver;
import dev.webfx.cli.modulefiles.abstr.GavApi;

/**
 * @author Bruno Salmon
 */
public interface Module extends GavApi, Comparable<Module> {

    String getName();

    String getType();

    // Comparison function used to sort modules dependencies in the Maven pom files
    @Override
    default int compareTo(Module m) {
        // Moving JavaFX emulation modules on top (before JavaFX itself even if scope is just provided) so specific emulation API can be eventually be used in peer java code
        boolean thisEmul = RootModule.isJavaFxEmulModule(this);
        boolean mEmul = RootModule.isJavaFxEmulModule(m);
        if (thisEmul != mEmul)
            return thisEmul ? -1 : 1;
        // This (temporary) rule is just for GridCollator (which has a different implementation in gwt so must be listed first)
        String GridCollatorPeerPrefix = "webfx-extras-visual-grid-peers";
        if (getName().startsWith(GridCollatorPeerPrefix) && m.getName().startsWith(GridCollatorPeerPrefix)) {
            if (getName().endsWith("-gwt"))
                return -1;
            if (m.getName().endsWith("-gwt"))
                return 1;
        }
        // Everything else is sorted in alphabetic order (using preferably the artifactId, otherwise the name)

        // Note: ArtifactResolver will resolve the artifactId differently depending on the context (which is
        // synthesised by BuildInfo). It will search first in BuildInfoThreadLocal. If not found, it will take it from
        // the module if it's a project module, otherwise it will take BuildInfo default values.
        String thisArtifactId = ArtifactResolver.getArtifactId(this);
        if (thisArtifactId == null)
            thisArtifactId = getName();
        String mArtifactId = ArtifactResolver.getArtifactId(m);
        if (mArtifactId == null)
            mArtifactId = m.getName();
        return thisArtifactId.compareTo(mArtifactId);
    }
}
