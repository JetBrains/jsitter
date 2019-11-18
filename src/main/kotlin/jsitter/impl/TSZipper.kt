package jsitter.impl

import jsitter.api.Node
import jsitter.api.NodeType
import jsitter.api.Zipper
import jsitter.interop.JSitter

private tailrec fun up(z: TSZipper<*>): TSZipper<*>? =
        when {
          z.parent == null -> null
          z.parent.visible() -> z.parent
          else -> up(z.parent)
        }

private tailrec fun down(zip: TSZipper<*>) : TSZipper<*>? =
        if (SubtreeAccess.childCount(zip.node.subtreePtr) == 0) {
          null
        } else {
          val child = SubtreeAccess.childAt(zip.node.subtreePtr, 0)
          val lang = zip.node.language.languagePtr
          val productionId = SubtreeAccess.productionId(zip.node.subtreePtr)
          val aliasSequence = SubtreeAccess.aliasSequence(lang, productionId)
          val res = TSZipper(
                  parent = zip,
                  parentAliasSequence = aliasSequence,
                  node = TSSubtree<NodeType>(
                          subtreePtr = child,
                          lifetime = zip.node.lifetime,
                          language = zip.node.language),
                  byteOffset = zip.byteOffset,
                  childIndex = 0,
                  structuralChildIndex = 0)
          if (res.visible()) {
            res
          } else {
            if (res.visibleChildCount() > 0) {
                down(res)
            } else {
                right(res)
            }
          }
        }

private tailrec fun right(zip: TSZipper<*>): TSZipper<*>? =
        when {
          zip.parent == null ->
            null

          zip.childIndex == SubtreeAccess.childCount(zip.parent.node.subtreePtr) - 1 ->
            if (zip.parent.visible()) {
              null
            } else {
                right(zip.parent)
            }

          else -> {
            val sibling: Ptr = SubtreeAccess.childAt(zip.parent.node.subtreePtr, zip.childIndex + 1)
            val structuralChildIndex =
                    if (!SubtreeAccess.extra(zip.node.subtreePtr)) {
                      zip.structuralChildIndex + 1
                    } else {
                      zip.structuralChildIndex
                    }
            val byteOffset = zip.byteOffset + SubtreeAccess.subtreeBytesSize(zip.node.subtreePtr) + SubtreeAccess.subtreeBytesPadding(sibling)
            val res = TSZipper(
                    parent = zip.parent,
                    parentAliasSequence = zip.parentAliasSequence,
                    node = TSSubtree<NodeType>(
                            subtreePtr = sibling,
                            lifetime = zip.node.lifetime,
                            language = zip.node.language),
                    byteOffset = byteOffset,
                    childIndex = zip.childIndex + 1,
                    structuralChildIndex = structuralChildIndex)
            if (res.visible()) {
              res
            } else {
              down(res) ?: right(res)
            }
          }
        }

private fun invisibleRight(zip: TSZipper<*>): TSZipper<*>? =
        when {
          zip.parent == null -> null
          zip.childIndex == SubtreeAccess.childCount(zip.parent.node.subtreePtr) - 1 -> null
          else -> {
            val sibling: Ptr = SubtreeAccess.childAt(zip.parent.node.subtreePtr, zip.childIndex + 1)
            val structuralChildIndex =
                    if (!SubtreeAccess.extra(zip.node.subtreePtr)) {
                      zip.structuralChildIndex + 1
                    } else {
                      zip.structuralChildIndex
                    }
            val byteOffset = zip.byteOffset + SubtreeAccess.subtreeBytesSize(zip.node.subtreePtr) + SubtreeAccess.subtreeBytesPadding(sibling)
              TSZipper(
                      parent = zip.parent,
                      parentAliasSequence = zip.parentAliasSequence,
                      node = TSSubtree<NodeType>(
                              subtreePtr = sibling,
                              lifetime = zip.node.lifetime,
                              language = zip.node.language),
                      byteOffset = byteOffset,
                      childIndex = zip.childIndex + 1,
                      structuralChildIndex = structuralChildIndex)
          }
        }

