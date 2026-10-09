package com.alex.guestvm;

import android.view.Surface;

public class NativeVm {
    private static boolean loaded;
    static {
        try {
            System.loadLibrary("guestvm");
            loaded = true;
        } catch (Throwable e) {
            loaded = false;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static native int startDisplay(Surface surface);
    public static native void stopDisplay();
}
