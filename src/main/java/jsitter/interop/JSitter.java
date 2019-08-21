package jsitter.interop;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class JSitter {

    public static List stuff = new ArrayList();

    public static long loadLang(String fnName, String libnameOrPath, ClassLoader loader) {
        NativeLibrary instance = NativeLibrary.getInstance(libnameOrPath, loader);
        stuff.add(instance);
        Function function = instance.getFunction(fnName);
        return function.invokeLong(new Object[]{});
    }

    static {
        try {
            File jsitter = Native.extractFromResourcePath("jsitter", JSitter.class.getClassLoader());
            System.load(jsitter.getAbsolutePath());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static native void releaseSubtree(long subtree);

    public static native void retainSubtree(long subtree);

    public interface Input {
        int read(int byteOffset);
    }

    public static native String getSymbolName(long languagePtr, int symbol);

    public static native boolean isTerminal(long languagePtr, int symbol);

    public static native int getSymbolByName(long languagePtr, String name);

    public static native void releaseTree(long treePtr);

    public static native void releaseParser(long parserPtr);

    public static native long parse(long parserPtr,
                                    long editedTreePtr,
                                    @NotNull Input input,
                                    int encoding,
                                    ByteBuffer readingBuffer);

    public static native long copyTree(long treePtr);

    public static native void editTree(long treePtr, int startByte, int oldEndByte, int newEndByte);

    public static native long editSubtree(long subtree, int startByte, int oldEndByte, int newEndByte);

    @Nullable
    public static native int[] getChangedRanges(long editedTreePtr, long newTreePtr);

    public static native long newParser(long languagePtr, long cancellationFlagPtr);

    public static native void parserReset(long parserPtr);
}
