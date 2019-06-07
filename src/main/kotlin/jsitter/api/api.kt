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
    fun left(filter: Filter = Filter.AnyNode): Zipper<*>?
    fun right(filter: Filter = Filter.AnyNode): Zipper<*>?
    fun next(filter: Filter = Filter.AnyNode): Zipper<*>?
    fun prev(filter: Filter = Filter.AnyNode): Zipper<*>?

    fun copy(): Zipper<*>
    fun str(): String?
    val range: BytesRange?
    val nodeType: T
}

open class NodeType(val name: String) {
    var id: Int = -1
    var initialized = false
}

object Error: NodeType("ERROR") {
    init {
        id = -1
        initialized = true
    }
}

open class Terminal(name: String) : NodeType(name)


interface Language {
    val name: String
    fun parser(): Parser
    fun nodeType(name: String) : NodeType
}

interface AST<T : NodeType> {
    val language: Language
    fun zipper(): Zipper<T>
}

typealias CST = AST<*>

enum class Encoding { UTF8, UTF16 }

interface Text {
    /*
    * put data into ByteBuffer up to it's limit
    * */
    fun read(byteOffset: Int, output: ByteBuffer)

    val encoding: Encoding
}

data class Edit(val startByte: Int,
                val oldEndByte: Int,
                val newEndByte: Int)

interface Parser {
    fun parse(text: Text, edit: Edit? = null): CST
}