private tailrec fun downRight(zip: TSZipper<*>): TSZipper<*>? {
  return if (SubtreeAccess.childCount(zip.node.subtreePtr) == 0) {
    null
  } else {
    val firstChild = SubtreeAccess.childAt(zip.node.subtreePtr, 0)
    val lang = zip.node.language.languagePtr
    val productionId = SubtreeAccess.productionId(zip.node.subtreePtr)
    val aliasSequence = SubtreeAccess.aliasSequence(lang, productionId)
    var res: TSZipper<*> = TSZipper(
            parent = zip,
            parentAliasSequence = aliasSequence,
            node = TSSubtree<NodeType>(
                    subtreePtr = firstChild,
                    lifetime = zip.node.lifetime,
                    language = zip.node.language),
            byteOffset = zip.byteOffset,
            childIndex = 0,
            structuralChildIndex = 0)
    var r: TSZipper<*>? = res
    while (r != null) {
      r = invisibleRight(r)
      if (r != null) {
        if (r.visible() || r.visibleChildCount() > 0) {
          res = r
        }
      }
    }

    if (res.visible()) {
      res
    } else {
      if (res.visibleChildCount() > 0) {
          downRight(res)
      } else {
        null
      }
    }
  }
}

private tailrec fun left(z: TSZipper<*>): TSZipper<*>? {
  val parent = z.parent
  return when {
    parent == null -> {
      null
    }
    z.childIndex == 0 -> {
      if (parent.visible()) {
        null
      } else {
          left(parent)
      }
    }
    else -> {
      val sibling: Ptr = SubtreeAccess.childAt(parent.node.subtreePtr, z.childIndex - 1)
      val structuralChildIndex =
              if (!SubtreeAccess.extra(z.node.subtreePtr)) {
                z.structuralChildIndex - 1
              } else {
                z.structuralChildIndex
              }
      val byteOffset = z.byteOffset - SubtreeAccess.subtreeBytesPadding(z.node.subtreePtr) - SubtreeAccess.subtreeBytesSize(sibling)
      val res = TSZipper(
              parent = parent,
              parentAliasSequence = z.parentAliasSequence,
              node = TSSubtree<NodeType>(
                      subtreePtr = sibling,
                      language = z.node.language,
                      lifetime = z.node.lifetime),
              byteOffset = byteOffset,
              childIndex = z.childIndex - 1,
              structuralChildIndex = structuralChildIndex)
      if (res.visible()) {
        res
      } else {
        downRight(res) ?: left(res)
      }
    }
  }
}

class TSZipper<T : NodeType>(val parent: TSZipper<*>?,
                             val parentAliasSequence: Ptr,
                             override val node: TSSubtree<T>,
                             override val byteOffset: Int,
                             val childIndex: Int,
                             val structuralChildIndex: Int) : Zipper<T> {
  override val alias: NodeType?
    get() {
      val alias = this.aliasSymbol()
      return if (alias != 0) {
        this.node.language.getNodeType(alias)
      }
      else {
        null
      }
    }

  fun aliasSymbol(): TSSymbol =
    if (this.parentAliasSequence != 0L && !SubtreeAccess.extra(this.node.subtreePtr)) {
        SubtreeAccess.aliasSequenceAt(this.parentAliasSequence, this.structuralChildIndex)
    }
    else {
      0
    }

  fun visible(): Boolean =
    SubtreeAccess.isVisible(this.node.subtreePtr) || this.aliasSymbol() != 0

  override fun up(): Zipper<*>? = up(this)

  override fun down(): Zipper<*>? = down(this)

  fun visibleChildCount(): Int = SubtreeAccess.visibleChildCount(this.node.subtreePtr)

  override fun downRight(): Zipper<*>? = downRight(this)

  override fun left(): Zipper<*>? = left(this)

  override fun right(): Zipper<*>? = right(this)

  override fun retainSubtree(): Node<T> {
    JSitter.retainSubtree(this.node.subtreePtr)
    return node.copy(lifetime = TSSubtreeResource(this.node.subtreePtr))
  }
}