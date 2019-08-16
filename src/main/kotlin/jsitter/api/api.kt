package jsitter.api

import java.nio.ByteBuffer

interface Node<out T : NodeType> {
    val language: Language
    val type: T
    val byteSize: Int
    fun zipper(): Zipper<T>
    fun adjust(edits: List<Edit>): Node<T>
}

interface Tree<T : NodeType> {
    fun adjust(edits: List<Edit>): Tree<T>
    fun getChangedRanges(newTree: Tree<T>): List<BytesRange>
    val root: Node<T>
}

interface Zipper<out T : NodeType> {
    fun up(): Zipper<*>?
    fun down(): Zipper<*>?
    fun right(): Zipper<*>?
    fun left(): Zipper<*>?
    fun skip(): Zipper<*>? {
        var u: Zipper<*>? = this
        while (u != null) {
            val r = u.right()
            if (r != null) {
                return r
            } else {
                u = u.up()
            }
        }
        return null
    }
    fun next(): Zipper<*>? = down() ?: skip()

    fun retainSubtree(): Node<T>
    val node: Node<T>

    val byteOffset: Int
}

data class Edit(val startByte: Int,
                val oldEndByte: Int,
                val newEndByte: Int)

open class NodeType(val name: String) {
    var id: Int = -1
    var initialized = false
    override fun toString(): String = name
}

object Error : NodeType("ERROR") {
    init {
        id = -1
        initialized = true
    }
}

open class Terminal(name: String) : NodeType(name)

interface Language {
    val name: String
    fun <T : NodeType> parser(nodeType: T): Parser<T>
    fun nodeType(name: String): NodeType
    fun register(nodeType: NodeType)
}

enum class Encoding(val i: Int) { UTF8(0), UTF16(1) }

interface Text {
    /*
    * put data into ByteBuffer up to it's limit
    * */
    fun read(byteOffset: Int, output: ByteBuffer)

    val encoding: Encoding
}

data class BytesRange(val start: Int,
                      val end: Int)

class CancellationToken {
    @Volatile
    internal var cancelled = false
    @Volatile
    internal var handler: (() -> Unit)? = null

    fun isCancelled() = cancelled
    fun cancel() {
        synchronized(this) {
            if (!cancelled) {
                cancelled = true
                val h = handler
                if (h != null) {
                    h()
                }
            }
        }
    }

    internal fun onCancel(f: () -> Unit) {
        synchronized(this) {
            handler = f
        }
    }
}

interface Parser<T : NodeType> {
    fun parse(text: Text, adjustedTree: Tree<T>? = null, cancellationToken: CancellationToken?): Tree<T>?
    fun parse(text: Text, adjustedTree: Tree<T>? = null): Tree<T> = this.parse(text, adjustedTree, null)!!
    val language: Language
}
