@file:Suppress("UNCHECKED_CAST")

package jsitter.impl

import jsitter.api.*
import jsitter.interop.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import java.nio.ByteBuffer

const val READING_BUFFER_CAPACITY = 1024 * 1024

class TSTextInput(val text: Text,
                  val readingBuffer: ByteBuffer) : JSitter.Input {
  override fun read(byteOffset: Int): Int {
    try {
      text.read(byteOffset, readingBuffer)
      val bytesCount = readingBuffer.position()
      readingBuffer.rewind()
      return bytesCount
    }
    catch (x: Throwable) {
      System.err.println(x)
      return 0
    }
  }
}

typealias TSSymbol = Int

class TSLanguage<T : NodeType>(
  val languagePtr: Ptr,
  override val name: String,
  override val rootNodeType: T,
  val registry: ConcurrentMap<String, NodeType> = ConcurrentHashMap(),
  val nodeTypesCache: ConcurrentMap<TSSymbol, NodeType> = ConcurrentHashMap()) : Language<T> {

  fun getNodeType(tsSymbol: TSSymbol): NodeType =
    nodeTypesCache.computeIfAbsent(tsSymbol) { symbol ->
      if (symbol.toInt() == -1) {
        Error
      }
      else {
        val name: String = JSitter.getSymbolName(languagePtr, symbol)
        val isTerminal: Boolean = JSitter.isTerminal(languagePtr, symbol)
        val nodeType = registry.computeIfAbsent(name) { name ->
          if (isTerminal) {
            Terminal(name)
          }
          else {
            NodeType(name)
          }
        }
        nodeType.id = symbol.toInt()
        nodeType
      }
    }

  fun getNodeTypeSymbol(nodeType: NodeType): TSSymbol =
    if (nodeType.initialized) {
      nodeType.id
    }
    else {
      val symbol: TSSymbol = JSitter.getSymbolByName(languagePtr, nodeType.name)
      nodeType.id = symbol
      nodeType.initialized = true
      symbol
    }

  override fun nodeType(name: String): NodeType = registry[name]!!

  override fun parser(): Parser<T> {
    val cancellationFlagPtr = SubtreeAccess.unsafe.allocateMemory(8)
    return TSParser(parserPtr = JSitter.newParser(languagePtr, cancellationFlagPtr),
                    language = this,
                    nodeType = rootNodeType,
                    cancellationFlagPtr = cancellationFlagPtr)
  }

  override fun register(nodeType: NodeType) {
    registry[nodeType.name] = nodeType
  }
}

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

  override fun zipper(): Zipper<T> =
    TSZipper(
      node = this,
      parent = null,
      structuralChildIndex = 0,
      parentAliasSequence = 0L,
      byteOffset = 0,
      childIndex = 0)
}

