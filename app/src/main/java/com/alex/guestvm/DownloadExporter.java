package com.alex.guestvm;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;

public class DownloadExporter {
    public static String copyZip(Context c, File src, GuestDownloader.Progress p) throws Exception {
        String name = "android-guest-api33-arm64.zip";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AIDEGuestVM");
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("Cannot create Downloads file");
            try {
                OutputStream out = cr.openOutputStream(u, "w");
                if (out == null) throw new IOException("Cannot open Downloads output");
                copy(src, out, p); out.close();
                ContentValues done = new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING, 0); cr.update(u, done, null, null);
                return "Download/AIDEGuestVM/" + name;
            } catch (Exception e) { cr.delete(u, null, null); throw e; }
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AIDEGuestVM");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Downloads folder");
        File dst = new File(dir, name); OutputStream out = new FileOutputStream(dst); copy(src, out, p); out.close(); return dst.getAbsolutePath();
    }
    public static String copyLog(Context c, File src) throws Exception {
        String name = "vm-log.txt";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME,name); v.put(MediaStore.Downloads.MIME_TYPE,"text/plain"); v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AIDEGuestVM");
            Uri u=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v); if(u==null)throw new IOException("Cannot create log file"); OutputStream out=c.getContentResolver().openOutputStream(u,"w"); if(out==null)throw new IOException("Cannot open log output"); InputStream in=new FileInputStream(src); byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();out.close();return "Download/AIDEGuestVM/"+name;
        }
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"AIDEGuestVM");if(!dir.exists())dir.mkdirs();File dst=new File(dir,name);InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(dst);byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();out.close();return dst.getAbsolutePath();
    }
    private static void copy(File src, OutputStream out, GuestDownloader.Progress p) throws Exception { InputStream in=new BufferedInputStream(new FileInputStream(src));byte[] b=new byte[131072];int n;long total=0,last=-1,len=src.length();while((n=in.read(b))>0){out.write(b,0,n);total+=n;long pct=len>0?total*100/len:0;if(p!=null&&pct/10!=last/10){last=pct;p.onMessage("Saving copy to Downloads: "+pct+"%");}}in.close();out.flush(); }
}
