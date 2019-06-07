package jsitter.interop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

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

    @Nullable
    public static native String getName(long cursorPtr);

    public interface Input {
        int read(int byteOffset);
    }

    public static native long copyCursor(long cursor);

    public static native boolean move(long cursor, int dir, short tsSymbol, boolean named);

    public static native String getSymbolName(long languagePtr, short symbol);

    public static native boolean isTerminal(long languagePtr, short symbol);

    public static native short getSymbolByName(long languagePtr, String name);

    public static native void releaseTree(long treePtr);

    public static native void releaseParser(long parserPtr);

    public static native long parse(long parserPtr,
                                    long oldTreePtr,
                                    @NotNull Input input,
                                    long readingBufferPtr,
                                    int startByte,
                                    int oldEndByte,
                                    int newEndByte);

    public static native void releaseZipper(long cursorPtr);

    public static native long makeCursor(long treePtr);

    public static native long newParser(long languagePtr);
}
