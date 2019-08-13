package jsitter.impl

import jsitter.api.*
import jsitter.interop.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import java.nio.ByteBuffer

const val READING_BUFFER_CAPACITY = 1024 * 1024

class TextInput(val text: Text,
                val readingBuffer: ByteBuffer) : JSitter.Input {
    override fun read(byteOffset: Int): Int {
        try {
            text.read(byteOffset, readingBuffer)
            val bytesCount = readingBuffer.position()
            readingBuffer.rewind()
            return bytesCount
        } catch (x: Throwable) {
            System.err.println(x);
            return 0;
        }
    }
}

typealias TSSymbol = Int

class TSLanguage(val languagePtr: Ptr,
                 override val name: String,
                 val registry: ConcurrentMap<String, NodeType> = ConcurrentHashMap(),
                 val nodeTypesCache: ConcurrentMap<TSSymbol, NodeType> = ConcurrentHashMap()) : Language {

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

    override fun <T : NodeType> parser(nodeType: T): Parser<T> {
        val cancellationFlagPtr = SubtreeAccess.unsafe.allocateMemory(8)
        return TSParser(parserPtr = JSitter.newParser(languagePtr, cancellationFlagPtr),
                language = this,
                nodeType = nodeType,
                cancellationFlagPtr = cancellationFlagPtr) as Parser<T>
    }

    override fun register(nodeType: NodeType) {
        registry[nodeType.name] = nodeType
    }
}


fun loadTSLanguage(name: String): TSLanguage? {
    val langPtr: Ptr = JSitter.findLanguage(name)
    return if (langPtr != 0L) {
        TSLanguage(langPtr, name)
    } else {
        null
    }
}

data class TSTree(val treePtr: Ptr,
                  override val language: TSLanguage,
                  override val nodeType: NodeType) : Tree<NodeType>, Resource {

    init {
        Cleaner.register(this)
    }

    override val byteSize: Int
        get() = SubtreeAccess.subtreeBytesSize(SubtreeAccess.root(treePtr))

    override fun zipper(): Zipper<NodeType> =
            TSZipper(
                    subtree = SubtreeAccess.root(treePtr),
                    parent = null,
                    structuralChildIndex = 0,
                    parentAliasSequence = 0L,
                    byteOffset = 0,
                    childIndex = 0,
                    root = this)

    override fun disposer(): Disposer {
        val treePtr = this.treePtr
        return {
            JSitter.releaseTree(treePtr)
        }
    }

    fun sudorelease() {
        JSitter.releaseTree(treePtr)
    }

    override fun adjust(edits: List<Edit>): Tree<NodeType> {
        val treeCopy = JSitter.copyTree(this.treePtr)
        for (e in edits) {
            JSitter.editTree(treeCopy, e.startByte, e.oldEndByte, e.newEndByte)
        }
        return TSTree(treeCopy, language, nodeType)
    }

    override fun getChangedRanges(newTree: Tree<NodeType>): List<BytesRange> {
        val rs = JSitter.getChangedRanges(this.treePtr, (newTree as TSTree).treePtr)
        return if (rs == null) {
            emptyList()
        } else {
            val changedRanges = arrayListOf<BytesRange>()
            for (i in rs.indices step 2) {
                changedRanges.add(rs[i] to rs[i + 1])
            }
            changedRanges
        }
    }
}

data class Subtree(val subtree: Ptr,
                   override val language: TSLanguage) : SubTree<NodeType>, Resource {

    override fun adjust(edits: List<Edit>): SubTree<NodeType> {
        var treeCopy = this.subtree
        JSitter.retainSubtree(treeCopy)
        for (e in edits) {
            treeCopy = JSitter.editSubtree(treeCopy, e.startByte, e.oldEndByte, e.newEndByte)
        }
        return Subtree(treeCopy, this.language)
    }

    init {
        JSitter.retainSubtree(subtree)
        Cleaner.register(this)
    }

    override fun disposer(): Disposer {
        val subtreePtr = this.subtree
        return {
            JSitter.releaseSubtree(subtreePtr)
        }
    }

    override val nodeType: NodeType by lazy {
        language.getNodeType(SubtreeAccess.subtreeNodeType(subtree))
    }

    override val byteSize: Int
        get() = SubtreeAccess.subtreeBytesSize(subtree)

    override fun zipper(): TSZipper =
            TSZipper(
                    subtree = subtree,
                    parent = null,
                    structuralChildIndex = 0,
                    parentAliasSequence = 0L,
                    byteOffset = 0,
                    childIndex = 0,
                    root = this)
}


data class TSParser(val parserPtr: Ptr,
                    override val language: TSLanguage,
                    val nodeType: NodeType,
                    val cancellationFlagPtr: Ptr) : Parser<NodeType>, Resource {
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

    override fun parse(text: Text, adjustedTree: Tree<NodeType>?, cancellationToken: CancellationToken?): Tree<NodeType>? {
        synchronized(this) {
            SubtreeAccess.unsafe.putLong(cancellationFlagPtr, 0)
            cancellationToken?.onCancel {
                SubtreeAccess.unsafe.putLong(cancellationFlagPtr, 1)
            }
            if (cancellationToken?.cancelled == true) {
                return null
            }
            val tsInput = TextInput(text, readingBuffer)
            if (cancellationToken?.cancelled == true) {
                return null
            }
            val oldTreePtr = (adjustedTree as TSTree?)?.treePtr
            val newTreePtr = JSitter.parse(
                    parserPtr,
                    oldTreePtr ?: 0,
                    tsInput,
                    text.encoding.i,
                    readingBuffer)
            JSitter.parserReset(parserPtr)
            if (newTreePtr == 0L) {
                return null
            }
            if (cancellationToken?.cancelled == true) {
                JSitter.releaseTree(newTreePtr)
                return null
            }
            return TSTree(newTreePtr, language, nodeType)
        }
    }
}

