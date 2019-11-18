@file:Suppress("UNCHECKED_CAST")

package jsitter.impl

import jsitter.api.*
import jsitter.interop.*

class TSTreeResource(val treePtr: Ptr) : Resource {
  init {
    Cleaner.register(this)
  }

  override fun disposer(): Disposer {
    val treePtr = this.treePtr
    return {
      JSitter.releaseTree(treePtr)
    }
  }
}

class TSSubtreeResource(val subtreePtr: Ptr) : Resource {
  init {
    Cleaner.register(this)
  }

  override fun disposer(): Disposer {
    val subtreePtr = this.subtreePtr
    return {
      JSitter.releaseSubtree(subtreePtr)
    }
  }
}

data class TSSubtree<out T : NodeType>(override val language: TSLanguage<*>,
                                       val subtreePtr: Ptr,
                                       val lifetime: Resource?) : Node<T> {
  override fun equals(other: Any?): Boolean =
    other is TSSubtree<*> && other.subtreePtr == this.subtreePtr

  override fun hashCode(): Int =
    subtreePtr.toInt() + 1

  override val type: T by lazy {
    this.language.getNodeType(SubtreeAccess.subtreeNodeType(this.subtreePtr)) as T
  }

  override val byteSize: Int
    get() = SubtreeAccess.subtreeBytesSize(this.subtreePtr)

  override val padding: Int
    get() = SubtreeAccess.subtreeBytesPadding(this.subtreePtr)

  override fun zipper(): Zipper<T> =
    TSZipper(
      node = this,
      parent = null,
      structuralChildIndex = 0,
      parentAliasSequence = 0L,
      byteOffset = SubtreeAccess.subtreeBytesPadding(this.subtreePtr),
      childIndex = 0)
}

data class TSTree<T : NodeType>(val treePtr: Ptr,
                                override val root: TSSubtree<T>,
                                override val actual: Boolean) : Tree<T> {
  override fun adjust(edits: List<Edit>): Tree<T> {
    if (edits.isEmpty()) {
      return this
    }
    else {
      val treeCopy = JSitter.copyTree(this.treePtr)
      for (e in edits) {
        JSitter.editTree(treeCopy, e.startByte, e.oldEndByte, e.newEndByte)
      }
      return TSTree(
        treePtr = treeCopy,
        root = root.copy(
          subtreePtr = SubtreeAccess.root(treeCopy),
          lifetime = TSTreeResource(treeCopy)),
        actual = false)
    }
  }
}


