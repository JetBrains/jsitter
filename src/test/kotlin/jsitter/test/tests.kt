package jsitter.test

import jsitter.api.*
import junit.framework.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths

object SourceFile : NodeType("source_file")

fun golang(): Language<SourceFile> {
    val lang = Language.load(SourceFile, "go", "tree_sitter_go", "libtsgo.dylib", Language::class.java.classLoader)
    lang.register(SourceFile)
    return lang
}

class Test1 {

    @Test
    fun visiting2() {
        val lang = golang()
        val parser = lang.parser()
        val tree = parser.parse(StringText("type Y = struct {x []string `yyy`}"))
        var zipper: Zipper<*>? = tree.root.zipper()
        val str = arrayListOf<String>()
        while (zipper != null) {
            val nodeTypeStr = zipper.node.type.toString()
            val aliasStr = zipper.alias?.toString()
            str.add(if (aliasStr != null) "$nodeTypeStr($aliasStr)" else nodeTypeStr)
            zipper = zipper.next()
        }
        assertEquals(listOf(
                "source_file",
                "type_declaration",
                "type",
                "type_alias",
                "identifier(type_identifier)",
                "=",
                "struct_type",
                "struct",
                "field_declaration_list",
                "{",
                "field_declaration",
                "identifier(field_identifier)",
                "slice_type",
                "[",
                "]",
                "identifier(type_identifier)",
                "raw_string_literal",
                "}"
        ), str)
    }

    @Test
    fun visitingTree() {
        val lang = golang()
        val parser = lang.parser()
        var tree = parser.parse(StringText("func hello() { sayHello() }"))
        var zipper: Zipper<*>? = tree.root.zipper()
        val str = arrayListOf<String>()
        while (zipper != null) {
            str.add(zipper.node.type.toString())
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
        val codeBlock = tree.root.zipper()
                .down()!!
                .down()!!
                .right()!!
                .right()!!
                .right()!!
                .down()!!
                .right()!!
        assertEquals("call_expression", codeBlock.node.type.name)
        tree = tree.adjust(listOf(Edit(5*2, (5 + 5)*2, (5 + 3) * 2)))
        tree = parser.parse(StringText("func bye() { sayHello() }"), adjustedTree = tree)
        var z: Zipper<*>? = tree.root.zipper()
        val str2 = arrayListOf<String>()
        while (z != null) {
            str2 += z.node.type.toString()
            z = z.next()
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
                "}"), str2)
        tree = tree.adjust(listOf(Edit(8 * 2, 8 * 2, 13 * 2), Edit(17 * 2, (17 + 11) * 2, 17 * 2)))
        tree = parser.parse(StringText("func byeWorld() { }"), adjustedTree = tree)
        z = tree.root.zipper()
        val str3 = arrayListOf<String>()
        while (z != null) {
            str3 += z.node.type.toString()
            z = z.next()
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
                "}"), str3)
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
        val parser = lang.parser()
        val start1 = System.nanoTime()
        val tree = parser.parse(text)
        val end1 = System.nanoTime()
        println("parse time = ${end1 - start1}")
        var zipper: Zipper<*>? = tree.root.zipper()
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
        zipper = tree.root.zipper()
        while (zipper != null) {
            zipper = zipper.next()
        }
        val end = System.nanoTime()
        println("walk2 time = ${end - start}")
    }
}

