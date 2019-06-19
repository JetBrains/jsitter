package jsitter.api

import java.nio.ByteBuffer

data class Key<T>(val key: Any)

interface DataHolder<out T> {
    fun <U> assoc(k: Key<U>, v: U): T
    operator fun <U> get(k: Key<U>): U?
}

interface Tree<out T : NodeType> : DataHolder<Tree<T>> {
    val language: Language
    val nodeType: T
    val byteSize: Int
    fun zipper(): Zipper<T>
}

interface Zipper<out T : NodeType> : DataHolder<Zipper<T>> {
    fun up(): Zipper<*>?
    fun down(): Zipper<*>?
    fun right(): Zipper<*>?
    fun left(): Zipper<*>?
    fun next(): Zipper<*>?
    fun skip(): Zipper<*>?

    fun retainSubtree(): Tree<T>

    val byteOffset: Int
    val byteSize: Int
    val nodeType: T
    val language: Language
}

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

data class Edit(val startByte: Int,
                val oldEndByte: Int,
                val newEndByte: Int)

typealias BytesRange = Pair<Int, Int>

val CHANGED_RANGES: Key<List<BytesRange>> = Key("jsitter/changed-ranges")

//TODO: attach changed_ranges info to Tree returned from parse
interface Parser<T : NodeType> {
    fun parse(text: Text, edit: Edit? = null): Tree<T>
    val language: Language
}

interface SyntaxHighlighter<Acc> {
    fun highlight(acc: Acc, zip: Zipper<*>): Acc
    fun skip(acc: Acc, toOffset: Int): Acc
}

fun <Acc> highlightSyntax(tree: Tree<*>, init: Acc, h: SyntaxHighlighter<Acc>): Acc {
    val changedRanges = tree[CHANGED_RANGES] ?: listOf(0 to tree.byteSize)
    var acc = init
    var zip: Zipper<*>? = tree.zipper()
    for (range in changedRanges) {
        acc = h.skip(acc, range.first)
        while (zip != null && zip.byteOffset < range.second) {
            val byteEnd = zip.byteOffset + zip.byteSize
            if (byteEnd <= range.first) {
                zip = zip.skip()
            } else if (zip.byteOffset < range.first && range.first < byteEnd) {
                zip = zip.next()
            } else {
                acc = h.highlight(acc, zip)
                zip = zip.next()
            }
        }
    }
    return acc
}

