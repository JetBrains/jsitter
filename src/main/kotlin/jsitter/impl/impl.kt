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
        } catch (x: Throwable) {
            System.err.println(x)
            return 0
        }
    }
}

typealias TSSymbol = Int

class TSLanguage<T: NodeType>(
        val languagePtr: Ptr,
        override val name: String,
        override val sourceFileNodeType: T,
        val registry: ConcurrentMap<String, NodeType> = ConcurrentHashMap(),
        val nodeTypesCache: ConcurrentMap<TSSymbol, NodeType> = ConcurrentHashMap()) : Language<T> {

    fun getNodeType(tsSymbol: TSSymbol): NodeType =
            nodeTypesCache.computeIfAbsent(tsSymbol) { symbol ->
                if (symbol.toInt() == -1) {
                    Error
                } else {
                    val name: String = JSitter.getSymbolName(languagePtr, symbol)
                    val isTerminal: Boolean = JSitter.isTerminal(languagePtr, symbol)
                    val nodeType = registry.computeIfAbsent(name) { name ->
                        if (isTerminal) {
                            Terminal(name)
                        } else {
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
            } else {
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
                nodeType = sourceFileNodeType,
                cancellationFlagPtr = cancellationFlagPtr) as Parser<T>
    }

    override fun register(nodeType: NodeType) {
        registry[nodeType.name] = nodeType
    }
}

class TSTReeResource(val treePtr: Ptr) : Resource {
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

    override fun adjust(edits: List<Edit>): Node<T> {
        var treeCopy = this.subtreePtr
        JSitter.retainSubtree(treeCopy)
        for (e in edits) {
            treeCopy = JSitter.editSubtree(treeCopy, e.startByte, e.oldEndByte, e.newEndByte)
        }
        return this.copy(
                subtreePtr = treeCopy,
                lifetime = TSSubtreeResource(treeCopy))
    }
}

data class TSTree<T : NodeType>(val treePtr: Ptr,
                                override val root: TSSubtree<T>) : Tree<T> {
    override fun adjust(edits: List<Edit>): Tree<T> {
        val treeCopy = JSitter.copyTree(this.treePtr)
        for (e in edits) {
            JSitter.editTree(treeCopy, e.startByte, e.oldEndByte, e.newEndByte)
        }
        return TSTree(
                treePtr = treeCopy,
                root = root.copy(
                        subtreePtr = SubtreeAccess.root(treeCopy),
                        lifetime = TSTReeResource(treeCopy)))
    }

    override fun getChangedRanges(newTree: Tree<T>): List<BytesRange> {
        val rs = JSitter.getChangedRanges(this.treePtr, (newTree as TSTree).treePtr)
        return if (rs == null) {
            emptyList()
        } else {
            val changedRanges = arrayListOf<BytesRange>()
            for (i in rs.indices step 2) {
                changedRanges.add(BytesRange(rs[i], rs[i + 1]))
            }
            changedRanges
        }
    }
}

data class TSParser<T: NodeType>(val parserPtr: Ptr,
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
            return TSTree(newTreePtr,
                    TSSubtree(
                            language = this.language,
                            lifetime = TSTReeResource(newTreePtr),
                            subtreePtr = SubtreeAccess.root(newTreePtr)))
        }
    }
}

