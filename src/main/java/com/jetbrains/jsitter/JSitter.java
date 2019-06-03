package com.jetbrains.jsitter;

import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JSitter {
    public static boolean DEBUG = false;
    private static final ReferenceQueue<NativeTrie> REFERENCE_QUEUE = new ReferenceQueue<>();
    private static final Map<Object, Long> REFS = new ConcurrentHashMap<>();
    public static final AtomicInteger triesCount = new AtomicInteger(0);

    static {
        String libName = System.mapLibraryName("trie");
        try {
            LibLoader.loadLibraryFromJar("/" + libName);
        } catch (IOException e) {
            String filename = System.getProperty("user.dir") + "/native/build/" + libName;
            System.out.println("loading native lib from " + filename);
            System.load(filename);
        }

        new Thread(() -> {
            while (true) {
                try {
                    final Object key = REFERENCE_QUEUE.remove();
                    Long address = REFS.remove(key);
                    if (address != null) {
                        NativeTrie.releaseTrie(address);
                        triesCount.decrementAndGet();
                    } else {
                        throw new IllegalStateException("Unknown trie reference");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }
        }, "native trie cleaner").start();
    }

    private final long address;

    private NativeTrie(long address) {
        this.address = address;
        triesCount.incrementAndGet();
        REFS.put(new PhantomReference<>(this, REFERENCE_QUEUE), address);
    }

    @Override
    public String toString() {
        return "Trie{address=" + address + '}';
    }

    public static NativeTrie empty() {
        return new NativeTrie(emptyImpl());
    }

    public static NativeTrie insert(NativeTrie trie, int epoch, ByteBuffer bytes) {
        long keyAddress = Allocator.address(bytes); // see DAOS#nextCapacity
        long result = insert(trie.address, epoch, debug("insert", bytes), keyAddress);
        if (trie.address == result) {
            return trie;
        } else {
            return new NativeTrie(result);
        }
    }

    public static boolean contains(NativeTrie trie, ByteBuffer bytes) {
        debug("contains", bytes);
        long keyAddress = ((DirectBuffer) bytes).address(); // see DAOS#nextCapacity
        return contains(trie.address, bytes.position(), keyAddress);
    }

    public static NativeTrie retract(NativeTrie trie, int epoch, ByteBuffer bytes) {
        long keyAddress = ((DirectBuffer) bytes).address(); // see DAOS#nextCapacity
        long result = remove(trie.address, epoch, bytes.position(), keyAddress);
        if (trie.address == result) {
            return trie;
        } else {
            return new NativeTrie(result);
        }
    }

    public static Reducible<ByteBuffer> seek(NativeTrie trie, ByteBuffer prefix, Allocator allocator, Order dir) {
        return new Reducible<ByteBuffer>() {
            @Override
            public <Acc> Acc reduce(Reducer<Acc, ByteBuffer> f, Acc init) {
                debug("seek", prefix);
                ByteBuffer iterRef = allocator.alloc(3 * 8);
                long iterRefAddr = Allocator.address(iterRef);
                ByteBuffer sealedOutputBuffer = null;
                try {
                    ByteBuffer outputBuffer = allocator.output();
                    long outputBufferSize = NativeTrie.seek(
                            trie.address,
                            prefix.position(),
                            Allocator.address(prefix),
                            iterRefAddr,
                            dir.ordinal,
                            Allocator.address(outputBuffer),
                            outputBuffer.capacity());
                    outputBuffer.position((int) outputBufferSize);
                    sealedOutputBuffer = allocator.seal(outputBuffer);
                    sealedOutputBuffer.position(0);
                    if (outputBufferSize == 0) {
                        return init;
                    } else {
                        Acc acc = init;
                        while (true) {
                            Reduction<Acc> acc1 = f.step(acc, sealedOutputBuffer);
                            allocator.release(sealedOutputBuffer);
                            sealedOutputBuffer = null;
                            if (acc1.reduced) {
                                return acc1.value;
                            } else {
                                outputBuffer = allocator.output();
                                outputBufferSize = NativeTrie.next(iterRefAddr);
                                outputBuffer.position((int) outputBufferSize);
                                sealedOutputBuffer = allocator.seal(outputBuffer);
                                sealedOutputBuffer.position(0);
                                if (outputBufferSize == 0) {
                                    return acc1.value;
                                } else {
                                    acc = acc1.value;
                                }
                            }
                        }
                    }
                } finally {
                    NativeTrie.closeIter(iterRefAddr);
                    if (sealedOutputBuffer != null) {
                        allocator.release(sealedOutputBuffer);
                    }
                    allocator.release(iterRef);
                }

            }
        };
    }

    public static long bytesSize(NativeTrie trie) {
        return bytesSize(trie.address);
    }

    private static native long emptyImpl();

    private static native long insert(long trieAddress, int epoch, int length, long keyAddress);

    private static native boolean contains(long trieAddress, int length, long keyAddress);

    private static native long remove(long trieAddress, int epoch, int length, long keyAddress);

    private static native long seek(long trieAddress, int length, long keyAddress, long iterAddress, int direction, long outputBufferAddr, long outputBufferSize);

    private static native long next(long iterAddress);

    private static native void closeIter(long iterAddress);

    private static native void releaseTrie(long trieAddress);

    private static native long bytesSize(long trieAddress);

    private static int debug(String what, ByteBuffer bytes) {
        int length = bytes.position();
        if (DEBUG) {
            StringBuilder sb = new StringBuilder(what);
            for (int i = 0; i < length; i++) {
                String s = Integer.toHexString(bytes.get(i));
                if (s.length() < 2) {
                    sb.append(" 0x0");
                } else {
                    sb.append(" 0x");
                }
                sb.append(s);
            }
            System.out.println(sb.toString());
        }
        return length;
    }
}
