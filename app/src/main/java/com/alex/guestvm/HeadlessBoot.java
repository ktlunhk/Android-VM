package com.alex.guestvm;
import android.content.Context;
import android.os.Build;
import java.io.*;

public final class HeadlessBoot {
 public interface Logger { void log(String m); }
 private HeadlessBoot(){}
 public static void prepare(Context c, Logger l) throws Exception {
  l.log("ABI = "+Build.SUPPORTED_ABIS[0]);
  File kvm=new File("/dev/kvm");
  l.log("/dev/kvm exists = "+kvm.exists());
  l.log("/dev/kvm readable = "+kvm.canRead()+", writable = "+kvm.canWrite());
  File dir=GuestPackageManager.getGuestDir(c);
  File kernel=first(dir,new String[]{"kernel-ranchu","kernel"});
  File ramdisk=new File(dir,"ramdisk.img"); File system=new File(dir,"system.img"); File vendor=new File(dir,"vendor.img"); File userdata=new File(dir,"userdata.img");
  info(l,"kernel",kernel); info(l,"ramdisk",ramdisk); info(l,"system",system); info(l,"vendor",vendor); info(l,"userdata",userdata);
  File engine=new File(c.getApplicationInfo().nativeLibraryDir,"libqemu-system-aarch64.so");
  l.log("QEMU engine path = "+engine.getAbsolutePath());
  l.log("QEMU engine bundled = "+engine.exists());
  l.log("Acceleration plan = "+((kvm.exists()&&kvm.canRead()&&kvm.canWrite())?"KVM candidate":"TCG/software emulation required"));
  l.log("Headless first-boot plan prepared: serial output to in-app VM Log; graphics disabled initially.");
  l.log("Guest images will remain read-only during diagnostics except userdata when a real engine is integrated.");
  if(!engine.exists()) l.log("BOOT NOT ATTEMPTED: a compatible Android ARM64 QEMU engine is not bundled. This build intentionally does not fake a boot.");
 }
 private static File first(File d,String[] n){for(int i=0;i<n.length;i++){File f=new File(d,n[i]);if(f.exists())return f;}return new File(d,n[0]);}
 private static void info(Logger l,String n,File f){l.log(n+" = "+(f.exists()?f.getAbsolutePath()+" ("+(f.length()/1024/1024)+" MB)":"MISSING"));}
}
