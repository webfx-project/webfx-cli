// File managed by WebFX (DO NOT EDIT MANUALLY)

module webfx.buildtool {

    // Direct dependencies modules
    requires info.picocli;
    requires java.base;
    requires java.xml;
    requires maven.invoker;
    requires webfx.lib.reusablestream;

    // Exported packages
    exports dev.webfx.tool.buildtool;
    exports dev.webfx.tool.buildtool.cli;
    exports dev.webfx.tool.buildtool.modulefiles;
    exports dev.webfx.tool.buildtool.modulefiles.abstr;
    exports dev.webfx.tool.buildtool.sourcegenerators;
    exports dev.webfx.tool.buildtool.util.javacode;
    exports dev.webfx.tool.buildtool.util.process;
    exports dev.webfx.tool.buildtool.util.splitfiles;
    exports dev.webfx.tool.buildtool.util.textfile;
    exports dev.webfx.tool.buildtool.util.xml;

    // Resources packages
    opens dev.webfx.tool.buildtool.cli;
    opens dev.webfx.tool.buildtool.jdk;
    opens dev.webfx.tool.buildtool.templates;

}