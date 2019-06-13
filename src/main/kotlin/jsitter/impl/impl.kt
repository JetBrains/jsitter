package jsitter.impl

import jsitter.api.*
import jsitter.interop.*

import java.nio.ByteBuffer

const val READING_BUFFER_CAPACITY = 1024 * 1024

class TextInput(val text: Text,
                val readingBuffer: ByteBuffer) : JSitter.Input {
    override fun read(byteOffset: Int): Int {
        text.read(byteOffset, readingBuffer)
        val bytesCount = readingBuffer.position()
        readingBuffer.rewind()
        return bytesCount
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

typealias UserData = io.lacuna.bifurcan.Map<Key<*>, Any?>

data class TSTree(val treePtr: Ptr,
                  override val language: TSLanguage,
                  val text: Text,
                  override val nodeType: NodeType,
                  val userData: UserData = UserData()) : Tree<NodeType>, Resource {
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

    override fun disposer(): Disposer =
            {
                JSitter.releaseTree(treePtr)
            }

    override fun <U> assoc(k: Key<U>, v: U): Tree<NodeType> =
            copy(userData = userData.put(k, v))

    override fun <U> get(k: Key<U>): U? = userData.get(k) as U?
}


data class Subtree(val subtree: Ptr,
                   override val language: TSLanguage,
                   val userData: UserData = UserData()) : Tree<NodeType>, Resource {
    init {
        JSitter.retainSubtree(subtree)
        Cleaner.register(this)
    }

    override fun disposer(): Disposer =
            {
                JSitter.releaseSubtree(subtree)
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

    override fun <U> assoc(k: Key<U>, v: U): Tree<NodeType> =
            copy(userData = userData.put(k, v))

    override fun <U> get(k: Key<U>): U? = userData.get(k) as U?
}


data class TSParser(val parserPtr: Ptr,
                    override val language: TSLanguage,
                    val nodeType: NodeType) : Parser<NodeType>, Resource {

    @Volatile
    var oldTree: TSTree? = null

    val readingBuffer: ByteBuffer = ByteBuffer.allocateDirect(READING_BUFFER_CAPACITY)

    init {
        Cleaner.register(this)
    }

    override fun disposer(): Disposer =
            {
                JSitter.releaseParser(parserPtr)
            }

    override fun parse(text: Text, edit: Edit?): TSTree {
        synchronized(this) {
            val tsInput = TextInput(text, readingBuffer)
            val treePtr = JSitter.parse(parserPtr,
                    oldTree?.treePtr ?: 0,
                    tsInput,
                    text.encoding.i,
                    readingBuffer,
                    edit?.startByte ?: -1,
                    edit?.oldEndByte ?: -1,
                    edit?.newEndByte ?: -1)
            val newTree = TSTree(treePtr, language, text, nodeType)
            oldTree = newTree
            return newTree
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
        if (zip.parent == null || zip.childIndex == SubtreeAccess.childCount(zip.parent.subtree) - 1) {
            null
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
        if (zip.parent == null || zip.childIndex == 0) {
            null
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
                left(res)
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
                    val root: Tree<*>,
                    val userData: UserData = UserData()) : Zipper<NodeType> {
    override val byteSize: Int
        get() = SubtreeAccess.subtreeBytesSize(subtree)

    override val nodeType: NodeType by lazy {
        (root.language as TSLanguage).getNodeType(SubtreeAccess.subtreeNodeType(subtree))
    }

    override val language: TSLanguage
        get() = root.language as TSLanguage

    override fun up(): Zipper<*>? = parent

    override fun down(): Zipper<*>? = down(this)

    override fun left(): Zipper<*>? = left(this)

    override fun right(): Zipper<*>? = right(this)

    override fun retainSubtree(): Tree<NodeType> = Subtree(subtree, language)

    override fun <U> assoc(k: Key<U>, v: U): TSZipper = copy(userData = userData.put(k, v))

    override fun <U> get(k: Key<U>): U? = userData.get(k) as U?
}

