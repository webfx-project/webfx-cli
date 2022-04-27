package dev.webfx.tool.buildtool.cli;

/**
 * @author Bruno Salmon
 */
public class Keywords {

    interface Keyword {
    }

    enum ToKeyword implements Keyword { to }

    enum UnderKeyword implements Keyword { under }

    enum ModuleOrPackageKeyword implements Keyword {
        module, pkg {
            @Override
            public String toString() {
                return "package";
            }
        }
    }

}
