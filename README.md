# WebFX CLI

The WebFX CLI is the Command Line Interface for WebFX. It's an essential tool for developing WebFX applications.

<p align="center">
  <img src="https://docs.webfx.dev/webfx-cli.svg" />
</p>

It creates and maintains all the application modules, with all the necessary module files for the build chain, from the information declared in webfx.xml files and the module source code. For example, it automatically computes all the dependencies of a module (in pom.xml) from an analysis of its Java source code.

More information is given in the WebFX [documentation][webfx-cli-docs], including the command line usage.

## How it works

The WebFX CLI scans all the application repository and possible libraries sources to build the knowledge of what packages belong to what modules. Knowing this, it can then tell what modules a Java source file is using, just by detecting what packages it is using. Each time a Java file references a package that is not declared in its own module, this introduces a dependency to the external module that package belongs to (it can be another module of the application or of a library).   

## Known limitation

The current Java source file analyser is a simple implementation based on regular expressions. It can detect only packages that are explicitly written in the Java source file (such as the ones listed in the imports, or in the code when using a fully qualified class name). However, it's possible to have implicit usages of packages, and the CLI currently doesn't detect them, like for example in the following code:

```java
package mypackage;

import packageA; // Where ClassA is defined in moduleA
import packageC; // Where ClassC is defined in moduleC

public class MyClass {

    public void myMethod() {
        ClassA a = new CLassA();
        ClassC c = a.getB().getC(); // implicit usage of packageB where ClassB is defined <= not detected by the CLI 
    }

}
```

## Workaround

There are 2 possible workarounds. The first is to rewrite the code in an explicit way, like for example: 

```java
package mypackage;

import packageA; // Where ClassA is defined in moduleA
import packageB; // Where ClassB is defined in moduleB
import packageC; // Where ClassC is defined in moduleC

public class MyClass {

    public void myMethod() {
        ClassA a = new CLassA();
        ClassB b = a.getB();
        ClassC c = b.getC(); 
    }

}
```

The second way is to tell the CLI what module it missed in webfx.xml:

```xml

<dependencies>
    <used-by-source-modules>
        <undetected-module>moduleC</undetected-module>
    </used-by-source-modules>
</dependencies>

```

## How to help

We are looking for a contributor with expertise in Java language parsing to replace the current implementation with a proper Java parser in order to fix the current limitation. If you are happy to work on this topic, please apply in the related [issue][webfx-cli-issue]. Thank you! 

[webfx-cli-docs]: https://docs.webfx.dev/#_introducing_the_webfx_cli
[webfx-cli-issue]: https://github.com/webfx-project/webfx-cli/issues/1