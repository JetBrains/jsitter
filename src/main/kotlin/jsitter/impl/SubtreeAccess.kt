package jsitter.impl

import sun.misc.Unsafe
import kotlin.experimental.and

typealias Ptr = Long

object SubtreeAccess {

    val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let { field ->
        field.isAccessible = true
        field.get(null) as Unsafe
    }

    fun isInline(subtree: Ptr) : Boolean {
        return subtree.and(1) == 1L
    }

    val ref_count = 0
    val padding = 4
    val size = padding + 12
    val lookahead_bytes = size + 12
    val error_cost = lookahead_bytes + 4
    val child_count = error_cost + 4
    val symbol = child_count + 4
    val parse_state = symbol + 2
    val flags = parse_state + 2

    val children = 48
    val visible_children_count = children + 8
    val named_child_count = visible_children_count + 4
    val node_count = named_child_count + 4
    val repeat_depth = node_count + 4
    val dyn_prec = repeat_depth + 4
    val production_id = dyn_prec + 4


    fun readShort(addr: Ptr) : Int {
        return ((unsafe.getShort(addr)).toInt()).and(0xFFFF)
    }

    fun subtreeNodeType(subtree: Ptr) : Int {
        if (isInline(subtree)) {
            return subtree.byte(1)
        } else {
            return readShort(subtree + symbol)
        }
    }

    fun childCount(subtree: Ptr): Int {
        if (isInline(subtree)) {
            return 0
        } else {
            return unsafe.getInt(subtree + child_count)
        }
    }

    fun childAt(subtree: Ptr, i: Int): Ptr {
        val children_ptr = unsafe.getAddress(subtree + children)
        return unsafe.getAddress(children_ptr + i * 8)
    }

    fun extra(subtree: Ptr): Boolean {
        if (isInline(subtree)) {
            return subtree.byte(0).and(8) == 8
        } else {
            val flags = unsafe.getByte(subtree + flags)
            return flags.and(1.shl(2)) != 0.toByte()
        }
    }

    fun subtreeBytesSize(subtree: Ptr): Int {
        if (isInline(subtree)) {
            return subtree.byte(3)
        } else {
            return unsafe.getInt(subtree + size)
        }
    }

    fun subtreeBytesPadding(subtree: Ptr): Int {
        if (isInline(subtree)) {
            return subtree.byte(2)
        } else {
            return unsafe.getInt(subtree + padding)
        }
    }

    fun isVisible(subtree: Ptr): Boolean {
        if (isInline(subtree)) {
            return subtree.byte(0).and(2) == 2
        } else {
            return unsafe.getByte(subtree + flags).and(1) == 1.toByte()
        }
    }

    fun productionId(subtree: Ptr): Int {
        return readShort(subtree + production_id)
    }

    fun visibleChildCount(subtree: Ptr): Int {
        return unsafe.getInt(subtree + visible_children_count)
    }

    fun aliasSequenceAt(aliasSequence: Ptr, structuralChildIndex: Int): Int {
        return readShort(aliasSequence + structuralChildIndex * 2)
    }

    fun aliasSequence(lang: Ptr, productionId: Int): Ptr {
        val alias_sequences_offset = 64
        val max_alias_sequence_length = alias_sequences_offset + 8
        if (productionId > 0) {
            return unsafe.getAddress(lang + alias_sequences_offset) + productionId * readShort(lang + max_alias_sequence_length)
        } else {
            return 0L
        }
    }

    fun root(treePtr: Ptr): Ptr {
        return unsafe.getAddress(treePtr)
    }
}

fun Long.byte(i: Int): Int {
    val l = this
    return when (i) {
        0 -> l.and(0xFF).toInt()
        1 -> l.and(0xFF00).shr(8).toInt()
        2 -> l.and(0xFF0000).shr(16).toInt()
        3 -> l.and(0xFF000000).shr(24).toInt()
        4 -> l.and(0xFF00000000).shr(32).toInt()
        5 -> l.and(0xFF0000000000).shr(40).toInt()
        6 -> l.and(0xFF000000000000).shr(48).toInt()
        7 -> l.shr(56).toInt()
        else -> throw AssertionError()
    }
}