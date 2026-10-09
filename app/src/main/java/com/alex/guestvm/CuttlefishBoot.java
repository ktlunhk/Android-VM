package com.alex.guestvm;
import java.io.*;
public final class CuttlefishBoot {
 private CuttlefishBoot(){}
 private static long u32(byte[] b,int o){return ((long)b[o]&255)|(((long)b[o+1]&255)<<8)|(((long)b[o+2]&255)<<16)|(((long)b[o+3]&255)<<24);}
 private static long align(long n,long a){return ((n+a-1)/a)*a;}
 private static void copyRange(RandomAccessFile in,FileOutputStream out,long off,long len)throws IOException{in.seek(off);byte[] b=new byte[1024*1024];long left=len;while(left>0){int n=(int)Math.min((long)b.length,left);in.readFully(b,0,n);out.write(b,0,n);left-=n;}}
 public static File workDir(File guest)throws IOException{File d=new File(guest,"stage57-boot");if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create "+d);return d;}
 public static File extractKernel(File guest)throws IOException{File src=new File(guest,"boot.img");File out=new File(workDir(guest),"kernel");RandomAccessFile r=new RandomAccessFile(src,"r");try{byte[] h=new byte[64];r.readFully(h);if(!"ANDROID!".equals(new String(h,0,8,"US-ASCII")))throw new IOException("boot.img magic invalid");long k=u32(h,8);long hv=u32(h,40);if(hv<3||hv>4||k<1024)throw new IOException("Unsupported boot header v"+hv+" kernel="+k);FileOutputStream o=new FileOutputStream(out);try{copyRange(r,o,4096,k);}finally{o.close();}return out;}finally{r.close();}}

 // Vendor ramdisk is LZ4-legacy compressed and carries the REAL AVB fstab under first_stage_ramdisk/system/etc/.
 // First-stage init prefers that file, which needs boot_a/vbmeta_* partitions we do not provide.
 // So decompress it, drop those fstab files, and write it back uncompressed (cpio) so our own fstab is used.
 private static void writeVendorRamdisk(File guest,RandomAccessFile vr,long off,long size,FileOutputStream o)throws IOException{
  byte[] m=new byte[4];vr.seek(off);vr.readFully(m);
  if(u32(m,0)!=0x184C2102L){copyRange(vr,o,off,size);return;}
  File tmp=new File(workDir(guest),"vendor-ramdisk.cpio");
  FileOutputStream t=new FileOutputStream(tmp);
  try{
   long p=off+4,end=off+size;byte[] src=new byte[9*1024*1024];byte[] dst=new byte[9*1024*1024];byte[] hb=new byte[4];
   while(p+4<=end){
    vr.seek(p);vr.readFully(hb);long csz=u32(hb,0);p+=4;
    if(csz==0x184C2102L)continue;
    if(csz<=0||csz>src.length||p+csz>end)break;
    vr.readFully(src,0,(int)csz);
    int n=lz4Block(src,(int)csz,dst);t.write(dst,0,n);p+=csz;
   }
  }finally{try{t.close();}catch(Exception e){}}
  filterCpio(tmp,o);
 }
 private static int lz4Block(byte[] s,int n,byte[] d)throws IOException{
  int i=0,o=0;
  while(i<n){
   int t=s[i++]&255;int l=t>>4;
   if(l==15){int b;do{b=s[i++]&255;l+=b;}while(b==255);}
   if(o+l>d.length)throw new IOException("lz4 literal overflow");
   System.arraycopy(s,i,d,o,l);i+=l;o+=l;
   if(i>=n)break;
   int off=(s[i]&255)|((s[i+1]&255)<<8);i+=2;
   int mm=t&15;if(mm==15){int b;do{b=s[i++]&255;mm+=b;}while(b==255);}
   mm+=4;int from=o-off;
   if(from<0||o+mm>d.length)throw new IOException("lz4 corrupt block");
   for(int k=0;k<mm;k++)d[o++]=d[from++];
  }
  return o;
 }
 private static long hex(byte[] h,int off)throws IOException{return Long.parseLong(new String(h,off,8,"US-ASCII"),16);}
 private static void filterCpio(File in,FileOutputStream o)throws IOException{
  RandomAccessFile r=new RandomAccessFile(in,"r");
  try{
   long len=r.length(),p=0;byte[] h=new byte[110];byte[] buf=new byte[1024*1024];
   while(p+110<=len){
    r.seek(p);r.readFully(h);
    if(!"070701".equals(new String(h,0,6,"US-ASCII")))break;
    long fs=hex(h,6+8*6),ns=hex(h,6+8*11);
    byte[] nb=new byte[(int)ns];r.readFully(nb);
    String name=new String(nb,0,(int)ns-1,"US-ASCII");
    long span=((110+ns+3)&~3L)+((fs+3)&~3L);
    boolean drop=name.startsWith("first_stage_ramdisk/system/etc/fstab.");
    if(!drop){
     r.seek(p);long left=span;
     while(left>0){int n=(int)Math.min((long)buf.length,left);r.readFully(buf,0,n);o.write(buf,0,n);left-=n;}
    }
    p+=span;
    if("TRAILER!!!".equals(name))break;
   }
  }finally{try{r.close();}catch(Exception e){}}
 }
 public static File extractInitrd(File guest)throws IOException{File init=new File(guest,"init_boot.img");File vend=new File(guest,"vendor_boot.img");File out=new File(workDir(guest),"initrd-combined.img");RandomAccessFile ir=new RandomAccessFile(init,"r");RandomAccessFile vr=new RandomAccessFile(vend,"r");FileOutputStream o=new FileOutputStream(out);try{byte[] ih=new byte[64];ir.readFully(ih);if(!"ANDROID!".equals(new String(ih,0,8,"US-ASCII")))throw new IOException("init_boot magic invalid");long generic=u32(ih,12);long ihv=u32(ih,40);if(ihv<3||ihv>4||generic<1)throw new IOException("init_boot unsupported header="+ihv+" ramdisk="+generic);byte[] vh=new byte[4096];vr.readFully(vh);if(!"VNDRBOOT".equals(new String(vh,0,8,"US-ASCII")))throw new IOException("vendor_boot magic invalid");long hv=u32(vh,8),page=u32(vh,12),vendorSize=u32(vh,24),headerSize=u32(vh,2096);if(page<512||page>65536)throw new IOException("vendor_boot page invalid "+page);if(headerSize<2100||headerSize>65536)headerSize=2128;long vendorOff=align(headerSize,page);if(vendorSize<1||vendorOff+vendorSize>vr.length())throw new IOException("vendor ramdisk invalid size="+vendorSize+" off="+vendorOff);writeVendorRamdisk(guest,vr,vendorOff,vendorSize,o);copyRange(ir,o,4096,generic);return out;}finally{try{o.close();}catch(Exception e){}try{ir.close();}catch(Exception e){}try{vr.close();}catch(Exception e){}}}

 public static String bootconfigText(){
  return "androidboot.hardware=cutf_cvm\nandroidboot.console=ttyAMA0\nandroidboot.boot_devices=4010000000.pcie\nandroidboot.selinux=permissive\nandroidboot.force_normal_boot=1\nandroidboot.fstab_suffix=cf.f2fs.hctr2\nandroidboot.hardware.platform=cf.f2fs.cts\nandroidboot.slot_suffix=_a\nandroidboot.slot=a\nandroidboot.serialno=CUTTLEFISHVM01\nandroidboot.verifiedbootstate=orange\nandroidboot.flash.locked=0\nandroidboot.hardware.gralloc=minigbm\nandroidboot.hardware.hwcomposer=drm\nandroidboot.hardware.hwcomposer.display_finder_mode=drm\nandroidboot.hardware.egl=angle\nandroidboot.hardware.vulkan=pastel\nandroidboot.hardware.guest_hwui_renderer=skiagl\nandroidboot.opengles.version=196609\nandroidboot.cpuvulkan.version=4202496\nandroidboot.lcd_density=320\nandroidboot.setupwizard_mode=DISABLED\nandroidboot.vendor.apex.com.android.hardware.gatekeeper=com.android.hardware.gatekeeper.nonsecure\nandroidboot.vendor.apex.com.android.hardware.graphics.composer=com.android.hardware.graphics.composer.drm_hwcomposer\nandroidboot.vendor.apex.com.android.hardware.keymint=com.android.hardware.keymint.rust_nonsecure\nandroidboot.vendor.apex.com.google.emulated.camera.provider.hal=com.google.emulated.camera.provider.hal\nkernel.vmw_vsock_virtio_transport_common.virtio_transport_max_vsock_pkt_buf_size=16384\n";
 }
 public static File withBootconfig(File guest)throws IOException{
  File base=extractInitrd(guest);File patched=new File(workDir(guest),"initrd-stage68-fstab.img");prependStage66Fstab(base,patched);File out=new File(workDir(guest),"initrd-stage68-bootconfig.img");
  String cfg=bootconfigText();
  byte[] data=cfg.getBytes("UTF-8");long sum=0;for(int i=0;i<data.length;i++)sum+=(data[i]&255);
  FileInputStream in=new FileInputStream(patched);FileOutputStream o=new FileOutputStream(out);try{byte[] b=new byte[1024*1024];int n;while((n=in.read(b))>0)o.write(b,0,n);o.write(data);writeLe32(o,data.length);writeLe32(o,sum);o.write("#BOOTCONFIG\n".getBytes("US-ASCII"));}finally{try{in.close();}catch(Exception e){}try{o.close();}catch(Exception e){}}
  return out;
 }

 private static void prependStage66Fstab(File base,File out)throws IOException{
  FileOutputStream o=new FileOutputStream(out);FileInputStream in=null;try{
   String f="# Stage66 first_stage_ramdisk Cuttlefish fstab\n"+
    "/dev/block/by-name/metadata /metadata ext4 nodev,noatime,nosuid,errors=panic wait,formattable,first_stage_mount,check\n"+
    "system /system erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "system /system ext4 noatime,ro,errors=panic wait,logical,first_stage_mount,slotselect\n"+
    "vendor /vendor erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "vendor /vendor ext4 noatime,ro,errors=panic wait,logical,first_stage_mount,slotselect\n"+
    "product /product erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "product /product ext4 noatime,ro,errors=panic wait,logical,first_stage_mount,slotselect\n"+
    "system_ext /system_ext erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "system_ext /system_ext ext4 noatime,ro,errors=panic wait,logical,first_stage_mount,slotselect\n"+
    "odm /odm erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "system_dlkm /system_dlkm erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "vendor_dlkm /vendor_dlkm erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "odm_dlkm /odm_dlkm erofs ro wait,logical,first_stage_mount,slotselect\n"+
    "/dev/block/by-name/userdata /data f2fs nodev,noatime,nosuid latemount,wait,check,quota,formattable\n";
   byte[] d=f.getBytes("UTF-8");
   String[] names=new String[]{"fstab.cutf_cvm","fstab.f2fs","fstab.cf.f2fs.hctr2","fstab.cf.f2fs.cts"};
   ByteArrayOutputStream cb=new ByteArrayOutputStream();
   for(int i=0;i<names.length;i++)writeNewc(cb,names[i],d,0100644);
   writeNewc(cb,"first_stage_ramdisk",new byte[0],0040755);
   for(int i=0;i<names.length;i++)writeNewc(cb,"first_stage_ramdisk/"+names[i],d,0100644);
   writeNewc(cb,"TRAILER!!!",new byte[0],0);while((cb.size()&3)!=0)cb.write(0);
   o.write(cb.toByteArray());
   in=new FileInputStream(base);byte[] b=new byte[1024*1024];int n;while((n=in.read(b))>0)o.write(b,0,n);
  }finally{try{if(in!=null)in.close();}catch(Exception e){}try{o.close();}catch(Exception e){}}
 }
 private static void writeNewc(OutputStream o,String name,byte[] data,int mode)throws IOException{
  byte[] nb=(name+"\0").getBytes("US-ASCII");String h="070701"+hex8(1)+hex8(mode)+hex8(0)+hex8(0)+hex8(1)+hex8(0)+hex8(data.length)+hex8(0)+hex8(0)+hex8(0)+hex8(0)+hex8(nb.length)+hex8(0);o.write(h.getBytes("US-ASCII"));o.write(nb);pad4(o,110+nb.length);o.write(data);pad4(o,data.length);
 }
 private static String hex8(long v){String s=Long.toHexString(v&0xffffffffL);while(s.length()<8)s="0"+s;return s;}
 private static void pad4(OutputStream o,long n)throws IOException{int p=(int)((4-(n&3))&3);while(p>0){o.write(0);p--;}}
 private static void writeLe32(OutputStream o,long v)throws IOException{o.write((int)(v&255));o.write((int)((v>>8)&255));o.write((int)((v>>16)&255));o.write((int)((v>>24)&255));}
 public static String vendorBootReport(File guest)throws IOException{
  File vend=new File(guest,"vendor_boot.img");RandomAccessFile vr=new RandomAccessFile(vend,"r");try{
   byte[] h=new byte[2128];vr.readFully(h);if(!"VNDRBOOT".equals(new String(h,0,8,"US-ASCII")))return "STAGE68 vendor_boot magic invalid";
   long hv=u32(h,8),page=u32(h,12),vendorSize=u32(h,24),headerSize=u32(h,2096),dtbSize=u32(h,2100);
   long tableSize=hv>=4?u32(h,2112):0,entryNum=hv>=4?u32(h,2116):0,entrySize=hv>=4?u32(h,2120):0,bootconfigSize=hv>=4?u32(h,2124):0;
   long vendorOff=align(headerSize,page),dtbOff=vendorOff+align(vendorSize,page),tableOff=dtbOff+align(dtbSize,page),bootconfigOff=tableOff+align(tableSize,page);
   StringBuilder b=new StringBuilder();b.append("STAGE68 vendor_boot v").append(hv).append(" page=").append(page).append(" header=").append(headerSize).append(" vendorRamdisk=").append(vendorSize).append(" dtb=").append(dtbSize).append(" table=").append(tableSize).append(" entries=").append(entryNum).append(" entrySize=").append(entrySize).append(" bootconfig=").append(bootconfigSize);
   if(hv>=4&&entrySize>=44&&entryNum>0&&entryNum<128&&tableOff+tableSize<=vr.length()){
    byte[] e=new byte[(int)entrySize];for(int i=0;i<(int)entryNum;i++){vr.seek(tableOff+(long)i*entrySize);vr.readFully(e);long sz=u32(e,0),off=u32(e,4),type=u32(e,8);int n=0;while(n<32&&e[12+n]!=0)n++;String name=new String(e,12,n,"US-ASCII");b.append("\nSTAGE68 fragment ").append(i).append(" name=").append(name).append(" type=").append(type).append(" offset=").append(off).append(" size=").append(sz);if(vendorOff+off+4<=vr.length()){vr.seek(vendorOff+off);byte[] m=new byte[4];vr.readFully(m);b.append(" magic=").append(hexByte(m[0])).append(hexByte(m[1])).append(hexByte(m[2])).append(hexByte(m[3]));}}
   }
   if(bootconfigSize>0&&bootconfigOff+bootconfigSize<=vr.length()){int n=(int)Math.min(bootconfigSize,4096);byte[] bc=new byte[n];vr.seek(bootconfigOff);vr.readFully(bc);String t=new String(bc,"UTF-8").replace('\0',' ').trim();b.append("\nSTAGE68 vendor bootconfig preview: ").append(t);}
   return b.toString();
  }finally{vr.close();}
 }
 private static String hexByte(byte v){String s=Integer.toHexString(v&255);return s.length()<2?"0"+s:s;}
 public static String report(File guest)throws IOException{File k=extractKernel(guest);File r=extractInitrd(guest);String bc=bootconfigText();return "STAGE57 extracted kernel="+k.length()+" bytes initrd="+r.length()+" bytes\n"+vendorBootReport(guest)+"\nSTAGE70 bootconfig compiled into this build: "+bc.length()+" bytes, "+bc.split("\n").length+" lines (expect 1275 bytes / 27 lines)\n"+bc;}
}
