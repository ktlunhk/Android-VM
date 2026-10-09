package com.alex.guestvm;
import android.content.Context;
import android.os.Build;
import java.io.*;
import java.util.*;

public final class QemuEngine {
 public interface Logger { void log(String s); }
 private static final String EXPECTED_VERSION="QEMU emulator version 10.2.4";
 private QemuEngine() {}
 public static File getPackagedEngine(Context c){return new File(c.getApplicationInfo().nativeLibraryDir,"libqemu-system-aarch64.so");}
 public static File getImportedEngine(Context c){return EngineImporter.engine(c);}
 public static File getEngine(Context c){File p=getPackagedEngine(c);if(p.isFile())return p;return getImportedEngine(c);}
 public static File getImportedLibDir(Context c){return new File(new File(c.getFilesDir(),"qemu-engine"),"lib");}
 public static boolean isPresent(Context c){return getEngine(c).isFile();}
 public static boolean isPackaged(Context c){return getPackagedEngine(c).isFile();}
 public static File getDataDir(Context c){return new File(c.getFilesDir(),"qemu-data");}
 public static File ensureMetadataImage(Context c)throws IOException{
  File d=GuestPackageManager.getGuestDir(c);if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create guest directory: "+d);
  File f=new File(d,"metadata.img");long wanted=64L*1024L*1024L;
  if(!isStage19MetadataGpt(f,wanted)||!fileHasExt4(f)){if(!installPreformattedMetadata(c,f)&&!isStage19MetadataGpt(f,wanted))createMetadataGpt(f,wanted);}
  return f;
 }
 private static boolean fileHasExt4(File f){RandomAccessFile r=null;try{r=new RandomAccessFile(f,"r");r.seek(1048576L+1024L+56L);int a=r.read();int b=r.read();return a==0x53&&b==0xEF;}catch(Exception e){return false;}finally{if(r!=null)try{r.close();}catch(Exception e){}}}
 private static boolean installPreformattedMetadata(Context c,File f){
  try{InputStream in=new java.util.zip.GZIPInputStream(c.getAssets().open("qemu/metadata.img.gz"));FileOutputStream o=new FileOutputStream(f);try{byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);}finally{try{o.close();}catch(Exception e){}try{in.close();}catch(Exception e){}}return true;}catch(Exception e){return false;}
 }
 public static File ensureMiscImage(Context c)throws IOException{
  File d=GuestPackageManager.getGuestDir(c);if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create guest directory: "+d);
  File f=new File(d,"misc.img");long wanted=4L*1024L*1024L;if(!f.isFile()||f.length()!=wanted)createGptNamed(f,wanted,"misc");return f;
 }
 public static File resetMetadataImage(Context c)throws IOException{
  File d=GuestPackageManager.getGuestDir(c);if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create guest directory: "+d);
  File f=new File(d,"metadata.img");if(f.exists()&&!f.delete())throw new IOException("Cannot reset metadata.img");createMetadataGpt(f,64L*1024L*1024L);return f;
 }
 private static boolean isStage19MetadataGpt(File f,long wanted){
  if(!f.isFile()||f.length()!=wanted)return false;RandomAccessFile r=null;
  try{r=new RandomAccessFile(f,"r");byte[] sig=new byte[8];r.seek(512);r.readFully(sig);if(!"EFI PART".equals(new String(sig,"US-ASCII")))return false;byte[] n=new byte[16];r.seek(1080);r.readFully(n);return n[0]=='m'&&n[2]=='e'&&n[4]=='t'&&n[6]=='a'&&n[8]=='d'&&n[10]=='a'&&n[12]=='t'&&n[14]=='a';}catch(Exception e){return false;}finally{if(r!=null)try{r.close();}catch(Exception e){}}
 }
 private static void put16(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>>8);}
 private static void put32(byte[] b,int o,long v){for(int i=0;i<4;i++)b[o+i]=(byte)(v>>>(8*i));}
 private static void put64(byte[] b,int o,long v){for(int i=0;i<8;i++)b[o+i]=(byte)(v>>>(8*i));}
 private static void guid(byte[] b,int o,long a,long z){put32(b,o,a);put16(b,o+4,(int)(a>>>32));put16(b,o+6,(int)(a>>>48));for(int i=0;i<8;i++)b[o+8+i]=(byte)(z>>>(56-8*i));}
 private static long crc(byte[] b,int o,int n){java.util.zip.CRC32 c=new java.util.zip.CRC32();c.update(b,o,n);return c.getValue();}
 private static void createMetadataGpt(File f,long bytes)throws IOException{createGptNamed(f,bytes,"metadata");}
 private static void createGptNamed(File f,long bytes,String name)throws IOException{
  RandomAccessFile r=new RandomAccessFile(f,"rw");try{
   r.setLength(bytes);long sectors=bytes/512L,first=2048L,last=sectors-34L;
   byte[] m=new byte[512];m[450]=(byte)0xEE;put32(m,454,1);put32(m,458,Math.min(0xffffffffL,sectors-1));m[510]=0x55;m[511]=(byte)0xAA;r.seek(0);r.write(m);
   byte[] e=new byte[16384];byte[] t=new byte[]{(byte)0xaf,(byte)0x3d,(byte)0xc6,(byte)0x0f,(byte)0x83,(byte)0x84,(byte)0x72,(byte)0x47,(byte)0x8e,(byte)0x79,(byte)0x3d,(byte)0x69,(byte)0xd8,(byte)0x47,(byte)0x7d,(byte)0xe4};System.arraycopy(t,0,e,0,16);guid(e,16,0x1122334455667788L,0x99AABBCCDDEEFF01L);put64(e,32,first);put64(e,40,last);for(int i=0;i<name.length();i++)put16(e,56+i*2,name.charAt(i));long ec=crc(e,0,e.length);r.seek(1024);r.write(e);
   byte[] h=new byte[512];byte[] es="EFI PART".getBytes("US-ASCII");System.arraycopy(es,0,h,0,8);put32(h,8,0x10000);put32(h,12,92);put64(h,24,1);put64(h,32,sectors-1);put64(h,40,34);put64(h,48,sectors-34);guid(h,56,0xA1B2C3D4E5F60718L,0x192A3B4C5D6E7F80L);put64(h,72,2);put32(h,80,128);put32(h,84,128);put32(h,88,ec);put32(h,16,crc(h,0,92));r.seek(512);r.write(h);
   r.seek((sectors-33)*512L);r.write(e);byte[] bh=(byte[])h.clone();put32(bh,16,0);put64(bh,24,sectors-1);put64(bh,32,1);put64(bh,72,sectors-33);put32(bh,16,crc(bh,0,92));r.seek((sectors-1)*512L);r.write(bh);
  }finally{r.close();}
 }

 public static File ensureRawUserdata(Context c)throws IOException{
  File d=GuestPackageManager.getGuestDir(c);
  File src=new File(d,"userdata.img");
  if(!src.isFile())throw new FileNotFoundException("userdata.img missing");
  RandomAccessFile in=new RandomAccessFile(src,"r");
  try{
   if(in.length()<28)return src;
   byte[] h=new byte[28];in.readFully(h);
   if(le32(h,0)!=0xed26ff3aL){
    File out=new File(d,"userdata-2g-stage40-prep.img");
    long target=2L*1024L*1024L*1024L;
    if(!out.isFile()||out.length()!=target)createMetadataGpt(out,target);
    return out;
   }
   int fileHdr=(int)(((long)h[8]&255L)|(((long)h[9]&255L)<<8));
   int chunkHdr=(int)(((long)h[10]&255L)|(((long)h[11]&255L)<<8));
   long blk=le32(h,12),totalBlks=le32(h,16),chunks=le32(h,20);
   if(fileHdr<28||chunkHdr<12||blk<512||totalBlks<1||chunks<1)throw new IOException("Invalid Android sparse userdata header");
   long outBytes=blk*totalBlks;if(outBytes<=0||outBytes>64L*1024L*1024L*1024L)throw new IOException("Unreasonable userdata expanded size: "+outBytes);
   File out=new File(d,"userdata.raw.img");File stamp=new File(d,"userdata.raw.info");
   String want=src.length()+":"+src.lastModified()+":"+outBytes;
   if(out.isFile()&&out.length()==outBytes&&stamp.isFile()){BufferedReader br=new BufferedReader(new FileReader(stamp));String got=br.readLine();br.close();if(want.equals(got))return out;}
   File tmp=new File(d,"userdata.raw.tmp");if(tmp.exists())tmp.delete();RandomAccessFile o=new RandomAccessFile(tmp,"rw");
   try{
    o.setLength(outBytes);in.seek(fileHdr);long pos=0;byte[] ch=new byte[chunkHdr];byte[] buf=new byte[1024*1024];
    for(long i=0;i<chunks;i++){in.readFully(ch);int type=(ch[0]&255)|((ch[1]&255)<<8);long blocks=le32(ch,4),total=le32(ch,8),bytes=blocks*blk;if(pos+bytes>outBytes)throw new IOException("Sparse chunk exceeds userdata size");
     if(type==0xcac1){long payload=total-chunkHdr;if(payload!=bytes)throw new IOException("Bad RAW sparse chunk");o.seek(pos);long left=bytes;while(left>0){int n=(int)Math.min((long)buf.length,left);in.readFully(buf,0,n);o.write(buf,0,n);left-=n;}}
     else if(type==0xcac2){if(total-chunkHdr!=4)throw new IOException("Bad FILL sparse chunk");byte[] f=new byte[4];in.readFully(f);o.seek(pos);long left=bytes;while(left>0){int n=(int)Math.min((long)buf.length,left);for(int x=0;x<n;x++)buf[x]=f[x&3];o.write(buf,0,n);left-=n;}}
     else if(type==0xcac3){if(total!=chunkHdr)throw new IOException("Bad DONT_CARE sparse chunk");}
     else if(type==0xcac4){long extra=total-chunkHdr;if(extra>0)in.seek(in.getFilePointer()+extra);bytes=0;}
     else throw new IOException("Unknown Android sparse chunk type 0x"+Integer.toHexString(type));
     pos+=bytes;
    }
    if(pos!=outBytes)throw new IOException("Sparse userdata expanded bytes mismatch: "+pos+" != "+outBytes);
   }finally{o.close();}
   if(out.exists()&&!out.delete())throw new IOException("Cannot replace "+out);if(!tmp.renameTo(out))throw new IOException("Cannot install "+out);
   FileWriter fw=new FileWriter(stamp,false);fw.write(want+"\n");fw.close();return out;
  }finally{in.close();}
 }

 private static String userdataFilesystemReport(File f){
  RandomAccessFile r=null;
  try{
   r=new RandomAccessFile(f,"r");
   StringBuilder b=new StringBuilder();
   b.append("USERDATA FS file=").append(f.getName()).append(" bytes=").append(f.length());
   if(f.length()>=1082){r.seek(1080);int lo=r.read();int hi=r.read();int magic=(lo&255)|((hi&255)<<8);b.append(" ext4Magic=0x").append(Integer.toHexString(magic));if(magic==0xef53)b.append(" EXT4");else b.append(" NOT_EXT4");}
   if(f.length()>=1024){r.seek(1024);byte[] sb=new byte[64];r.readFully(sb);long blocks=le32(sb,4);long logBlock=le32(sb,24);long blockSize=1024L<<logBlock;b.append(" extBlocks=").append(blocks).append(" extBlockSize=").append(blockSize).append(" extBytes=").append(blocks*blockSize);}
   return b.toString();
  }catch(Exception e){return "USERDATA FS check failed: "+e.toString();}finally{if(r!=null)try{r.close();}catch(Exception e){}}
 }
 private static String userdataFormatReport(File f){RandomAccessFile r=null;try{r=new RandomAccessFile(f,"r");if(r.length()<28)return "plain/raw (too small for sparse header)";byte[] h=new byte[28];r.readFully(h);if(le32(h,0)!=0xed26ff3aL)return "plain/raw";long blk=le32(h,12),blks=le32(h,16),chunks=le32(h,20);return "Android sparse: blockSize="+blk+" totalBlocks="+blks+" chunks="+chunks+" expandedBytes="+(blk*blks);}catch(Exception e){return "check failed: "+e;}finally{if(r!=null)try{r.close();}catch(Exception e){}}}
 public static File ensureSuperGptImage(Context c)throws IOException{
  File d=GuestPackageManager.getGuestDir(c);
  File source=new File(d,"system.img");
  if(!source.isFile())throw new IOException("system.img missing");
  File f=new File(d,"super-gpt.img");
  long payload=source.length();
  long first=2048L;
  long payloadSectors=(payload+511L)/512L;
  long sectors=first+payloadSectors+34L;
  long bytes=sectors*512L;
  if(!isStage20SuperGpt(f,bytes)){
   createSuperGpt(f,source,sectors,first,payloadSectors);
  }
  return f;
 }
 private static boolean isStage20SuperGpt(File f,long wanted){
  if(!f.isFile()||f.length()!=wanted)return false;
  RandomAccessFile r=null;
  try{
   r=new RandomAccessFile(f,"r");
   byte[] sig=new byte[8];r.seek(512);r.readFully(sig);
   if(!"EFI PART".equals(new String(sig,"US-ASCII")))return false;
   byte[] n=new byte[10];r.seek(1080);r.readFully(n);
   return n[0]=='s'&&n[2]=='u'&&n[4]=='p'&&n[6]=='e'&&n[8]=='r';
  }catch(Exception e){return false;}finally{if(r!=null)try{r.close();}catch(Exception e){}}
 }
 private static void createSuperGpt(File f,File source,long sectors,long first,long payloadSectors)throws IOException{
  RandomAccessFile r=new RandomAccessFile(f,"rw");
  FileInputStream in=null;
  try{
   r.setLength(sectors*512L);
   byte[] m=new byte[512];m[450]=(byte)0xEE;put32(m,454,1);put32(m,458,Math.min(0xffffffffL,sectors-1));m[510]=0x55;m[511]=(byte)0xAA;r.seek(0);r.write(m);
   byte[] e=new byte[16384];
   byte[] t=new byte[]{(byte)0xaf,(byte)0x3d,(byte)0xc6,(byte)0x0f,(byte)0x83,(byte)0x84,(byte)0x72,(byte)0x47,(byte)0x8e,(byte)0x79,(byte)0x3d,(byte)0x69,(byte)0xd8,(byte)0x47,(byte)0x7d,(byte)0xe4};
   System.arraycopy(t,0,e,0,16);
   guid(e,16,0x2233445566778899L,0xAABBCCDDEEFF1020L);
   put64(e,32,first);put64(e,40,first+payloadSectors-1L);
   String name="super";for(int i=0;i<name.length();i++)put16(e,56+i*2,name.charAt(i));
   long ec=crc(e,0,e.length);r.seek(1024);r.write(e);
   byte[] h=new byte[512];byte[] es="EFI PART".getBytes("US-ASCII");System.arraycopy(es,0,h,0,8);
   put32(h,8,0x10000);put32(h,12,92);put64(h,24,1);put64(h,32,sectors-1);put64(h,40,34);put64(h,48,sectors-34);
   guid(h,56,0xB1C2D3E4F5061728L,0x293A4B5C6D7E8F90L);put64(h,72,2);put32(h,80,128);put32(h,84,128);put32(h,88,ec);put32(h,16,crc(h,0,92));r.seek(512);r.write(h);
   in=new FileInputStream(source);r.seek(first*512L);byte[] buf=new byte[1024*1024];int n;while((n=in.read(buf))>0)r.write(buf,0,n);
   r.seek((sectors-33)*512L);r.write(e);byte[] bh=(byte[])h.clone();put32(bh,16,0);put64(bh,24,sectors-1);put64(bh,32,1);put64(bh,72,sectors-33);put32(bh,16,crc(bh,0,92));r.seek((sectors-1)*512L);r.write(bh);
  }finally{if(in!=null)try{in.close();}catch(Exception e){}r.close();}
 }
 public static File ensureDataDir(Context c)throws IOException{
  File d=getDataDir(c);if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create QEMU data directory: "+d);
  copyAssetIfNeeded(c,"qemu/efi-virtio.rom",new File(d,"efi-virtio.rom"));
  return d;
 }
 private static void copyAssetIfNeeded(Context c,String asset,File out)throws IOException{
  if(out.isFile()&&out.length()>0)return;
  InputStream in=c.getAssets().open(asset);File tmp=new File(out.getParentFile(),out.getName()+".tmp");
  FileOutputStream fos=new FileOutputStream(tmp);byte[] b=new byte[32768];int n;
  try{while((n=in.read(b))!=-1)fos.write(b,0,n);fos.flush();}finally{try{in.close();}catch(Exception e){}try{fos.close();}catch(Exception e){}}
  if(out.exists()&&!out.delete())throw new IOException("Cannot replace "+out);
  if(!tmp.renameTo(out))throw new IOException("Cannot install "+out);
 }
 private static ArrayList<String> prefix(Context c){ArrayList<String>a=new ArrayList<String>();a.add("/system/bin/linker64");a.add(getEngine(c).getAbsolutePath());return a;}
 private static ProcessBuilder builder(Context c,List<String> cmd)throws IOException{ProcessBuilder pb=new ProcessBuilder(cmd);pb.redirectErrorStream(true);pb.directory(c.getFilesDir());String libs=isPackaged(c)?c.getApplicationInfo().nativeLibraryDir:getImportedLibDir(c).getAbsolutePath();pb.environment().put("LD_LIBRARY_PATH",libs);File share=isPackaged(c)?ensureDataDir(c):new File(new File(c.getFilesDir(),"qemu-engine"),"share/qemu");if(share.isDirectory())pb.environment().put("QEMU_DATADIR",share.getAbsolutePath());return pb;}
 public static void selfTest(Context c,Logger l)throws Exception{if(!isPresent(c))throw new FileNotFoundException("QEMU engine missing");ArrayList<String>a=prefix(c);a.add("--version");l.log("SELFTEST expected: "+EXPECTED_VERSION);l.log("SELFTEST command: "+joinArgs(a));Process p=builder(c,a).start();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String s;StringBuilder out=new StringBuilder();while((s=r.readLine())!=null){out.append(s).append('\n');l.log("SELFTEST: "+s);}int rc=p.waitFor();l.log("SELFTEST exit code = "+rc);if(rc!=0)throw new IOException("QEMU self-test failed rc="+rc);if(out.indexOf(EXPECTED_VERSION)<0)throw new IOException("QEMU self-test returned rc=0 but expected version string was not found");l.log("SELFTEST VERIFIED: "+EXPECTED_VERSION+"; rc=0");}
 private static String joinArgs(List<String> a){StringBuilder b=new StringBuilder();for(int i=0;i<a.size();i++){if(i>0)b.append(" ");String x=a.get(i);if(x==null)x="";if(x.indexOf(' ')>=0||x.indexOf('\t')>=0){b.append('"').append(x).append('"');}else b.append(x);}return b.toString();}
 private static long le32(byte[] b,int o){return ((long)b[o]&255L)|(((long)b[o+1]&255L)<<8)|(((long)b[o+2]&255L)<<16)|(((long)b[o+3]&255L)<<24);}
 private static long le64(byte[] b,int o){return le32(b,o)|(le32(b,o+4)<<32);}
 private static String hex2(int v){String h=Integer.toHexString(v&255);return h.length()<2?"0"+h:h;}
 private static String partitionTableReport(File f){
  StringBuilder out=new StringBuilder();RandomAccessFile r=null;
  try{
   r=new RandomAccessFile(f,"r");out.append("PARTSCAN file=").append(f.getName()).append(" size=").append(f.length()).append('\n');
   if(f.length()<1024){out.append("PARTSCAN too small\n");return out.toString();}
   byte[] m=new byte[512];r.seek(0);r.readFully(m);out.append("PARTSCAN MBR signature=").append((m[510]&255)==0x55&&(m[511]&255)==0xaa?"55aa":"missing").append('\n');
   for(int i=0;i<4;i++){int o=446+i*16;int type=m[o+4]&255;long first=le32(m,o+8);long count=le32(m,o+12);if(type!=0||count!=0)out.append("PARTSCAN MBR p").append(i+1).append(" type=0x").append(hex2(type)).append(" firstLBA=").append(first).append(" sectors=").append(count).append('\n');}
   byte[] h=new byte[512];r.seek(512);r.readFully(h);String sig=new String(h,0,8,"US-ASCII");
   if("EFI PART".equals(sig)){long entries=le64(h,72);long n=le32(h,80);long es=le32(h,84);out.append("PARTSCAN GPT entriesLBA=").append(entries).append(" count=").append(n).append(" entrySize=").append(es).append('\n');int limit=(int)Math.min(n,16L);if(es>=128&&es<=1024){byte[] e=new byte[(int)es];for(int i=0;i<limit;i++){r.seek(entries*512L+(long)i*es);r.readFully(e);boolean empty=true;for(int j=0;j<16;j++)if(e[j]!=0){empty=false;break;}if(empty)continue;long first=le64(e,32),last=le64(e,40);StringBuilder name=new StringBuilder();for(int j=56;j+1<e.length&&j<128;j+=2){int ch=(e[j]&255)|((e[j+1]&255)<<8);if(ch==0)break;if(ch>=32&&ch<127)name.append((char)ch);else name.append('?');}out.append("PARTSCAN GPT p").append(i+1).append(" name=").append(name).append(" firstLBA=").append(first).append(" lastLBA=").append(last).append('\n');}}}
   else out.append("PARTSCAN GPT signature=absent\n");
  }catch(Exception e){out.append("PARTSCAN error=").append(e.toString()).append('\n');}finally{if(r!=null)try{r.close();}catch(Exception e){}}
  return out.toString();
 }
 public static boolean userdataPrepFormatted(Context c){
  try{File f=ensureRawUserdata(c);if(!f.isFile()||f.length()<1052672L)return false;RandomAccessFile r=new RandomAccessFile(f,"r");try{r.seek(1048576L+1024L+56L);int a=r.read();int b=r.read();return a==0x53&&b==0xEF;}finally{r.close();}}catch(Exception e){return false;}
 }
 private static File userdataPreparedFlagFile(Context c){return new File(GuestPackageManager.getGuestDir(c),"userdata-stage40-prepared.flag");}
 public static boolean userdataPreparedFlag(Context c){return userdataPreparedFlagFile(c).isFile();}
 private static void markUserdataPrepared(Context c)throws IOException{File f=userdataPreparedFlagFile(c);FileOutputStream o=new FileOutputStream(f,false);try{o.write("stage39-clean-ext4-prepared\n".getBytes("UTF-8"));o.flush();}finally{o.close();}}
 public static List<String> buildHeadlessCommand(Context c){File d=GuestPackageManager.getGuestDir(c);File prep;try{ensureMetadataImage(c);prep=ensureRawUserdata(c);}catch(IOException e){throw new RuntimeException(e);}boolean formatted=userdataPrepFormatted(c)||userdataPreparedFlag(c);File k=new File(d,"kernel-ranchu");if(!k.exists())k=new File(d,"kernel");ArrayList<String>a=prefix(c);a.add("-L");a.add(getDataDir(c).getAbsolutePath());a.add("-accel");a.add("tcg,thread=multi,tb-size=1024");a.add("-machine");a.add("virt");a.add("-device");a.add("virtio-rng-pci");a.add("-device");a.add("virtio-gpu-device,bus=virtio-mmio-bus.27");a.add("-cpu");a.add("cortex-a57");a.add("-smp");a.add("4");a.add("-m");a.add("3072");a.add("-kernel");a.add(k.getAbsolutePath());a.add("-initrd");a.add(new File(d,"ramdisk.img").getAbsolutePath());a.add("-drive");a.add("file="+new File(d,"system.img").getAbsolutePath()+",if=none,id=systemdisk,format=raw,readonly=on");a.add("-device");a.add("virtio-blk-device,drive=systemdisk,serial=system,bus=virtio-mmio-bus.28");if(!formatted){a.add("-drive");a.add("file="+prep.getAbsolutePath()+",if=none,id=metadatadisk,format=raw");a.add("-device");a.add("virtio-blk-device,drive=metadatadisk,serial=metadata,bus=virtio-mmio-bus.30");}else{a.add("-drive");a.add("file="+new File(d,"metadata.img").getAbsolutePath()+",if=none,id=metadatadisk,format=raw");a.add("-device");a.add("virtio-blk-device,drive=metadatadisk,serial=metadata,bus=virtio-mmio-bus.30");a.add("-blockdev");a.add("driver=file,filename="+prep.getAbsolutePath()+",node-name=udfile,discard=ignore,cache.direct=off,cache.no-flush=off");a.add("-blockdev");a.add("driver=raw,file=udfile,node-name=udraw,offset=1048576,size=2146418176");a.add("-device");a.add("virtio-blk-device,drive=udraw,serial=userdata,bus=virtio-mmio-bus.31");}a.add("-drive");a.add("file="+new File(d,"vendor.img").getAbsolutePath()+",if=none,id=vendordisk,format=raw,readonly=on");a.add("-device");a.add("virtio-blk-pci,drive=vendordisk,serial=vendor");a.add("-append");a.add("console=ttyAMA0 loglevel=4 audit=0 androidboot.hardware=ranchu androidboot.logcat=*:V androidboot.boot_devices=a003e00.virtio_mmio,a003c00.virtio_mmio,a003a00.virtio_mmio,a003800.virtio_mmio androidboot.selinux=permissive androidboot.force_normal_boot=1 androidboot.zygote=zygote64 ro.zygote=zygote64 androidboot.native_bridge=0 androidboot.debug.hwui.renderer=skiagl androidboot.debug.sf.nobootanimation=1");a.add("-nographic");a.add("-no-reboot");return a;}
 public static String commandForLog(Context c){return joinArgs(buildHeadlessCommand(c));}
 public static String engineReport(Context c){File f=getEngine(c);StringBuilder b=new StringBuilder();b.append("Host ABI = ").append(Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"unknown").append('\n');b.append("nativeLibraryDir = ").append(c.getApplicationInfo().nativeLibraryDir).append('\n');b.append("Engine path = ").append(f.getAbsolutePath()).append('\n');b.append("Engine present = ").append(f.isFile()).append('\n');b.append("Engine APK-packaged = ").append(isPackaged(c)).append('\n');b.append("Launch mode = /system/bin/linker64 + native library directory\n");if(f.isFile()){b.append("Engine size = ").append(f.length()).append(" bytes\n");try{b.append("Engine ELF = ").append(EngineImporter.elfReport(f)).append('\n');}catch(Exception e){b.append("Engine ELF check failed = ").append(e).append('\n');}}b.append("Acceleration = TCG; /dev/kvm unavailable\n");return b.toString();}
 private static boolean metadataExt4Formatted(Context c){RandomAccessFile r=null;try{File f=ensureMetadataImage(c);long off=1048576L+1024L+56L;r=new RandomAccessFile(f,"r");r.seek(off);int m=r.read()|(r.read()<<8);return m==0xEF53;}catch(Throwable x){return false;}finally{if(r!=null)try{r.close();}catch(Throwable x){}}}
 private static List<String> buildMetadataPrepCommand(Context c){List<String> a=buildHeadlessCommand(c);File d=GuestPackageManager.getGuestDir(c);File prep=ensureRawUserdataUnchecked(c);File meta=new File(d,"metadata.img");for(int i=0;i<a.size();i++){String v=a.get(i);if(v.indexOf(prep.getAbsolutePath())>=0)a.set(i,v.replace(prep.getAbsolutePath(),meta.getAbsolutePath()));}return a;}
 private static File ensureRawUserdataUnchecked(Context c){try{return ensureRawUserdata(c);}catch(IOException e){throw new RuntimeException(e);}}
 private static int launchOnceTarget(final Context c,final Logger l,final boolean stopAfterMkfs,final File watchFile,final List<String> cmd)throws Exception{
  final Process p=builder(c,cmd).start(); final boolean[] watchdogStopped=new boolean[]{false}; Thread watchdog=null;
  if(stopAfterMkfs){watchdog=new Thread(new Runnable(){public void run(){try{long magicOff=1048576L+1024L+56L;while(true){try{if(p.exitValue()>=0)return;}catch(IllegalThreadStateException alive){} RandomAccessFile rr=null;int magic=0;try{rr=new RandomAccessFile(watchFile,"r");if(rr.length()>magicOff+2){rr.seek(magicOff);magic=rr.read()|(rr.read()<<8);}}catch(Throwable x){}finally{if(rr!=null)try{rr.close();}catch(Throwable x){}} if(magic==0xEF53){l.log("STAGE55 WATCHDOG: EXT4 detected in "+watchFile.getName()+"; allowing 2500ms final writes");Thread.sleep(2500L);watchdogStopped[0]=true;l.log("STAGE55 WATCHDOG: stopping formatting boot cleanly");p.destroy();return;}Thread.sleep(100L);}}catch(Throwable x){l.log("STAGE55 WATCHDOG warning: "+x.toString());}}});watchdog.start();}
  BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;try{while((line=r.readLine())!=null){if(line.indexOf("process with updatable components 'goldfish-logcat' exited 4 times")>=0||line.indexOf("Native process 'goldfish-logcat' is crashing")>=0||line.indexOf("process with updatable components 'vendor.hwcomposer-2-4' exited 4 times")>=0){l.log("STAGE55 COMPAT: ignored known ranchu service crash-loop: "+line);}else{l.log("QEMU: "+line);}}}catch(java.io.InterruptedIOException x){if(watchdogStopped[0])l.log("STAGE55 WATCHDOG: expected serial close");else throw x;}catch(IOException x){if(watchdogStopped[0])l.log("STAGE55 WATCHDOG: expected stream close");else throw x;}int rc=p.waitFor();if(watchdog!=null)try{watchdog.join(1000L);}catch(Throwable x){}l.log("QEMU exited rc="+rc+(watchdogStopped[0]?" (intentional format stop)":""));return rc;
 }
 private static int launchOnce(final Context c,final Logger l,final boolean stopAfterMkfs)throws Exception{
  final Process p=builder(c,buildHeadlessCommand(c)).start();
  final boolean[] watchdogStopped=new boolean[]{false};
  Thread watchdog=null;
  if(stopAfterMkfs){
   watchdog=new Thread(new Runnable(){public void run(){
    try{
     File prep=ensureRawUserdata(c);long magicOff=1048576L+1024L+56L;
     while(true){
      try{if(p.exitValue()>=0)return;}catch(IllegalThreadStateException alive){}
      RandomAccessFile rr=null;int magic=0;try{rr=new RandomAccessFile(prep,"r");if(rr.length()>magicOff+2){rr.seek(magicOff);magic=rr.read()|(rr.read()<<8);}}catch(Throwable x){}finally{if(rr!=null)try{rr.close();}catch(Throwable x){}}
      if(magic==0xEF53){
       l.log("STAGE55 WATCHDOG: EXT4 superblock detected; allowing 2500ms for mke2fs final writes");
       Thread.sleep(2500L);watchdogStopped[0]=true;l.log("STAGE55 WATCHDOG: stopping phase 1 before temporary /metadata can be mounted or modified");p.destroy();return;
      }
      Thread.sleep(100L);
     }
    }catch(Throwable x){l.log("STAGE55 WATCHDOG warning: "+x.toString());}
   }});watchdog.start();
  }
  Thread frameworkDiag=null;
  if(!stopAfterMkfs){frameworkDiag=new Thread(new Runnable(){public void run(){
   try{
    final OutputStream out=p.getOutputStream();
    Thread.sleep(120000L);
    try{p.exitValue();return;}catch(IllegalThreadStateException alive){}
    String bypass="echo STAGE55_BYPASS_BEGIN\ngetprop ro.zygote\ngetprop init.svc.zygote_secondary\nstop zygote_secondary\nstop goldfish-logcat\nsleep 2\ngetprop init.svc.zygote_secondary\ngetprop init.svc.goldfish-logcat\necho STAGE55_BYPASS_END\n";
    synchronized(out){out.write(bypass.getBytes("UTF-8"));out.flush();}
    l.log("STAGE55 COMPAT: requested stop of impossible 32-bit zygote_secondary and nonessential goldfish-logcat at 120 seconds");
    int[] waits=new int[]{60,240,360};int elapsed=120;
    for(int i=0;i<waits.length;i++){Thread.sleep(waits[i]*1000L);elapsed+=waits[i];try{p.exitValue();return;}catch(IllegalThreadStateException alive){}String tag="STAGE55_DIAG_"+elapsed+"S";String commands="echo "+tag+"\ngetprop ro.zygote\ngetprop sys.boot_completed\ngetprop init.svc.zygote\ngetprop init.svc.zygote_secondary\ngetprop init.svc.surfaceflinger\ngetprop init.svc.goldfish-logcat\nps -A | grep -E 'zygote|system_server|surfaceflinger'\nls -ld /data/dalvik-cache /data/dalvik-cache/arm64 2>/dev/null\necho "+tag+"_END\n";synchronized(out){out.write(commands.getBytes("UTF-8"));out.flush();}l.log("STAGE55 DIAGNOSTIC: injected guest framework snapshot at "+elapsed+" seconds");}
   }catch(Throwable x){l.log("STAGE55 DIAGNOSTIC warning: "+x.toString());}
  }});frameworkDiag.start();}
  BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;
  try{while((line=r.readLine())!=null){l.log("QEMU: "+line);if(line.indexOf("zygote")>=0||line.indexOf("system_server")>=0||line.indexOf("SurfaceFlinger")>=0||line.indexOf("sys.boot_completed")>=0||line.indexOf("BootAnimation")>=0){l.log("STAGE55 FRAMEWORK PROBE: "+line);} if(line.indexOf("keystore2")>=0||line.indexOf("vold:")>=0||line.indexOf("Exec service is hung")>=0||line.indexOf("apexd:")>=0||line.indexOf("dalvik-cache")>=0){l.log("STAGE55 DATA/VOLD PROBE: "+line);}}}catch(java.io.InterruptedIOException x){if(watchdogStopped[0]){l.log("STAGE55 WATCHDOG: serial pipe closed as expected after intentional QEMU stop");}else{throw x;}}catch(IOException x){if(watchdogStopped[0]){l.log("STAGE55 WATCHDOG: serial stream closed as expected: "+x.toString());}else{throw x;}}
  int rc=p.waitFor();if(watchdog!=null)try{watchdog.join(1000L);}catch(Throwable x){}
  if(frameworkDiag!=null)frameworkDiag.interrupt();
  l.log("QEMU exited rc="+rc+(watchdogStopped[0]?" (intentional clean-ext4 watchdog stop)":""));return rc;
 }
 public static void launch(final Context c,final Logger l)throws Exception{
  if(!isPresent(c))throw new FileNotFoundException("QEMU engine missing");
  File gd=GuestPackageManager.getGuestDir(c);
  if(!metadataExt4Formatted(c)){
   l.log("STAGE55 METADATA PREP: metadata EXT4 missing; formatting it in an isolated guest pass");
   File meta=ensureMetadataImage(c); launchOnceTarget(c,l,true,meta,buildMetadataPrepCommand(c));
   l.log("STAGE55 METADATA PREP result ext4="+metadataExt4Formatted(c));
   if(!metadataExt4Formatted(c)){l.log("STAGE55 STOP: metadata format did not complete");return;}
  }
  boolean prepared=userdataPreparedFlag(c); boolean before=userdataPrepFormatted(c);
  l.log("STAGE55 userdata state: preparedFlag="+prepared+" ext4Magic="+before);
  if(!prepared){File prep=ensureRawUserdata(c);launchOnceTarget(c,l,true,prep,buildHeadlessCommand(c));boolean after=userdataPrepFormatted(c);l.log("STAGE55 userdata prep result ext4="+after);if(!after){l.log("STAGE55 STOP: userdata format did not complete");return;}markUserdataPrepared(c);l.log("STAGE55 USERDATA PREP SUCCESS: persistent flag written");}
  l.log("STAGE55 FINAL BOOT: preformatted metadata + clean userdata");int rc=launchOnce(c,l,false);l.log("STAGE55 FINAL BOOT EXIT rc="+rc);
 }
 public static void nativeLibraryPreflight(Context c,Logger l)throws Exception{if(!isPackaged(c)){l.log("PREFLIGHT: imported legacy engine selected; packaged engine not found");return;}String[] names=new String[]{"libqemu-system-aarch64.so","libpixman-1.so","libgio-2.0.so","libgobject-2.0.so","libglib-2.0.so","libintl.so","libgmodule-2.0.so","libffi.so","libiconv.so"};File nd=new File(c.getApplicationInfo().nativeLibraryDir);l.log("PREFLIGHT nativeLibraryDir = "+nd.getAbsolutePath());for(int i=0;i<names.length;i++){File f=new File(nd,names[i]);l.log("PREFLIGHT ["+(i+1)+"/"+names.length+"] "+names[i]+" present="+f.isFile()+" size="+(f.isFile()?f.length():0));if(!f.isFile())throw new FileNotFoundException("Packaged native file missing: "+names[i]);}File data=ensureDataDir(c);File rom=new File(data,"efi-virtio.rom");l.log("PREFLIGHT QEMU data dir = "+data.getAbsolutePath());l.log("PREFLIGHT efi-virtio.rom present="+rom.isFile()+" size="+(rom.isFile()?rom.length():0));if(!rom.isFile())throw new FileNotFoundException("efi-virtio.rom missing");File gd=GuestPackageManager.getGuestDir(c);File sys=new File(gd,"system.img");File ven=new File(gd,"vendor.img");File usr=new File(gd,"userdata.img");File meta=ensureMetadataImage(c);l.log("PREFLIGHT Stage 55 clean EXT4 handoff + long framework observation");l.log("PREFLIGHT system.img size="+sys.length());l.log("PREFLIGHT vendor.img size="+ven.length());l.log("PREFLIGHT userdata.img size="+usr.length()+" format="+userdataFormatReport(usr));File rawUsr=ensureRawUserdata(c);l.log("PREFLIGHT userdata backing file="+rawUsr.getName()+" size="+rawUsr.length());l.log("PREFLIGHT userdata prep formatted="+userdataPrepFormatted(c)+" (ext4 magic checked at GPT partition offset)");l.log("PREFLIGHT metadata.img size="+meta.length()+" GPT partition name=metadata");l.log("PREFLIGHT layout: system=MMIO.28 metadata=MMIO.30 userdata=MMIO.31 vendor=PCI");l.log("PREFLIGHT Stage 55: stop impossible 32-bit zygote_secondary + goldfish-logcat at 120s; framework snapshots at 180s/420s/780s; storage unchanged");}

 private static File sparseGptDisk(Context c,String srcName,String outName,String partName)throws IOException{
  File d=GuestPackageManager.getGuestDir(c);File src=new File(d,srcName);if(!src.isFile())throw new FileNotFoundException(srcName+" missing");
  RandomAccessFile in=new RandomAccessFile(src,"r");try{byte[] sh=new byte[28];in.readFully(sh);if(le32(sh,0)!=0xed26ff3aL)throw new IOException(srcName+" is not Android sparse");int fh=(int)((sh[8]&255)|((sh[9]&255)<<8));int chs=(int)((sh[10]&255)|((sh[11]&255)<<8));long blk=le32(sh,12),blocks=le32(sh,16),chunks=le32(sh,20),payload=blk*blocks;long first=2048L,psecs=(payload+511L)/512L,sectors=first+psecs+34L;sectors=((sectors+7L)/8L)*8L;long bytes=sectors*512L;File out=new File(d,outName);File stamp=new File(d,outName+".info");String want=src.length()+":"+src.lastModified()+":"+payload+":"+partName+":align4096";if(out.isFile()&&out.length()==bytes&&stamp.isFile()){BufferedReader br=new BufferedReader(new FileReader(stamp));String got=br.readLine();br.close();if(want.equals(got))return out;}File tmp=new File(d,outName+".tmp");if(tmp.exists())tmp.delete();RandomAccessFile o=new RandomAccessFile(tmp,"rw");try{o.setLength(bytes);writeSingleGpt(o,sectors,first,psecs,partName);in.seek(fh);long pos=first*512L;byte[] h=new byte[chs];byte[] buf=new byte[1024*1024];for(long i=0;i<chunks;i++){in.readFully(h);int type=(h[0]&255)|((h[1]&255)<<8);long nbl=le32(h,4),total=le32(h,8),nbytes=nbl*blk;if(type==0xcac1){if(total-chs!=nbytes)throw new IOException("Bad RAW sparse chunk");o.seek(pos);long left=nbytes;while(left>0){int n=(int)Math.min((long)buf.length,left);in.readFully(buf,0,n);o.write(buf,0,n);left-=n;}}else if(type==0xcac2){if(total-chs!=4)throw new IOException("Bad FILL sparse chunk");byte[] f=new byte[4];in.readFully(f);boolean zero=f[0]==0&&f[1]==0&&f[2]==0&&f[3]==0;if(!zero){o.seek(pos);long left=nbytes;while(left>0){int n=(int)Math.min((long)buf.length,left);for(int x=0;x<n;x++)buf[x]=f[x&3];o.write(buf,0,n);left-=n;}}}else if(type==0xcac3){if(total!=chs)throw new IOException("Bad DONT_CARE sparse chunk");}else if(type==0xcac4){long extra=total-chs;if(extra>0)in.seek(in.getFilePointer()+extra);nbytes=0;}else throw new IOException("Unknown sparse chunk 0x"+Integer.toHexString(type));pos+=nbytes;}}finally{o.close();}if(out.exists()&&!out.delete())throw new IOException("Cannot replace "+out);if(!tmp.renameTo(out))throw new IOException("Cannot install "+out);FileWriter fw=new FileWriter(stamp,false);fw.write(want+"\n");fw.close();return out;}finally{in.close();}
 }
 private static void writeSingleGpt(RandomAccessFile r,long sectors,long first,long payloadSectors,String name)throws IOException{byte[] m=new byte[512];m[450]=(byte)0xEE;put32(m,454,1);put32(m,458,Math.min(0xffffffffL,sectors-1));m[510]=0x55;m[511]=(byte)0xAA;r.seek(0);r.write(m);byte[] e=new byte[16384];byte[] t=new byte[]{(byte)0xaf,(byte)0x3d,(byte)0xc6,(byte)0x0f,(byte)0x83,(byte)0x84,(byte)0x72,(byte)0x47,(byte)0x8e,(byte)0x79,(byte)0x3d,(byte)0x69,(byte)0xd8,(byte)0x47,(byte)0x7d,(byte)0xe4};System.arraycopy(t,0,e,0,16);guid(e,16,0x33445566778899AAL,0xBBCCDDEEFF102031L);put64(e,32,first);put64(e,40,first+payloadSectors-1);for(int i=0;i<name.length()&&i<36;i++)put16(e,56+i*2,name.charAt(i));long ec=crc(e,0,e.length);r.seek(1024);r.write(e);byte[] h=new byte[512];byte[] es="EFI PART".getBytes("US-ASCII");System.arraycopy(es,0,h,0,8);put32(h,8,0x10000);put32(h,12,92);put64(h,24,1);put64(h,32,sectors-1);put64(h,40,34);put64(h,48,sectors-34);guid(h,56,0xC1D2E3F405162738L,0x394A5B6C7D8E9FA0L);put64(h,72,2);put32(h,80,128);put32(h,84,128);put32(h,88,ec);put32(h,16,crc(h,0,92));r.seek(512);r.write(h);r.seek((sectors-33)*512L);r.write(e);byte[] bh=(byte[])h.clone();put32(bh,16,0);put64(bh,24,sectors-1);put64(bh,32,1);put64(bh,72,sectors-33);put32(bh,16,crc(bh,0,92));r.seek((sectors-1)*512L);r.write(bh);}
 public static void nativeLibraryPreflightCuttlefish(Context c,Logger l)throws Exception{
  if(!isPresent(c))throw new FileNotFoundException("QEMU engine missing");File d=GuestPackageManager.getGuestDir(c);l.log("PREFLIGHT Stage 68 vendor_boot table + real fstab diagnostics");l.log(CuttlefishBoot.report(d));File sd=sparseGptDisk(c,"super.img","stage58-super-gpt.img","super");File ud=sparseGptDisk(c,"userdata.img","stage58-userdata-gpt.img","userdata");File md=ensureMetadataImage(c);l.log("STAGE58 super GPT bytes="+sd.length());l.log(partitionTableReport(sd));l.log("STAGE58 userdata GPT bytes="+ud.length());l.log(partitionTableReport(ud));l.log("STAGE58 metadata GPT bytes="+md.length());l.log("PREFLIGHT: Android init reached in Stage57; now attach real dynamic super + userdata + metadata on PCI VirtIO");
 }
 public static List<String> buildCuttlefishProbeCommand(Context c)throws Exception{
  File d=GuestPackageManager.getGuestDir(c);File k=CuttlefishBoot.extractKernel(d);File initrd=CuttlefishBoot.withBootconfig(d);File sd=sparseGptDisk(c,"super.img","stage58-super-gpt.img","super");File ud=sparseGptDisk(c,"userdata.img","stage58-userdata-gpt.img","userdata");File md=ensureMetadataImage(c);File mi=ensureMiscImage(c);ArrayList<String>a=prefix(c);a.add("-L");a.add(getDataDir(c).getAbsolutePath());a.add("-accel");a.add("tcg,thread=multi,tb-size=1024");a.add("-machine");a.add("virt,gic-version=2");a.add("-cpu");a.add("cortex-a57");a.add("-smp");a.add("4");a.add("-m");a.add("3072");a.add("-kernel");a.add(k.getAbsolutePath());a.add("-initrd");a.add(initrd.getAbsolutePath());
  String[] files=new String[]{sd.getAbsolutePath(),ud.getAbsolutePath(),md.getAbsolutePath(),mi.getAbsolutePath()};for(int i=0;i<files.length;i++){a.add("-drive");a.add("file="+files[i]+",if=none,id=cfdrive"+i+",format=raw,cache=none,aio=threads");a.add("-device");a.add("virtio-blk-pci-non-transitional,drive=cfdrive"+i+",id=cfdisk"+i);}
  a.add("-device");a.add("virtio-gpu-pci,xres=720,yres=1280");a.add("-device");a.add("virtio-rng-pci-non-transitional");a.add("-append");a.add("console=ttyAMA0 earlycon=pl011,mmio32,0x09000000 panic=-1 loop.max_part=7 init=/init cma=0 firmware_class.path=/vendor/etc printk.devkmsg=on audit=0 androidboot.hardware.hwcomposer.display_finder_mode=drm androidboot.hardware.egl=angle androidboot.hardware.vulkan=pastel androidboot.hardware.guest_hwui_renderer=skiagl androidboot.opengles.version=196609 androidboot.cpuvulkan.version=4202496 androidboot.lcd_density=320 androidboot.setupwizard_mode=DISABLED");a.add("-nographic");a.add("-no-reboot");return a;
 }
 public static void launchCuttlefishProbe(Context c,Logger l)throws Exception{List<String>a=buildCuttlefishProbeCommand(c);l.log("STAGE68 COMMAND: "+joinArgs(a));final Process p=builder(c,a).start();Thread diag70=new Thread(new Runnable(){public void run(){try{OutputStream out=p.getOutputStream();int[] at=new int[]{300,420,600,900};int el=0;for(int i=0;i<at.length;i++){Thread.sleep((at[i]-el)*1000L);el=at[i];try{p.exitValue();return;}catch(IllegalThreadStateException alive){}String tag="STAGE70_"+el+"S";String cmd="echo TAG\n"+"getprop sys.boot_completed; getprop init.svc.zygote; getprop init.svc.surfaceflinger; getprop init.svc.bootanim\n"+"echo TAG_CRASHES\n"+"logcat -b crash -d | grep -E \"Abort message|>>> |signal 6\" | tail -40\n"+"echo TAG_SF\n"+"logcat -b all -d | grep -E \"SurfaceFlinger|surfaceflinger\" | tail -40\n"+"echo TAG_GFX\n"+"logcat -b all -d | grep -iE \"hwcomposer|drm_hwc|hwc|minigbm|gralloc|ANGLE|libEGL|vulkan|swiftshader|display\" | tail -50\n"+"echo TAG_END\n";cmd=cmd.replace("TAG",tag);synchronized(out){out.write(cmd.getBytes("UTF-8"));out.flush();}l.log("STAGE70 DIAGNOSTIC: injected guest snapshot at "+el+" seconds");}}catch(Throwable x){l.log("STAGE70 DIAGNOSTIC warning: "+x.toString());}}});diag70.setDaemon(true);diag70.start();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;long start=System.currentTimeMillis();while((line=r.readLine())!=null){l.log("QEMU: "+line);if(System.currentTimeMillis()-start>1800000L){l.log("STAGE68: 30 minute probe limit reached; stopping QEMU");p.destroy();break;}}int rc=p.waitFor();l.log("STAGE68 QEMU exit rc="+rc);}


}
