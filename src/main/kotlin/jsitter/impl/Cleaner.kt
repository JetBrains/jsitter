package jsitter.impl

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

typealias Disposer = () -> Unit
interface Resource {
    fun disposer(): Disposer
}

object Cleaner {
    var DEBUG = false

    val refQueue = ReferenceQueue<Resource>()
    val refs = ConcurrentHashMap<Any, Disposer>()
    val refsCount = AtomicInteger()
    val started = AtomicBoolean()

    fun start() {
        val thread = Thread({
            while (true) {
                val key = refQueue.remove()
                val disposer = refs.remove(key)!!
                refsCount.decrementAndGet()
                disposer()
            }
        }, "com.jetbrains.jsitter.cleaner")
        thread.isDaemon = true
        thread.start()
    }

    fun register(r: Resource) {
        val ref = PhantomReference(r, refQueue)
        val disposer = r.disposer()
        refs.put(ref, disposer)
        refsCount.incrementAndGet()
        if (started.compareAndSet(false, true)) {
            start()
        }
    }
}

