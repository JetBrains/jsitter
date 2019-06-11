package jsitter.test

import jsitter.api.*
import junit.framework.Assert.*

import org.junit.Test

class Test1 {
    @Test
    fun testVisitingTree() {
        val lang = jsitter.impl.loadTSLanguage("go")!!
        val parser = lang.parser()
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
    }
}