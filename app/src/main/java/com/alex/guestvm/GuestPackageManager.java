package com.alex.guestvm;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.zip.*;

public class GuestPackageManager {
    public interface Progress { void onMessage(String s); }
    public static File getGuestDir(Context c){ return new File(c.getFilesDir(), "guest"); }
    public static File importZip(Context c, Uri uri, Progress p) throws Exception {
        InputStream in=c.getContentResolver().openInputStream(uri);
        try{return importStream(c,in,p);}finally{if(in!=null)in.close();}
    }
    public static File importStream(Context c, InputStream source, Progress p) throws Exception {
        File dir=getGuestDir(c); File temp=new File(c.getFilesDir(), "guest-importing"); delete(temp);
        if(!temp.mkdirs()) throw new IOException("Cannot create guest-importing directory");
        ZipInputStream zin=new ZipInputStream(new BufferedInputStream(source,131072)); ZipEntry e; byte[] b=new byte[131072]; int count=0;
        String root=temp.getCanonicalPath()+File.separator;
        while((e=zin.getNextEntry())!=null){
            if(e.isDirectory()) continue;
            String simple=new File(e.getName()).getName();
            if(!isAllowed(simple)) continue;
            File out=new File(temp,simple); if(!out.getCanonicalPath().startsWith(root)) throw new IOException("Unsafe ZIP path");
            FileOutputStream fos=new FileOutputStream(out); int n; long total=0,last=-1;
            try{while((n=zin.read(b))>0){fos.write(b,0,n);total+=n;long mb=total/1024/1024;if(mb/128!=last/128){last=mb;if(p!=null)p.onMessage("Extracting "+simple+": "+mb+" MB");}}}finally{fos.close();}
            count++; if(p!=null)p.onMessage("Extracted "+simple+" ("+(total/1024/1024)+" MB)");
        }
        zin.close(); if(count==0) throw new IOException("No recognized Android/Cuttlefish images in ZIP");
        boolean cf=isCuttlefishDir(temp); boolean legacy=isLegacyDir(temp);
        if(!cf&&!legacy) throw new IOException("ZIP recognized but incomplete. For Cuttlefish need boot.img, init_boot.img, vendor_boot.img, super.img and userdata.img");
        FileOutputStream m=new FileOutputStream(new File(temp,"guest.properties"));
        m.write(buildManifest(temp).getBytes("UTF-8"));m.close();
        delete(dir); if(!temp.renameTo(dir))throw new IOException("Cannot activate guest directory"); return dir;
    }
    private static boolean isAllowed(String n){
        return n.equals("boot.img")||n.equals("init_boot.img")||n.equals("vendor_boot.img")||n.equals("super.img")||n.equals("userdata.img")||
          n.equals("vbmeta.img")||n.equals("vbmeta_system.img")||n.equals("vbmeta_system_dlkm.img")||n.equals("vbmeta_vendor_dlkm.img")||
          n.equals("cuttlefish_example_custom.img")||n.equals("android-info.txt")||n.equals("fastboot-info.txt")||n.equals("dtb.img")||n.equals("kernel")||
          n.equals("system.img")||n.equals("vendor.img")||n.equals("ramdisk.img")||n.equals("kernel-ranchu")||n.equals("product.img")||n.equals("system_ext.img");
    }
    private static boolean has(File d,String n,long min){File f=new File(d,n);return f.isFile()&&f.length()>=min;}
    private static boolean isCuttlefishDir(File d){return has(d,"boot.img",1024*1024)&&has(d,"init_boot.img",1024*1024)&&has(d,"vendor_boot.img",1024*1024)&&has(d,"super.img",128L*1024L*1024L)&&has(d,"userdata.img",1024*1024);}
    private static boolean isLegacyDir(File d){File kr=new File(d,"kernel-ranchu"),k=new File(d,"kernel");return has(d,"system.img",256L*1024L*1024L)&&has(d,"ramdisk.img",1024*1024)&&((kr.isFile()&&kr.length()>1024*1024)||(k.isFile()&&k.length()>1024*1024));}
    public static boolean isCuttlefish(Context c){return isCuttlefishDir(getGuestDir(c));}
    public static boolean bootReady(Context c){File d=getGuestDir(c);return isCuttlefishDir(d)||isLegacyDir(d);}
    public static boolean bootMinimumPresent(Context c){return bootReady(c);}
    private static String buildManifest(File d){String[] a={"boot.img","init_boot.img","vendor_boot.img","super.img","userdata.img","vbmeta.img","vbmeta_system.img","vbmeta_system_dlkm.img","vbmeta_vendor_dlkm.img","cuttlefish_example_custom.img","dtb.img","kernel","kernel-ranchu","ramdisk.img","system.img","vendor.img","product.img","system_ext.img","android-info.txt","fastboot-info.txt"};StringBuilder s=new StringBuilder();for(int i=0;i<a.length;i++){File f=new File(d,a[i]);if(f.isFile())s.append(a[i]).append("=").append(f.length()).append("\n");}return s.toString();}
    public static String inspect(Context c){File d=getGuestDir(c);if(!d.isDirectory())return "No guest package imported.";StringBuilder s=new StringBuilder();s.append(isCuttlefishDir(d)?"Guest type: CUTTLEFISH ARM64 image set\n":"Guest type: legacy/Ranchu or incomplete\n");String[] a={"boot.img","init_boot.img","vendor_boot.img","super.img","userdata.img","vbmeta.img","vbmeta_system.img","vbmeta_system_dlkm.img","vbmeta_vendor_dlkm.img","cuttlefish_example_custom.img","dtb.img","kernel","kernel-ranchu","ramdisk.img","system.img","vendor.img"};for(int i=0;i<a.length;i++){File f=new File(d,a[i]);if(f.isFile())s.append(a[i]).append(" = ").append(f.length()).append(" bytes\n");}if(isCuttlefishDir(d)){s.append("\n");s.append(CuttlefishProbe.report(d));}return s.toString();}
    private static void delete(File f){if(!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(int i=0;i<a.length;i++)delete(a[i]);}f.delete();}
}
