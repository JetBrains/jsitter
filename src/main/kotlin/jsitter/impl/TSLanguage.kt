package jsitter.impl

import jsitter.api.*
import jsitter.interop.JSitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

typealias TSSymbol = Int

class TSLanguage(val languagePtr: Ptr,
                 override val name: String,
                 val registry: ConcurrentMap<String, NodeType> = ConcurrentHashMap(),
                 val nodeTypesCache: ConcurrentMap<TSSymbol, NodeType> = ConcurrentHashMap()) : Language {

    fun getNodeType(tsSymbol: TSSymbol): NodeType =
            nodeTypesCache.computeIfAbsent(tsSymbol) { symbol ->
                if (symbol.toInt() == -1) {
                    Error
                } else {
                    val name: String = JSitter.getSymbolName(languagePtr, symbol)
                    val isTerminal: Boolean = JSitter.isTerminal(languagePtr, symbol)
                    val nodeType = registry.computeIfAbsent(name) { name ->
                        if (isTerminal) {
                            Terminal(name)
                        } else {
                            NodeType(name)
                        }
                    }
                    nodeType.id = symbol.toInt()
                    nodeType
                }
            }

    fun getNodeTypeSymbol(nodeType: NodeType): TSSymbol =
            if (nodeType.initialized) {
                nodeType.id
            } else {
                val symbol: TSSymbol = JSitter.getSymbolByName(languagePtr, nodeType.name)
                nodeType.id = symbol
                nodeType.initialized = true
                symbol
            }

    override fun nodeType(name: String): NodeType = registry[name]!!

    override fun<T: NodeType> parser(nodeType: T): Parser<T> =
            TSParser(parserPtr = JSitter.newParser(languagePtr),
                    language = this,
                    nodeType = nodeType) as Parser<T>

    override fun register(nodeType: NodeType) {
        registry[nodeType.name] = nodeType
    }
}