# WebFX CLI

The WebFX CLI is the Command Line Interface for WebFX. It's an essential tool for developing WebFX applications.

<p align="center">
  <img src="https://docs.webfx.dev/webfx-cli.svg" />
</p>

The WebFX CLI creates and maintains all the application modules, including all the necessary build chain module files, from the information declared in the webfx.xml files and the module source code. For example, it will automatically compute all the dependencies of a module (in pom.xml) from an analysis of the module's Java source code.

More information is given in the WebFX [documentation][webfx-cli-docs], including the installation and the command line usage.

## How it works

The WebFX CLI scans the entire application repository, together with possible library sources, to understand which packages belong to which modules. Use this information, it can then determine which modules a Java source file is using, just by detecting which packages it is using. Each time a Java file references a package that is not declared in its own module, the CLI will introduce a dependency to the external module that the package belongs to (it can be module within the application or within a library source).  

## Known limitation

The current Java source file analyser is a simple implementation based on regular expressions. It can only detect packages that are explicitly written in the Java source file (such as the ones listed in the imports, or in the code when using a fully qualified class name). However, where developers have used packages implicitly, the CLI currently cannot detect them. For example:

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

There are 2 possible workarounds. The first is to rewrite the code in an explicit way, for example: 

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

The second way is to list the modules missed by the CLI in the webfx.xml:

```xml

<dependencies>
    <used-by-source-modules>
        <undetected-module>moduleC</undetected-module>
    </used-by-source-modules>
</dependencies>

```

## Get involved!

We are looking for a contributor with expertise in Java language parsing. The task will be to replace the current implementation with a proper Java parser, in order to fix the implict package limitation. If you would like to work on this topic, please apply in the related [issue][webfx-cli-issue]. Thank you! 

[webfx-cli-docs]: https://docs.webfx.dev/#_introducing_the_webfx_cli
[webfx-cli-issue]: https://github.com/webfx-project/webfx-cli/issues/1