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
Zipper gives us a nice abstraction over the tree implementation without producing expensive wrappers/adaptors per node.
Zippers over tree-sitter currently implemented with JNI. However it is not the only way to do that. There is an opportunity to avoid JNI overhead in future by navigating trees in native memory using only Unsafe intrinsics.
