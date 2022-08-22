// File managed by WebFX (DO NOT EDIT MANUALLY)
package ${package};

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;
import dev.webfx.platform.resource.spi.impl.gwt.GwtResourceBundleBase;

public interface EmbedResourcesBundle extends ClientBundle {

    EmbedResourcesBundle R = GWT.create(EmbedResourcesBundle.class);
${resourceDeclaration}

    final class ProvidedGwtResourceBundle extends GwtResourceBundleBase {
        public ProvidedGwtResourceBundle() {
${resourceRegistration}
        }
    }
}

