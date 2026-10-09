package com.alex.guestvm;
import android.content.Context; import android.net.Uri; import java.io.*; import java.util.zip.*;
public final class EngineImporter {
 public interface Progress { void onMessage(String s); }
 private EngineImporter() {}
 public static File dir(Context c){return new File(c.getFilesDir(),"qemu-engine");}
 public static File engine(Context c){return new File(dir(c),"qemu-system-aarch64");}
 public static void importZip(Context c,Uri uri,Progress p)throws Exception{
  File tmp=new File(c.getFilesDir(),"qemu-engine-importing"); del(tmp); tmp.mkdirs(); File lib=new File(tmp,"lib"); lib.mkdirs(); File share=new File(tmp,"share/qemu"); share.mkdirs();
  ZipInputStream z=new ZipInputStream(new BufferedInputStream(c.getContentResolver().openInputStream(uri))); ZipEntry e; byte[] b=new byte[1024*1024];
  while((e=z.getNextEntry())!=null){if(e.isDirectory())continue;String path=e.getName().replace('\\','/');String n=new File(path).getName();if(n.length()==0)continue;File out;
   if(n.equals("qemu-system-aarch64")||n.equals("libqemu-system-aarch64.so"))out=new File(tmp,"qemu-system-aarch64");
   else if(n.indexOf(".so")>=0)out=new File(lib,n);
   else {int q=path.indexOf("/share/qemu/");if(q>=0){String rel=path.substring(q+12);out=safeChild(share,rel);}else continue;}
   File par=out.getParentFile();if(par!=null)par.mkdirs();FileOutputStream o=new FileOutputStream(out);int r;long total=0;while((r=z.read(b))>0){o.write(b,0,r);total+=r;}o.close();if(p!=null)p.onMessage("Engine bundle: "+n+" ("+(total/1024)+" KB)");
  }z.close();
  File q=engineIn(tmp);if(q==null)throw new IOException("qemu-system-aarch64 not found in ZIP");String elf=elfReport(q);if(elf.indexOf("ELF64 AArch64")<0)throw new IOException("Wrong engine format: "+elf);
  File z1=new File(lib,"libz.so.1");if(p!=null)p.onMessage("NORMALIZE lib dir = "+lib.getAbsolutePath());if(p!=null)p.onMessage("NORMALIZE libz.so.1 present="+z1.isFile()+" size="+(z1.isFile()?z1.length():0));if(!z1.isFile())throw new IOException("Normalized libz.so.1 missing");
  File dst=dir(c);del(dst);if(!tmp.renameTo(dst))throw new IOException("Cannot activate engine bundle");if(p!=null)p.onMessage("Engine imported, normalized and ELF verified.");
 }
 private static File safeChild(File root,String rel)throws Exception{File f=new File(root,rel);String rp=root.getCanonicalPath()+File.separator;String fp=f.getCanonicalPath();if(!fp.startsWith(rp))throw new IOException("Unsafe ZIP path");return f;}
 private static File engineIn(File d){File f=new File(d,"qemu-system-aarch64");return f.isFile()?f:null;}
 public static String elfReport(File f)throws Exception{RandomAccessFile r=new RandomAccessFile(f,"r");byte[] h=new byte[20];r.readFully(h);r.close();if((h[0]&255)!=0x7f||h[1]!='E'||h[2]!='L'||h[3]!='F')return "not ELF";int cls=h[4]&255;int mach=(h[18]&255)|((h[19]&255)<<8);return (cls==2?"ELF64":"ELF32")+" "+(mach==183?"AArch64":"machine="+mach);}
 private static void del(File f){if(!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(int i=0;i<a.length;i++)del(a[i]);}f.delete();}
}
