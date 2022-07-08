// File managed by WebFX (DO NOT EDIT MANUALLY)

module webfx.cli {

    // Direct dependencies modules
    requires info.picocli;
    requires java.base;
    requires java.xml;
    requires org.apache.commons.compress;
    requires webfx.lib.reusablestream;

    // Exported packages
    exports dev.webfx.cli;
    exports dev.webfx.cli.commands;
    exports dev.webfx.cli.core;
    exports dev.webfx.cli.modulefiles;
    exports dev.webfx.cli.modulefiles.abstr;
    exports dev.webfx.cli.sourcegenerators;
    exports dev.webfx.cli.util.javacode;
    exports dev.webfx.cli.util.os;
    exports dev.webfx.cli.util.process;
    exports dev.webfx.cli.util.splitfiles;
    exports dev.webfx.cli.util.textfile;
    exports dev.webfx.cli.util.xml;

    // Resources packages
    opens dev.webfx.cli.commands;
    opens dev.webfx.cli.jdk;
    opens dev.webfx.cli.templates;
    opens dev.webfx.cli.version.dev;

}