// File managed by WebFX (DO NOT EDIT MANUALLY)

module webfx.cli {

    // Direct dependencies modules
    requires info.picocli;
    requires java.compiler;
    requires java.desktop;
    requires org.apache.commons.compress;
    requires org.dom4j;
    requires webfx.lib.reusablestream;
    requires webfx.platform.ast;
    requires webfx.platform.ast.json.plugin;
    requires webfx.platform.conf;
    requires webfx.platform.meta;
    requires webfx.platform.util;

    // Exported packages
    exports dev.webfx.cli;
    exports dev.webfx.cli.commands;
    exports dev.webfx.cli.core;
    exports dev.webfx.cli.modulefiles;
    exports dev.webfx.cli.modulefiles.abstr;
    exports dev.webfx.cli.sourcegenerators;
    exports dev.webfx.cli.util.hashlist;
    exports dev.webfx.cli.util.javacode;
    exports dev.webfx.cli.util.os;
    exports dev.webfx.cli.util.process;
    exports dev.webfx.cli.util.sort;
    exports dev.webfx.cli.util.splitfiles;
    exports dev.webfx.cli.util.stopwatch;
    exports dev.webfx.cli.util.textfile;
    exports dev.webfx.cli.util.texttable;
    exports dev.webfx.cli.util.xml;

    // Resources packages
    opens dev.webfx.cli.commands;

}