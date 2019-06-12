package jsitter.api

import java.nio.ByteBuffer

data class BytesRange(val from: Int, val to: Int)

sealed class Filter {
    data class WithNodeType(val nodeType: NodeType) : Filter()
    object AnyNode : Filter()
    object NamedNode : Filter()
}

interface Zipper<out T : NodeType> {
    fun up(filter: Filter = Filter.AnyNode): Zipper<*>?
    fun down(filter: Filter = Filter.AnyNode): Zipper<*>?
    fun right(filter: Filter = Filter.AnyNode): Zipper<*>?
    fun next(filter: Filter = Filter.AnyNode): Zipper<*>?

    fun copy(): Zipper<*>
    fun str(): String?
    val range: BytesRange?
    val nodeType: T
    val id: Any
}

open class NodeType(val name: String) {
    var id: Int = -1
    var initialized = false
    override fun toString(): String {
        return name
    }
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
    fun parser(): Parser
    fun nodeType(name: String): NodeType
}

interface AST<T : NodeType> {
    val language: Language
    fun zipper(): Zipper<T>
}

typealias CST = AST<*>

enum class Encoding(val i: Int) { UTF8(0), UTF16(1) }

interface Text {
    /*
    * put data into ByteBuffer up to it's limit
    * */
    fun read(byteOffset: Int, output: ByteBuffer)

    val encoding: Encoding
}

/*
 * Simple implementation of Text for testing purposes
 */
class StringText(val str: String) : Text {
    override val encoding: Encoding = Encoding.UTF8
    override fun read(byteOffset: Int, output: ByteBuffer) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        output.put(bytes, byteOffset, Math.min(bytes.size - byteOffset, output.limit()))
    }
}

data class Edit(val startByte: Int,
                val oldEndByte: Int,
                val newEndByte: Int)

interface Parser {
    fun parse(text: Text, edit: Edit? = null): CST
}