private fun down(zip: TSZipper): TSZipper? =
        if (SubtreeAccess.childCount(zip.subtree) == 0) {
            null
        } else {
            val child = SubtreeAccess.childAt(zip.subtree, 0)
            val lang = zip.language.languagePtr
            val productionId = SubtreeAccess.productionId(zip.subtree)
            val aliasSequence = SubtreeAccess.aliasSequence(lang, productionId)
            val res = TSZipper(
                    parent = zip,
                    parentAliasSequence = aliasSequence,
                    subtree = child,
                    byteOffset = zip.byteOffset,
                    childIndex = 0,
                    structuralChildIndex = 0,
                    root = zip.root)
            if (visible(res)) {
                res
            } else {
                val visibleChildCount = SubtreeAccess.visibleChildCount(child)
                if (visibleChildCount > 0) {
                    down(res)
                } else {
                    right(res)
                }
            }
        }

private fun right(zip: TSZipper): TSZipper? =
        if (zip.parent == null) {
            null
        } else if (zip.childIndex == SubtreeAccess.childCount(zip.parent.subtree) - 1) {
            if (visible(zip.parent)) {
                null
            } else {
                right(zip.parent)
            }
        } else {
            val sibling: Ptr = SubtreeAccess.childAt(zip.parent.subtree, zip.childIndex + 1)
            val structuralChildIndex =
                    if (zip.parentAliasSequence != 0L) {
                        val extra: Boolean = SubtreeAccess.extra(zip.subtree)
                        if (!extra) {
                            zip.structuralChildIndex + 1
                        } else {
                            zip.structuralChildIndex
                        }
                    } else {
                        zip.structuralChildIndex
                    }
            val byteOffset = zip.byteOffset + SubtreeAccess.subtreeBytesSize(zip.subtree) + SubtreeAccess.subtreeBytesPadding(sibling)
            val res = TSZipper(
                    parent = zip.parent,
                    parentAliasSequence = zip.parentAliasSequence,
                    subtree = sibling,
                    byteOffset = byteOffset,
                    childIndex = zip.childIndex + 1,
                    structuralChildIndex = structuralChildIndex,
                    root = zip.root)
            if (visible(res)) {
                res
            } else {
                val d = down(res)
                if (d != null) {
                    d
                } else {
                    right(res)
                }
            }
        }

private fun left(zip: TSZipper): TSZipper? =
        if (zip.parent == null) {
            null
        } else if (zip.childIndex == 0) {
            if (visible(zip.parent)) {
                null
            } else {
                left(zip.parent)
            }
        } else {
            val sibling: Ptr = SubtreeAccess.childAt(zip.parent.subtree, zip.childIndex - 1)
            val structuralChildIndex =
                    if (zip.parentAliasSequence != 0L) {
                        val extra: Boolean = SubtreeAccess.extra(zip.subtree)
                        if (!extra) {
                            zip.structuralChildIndex - 1
                        } else {
                            zip.structuralChildIndex
                        }
                    } else {
                        zip.structuralChildIndex
                    }
            val byteOffset = zip.byteOffset - SubtreeAccess.subtreeBytesSize(sibling) + SubtreeAccess.subtreeBytesPadding(zip.subtree)
            val res = TSZipper(
                    parent = zip.parent,
                    parentAliasSequence = zip.parentAliasSequence,
                    subtree = sibling,
                    byteOffset = byteOffset,
                    childIndex = zip.childIndex - 1,
                    structuralChildIndex = structuralChildIndex,
                    root = zip.root)
            if (visible(res)) {
                res
            } else {
                val d = down(res)
                if (d != null) {
                    d
                } else {
                    left(res)
                }
            }
        }

private fun up(zip: TSZipper): TSZipper? =
        if (zip.parent == null) {
            null
        } else {
            if (visible(zip.parent)) {
                zip.parent
            } else {
                up(zip.parent)
            }
        }


private fun visible(zip: TSZipper): Boolean =
        if (SubtreeAccess.isVisible(zip.subtree)) {
            true
        } else {
            if (zip.parentAliasSequence != 0L) {
                val extra: Boolean = SubtreeAccess.extra(zip.subtree)
                if (!extra) {
                    SubtreeAccess.aliasSequenceAt(zip.parentAliasSequence, zip.structuralChildIndex) != 0
                } else {
                    false
                }
            } else {
                false
            }
        }

data class TSZipper(val parent: TSZipper?,
                    val parentAliasSequence: Ptr,
                    val subtree: Ptr,
                    override val byteOffset: Int,
                    val childIndex: Int,
                    val structuralChildIndex: Int,
                    val root: SubTree<*>) : Zipper<NodeType> {
    override val byteSize: Int
        get() = SubtreeAccess.subtreeBytesSize(subtree)

    override val nodeType: NodeType by lazy {
        (root.language as TSLanguage).getNodeType(SubtreeAccess.subtreeNodeType(subtree))
    }

    override val language: TSLanguage
        get() = root.language as TSLanguage

    override fun up(): Zipper<*>? = up(this)

    override fun down(): Zipper<*>? = down(this)

    override fun left(): Zipper<*>? = left(this)

    override fun right(): Zipper<*>? = right(this)

    override fun retainSubtree(): SubTree<NodeType> = Subtree(subtree, language)
}

