package com.alex.guestvm;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLog {
    private static File file;
    public static synchronized void init(Context c) {
        file = new File(c.getFilesDir(), "vm-log.txt");
        write("=== App started ===");
    }
    public static synchronized void write(String s) {
        if (file == null) return;
        FileWriter w = null;
        try {
            String t = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            w = new FileWriter(file, true);
            w.write(t + "  " + s + "\n");
            w.flush();
        } catch (Throwable ignored) {
        } finally {
            if (w != null) try { w.close(); } catch (Throwable ignored) {}
        }
    }
    public static synchronized String read() {
        if (file == null || !file.exists()) return "";
        StringBuilder s = new StringBuilder();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(file));
            String line;
            while ((line = r.readLine()) != null) s.append(line).append('\n');
        } catch (Throwable e) { s.append("Cannot read log: ").append(e.toString()); }
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) {} }
        return s.toString();
    }
    public static synchronized boolean clear() {
        if (file == null) return false;
        FileWriter w = null;
        try {
            w = new FileWriter(file, false);
            w.write("");
            w.flush();
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            if (w != null) try { w.close(); } catch (Throwable ignored) {}
        }
    }
    public static File getFile() { return file; }
}
