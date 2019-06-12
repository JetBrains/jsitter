package jsitter.test

import jsitter.api.*
import junit.framework.Assert.*

import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths

class Test1 {
    @Test
    fun visitingTree() {
        val lang = jsitter.impl.loadTSLanguage("go")!!
        val parser = lang.parser()
        val tree = parser.parse(StringText("func hello() { sayHello() }"))
        var zipper: Zipper<*>? = tree.zipper()
        val str = arrayListOf<String>()
        while (zipper != null) {
            str.add(zipper.nodeType.toString())
            println(zipper.id)
            zipper = zipper.next()
        }
        assertEquals(listOf("source_file",
                "function_declaration",
                "func",
                "identifier",
                "parameter_list",
                "(",
                ")",
                "block",
                "{",
                "call_expression",
                "identifier",
                "argument_list",
                "(",
                ")",
                "}"), str)
        val codeBlock = tree.zipper().down()!!.down()!!.right()!!.right()!!.right()!!
        assertEquals("{ sayHello() }", codeBlock.str())
        val copy = codeBlock.copy()
        assertEquals("func hello() { sayHello() }", copy.up()!!.str())
        assertEquals("func hello() { sayHello() }", codeBlock.copy().up()!!.str())
    }

    @Test
    fun perf() {
        val bytes = Files.readAllBytes(Paths.get("testData/router_go"))
        val text = object : Text {
            override fun text(startByte: Int, endByte: Int): String =
                String(bytes, startByte, endByte - startByte, Charsets.UTF_8)

            override fun read(byteOffset: Int, output: ByteBuffer) {
                output.put(bytes, byteOffset, Math.min(bytes.size - byteOffset, output.limit()))
            }

            override val encoding: Encoding = Encoding.UTF8
        }
        val lang = jsitter.impl.loadTSLanguage("go")!!
        val parser = lang.parser()
        val tree = parser.parse(text)
        var zipper: Zipper<*>? = tree.zipper()
        var nodesCount = 0
        while (zipper != null) {
            zipper = zipper.next()
            nodesCount++
        }
        println("nodesCount = ${nodesCount}")

        val start = System.nanoTime()
        zipper = tree.zipper()
        while (zipper != null) {
            zipper = zipper.next()
        }
        val end = System.nanoTime()
        println("elapsed time = ${end - start}")
    }
}