package org.circus.core;

import java.io.*;

/** Current CHS NAMES global bytes, not a second parsed/normalized catalog.
 * Known BSS spans include their untouched suffix. Native count admission
 * belongs to41ABF0, never to raw addressing or this snapshot codec. */
public final class Pc1CatalogMemory {
    private static final int FORMAT=0x50314e31;
    public static final int CATEGORY=0x43da64,NAME=0x43da68,EXTENSION=0x43da70;
    public static final int MUSIC_COUNT=0xbc1d14,SOUND_COUNT=0xbc1d24,CG_COUNT=0xbb84c0,SCENARIO_COUNT=0xbb84c4,CRT_LOCALE=0xbc5e20;
    public enum Table {
        SYSTEM(0x971464,128,768),MUSIC(0xbb8578,192,9600),SOUND(0xbbaba8,132,26400),CG(0x9bb508,128,65536),SCENARIO(0x971778,188,192512);
        public final int address,stride,knownBytes;
        Table(int address,int stride,int knownBytes){this.address=address;this.stride=stride;this.knownBytes=knownBytes;}
    }
    private static final int[][] RANGES={{CATEGORY,20},{Table.SYSTEM.address,Table.SYSTEM.knownBytes},{Table.MUSIC.address,Table.MUSIC.knownBytes},{Table.SOUND.address,Table.SOUND.knownBytes},{Table.CG.address,Table.CG.knownBytes},{Table.SCENARIO.address,Table.SCENARIO.knownBytes},{MUSIC_COUNT,4},{SOUND_COUNT,4},{CG_COUNT,8},{CRT_LOCALE,4}};
    private final byte[][] raw=new byte[RANGES.length][];
    private final Pc1NativeMemory memory;
    private boolean active;
    public Pc1CatalogMemory(Pc1NativeMemory memory)throws IOException{
        if(memory==null)throw new IOException("PC1 NAMES canonical byte memory missing");this.memory=memory;
        for(int n=0;n<RANGES.length;n++){final byte[] bytes=raw[n]=new byte[RANGES[n][1]];memory.bind(RANGES[n][0],bytes.length,new Pc1NativeMemory.Region(){public int read(int at){return bytes[at]&255;}public void write(int at,int value){bytes[at]=(byte)value;}});}
        byte[] literals={'D','A','T',0,'n','a','m','e','s',0,0,0,'.','d','a','t',0,0,0,0};System.arraycopy(literals,0,raw[0],0,literals.length);
    }
    public int read(int address)throws IOException{return memory.readAddress(address);}
    public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}
    public int dword(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=read(address+n)<<(n*8);return value;}
    public void dword(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(n*8));}
    public int count(Table table)throws IOException{if(table==null)throw new IOException("PC1 NAMES table missing");switch(table){case SYSTEM:return 6;case MUSIC:return dword(MUSIC_COUNT);case SOUND:return dword(SOUND_COUNT);case CG:return dword(CG_COUNT);case SCENARIO:return dword(SCENARIO_COUNT);default:throw new IOException("PC1 NAMES table missing");}}
    public int address(Table table,int index,int offset)throws IOException{if(table==null)throw new IOException("PC1 NAMES table missing");return table.address+index*table.stride+offset;}
    /** Stride arithmetic is native signed/wrapped32-bit. Caller decides its
     *actual count check; neighboring known owners remain valid aliases. */
    public byte[] name(Table table,int index)throws IOException{return cstring(address(table,index,0));}
    public byte[] label(Table table,int index)throws IOException{if(table!=Table.MUSIC&&table!=Table.SCENARIO)throw new IOException("PC1 table has no reviewed label field");return cstring(address(table,index,128));}
    public byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=read(address+n);if(value==0)return out.toByteArray();out.write(value);}throw new IOException("PC1 NAMES C source exceeds known host scan bound");}
    /** Read-only lookup shared by41AE20/41AEA0. The caller owns scene marks,
     *bank90 and frame-label copy. A full raw path is cut at its FIRST dot. */
    public int findScenario(byte[] source)throws IOException{if(source==null)throw new IOException("PC1 scenario lookup source missing");int length=0;while(length<source.length&&source[length]!=0&&source[length]!='.')length++;if(length>=260)throw new IOException("PC1 scenario lookup local260 overflow");for(int index=0;index<count(Table.SCENARIO);index++){if(dword(CRT_LOCALE)!=0)throw new IOException("PC1 scenario CRT non-C locale requires42534C owner");for(int n=0;n<=length;n++){int a=n<length?source[n]&255:0,b=read(address(Table.SCENARIO,index,n));if(a>='A'&&a<='Z')a+=32;if(b>='A'&&b<='Z')b+=32;if(a!=b)break;if(a==0)return index;}}return -1;}
    public Pc1CgVisit.Catalog cgCatalog(){return new Pc1CgVisit.Catalog(){public int count()throws IOException{return dword(CG_COUNT);}public int nameByte(int ordinal,int offset)throws IOException{return read(address(Table.CG,ordinal,offset));}public int localeMode()throws IOException{return dword(CRT_LOCALE);}};}
    public Pc1Audio.Catalog audioCatalog(){return new Pc1Audio.Catalog(){public byte[] musicName(int index)throws IOException{return name(Table.MUSIC,index);}public byte[] soundName(int index)throws IOException{return name(Table.SOUND,index);}public int musicGainTrim(int index)throws IOException{return dword(address(Table.MUSIC,index,188));}public int soundGainTrim(int index)throws IOException{return dword(address(Table.SOUND,index,128));}public int musicCount()throws IOException{return count(Table.MUSIC);}public Pc1Audio.Entry music(int index)throws IOException{return new Pc1Audio.Entry(name(Table.MUSIC,index),dword(address(Table.MUSIC,index,188)));}public int soundCount()throws IOException{return count(Table.SOUND);}public Pc1Audio.Entry sound(int index)throws IOException{return new Pc1Audio.Entry(name(Table.SOUND,index),dword(address(Table.SOUND,index,128)));}};}
    public interface LiveText {
        byte[] givenName()throws IOException;
        byte[] familyName()throws IOException;
        int read(int bank,int index)throws IOException;
    }
    public Pc1TextExpander.Context textContext(LiveText live)throws IOException{
        if(live==null)throw new IOException("PC1 NAMES text adapter requires live name/variable owners");
        return new Pc1TextExpander.Context(){public byte[] givenName()throws IOException{return live.givenName();}public byte[] familyName()throws IOException{return live.familyName();}public int textCount()throws IOException{return count(Table.SCENARIO);}public byte[] textLabel(int index)throws IOException{return label(Table.SCENARIO,index);}public int musicCount()throws IOException{return count(Table.MUSIC);}public byte[] musicLabel(int index)throws IOException{return label(Table.MUSIC,index);}public byte[] catalogLabel(boolean text,int index)throws IOException{return label(text?Table.SCENARIO:Table.MUSIC,index);}public int read(int bank,int index)throws IOException{return live.read(bank,index);}};
    }
    void begin()throws IOException{if(active)throw new IOException("PC1 NAMES loader reentry");active=true;}
    void end(){active=false;}
    public boolean active(){return active;}
    public void save(DataOutput out)throws IOException{if(active)throw new IOException("PC1 NAMES snapshot during native file/local-stack call");out.writeInt(FORMAT);for(byte[] bytes:raw)out.write(bytes);}
    public static byte[] loadImage(DataInput in)throws IOException{if(in.readInt()!=FORMAT)throw new IOException("PC1 NAMES globals format");int size=0;for(int[] range:RANGES)size+=range[1];byte[] image=new byte[size];in.readFully(image);return image;}
    public void restore(byte[] image)throws IOException{if(active)throw new IOException("PC1 NAMES restore during native file/local-stack call");int size=0;for(int[] range:RANGES)size+=range[1];if(image==null||image.length!=size)throw new IOException("PC1 NAMES globals size");int at=0;for(byte[] bytes:raw){System.arraycopy(image,at,bytes,0,bytes.length);at+=bytes.length;}}
}
