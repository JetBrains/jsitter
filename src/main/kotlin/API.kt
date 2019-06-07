package com.jetbrains.jsitter

import java.nio.ByteBuffer

data class BytesRange(val from: Int, val to: Int)

sealed class Filter
data class WithNodeType(val nodeType: NodeType) : Filter()
object AnyNode: Filter()
object NamedNode: Filter()

interface Zipper<out T : NodeType> {
    fun up(filter: Filter = AnyNode): Zipper<*>?
    fun down(filter: Filter = AnyNode): Zipper<*>?
    fun left(filter: Filter = AnyNode): Zipper<*>?
    fun right(filter: Filter = AnyNode): Zipper<*>?
    fun next(filter: Filter = AnyNode): Zipper<*>?
    fun prev(filter: Filter = AnyNode): Zipper<*>?

    fun copy(): Zipper<*>
    fun str(): String?
    val range: BytesRange?
    val nodeType: T
}

typealias CSTZ = Zipper<*>
typealias ASTZ<T> = Zipper<T>

open class NodeType(val name: String) {
    var id: Int = -1
}

open class Terminal(name: String) : NodeType(name)

interface Language {
    val name: String
    fun parser() : Parser
}

interface AST<T : NodeType> {
    val language: Language
    fun zipper(): Zipper<T>
}

typealias CST = AST<*>

// TS specific

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
    fun parse(text: Text, edit: Edit? = null) : CST
}


























// examples

object Function : NodeType("go_function")
object Body : NodeType("function_body")
object Identifier : Terminal("identifier")

val <T : Terminal> Zipper<T>.text: String
    get() {
        return str()!!
    }

val Zipper<Function>.nameIdentifier: Zipper<Identifier>
    get() {
        return null!!
    }


val Zipper<Function>.name: String
    get() {
        return nameIdentifier.text
    }

val Zipper<Function>.body: Zipper<Body>
    get() {
        return null!!
    }


fun main(args: Array<String>) {
    val z: Zipper<*> = null!!

}

