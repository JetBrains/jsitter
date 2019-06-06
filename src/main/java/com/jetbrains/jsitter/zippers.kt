package com.jetbrains.jsitter

data class BytesRange(val from: Int, val to: Int)

interface Zipper<out T: NodeType> {
    val nodeType: T
    val lexeme: String?
    val range: BytesRange?
    fun up(): Zipper<*>
    fun down(): Zipper<*>
    fun left(): Zipper<*>
    fun right(): Zipper<*>
    fun next(): Zipper<*>
    fun prev(): Zipper<*>
    fun copy() : Zipper<*>
}

typealias CST = Zipper<*>

open class NodeType(val id: String)
object Function : NodeType("go_function")
object Body : NodeType("function_body")
object Identifier: Terminal("identifier")
open class Terminal(id: String) : NodeType(id)

val <T : Terminal> Zipper<T>.text : String
    get() {
        return lexeme!!
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

