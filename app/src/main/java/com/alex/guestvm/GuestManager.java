package com.alex.guestvm;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class GuestManager {
    public interface Progress { void onMessage(String text); }

    public static File guestDir(Context c) {
        File d = new File(c.getFilesDir(), "guest");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File importImage(Context c, Uri uri, Progress p) throws Exception {
        File out = new File(guestDir(c), "guest.img");
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Cannot open selected image");
        FileOutputStream fos = new FileOutputStream(out);
        byte[] b = new byte[1024 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(b)) > 0) {
            fos.write(b, 0, n);
            total += n;
            if (p != null && total % (16L * 1024L * 1024L) < n) p.onMessage("Copied " + (total / 1024 / 1024) + " MB");
        }
        fos.flush(); fos.close(); in.close();
        return out;
    }

    public static File getImportedImage(Context c) {
        File f = new File(guestDir(c), "guest.img");
        return f.exists() ? f : null;
    }
}
