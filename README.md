[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

# Rational

*tree-sitter* is a nice piece of parser technology. 

* Automatic error recovery
* Incremental reparse
* Immutable persistent trees, with structural sharing

This features makes it perfect for use in IDEs and editors.
However it is not obvious how we could take advantage over it on JVM platform.

Here we have options:

* Port it to Java. It requires too much work and probably some loss of performance.
* Wrap tree nodes with Java objects. Leads to significant memory pressure and redundancy.
* Navigate throught trees in native memory using Zippers.

API with Zippers is the solution taken.
Zipper gives us a nice abstraction over the place-in-tree (with ability to ascend) without compromising immutability of the tree itself.
Zippers are implemented by accessing internal TreeSitter data structures directly using sun.misc.Unsafe instrinsics and don't do any JNI calls and don't consume any additional off-heap memory.

Here is some discussion regarding the TreeSitter API:
https://github.com/tree-sitter/tree-sitter/pull/360#issuecomment-501686115

# Build Instructions

```
$ git submodule update --init --recursive
$ ./make.sh 
$ mvn install
```
