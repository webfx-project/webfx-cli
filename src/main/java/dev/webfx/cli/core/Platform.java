package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public enum Platform {

    /**************************************
     * Supported target platforms for now *
     **************************************/
      JRE       // Java Runtime Environment -> for desktop applications or servers
    , GWT       // Google Web Toolkit: no longer a build target of this CLI, but still known to recognise the
                // GWT-specific library modules of WebFX (ex: webfx-platform-javabase-emul-gwt), so that no other
                // target picks them
    , J2CL      // Java to Closure transpiler (will replace GWT) -> for browser applications
    , TEAVM      // Java to JavaScript or WebAssembly transpiler -> for browser applications

    /*****************************************************
     * Possible supported target platforms in the future *
     *****************************************************
    , JXBROWSER // Chromium browser for Java -> can be used to test/debug browser app with WebFX code (no transpiler)
    /****************************************************/
}
