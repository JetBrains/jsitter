package jsitter.interop;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class JSitter {
    static {
        String libName = System.mapLibraryName("jsitter");
        try {
            LibLoader.loadLibraryFromJar("/" + libName);
        } catch (IOException e) {
            String filename = System.getProperty("user.dir") + "/native/build/" + libName;
            System.out.println("loading native lib from " + filename);
            System.load(filename);
        }
    }

    public static native long findLanguage(@NotNull String name);

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
                                    long oldTreePtr,
                                    @NotNull Input input,
                                    int encoding,
                                    ByteBuffer readingBuffer,
                                    int startByte,
                                    int oldEndByte,
                                    int newEndByte);

    public static native long newParser(long languagePtr);
}
