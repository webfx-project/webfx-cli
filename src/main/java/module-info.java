// File managed by WebFX (DO NOT EDIT MANUALLY)

module webfx.cli {

    // Direct dependencies modules
    requires info.picocli;
    requires java.base;
    requires java.xml;
    requires maven.invoker;
    requires org.apache.commons.compress;
    requires webfx.lib.reusablestream;

    // Exported packages
    exports dev.webfx.tool.cli;
    exports dev.webfx.tool.cli.commands;
    exports dev.webfx.tool.cli.core;
    exports dev.webfx.tool.cli.modulefiles;
    exports dev.webfx.tool.cli.modulefiles.abstr;
    exports dev.webfx.tool.cli.sourcegenerators;
    exports dev.webfx.tool.cli.util.javacode;
    exports dev.webfx.tool.cli.util.os;
    exports dev.webfx.tool.cli.util.process;
    exports dev.webfx.tool.cli.util.splitfiles;
    exports dev.webfx.tool.cli.util.textfile;
    exports dev.webfx.tool.cli.util.xml;

    // Resources packages
    opens dev.webfx.tool.cli.commands;
    opens dev.webfx.tool.cli.jdk;
    opens dev.webfx.tool.cli.templates;
    opens dev.webfx.tool.cli.version.dev;

}