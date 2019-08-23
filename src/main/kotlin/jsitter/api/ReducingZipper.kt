package jsitter.api

fun <T : NodeType, Acc> Node<T>.reducingZipper(init: Acc,
                                               reducer: (Acc, Zipper<*>) -> Acc): ReducingZipper<T, Acc> =
        ReducingZipper(init, this.zipper(), reducer)

class ReducingZipper<T : NodeType, Acc>(val parentOrInit: Any?,
                                        val z: Zipper<T>,
                                        val reducer: (Acc, Zipper<*>) -> Acc) : Zipper<T> {
    override val alias: NodeType?
        get() = z.alias

    override fun up(): ReducingZipper<*, Acc>? {
        if (parentOrInit is ReducingZipper<*, *>) {
            return parentOrInit as ReducingZipper<*, Acc>
        } else {
            return null
        }
    }

    override fun down(): Zipper<*>? {
        val down = z.down()
        return if (down == null) {
            null
        } else {
            ReducingZipper(
                    parentOrInit = this,
                    z = down,
                    reducer = this.reducer)
        }
    }

    override fun right(): Zipper<*>? {
        val right = z.right()
        return if (right == null) {
            null
        } else {
            ReducingZipper(
                    parentOrInit = this.parentOrInit,
                    z = right,
                    reducer = this.reducer)
        }
    }

    override fun left(): Zipper<*>? {
        val left = z.left()
        return if (left == null) {
            null
        } else {
            ReducingZipper(
                    parentOrInit = this.parentOrInit,
                    z = left,
                    reducer = this.reducer)
        }
    }

    override fun retainSubtree(): Node<T> =
            z.retainSubtree()

    override val node: Node<T>
        get() = this.z.node

    override val byteOffset: Int
        get() = this.z.byteOffset

    val acc: Acc by lazy {
        val parentAcc = if (this.parentOrInit is ReducingZipper<*, *>) {
            (this.parentOrInit as ReducingZipper<*, Acc>).acc
        } else {
            this.parentOrInit as Acc
        }
        this.reducer(parentAcc, this.z)
    }
}