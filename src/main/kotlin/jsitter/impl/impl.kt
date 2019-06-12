package jsitter.impl

import jsitter.api.*
import jsitter.interop.*

import sun.misc.Unsafe
import sun.nio.ch.DirectBuffer
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

typealias Ptr = Long
typealias TSSymbol = Short
typealias Disposer = () -> Unit

interface Resource {
    fun disposer(): Disposer
}

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
                nodeType.id.toShort()
            } else {
                val symbol: TSSymbol = JSitter.getSymbolByName(languagePtr, nodeType.name)
                nodeType.id = symbol.toInt()
                nodeType.initialized = true
                symbol
            }

    override fun nodeType(name: String): NodeType = registry[name]!!

    override fun parser(): Parser =
            TSParser(oldTree = null,
                    parserPtr = JSitter.newParser(languagePtr),
                    language = this)

    fun register(nodeType: NodeType) {
        registry[nodeType.name] = nodeType
    }
}

object Cleaner {
    var DEBUG = false

    val refQueue = ReferenceQueue<Resource>()
    val refs = ConcurrentHashMap<Any, Disposer>()
    val refsCount = AtomicInteger()
    val started = AtomicBoolean()

    fun start() {
        val thread = Thread({
            while (true) {
                val key = refQueue.remove()
                val disposer = refs.remove(key)!!
                refsCount.decrementAndGet()
                disposer()
            }
        }, "com.jetbrains.jsitter.cleaner")
        thread.isDaemon = true
        thread.start()
    }

    fun register(r: Resource) {
        val ref = PhantomReference(r, refQueue)
        val disposer = r.disposer()
        refs.put(ref, disposer)
        refsCount.incrementAndGet()
        if (started.compareAndSet(false, true)) {
            start()
        }
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
                  val text: Text) : AST<NodeType>, Resource {
    init {
        Cleaner.register(this)
    }

    override fun zipper(): Zipper<NodeType> =
            TSZipper(JSitter.makeCursor(treePtr), this)

    override fun disposer(): Disposer =
            {
                JSitter.releaseTree(treePtr)
            }
}

const val READING_BUFFER_CAPACITY = 1024 * 1024

fun ByteBuffer.address(): Ptr? =
        if (this is DirectBuffer) {
            this.address()
        } else {
            null
        }

class TextInput(val text: Text,
                val readingBuffer: ByteBuffer) : JSitter.Input {
    override fun read(byteOffset: Int): Int {
        text.read(byteOffset, readingBuffer)
        val bytesCount = readingBuffer.position()
        readingBuffer.rewind()
        return bytesCount
    }
}

data class TSParser(val parserPtr: Ptr,
                    val language: TSLanguage,
                    @Volatile var oldTree: TSTree?,
                    val readingBuffer: ByteBuffer = ByteBuffer.allocateDirect(READING_BUFFER_CAPACITY)) : Parser, Resource {
    init {
        Cleaner.register(this)
    }

    override fun disposer(): Disposer =
            {
                JSitter.releaseParser(parserPtr)
            }

    override fun parse(text: Text, edit: Edit?): CST {
        synchronized(this) {
            val tsInput = TextInput(text, readingBuffer)
            val treePtr = JSitter.parse(parserPtr,
                    oldTree?.treePtr ?: 0,
                    tsInput,
                    text.encoding.i,
                    readingBuffer.address()!!,
                    edit?.startByte ?: -1,
                    edit?.oldEndByte ?: -1,
                    edit?.newEndByte ?: -1)
            val newTree = TSTree(treePtr, language, text)
            oldTree = newTree
            return newTree
        }
    }
}

object UnsafeAccess {
    val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { field ->
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    val TSCursorSize = 24

    val ZipperSize = TSCursorSize + 4 + 4 + 2L

    fun getCursorStartByte(cursor: Ptr): Int = unsafe.getInt(cursor + TSCursorSize)

    fun getCursorEndByte(cursor: Ptr): Int = unsafe.getInt(cursor + TSCursorSize + 4)

    fun getCursorId(cursor: Ptr): Long = unsafe.getLong(cursor + TSCursorSize + 4 + 4)

    fun getCursorSymbol(cursor: Ptr): TSSymbol = unsafe.getShort(cursor + TSCursorSize + 4 + 4 + 8)
}

enum class Dir(val i: Int) {
    UP(0), DOWN(1), RIGHT(3), NEXT(4)
}

data class TSZipper(val cursor: Ptr,
                    override val tree: TSTree) : Zipper<NodeType>, Resource {
    override val id: Any
        get() = UnsafeAccess.getCursorId(cursor)

    init {
        Cleaner.register(this)
    }

    override fun disposer(): Disposer =
            {
                JSitter.releaseZipper(cursor)
            }

    private var cachedNodeType: NodeType? = null

    private fun move(dir: Dir, filter: Filter): Zipper<*>? {
        val success = when (filter) {
            is Filter.WithNodeType -> {
                JSitter.move(cursor, dir.i, true, tree.language.getNodeTypeSymbol(filter.nodeType), false)
            }
            is Filter.AnyNode -> {
                JSitter.move(cursor, dir.i, false, -1, false)
            }
            is Filter.NamedNode -> {
                JSitter.move(cursor, dir.i, false, -1, true)
            }
        }
        return if (success) {
            cachedNodeType = null
            this
        } else {
            null
        }
    }

    override fun up(filter: Filter): Zipper<*>? = move(Dir.UP, filter)

    override fun down(filter: Filter): Zipper<*>? =
        if (nodeType is Terminal) {
            null
        } else {
            move(Dir.DOWN, filter)
        }


    override fun right(filter: Filter): Zipper<*>? = move(Dir.RIGHT, filter)

    override fun next(filter: Filter): Zipper<*>? = move(Dir.NEXT, filter)

    override fun copy(): Zipper<*> {
        val copyPtr = JSitter.copyCursor(cursor)
        return TSZipper(copyPtr, tree)
    }

    override fun str(): String? {
        val (startByte, endByte) = range
        return tree.text.text(startByte, endByte)
    }

    override val range: BytesRange
        get() = BytesRange(UnsafeAccess.getCursorStartByte(cursor), UnsafeAccess.getCursorEndByte(cursor))

    override val nodeType: NodeType
        get() =
            if (cachedNodeType == null) {
                val nt = tree.language.getNodeType(UnsafeAccess.getCursorSymbol(cursor))
                cachedNodeType = nt
                nt
            } else {
                cachedNodeType!!
            }
}

