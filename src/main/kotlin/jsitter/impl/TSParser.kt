package jsitter.impl

import jsitter.api.*
import jsitter.interop.JSitter
import java.nio.ByteBuffer

data class TSParser<T : NodeType>(val parserPtr: Ptr,
                                                                 override val language: TSLanguage<T>,
                                                                 val nodeType: NodeType,
                                                                 val cancellationFlagPtr: Ptr) : Parser<T>, Resource {
  val readingBuffer: ByteBuffer = ByteBuffer.allocateDirect(READING_BUFFER_CAPACITY)

  init {
      Cleaner.register(this)
  }

  override fun disposer(): Disposer {
    val parserPtr = this.parserPtr
    val cancellationFlagPtr = this.cancellationFlagPtr
    return {
      JSitter.releaseParser(parserPtr)
      SubtreeAccess.unsafe.freeMemory(cancellationFlagPtr)
    }
  }

  override fun parse(text: Text, adjustedTree: Tree<T>?, cancellationToken: CancellationToken?): Tree<T>? {
    if (adjustedTree?.actual == true) {
      return adjustedTree
    }
    synchronized(this) {
      SubtreeAccess.unsafe.putLong(this.cancellationFlagPtr, 0)
      cancellationToken?.onCancel {
        SubtreeAccess.unsafe.putLong(this.cancellationFlagPtr, 1)
      }
      if (cancellationToken?.cancelled == true) {
        return null
      }
      val newTreePtr = JSitter.parse(
              this.parserPtr,
              (adjustedTree as TSTree?)?.treePtr ?: 0L,
              TSTextInput(text, this.readingBuffer),
              text.encoding.i,
              this.readingBuffer)
        JSitter.parserReset(parserPtr)
      if (newTreePtr == 0L) {
        return null
      }
      if (cancellationToken?.cancelled == true) {
          JSitter.releaseTree(newTreePtr)
        return null
      }
      return TSTree(
              treePtr = newTreePtr,
              root = TSSubtree(
                      language = this.language,
                      lifetime = TSTreeResource(newTreePtr),
                      subtreePtr = SubtreeAccess.root(newTreePtr)),
              actual = true)
    }
  }
}