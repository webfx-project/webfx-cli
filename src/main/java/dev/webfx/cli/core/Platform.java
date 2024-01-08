package dev.webfx.cli.core;

/**
 * @author Bruno Salmon
 */
public enum Platform {

    /**************************************
     * Supported target platforms for now *
     **************************************/
      JRE       // Java Runtime Environment -> for desktop applications or servers
    , GWT       // Google Web Toolkit (Java to Javascript transpiler) -> for browser applications


    /****************************************
     * Partially supported target platforms *
     /**************************************/
    , TEAVM      // Java to JavaScript or WebAssembly transpiler -> for browser applications

    /*****************************************************
     * Possible supported target platforms in the future *
     *****************************************************
    , J2CL      // Java to Closure transpiler (will replace GWT) -> for browser applications
    , JXBROWSER // Chromium browser for Java -> can be used to test/debug browser app with WebFX code (no transpiler)
    /****************************************************/
}
