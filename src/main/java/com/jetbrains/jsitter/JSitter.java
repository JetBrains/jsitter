package com.jetbrains.jsitter;

import org.jetbrains.annotations.NotNull;

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

    public static native boolean move(long cursor, int dir, short tsSymbol, boolean named);

    public static native String getSymbolName(long languagePtr, short symbol);

    public static native boolean isTerminal(long languagePtr, short symbol);

    public static native short getSymbolByName(long languagePtr, String name);

    public static native void releaseLanguage(long languagePtr);

    public static native void releaseTree(long treePtr);

    public static native void releaseParser(long parserPtr);

    public static native long parse(long parserPtr,
                                    long oldTreePtr,
                                    @NotNull TSInput tsInput,
                                    long readingBufferPtr,
                                    int startByte,
                                    int oldEndByte,
                                    int newEndByte);

    public static native void releaseZipper(long cursorPtr);

    public static native long makeCursor(long treePtr);

    public static native long newParser(long languagePtr);
}
