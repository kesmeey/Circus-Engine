package org.circus.core;

import java.io.IOException;

/**41B6C0 validation and its41B670/41B620/41B5E0 helpers. Original byte
 *ctype/uppercase remain required CRT leaves; no Unicode case conversion.
 *The permutation is read from live.data43DA78, never a constant copy. */
public final class Pc1InstallKey {
    public interface Locale {
        int upper42097E(int signedByte)throws IOException;
        int digit420AD0(int signedByte)throws IOException;
    }
    private static final int TABLE=0x43da78;
    private final Pc1NativeMemory bus;private final Locale locale;private int depth;
    public Pc1InstallKey(Pc1NativeMemory bus,Locale locale)throws IOException{if(bus==null||locale==null)throw new IOException("PC1 install-key validator requires live bytes and native CRT locale leaves");this.bus=bus;this.locale=locale;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 install-key snapshot during native local/CRT call");}
    private int d(int at)throws IOException{return bus.readAddress(at)|bus.readAddress(at+1)<<8|bus.readAddress(at+2)<<16|bus.readAddress(at+3)<<24;}
    private static final class Local {
        final byte[] bytes=new byte[20];final boolean[] known=new boolean[20];
        int read(int at)throws IOException{if(at<0||at>=bytes.length||!known[at])throw new IOException("PC1 install-key reads unknown native local/caller ABI");return bytes[at]&255;}
        void write(int at,int value)throws IOException{if(at<0||at>=bytes.length)throw new IOException("PC1 install-key writes beyond known20-byte native local into caller ABI");bytes[at]=(byte)value;known[at]=true;}
    }
    private int length(int raw)throws IOException{for(int n=0;;n++){int value=bus.readAddress(raw+n);if(value==0||n==-2)return n;}}
    private void permute(Local original)throws IOException{
        int count=0;while(original.read(count++)!=0){}Local copied=new Local();int at=0;
        for(;at+4<=count;at+=4){int word=0;for(int k=0;k<4;k++)word|=original.read(at+k)<<(8*k);for(int k=0;k<4;k++)copied.write(at+k,word>>>(8*k));}
        for(;at<count;at++)copied.write(at,original.read(at));
        for(int n=0;n<11;n++)original.write(n,copied.read(d(TABLE+n*4)));
    }
    private int number(Local source,int start,int count)throws IOException{
        int value=0;for(int n=0;n<count;n++){int at=start+n,b=(byte)source.read(at);if(locale.digit420AD0(b)!=0)value=(byte)source.read(at)-0x16+value*36;else value=locale.upper42097E((byte)source.read(at))-0x41+value*36;}return value;
    }
    private static int crc(Local source,int count)throws IOException{int value=0;for(int n=0;n<count;n++){value^=source.read(n)<<8;for(int bit=0;bit<8;bit++)value=(value&0x8000)!=0?value*2^0x1021:value<<1;}return value;}
    public int validate(int rawText,int flags)throws IOException{depth++;try{
        if(flags!=0x20)return -5;if(length(rawText)!=11)return -4;
        Local local=new Local();int at=0,b=bus.readAddress(rawText);while(b!=0){int converted=locale.upper42097E((byte)b);local.write(at,converted);b=bus.readAddress(rawText+at+1);at++;}local.write(at,0);
        permute(local);int kind=number(local,0,2),encoded=number(local,2,5),check=number(local,7,4),sum=crc(local,7)&0xffff;
        if(sum%25+sum*25!=check)return -1;int result=encoded%10000000;if((result%6)*10000000+result!=encoded)return -2;return kind==0x20?result:-3;
    }finally{depth--;}}
}
