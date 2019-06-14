package jsitter.test

import jsitter.api.*

import junit.framework.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths

object SourceFile : NodeType("source_file")

fun golang(): Language {
    val lang = jsitter.impl.loadTSLanguage("go")!!
    lang.register(SourceFile)
    return lang
}

class Test1 {
    @Test
    fun visitingTree() {
        val lang = golang()
        val parser = lang.parser(SourceFile)
        val tree = parser.parse(StringText("func hello() { sayHello() }"))
        var zipper: Zipper<*>? = tree.zipper()
        val str = arrayListOf<String>()
        while (zipper != null) {
            str.add(zipper.nodeType.toString())
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
        val codeBlock = tree.zipper()
                .down()!!
                .down()!!
                .right()!!
                .right()!!
                .right()!!
                .down()!!
                .right()!!
        assertEquals("call_expression", codeBlock.nodeType.name)
        val tree1 = parser.parse(StringText("func bye() { sayHello() }"), Edit(5, 10, 8))
        var z: Zipper<*>? = tree1.zipper()
        while (z != null) {
            println(z.nodeType)
            z = z.next()
        }
    }

    @Test
    fun perf() {
        val bytes = Files.readAllBytes(Paths.get("testData/router_go"))
        val text = object : Text {
            override fun read(byteOffset: Int, output: ByteBuffer) {
                output.put(bytes, byteOffset, Math.min(bytes.size - byteOffset, output.limit()))
            }

            override val encoding: Encoding = Encoding.UTF8
        }
        val lang = golang()
        val parser = lang.parser(SourceFile)
        val start1 = System.nanoTime()
        val tree = parser.parse(text)
        val end1 = System.nanoTime()
        println("parse time = ${end1 - start1}")
        var zipper: Zipper<*>? = tree.zipper()
        var nodesCount = 0
        val start2 = System.nanoTime()
        while (zipper != null) {
            zipper = zipper.next()
            nodesCount++
        }
        val end2 = System.nanoTime()
        println("walk1 time = ${end2 - start2}")
        println("nodesCount = ${nodesCount}")
        val start = System.nanoTime()
        zipper = tree.zipper()
        while (zipper != null) {
            zipper = zipper.next()
        }
        val end = System.nanoTime()
        println("walk2 time = ${end - start}")
    }
}

/*
 * Simple implementation of Text for testing purposes
 */
class StringText(val str: String) : Text {
    fun text(startByte: Int, endByte: Int): String =
            str.substring(startByte, endByte)

    override val encoding: Encoding = Encoding.UTF8

    override fun read(byteOffset: Int, output: ByteBuffer) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        output.put(bytes, byteOffset, Math.min(bytes.size - byteOffset, output.limit()))
    }

}