class TSZipper<T : NodeType>(val parent: TSZipper<*>?,
                             val parentAliasSequence: Ptr,
                             override val node: TSSubtree<T>,
                             override val byteOffset: Int,
                             val childIndex: Int,
                             val structuralChildIndex: Int) : Zipper<T> {

    fun visible(): Boolean =
            if (SubtreeAccess.isVisible(this.node.subtreePtr)) {
                true
            } else {
                if (this.parentAliasSequence != 0L) {
                    val extra: Boolean = SubtreeAccess.extra(this.node.subtreePtr)
                    if (!extra) {
                        SubtreeAccess.aliasSequenceAt(this.parentAliasSequence, this.structuralChildIndex) != 0
                    } else {
                        false
                    }
                } else {
                    false
                }
            }

    override fun up(): Zipper<*>? =
            if (this.parent == null) {
                null
            } else {
                if (this.parent.visible()) {
                    this.parent
                } else {
                    this.parent.up()
                }
            }


    override fun down(): Zipper<*>? =
            if (SubtreeAccess.childCount(this.node.subtreePtr) == 0) {
                null
            } else {
                val child = SubtreeAccess.childAt(this.node.subtreePtr, 0)
                val lang = this.node.language.languagePtr
                val productionId = SubtreeAccess.productionId(this.node.subtreePtr)
                val aliasSequence = SubtreeAccess.aliasSequence(lang, productionId)
                val res = TSZipper(
                        parent = this,
                        parentAliasSequence = aliasSequence,
                        node = TSSubtree<NodeType>(
                                subtreePtr = child,
                                lifetime = this.node.lifetime,
                                language = this.node.language),
                        byteOffset = this.byteOffset,
                        childIndex = 0,
                        structuralChildIndex = 0)
                if (res.visible()) {
                    res
                } else {
                    val visibleChildCount = SubtreeAccess.visibleChildCount(child)
                    if (visibleChildCount > 0) {
                        res.down()
                    } else {
                        res.right()
                    }
                }
            }


    override fun left(): Zipper<*>? =
            if (this.parent == null) {
                null
            } else if (this.childIndex == 0) {
                if (this.parent.visible()) {
                    null
                } else {
                    this.parent.left()
                }
            } else {
                val sibling: Ptr = SubtreeAccess.childAt(this.parent.node.subtreePtr, this.childIndex - 1)
                val structuralChildIndex =
                        if (this.parentAliasSequence != 0L) {
                            val extra: Boolean = SubtreeAccess.extra(this.node.subtreePtr)
                            if (!extra) {
                                this.structuralChildIndex - 1
                            } else {
                                this.structuralChildIndex
                            }
                        } else {
                            this.structuralChildIndex
                        }
                val byteOffset = this.byteOffset - SubtreeAccess.subtreeBytesSize(sibling) + SubtreeAccess.subtreeBytesPadding(this.node.subtreePtr)
                val res = TSZipper(
                        parent = this.parent,
                        parentAliasSequence = this.parentAliasSequence,
                        node = TSSubtree<NodeType>(
                                subtreePtr = sibling,
                                language = this.node.language,
                                lifetime = this.node.lifetime),
                        byteOffset = byteOffset,
                        childIndex = this.childIndex - 1,
                        structuralChildIndex = structuralChildIndex)
                if (res.visible()) {
                    res
                } else {
                    val d = res.down()
                    if (d != null) {
                        d
                    } else {
                        res.left()
                    }
                }
            }


    override fun right(): Zipper<*>? =
            if (this.parent == null) {
                null
            } else if (this.childIndex == SubtreeAccess.childCount(this.parent.node.subtreePtr) - 1) {
                if (this.parent.visible()) {
                    null
                } else {
                    this.parent.right()
                }
            } else {
                val sibling: Ptr = SubtreeAccess.childAt(this.parent.node.subtreePtr, this.childIndex + 1)
                val structuralChildIndex =
                        if (this.parentAliasSequence != 0L) {
                            val extra: Boolean = SubtreeAccess.extra(this.node.subtreePtr)
                            if (!extra) {
                                this.structuralChildIndex + 1
                            } else {
                                this.structuralChildIndex
                            }
                        } else {
                            this.structuralChildIndex
                        }
                val byteOffset = this.byteOffset + SubtreeAccess.subtreeBytesSize(this.node.subtreePtr) + SubtreeAccess.subtreeBytesPadding(sibling)
                val res = TSZipper(
                        parent = this.parent,
                        parentAliasSequence = this.parentAliasSequence,
                        node = TSSubtree<NodeType>(
                                subtreePtr = sibling,
                                lifetime = this.node.lifetime,
                                language = this.node.language),
                        byteOffset = byteOffset,
                        childIndex = this.childIndex + 1,
                        structuralChildIndex = structuralChildIndex)
                if (res.visible()) {
                    res
                } else {
                    val d = res.down()
                    if (d != null) {
                        d
                    } else {
                        res.right()
                    }
                }
            }

    override fun retainSubtree(): Node<T> {
        JSitter.retainSubtree(this.node.subtreePtr)
        return node.copy(lifetime = TSSubtreeResource(this.node.subtreePtr))
    }
}

