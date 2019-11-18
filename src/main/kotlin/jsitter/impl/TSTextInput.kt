package jsitter.impl

import jsitter.api.Text
import jsitter.interop.JSitter
import java.nio.ByteBuffer

const val READING_BUFFER_CAPACITY = 1024 * 1024

class TSTextInput(val text: Text,
                  val readingBuffer: ByteBuffer) : JSitter.Input {
  override fun read(byteOffset: Int): Int {
    try {
      text.read(byteOffset, readingBuffer)
      val bytesCount = readingBuffer.position()
      readingBuffer.rewind()
      return bytesCount
    }
    catch (x: Throwable) {
      System.err.println(x)
      return 0
    }
  }
}