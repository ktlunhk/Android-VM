package com.alex.guestvm;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;

public class GuestDownloader {
    public interface Progress { void onMessage(String s); }
    public static final String NAME="AOSP Android 13 / API 33 / ARM64-v8a r02";
    public static final String URL="https://dl.google.com/android/repository/sys-img/android/arm64-v8a-33_r02.zip";
    public static final String SHA256="55ba9a20d473dd5351c77342016956342c40375f3e073b65a43dc9db13ccc5c6";

    public static File download(Context c, final Progress p) throws Exception {
        File cache=new File(c.getCacheDir(),"aosp-arm64-api33.zip");
        URLConnection con=new URL(URL).openConnection(); con.setConnectTimeout(20000); con.setReadTimeout(60000);
        long len=con.getContentLengthLong(); InputStream in=new BufferedInputStream(con.getInputStream());
        FileOutputStream out=new FileOutputStream(cache); byte[] b=new byte[131072]; int n; long total=0,last=-1;
        while((n=in.read(b))>0){out.write(b,0,n);total+=n;if(len>0){long pct=total*100/len;if(pct/5!=last/5){last=pct;if(p!=null)p.onMessage("Download "+pct+"% ("+(total/1024/1024)+" MB)");}}}
        out.close(); in.close();
        if(p!=null)p.onMessage("Checking SHA-256..."); String got=sha256(cache); if(!SHA256.equalsIgnoreCase(got)){cache.delete();throw new IOException("SHA-256 mismatch: "+got);}
        if(p!=null)p.onMessage("Download verified.");
        if(p!=null)p.onMessage("Saving verified guest ZIP to Downloads...");
        String saved=DownloadExporter.copyZip(c,cache,p);
        if(p!=null)p.onMessage("Guest ZIP saved: "+saved);
        if(p!=null)p.onMessage("Extracting guest files...");
        FileInputStream fin=new FileInputStream(cache); File result=GuestPackageManager.importStream(c,fin,new GuestPackageManager.Progress(){public void onMessage(String s){if(p!=null)p.onMessage(s);}}); fin.close(); cache.delete(); return result;
    }
    private static String sha256(File f) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");InputStream in=new BufferedInputStream(new FileInputStream(f));byte[] b=new byte[131072];int n;while((n=in.read(b))>0)md.update(b,0,n);in.close();byte[] d=md.digest();StringBuilder s=new StringBuilder();for(int i=0;i<d.length;i++){String x=Integer.toHexString(d[i]&255);if(x.length()<2)s.append('0');s.append(x);}return s.toString();}
}