data class TSTree<T : NodeType>(val treePtr: Ptr,
                                override val root: TSSubtree<T>,
                                override val actual: Boolean) : Tree<T> {
  override fun adjust(edits: List<Edit>): Tree<T> {
    if (edits.isEmpty()) {
      return this
    } else {
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

data class TSParser<T : NodeType>(val parserPtr: Ptr,
                                  override val language: TSLanguage<T>,
                                  val nodeType: NodeType,
                                  val cancellationFlagPtr: Ptr) : Parser<T>, Resource {
  val readingBuffer: ByteBuffer = ByteBuffer.allocateDirect(READING_BUFFER_CAPACITY)

  init {
    Cleaner.register(this)
  }

  override fun disposer(): Disposer {
    val parserPtr = this.parserPtr
    val cancellationFlagPtr = this.cancellationFlagPtr
    return {
      JSitter.releaseParser(parserPtr)
      SubtreeAccess.unsafe.freeMemory(cancellationFlagPtr)
    }
  }

  override fun parse(text: Text, adjustedTree: Tree<T>?, cancellationToken: CancellationToken?): Tree<T>? {
    if (adjustedTree?.actual == true) {
      return adjustedTree
    }
    synchronized(this) {
      SubtreeAccess.unsafe.putLong(this.cancellationFlagPtr, 0)
      cancellationToken?.onCancel {
        SubtreeAccess.unsafe.putLong(this.cancellationFlagPtr, 1)
      }
      if (cancellationToken?.cancelled == true) {
        return null
      }
      val newTreePtr = JSitter.parse(
        this.parserPtr,
        (adjustedTree as TSTree?)?.treePtr ?: 0L,
        TSTextInput(text, this.readingBuffer),
        text.encoding.i,
        this.readingBuffer)
      JSitter.parserReset(parserPtr)
      if (newTreePtr == 0L) {
        return null
      }
      if (cancellationToken?.cancelled == true) {
        JSitter.releaseTree(newTreePtr)
        return null
      }
      return TSTree(treePtr = newTreePtr,
                    root = TSSubtree(
                      language = this.language,
                      lifetime = TSTreeResource(newTreePtr),
                      subtreePtr = SubtreeAccess.root(newTreePtr)),
                    actual = true)
    }
  }
}

fun nodesPath(zip: TSZipper<*>): List<NodeType> {
  val r = arrayListOf<NodeType>()
  var z: TSZipper<*>? = zip
  val visitedNodes = hashSetOf<Ptr>()
  while (z != null) {
    r.add(z.node.type)
    if (!visitedNodes.add(z.node.subtreePtr)) {
      throw AssertionError("there is a cycle in tree!")
    }
    z = z.parent
  }
  return r
}

class TSZipper<T : NodeType>(val parent: TSZipper<*>?,
                             val parentAliasSequence: Ptr,
                             override val node: TSSubtree<T>,
                             override val byteOffset: Int,
                             val childIndex: Int,
                             val structuralChildIndex: Int) : Zipper<T> {
  override val alias: NodeType?
    get() {
      val alias = this.aliasSymbol()
      return if (alias != 0) {
        this.node.language.getNodeType(alias) as T
      }
      else {
        null
      }
    }

  fun aliasSymbol(): TSSymbol =
    if (this.parentAliasSequence != 0L && !SubtreeAccess.extra(this.node.subtreePtr)) {
      SubtreeAccess.aliasSequenceAt(this.parentAliasSequence, this.structuralChildIndex)
    }
    else {
      0
    }

  fun visible(): Boolean =
    SubtreeAccess.isVisible(this.node.subtreePtr) || this.aliasSymbol() != 0

  override fun up(): Zipper<*>? {
    var z: TSZipper<*> = this
    while (true) {
      if (z.parent == null) {
        return null
      }
      else {
        if (z.parent!!.visible()) {
          return z.parent
        }
        else {
          z = z.parent!!
          continue
        }
      }
    }
  }

  val MAX_DEP = 100000

  class TreeTooDeep(nodesPath: List<NodeType>) : RuntimeException("$nodesPath")

  override fun down(): Zipper<*>? {
    var zip: TSZipper<*> = this
    var dep = 0
    while (true) {
      dep = dep + 1
      if (SubtreeAccess.childCount(zip.node.subtreePtr) == 0) {
        return null
      }
      else {
        if (dep > MAX_DEP) {
          throw TreeTooDeep(nodesPath(zip))
        }
        val child = SubtreeAccess.childAt(zip.node.subtreePtr, 0)
        val lang = zip.node.language.languagePtr
        val productionId = SubtreeAccess.productionId(zip.node.subtreePtr)
        val aliasSequence = SubtreeAccess.aliasSequence(lang, productionId)
        val res = TSZipper(
          parent = zip,
          parentAliasSequence = aliasSequence,
          node = TSSubtree<NodeType>(
            subtreePtr = child,
            lifetime = zip.node.lifetime,
            language = zip.node.language),
          byteOffset = zip.byteOffset,
          childIndex = 0,
          structuralChildIndex = 0)
        if (res.visible()) {
          return res
        }
        else {
          if (res.visibleChildCount() > 0) {
            zip = res
            continue
          }
          else {
            return res.right()
          }
        }
      }
    }
  }

  fun visibleChildCount(): Int =
    SubtreeAccess.visibleChildCount(this.node.subtreePtr)

  override fun downRight(): Zipper<*>? {
    var zip: TSZipper<*> = this
    var dep = 0
    while (true) {
      dep = dep + 1
      if (SubtreeAccess.childCount(zip.node.subtreePtr) == 0) {
        return null
      }
      else {
        if (dep > MAX_DEP) {
          throw TreeTooDeep(nodesPath(zip))
        }
        val child = SubtreeAccess.childAt(zip.node.subtreePtr, 0)
        val lang = zip.node.language.languagePtr
        val productionId = SubtreeAccess.productionId(zip.node.subtreePtr)
        val aliasSequence = SubtreeAccess.aliasSequence(lang, productionId)
        var res: TSZipper<*> = TSZipper(
          parent = zip,
          parentAliasSequence = aliasSequence,
          node = TSSubtree<NodeType>(
            subtreePtr = child,
            lifetime = zip.node.lifetime,
            language = zip.node.language),
          byteOffset = zip.byteOffset,
          childIndex = 0,
          structuralChildIndex = 0)
        var r: TSZipper<*>? = res
        while (r != null) {
          r = r.invisibleRight()
          if (r != null) {
            if (r.visible() || r.visibleChildCount() > 0) {
              res = r
            }
          }
        }

        if (res.visible()) {
          return res
        }
        else {
          if (res.visibleChildCount() > 0) {
            zip = res
            continue
          }
          else {
            return null
          }
        }
      }
    }
  }


  override fun left(): Zipper<*>? {
    var z: TSZipper<*> = this
    while (true) {
      if (z.parent == null) {
        return null
      }
      else if (z.childIndex == 0) {
        if (z.parent!!.visible()) {
          return null
        }
        else {
          return z.parent!!.left()
        }
      }
      else {
        val sibling: Ptr = SubtreeAccess.childAt(z.parent!!.node.subtreePtr, z.childIndex - 1)
        val structuralChildIndex =
          if (!SubtreeAccess.extra(z.node.subtreePtr)) {
            z.structuralChildIndex - 1
          }
          else {
            z.structuralChildIndex
          }
        val byteOffset = z.byteOffset - SubtreeAccess.subtreeBytesPadding(z.node.subtreePtr) - SubtreeAccess.subtreeBytesSize(sibling)
        val res = TSZipper(
          parent = z.parent,
          parentAliasSequence = z.parentAliasSequence,
          node = TSSubtree<NodeType>(
            subtreePtr = sibling,
            language = z.node.language,
            lifetime = z.node.lifetime),
          byteOffset = byteOffset,
          childIndex = z.childIndex - 1,
          structuralChildIndex = structuralChildIndex)
        if (res.visible()) {
          return res
        }
        else {
          val d = res.downRight()
          if (d != null) {
            return d
          }
          else {
            z = res
            continue
          }
        }
      }
    }
  }


  override fun right(): Zipper<*>? {
    var zip: TSZipper<*> = this
    while (true) {
      if (zip.parent == null) {
        return null
      }
      else if (zip.childIndex == SubtreeAccess.childCount(zip.parent!!.node.subtreePtr) - 1) {
        if (zip.parent!!.visible()) {
          return null
        }
        else {
          zip = zip.parent!!
          continue
        }
      }
      else {
        val sibling: Ptr = SubtreeAccess.childAt(zip.parent!!.node.subtreePtr, zip.childIndex + 1)
        val structuralChildIndex =
          if (!SubtreeAccess.extra(zip.node.subtreePtr)) {
            zip.structuralChildIndex + 1
          }
          else {
            zip.structuralChildIndex
          }
        val byteOffset = zip.byteOffset + SubtreeAccess.subtreeBytesSize(zip.node.subtreePtr) + SubtreeAccess.subtreeBytesPadding(sibling)
        val res = TSZipper(
          parent = zip.parent,
          parentAliasSequence = zip.parentAliasSequence,
          node = TSSubtree<NodeType>(
            subtreePtr = sibling,
            lifetime = zip.node.lifetime,
            language = zip.node.language),
          byteOffset = byteOffset,
          childIndex = zip.childIndex + 1,
          structuralChildIndex = structuralChildIndex)
        if (res.visible()) {
          return res
        }
        else {
          val d = res.down()
          if (d != null) {
            return d
          }
          else {
            zip = res
            continue
          }
        }
      }
    }
  }

  fun invisibleRight(): TSZipper<*>? {
    var zip: TSZipper<*> = this
    while (true) {
      if (zip.parent == null) {
        return null
      }
      else if (zip.childIndex == SubtreeAccess.childCount(zip.parent!!.node.subtreePtr) - 1) {
        return null
      }
      else {
        val sibling: Ptr = SubtreeAccess.childAt(zip.parent!!.node.subtreePtr, zip.childIndex + 1)
        val structuralChildIndex =
          if (!SubtreeAccess.extra(zip.node.subtreePtr)) {
            zip.structuralChildIndex + 1
          }
          else {
            zip.structuralChildIndex
          }
        val byteOffset = zip.byteOffset + SubtreeAccess.subtreeBytesSize(zip.node.subtreePtr) + SubtreeAccess.subtreeBytesPadding(sibling)
        val res = TSZipper(
          parent = zip.parent,
          parentAliasSequence = zip.parentAliasSequence,
          node = TSSubtree<NodeType>(
            subtreePtr = sibling,
            lifetime = zip.node.lifetime,
            language = zip.node.language),
          byteOffset = byteOffset,
          childIndex = zip.childIndex + 1,
          structuralChildIndex = structuralChildIndex)
        return res
      }
    }
  }

  override fun retainSubtree(): Node<T> {
    JSitter.retainSubtree(this.node.subtreePtr)
    return node.copy(lifetime = TSSubtreeResource(this.node.subtreePtr))
  }
}

