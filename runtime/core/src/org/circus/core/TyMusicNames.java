package org.circus.core;

import java.io.*;
import java.util.*;

/** TY40A470's48 fixed260-byte music-name cells, read from the selected PE as
 * data. They are not NAMES resource filenames or localized MES prose. */
public final class TyMusicNames {
    public static final int COUNT=48,STRIDE=260,ADDRESS=0x4feb98;
    private final byte[][] raw;
    private final String[] text;
    public final String executableSha256,tableSha256,encoding;
    private TyMusicNames(byte[] table,String encoding,String executableSha256)throws IOException{
        if(table.length!=COUNT*STRIDE)throw new IOException("TY music-name table extent");
        this.encoding=encoding;this.executableSha256=executableSha256;tableSha256=Binary.sha256(table);raw=new byte[COUNT][];text=new String[COUNT];TextCodec codec=new TextCodec("","",encoding);
        for(int n=0;n<COUNT;n++){int start=n*STRIDE,end=start;while(end<start+STRIDE&&table[end]!=0)end++;if(end==start+STRIDE)throw new IOException("Unterminated TY music-name cell");raw[n]=Arrays.copyOfRange(table,start,end);text[n]=codec.decode(raw[n]);}
    }
    static TyMusicNames table(byte[] table,String encoding)throws IOException{return new TyMusicNames(table,encoding,"");}
    public static TyMusicNames load(CircusResources data)throws IOException{
        if(!data.profileId.equals("dc2ty-5f69"))throw new IOException("TY music names require their independent profile");
        for(String path:data.files.executableCandidates()){
            byte[] bytes=data.files.read(path,4*1024*1024);String hash=Binary.sha256(bytes),encoding;
            if(hash.equals("615704413dd00960bb47f4d6c1b33dd635931f13085a9a25dd5782fd7dbdf505"))encoding="GBK";
            else if(hash.equals("70d118b8e7d79011d0e7443d488897ed03ae184ebc83db8abde3af007aab328e")||hash.equals("21988ca3227d494ad8fd816fd6363670b1a6245f82ac28314c4bb4e8353d19bd"))encoding="windows-31j";
            else {if(data.files.explicitExecutable())throw new IOException("所选 EXE 的 To You 曲名表尚未验证："+path);continue;}
            Dc2NativeData.Pe pe=new Dc2NativeData.Pe(bytes);int start=pe.at(ADDRESS);Binary.range(start,COUNT*STRIDE,bytes.length);return new TyMusicNames(Arrays.copyOfRange(bytes,start,start+COUNT*STRIDE),encoding,hash);
        }
        throw new IOException("To You 音乐模式需要本作 EXE 中的曲名数据。请在游戏配置中选择原版或已验证的汉化 EXE（仅读取数据，不执行）。");
    }
    public String text(int index)throws IOException{check(index);return text[index];}
    public byte[] raw(int index)throws IOException{check(index);return raw[index].clone();}
    private static void check(int index)throws IOException{if(index<0||index>=COUNT)throw new IOException("TY music-name index outside reviewed48 cells: "+index);}
    /** Native424240 chooses the menu font from ordinary3:30/31 and raw byte count. */
    public static int fontSize(int rawLength,int flag30,int flag31){return flag30!=0?(rawLength>18?14:16):flag31!=0?16:28;}
}
