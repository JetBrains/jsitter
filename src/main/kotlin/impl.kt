package com.jetbrains.jsitter

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
                 val registry: MutableMap<String, NodeType>,
                 val nodeTypesCache: ConcurrentMap<TSSymbol, NodeType> = ConcurrentHashMap(),
                 override val name: String) : Language, Resource {

    init {
        Cleaner.register(this)
    }

    override fun disposer(): Disposer =
            {
                JSitter.releaseLanguage(languagePtr)
            }

    fun getNodeType(tsSymbol: TSSymbol): NodeType =
            nodeTypesCache.computeIfAbsent(tsSymbol) { symbol ->
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

    fun getNodeTypeSymbol(nodeType: NodeType): TSSymbol =
            if (nodeType.id != -1) {
                nodeType.id.toShort()
            } else {
                val symbol: TSSymbol = JSitter.getSymbolByName(languagePtr, nodeType.name)
                nodeType.id = symbol.toInt()
                symbol
            }

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
        Thread({
            while (true) {
                val key = refQueue.remove()
                val disposer = refs.remove(key)!!
                refsCount.decrementAndGet()
                disposer()
            }
        }, "com.jetbrains.jsitter.cleaner").start()
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
    TODO()
}


data class TSTree(val treePtr: Ptr,
                  override val language: TSLanguage) : AST<NodeType>, Resource {
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

val READING_BUFFER_CAPACITY = 1024

fun ByteBuffer.address(): Ptr? =
        if (this is DirectBuffer) {
            this.address()
        } else {
            null
        }

class TSInput(val text: Text,
              val readingBuffer: ByteBuffer) {
    fun read(byteOffset: Int): Int {
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
            val tsInput = TSInput(text, readingBuffer)
            val treePtr = JSitter.parse(parserPtr,
                    oldTree?.treePtr ?: 0,
                    tsInput,
                    readingBuffer.address()!!,
                    edit?.startByte ?: -1,
                    edit?.oldEndByte ?: -1,
                    edit?.newEndByte ?: -1)
            return TSTree(treePtr, language)
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

    fun getCursorSymbol(cursor: Ptr): TSSymbol = unsafe.getShort(cursor + TSCursorSize + 4 + 4)

    fun copyCursor(cursor: Ptr): Ptr {
        val dst = unsafe.allocateMemory(ZipperSize)
        unsafe.copyMemory(cursor, dst, ZipperSize)
        return dst
    }
}

enum class Dir(val i: Int) {
    UP(0), DOWN(1), LEFT(2), RIGHT(3), NEXT(4), PREV(5)
}

data class TSZipper(val cursor: Ptr,
                    val tree: TSTree) : Zipper<NodeType>, Resource {
    init {
        Cleaner.register(this)
    }

    override fun disposer(): Disposer =
            {
                JSitter.releaseZipper(cursor)
            }

    private var cachedNodeType: NodeType? = null

    private fun move(dir: Dir, filter: Filter): Zipper<*>? {
        val success: Boolean = when (filter) {
            is WithNodeType -> {
                JSitter.move(cursor, dir.i, tree.language.getNodeTypeSymbol(filter.nodeType), false)
            }
            is AnyNode -> {
                JSitter.move(cursor, dir.i, -1, false)
            }
            is NamedNode -> {
                JSitter.move(cursor, dir.i, -1, true)
            }
        }
        if (success) {
            cachedNodeType = null
            return this
        } else {
            return null
        }
    }

    override fun up(filter: Filter): Zipper<*>? = move(Dir.UP, filter)

    override fun down(filter: Filter): Zipper<*>? = move(Dir.DOWN, filter)

    override fun left(filter: Filter): Zipper<*>? = move(Dir.LEFT, filter)

    override fun right(filter: Filter): Zipper<*>? = move(Dir.RIGHT, filter)

    override fun next(filter: Filter): Zipper<*>? = move(Dir.NEXT, filter)

    override fun prev(filter: Filter): Zipper<*>? = move(Dir.PREV, filter)

    override fun copy(): Zipper<*> {
        val copyPtr = UnsafeAccess.copyCursor(cursor)
        return TSZipper(copyPtr, tree)
    }

    override fun str(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

