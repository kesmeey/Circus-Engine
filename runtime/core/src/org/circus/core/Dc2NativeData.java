package org.circus.core;

import java.io.*;
import java.util.*;

/** Read data tables from the user's PE as data only. No loading or native execution. */
public final class Dc2NativeData {
    // 496FF8: native music-room order, terminated by -1; unlike CG this menu has no unlock test.
    public static final int[] MUSIC={1,2,3,5,7,9,11,12,13,14,15,16,17,19,20,21,22,23,24,25,26,27,28,29,33,34,36,37,52,54,55,57,58,60,61,62,64,65,70,76,78,79,80,90,209,210,214,220,221,222,235,302,313,314,315,316,317,319,320,321,332,333,334,382,383,384,385,112,113,114,115,116,117};
    public final List<String> musicTitles=new ArrayList<>();
    public final Map<String,Integer> faces=new HashMap<>();
    public Dc2NativeData(CircusResources data,TextCodec codec)throws IOException{
        for(int id:MUSIC)musicTitles.add(data.named("music",id));
        for(String path:data.files.paths())if(path.equals("dc2dm.exe")||path.startsWith("dc2dm_")&&path.endsWith(".exe")){
            try{Pe pe=new Pe(data.files.read(path,4*1024*1024));List<String> titles=new ArrayList<>();
                for(int n=0;n<MUSIC.length;n++){int at=pe.at(0x496ff8+n*8);if(Binary.u32(pe.bytes,at)!=MUSIC[n])throw new IOException("Music table signature");titles.add(codec.apply(pe.string((int)Binary.u32(pe.bytes,at+4))));}
                for(int n=0;n<64;n++){int at=pe.at(0x48b4b0+n*8),id=(int)Binary.u32(pe.bytes,at);if(id==-1)break;if(id<0||id>100)throw new IOException("Face table signature");faces.put(codec.apply(pe.string((int)Binary.u32(pe.bytes,at+4))),Math.min(24,id));}
                musicTitles.clear();musicTitles.addAll(titles);break;
            }catch(IOException invalid){faces.clear();} // AdvData-only installs retain track resource names.
        }
    }
    static final class Pe {
        final byte[] bytes;final int base,sections,count;
        Pe(byte[] b)throws IOException{bytes=b;if(Binary.u16(b,0)!=0x5a4d)throw new IOException("PE DOS header");int p=(int)Binary.u32(b,60);if(Binary.u32(b,p)!=0x4550||Binary.u16(b,p+24)!=0x10b)throw new IOException("PE header");count=Binary.u16(b,p+6);if(count<1||count>32)throw new IOException("PE sections");base=(int)Binary.u32(b,p+52);sections=p+24+Binary.u16(b,p+20);Binary.range(sections,count*40,b.length);}
        int at(int va)throws IOException{long rva=(long)va-base;for(int n=0;n<count;n++){int s=sections+40*n;long start=Binary.u32(bytes,s+12),size=Binary.u32(bytes,s+16);if(rva>=start&&rva-start<size){long p=Binary.u32(bytes,s+20)+rva-start;Binary.range(p,8,bytes.length);return (int)p;}}throw new IOException("PE data address");}
        String string(int va)throws IOException{int start=at(va),end=start;while(end<bytes.length&&end-start<512&&bytes[end]!=0)end++;if(end==bytes.length||end-start==512)throw new IOException("PE string bound");return TextCodec.cp932(Arrays.copyOfRange(bytes,start,end));}
    }
}
