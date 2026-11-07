package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 7988971f... PC1 CN 418090/4123A0 byte expansion, before glyph layout.
 * The linked initial CRT Ctype table and its signed-char reads are part of this
 * exact image contract. No Unicode conversion or recursive macro expansion. */
public final class Pc1TextExpander {
    private Pc1TextExpander(){}
    public static final int BODY_CAPACITY=1024,SPEAKER_CAPACITY=256;
    /** Complete addressed4123A0 route. The existing byte[] Context API below
     *is a convenience projection; it cannot retain native source aliases. */
    public interface LiveState {
        int read(int address)throws IOException;void write(int address,int value)throws IOException;
        default int readInt32(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=(read(address+n)&255)<<(n*8);return value;}
    }
    public interface LiveServices {
        default int atoi420BDA(Pc1GlyphCache.Source source)throws IOException{throw liveRequired(0x420bda);}
        default int isDigit420AD0(int signedByte)throws IOException{throw liveRequired(0x420ad0);}
        default int variable411910(int bank,int index)throws IOException{throw liveRequired(0x411910);}
        /**Actual local80B destination and mutable native format pointer.
         *Neither its initial bytes nor a missing NUL are manufactured.*/
        default void sprintf420661(Local destination,int rawFormat,int value)throws IOException{throw liveRequired(0x420661);}
    }
    /** The linked420BDA/420B4F and420AD0 paths. Every classification
     *reloads MB_CUR_MAX and the native ctype pointer. Multi-byte locale work
     *remains an explicit423486 call, rather than a Java character predicate. */
    public static class LinkedCrt implements LiveServices {
        private final LiveState memory;
        public LinkedCrt(LiveState memory)throws IOException{if(memory==null)throw new IOException("PC1 linked CRT requires canonical memory");this.memory=memory;}
        public LinkedCrt(final Pc1NativeMemory bus)throws IOException{this(bus==null?null:new LiveState(){public int read(int at)throws IOException{return bus.readAddress(at);}public void write(int at,int value)throws IOException{bus.writeAddress(at,value);}public int readInt32(int at)throws IOException{return bus.readInt32(at);}});}
        protected int classify423486(int value,int mask)throws IOException{throw liveRequired(0x423486);}
        private int table(int value,int mask)throws IOException{int pointer=memory.readInt32(0x4403c0);return memory.read(pointer+value*2)&mask;}
        public int isDigit420AD0(int signedByte)throws IOException{if(memory.readInt32(0x4405cc)>1)return classify423486(signedByte,4);return table(signedByte,4);}
        private int unsignedDigit(int value)throws IOException{if(memory.readInt32(0x4405cc)>1)return classify423486(value,4);return table(value,4);}
        public int atoi420BDA(Pc1GlyphCache.Source source)throws IOException{
            if(source==null)throw new IOException("PC1 atoi requires live typed source");int at=0;
            for(;;){int mb=memory.readInt32(0x4405cc),c=source.unsignedByte(at)&255;int space=mb>1?classify423486(c,8):table(c,8);if(space==0)break;at++;}
            int current=source.unsignedByte(at++)&255,sign=current;if(current=='-'||current=='+')current=source.unsignedByte(at++)&255;
            int value=0;while(unsignedDigit(current)!=0){value=value*10+current-'0';current=source.unsignedByte(at++)&255;}return sign=='-'?-value:value;
        }
    }
    private static IOException liveRequired(int target){return new IOException("PC1 live expansion required native leaf0x"+Integer.toHexString(target));}
    public static final class Local {
        private final byte[] bytes=new byte[80],known=new byte[80];
        public int unsignedByte(int at)throws IOException{if(at<0||at>=bytes.length)throw new IOException("PC1 expansion crosses unknown caller ABI outside local80");if(known[at]==0)throw new IOException("PC1 expansion reads unknown local80 byte");return bytes[at]&255;}
        public void write(int at,int value)throws IOException{if(at<0||at>=bytes.length)throw new IOException("PC1 expansion crosses unknown caller ABI outside local80");bytes[at]=(byte)value;known[at]=1;}
    }
    public static final class Live {
        private final LiveState memory;private final LiveServices services;private Pc1NativeMemory bus;private int depth;
        public Live(LiveState memory,LiveServices services)throws IOException{if(memory==null)throw new IOException("PC1 live expansion requires canonical memory");this.memory=memory;this.services=services==null?new LiveServices(){}:services;}
        public Live(final Pc1NativeMemory memory,LiveServices services)throws IOException{this(memory==null?null:new LiveState(){public int read(int at)throws IOException{return memory.readAddress(at);}public void write(int at,int value)throws IOException{memory.writeAddress(at,value);}public int readInt32(int at)throws IOException{return memory.readInt32(at);}},services);bus=memory;}
        public void requireBus(Pc1NativeMemory memory)throws IOException{if(memory==null||memory!=bus)throw new IOException("PC1 live expansion requires its canonical bus");}
        public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 live expansion snapshot during native local/CRT callback");}
        private int b(int at)throws IOException{return memory.read(at)&255;}private int d(int at)throws IOException{return memory.readInt32(at);}
        private static int b(Pc1GlyphCache.Source source,int at)throws IOException{return source.unsignedByte(at)&255;}
        private static Pc1GlyphCache.Source shifted(Pc1GlyphCache.Source source,int at){return offset->b(source,at+offset);}
        private int digits(Pc1GlyphCache.Source source,int at)throws IOException{int digit=services.isDigit420AD0((byte)b(source,at));while(digit!=0){int next=(byte)b(source,at+1);at++;digit=services.isDigit420AD0(next);}return at;}
        private int copyCString(int address,int destination)throws IOException{int value=b(address);while(value!=0){memory.write(destination,value);value=b(address+1);destination++;address++;}return destination;}
        /** Source is already transformed by fetch for4A..4D. This function
         *does not decode, pre-scan, clone or bound output by a destination DTO.
         *Each byte store reaches the addressed owner at its actual position.*/
        public void expand(Pc1GlyphCache.Source source,int destination)throws IOException{
            if(source==null)throw new IOException("PC1 live expansion source missing");depth++;
            try{
                Local local=new Local();int at=0,out=destination,value=b(source,0);
                if(value==0){memory.write(out,0);memory.write(out+1,0);return;}
                for(;;){
                    if(value!='$'){memory.write(out,value);out++;}
                    else{
                        value=b(source,at+1);at++;
                        if(value=='n')out=copyCString(0x454d34,out);
                        else if(value=='f')out=copyCString(0x454d20,out);
                        else if(value=='t'||value=='m'){
                            boolean text=value=='t';at++;int index=services.atoi420BDA(shifted(source,at));at=digits(source,at);
                            int count=d(text?0xbb84c4:0xbc1d14);
                            if(index<count)out=copyCString((text?0x9717f8:0xbb85f8)+index*(text?188:192),out);
                            at--;
                        }else if(services.isDigit420AD0((byte)value)!=0){
                            int bank=services.atoi420BDA(shifted(source,at));at=digits(source,at);
                            if(b(source,at)==','){
                                at++;int index=services.atoi420BDA(shifted(source,at));at=digits(source,at);
                                int number=services.variable411910(bank,index);services.sprintf420661(local,0x43d988,number);
                                int p=0,c=local.unsignedByte(0);while(c!=0){memory.write(out,c);c=local.unsignedByte(p+1);out++;p++;}at--;
                            }
                        }
                    }
                    value=b(source,at+1);at++;if(value==0){memory.write(out,0);memory.write(out+1,0);return;}
                }
            }finally{depth--;}
        }
    }
    /** Current raw substitution owners. Names/labels are validated owned C
     *strings (their final NUL may be omitted); a missing record terminator is
     *not permission to truncate a native read at an arbitrary table boundary. */
    public interface Context {
        byte[] givenName()throws IOException;
        byte[] familyName()throws IOException;
        int textCount()throws IOException;
        byte[] textLabel(int index)throws IOException;
        int musicCount()throws IOException;
        byte[] musicLabel(int index)throws IOException;
        /** Actual4123A0 has a signed upper check only. Legacy row adapters
         *may reject unknown negative addresses; raw catalogs decide by bus. */
        default byte[] catalogLabel(boolean text,int index)throws IOException{
            if(index<0)throw new IOException("PC1 negative "+(text?"text":"music")+" catalog pointer");
            return text?textLabel(index):musicLabel(index);
        }
        /** The native411910 owner decides ordinary/special/alias boundaries. */
        int read(int bank,int index)throws IOException;
    }
    /** K/L encoded payload, excluding its opcode. 418090 copies at most a
     *1024-byte C string, then adds20h to each original nonzero byte. */
    public static byte[] decodeEncoded(byte[] encoded)throws IOException {
        return decodeEncoded(encoded,BODY_CAPACITY);
    }
    /**411D30's type10 consumer has separate2048-byte input/output stack
     *buffers. The raw companion marker never chooses whether to add20h. */
    public static byte[] expandDraw(byte[] encoded,Context context)throws IOException{
        return expand(decodeEncoded(encoded,2048),context,2048);
    }
    static byte[] decodeEncoded(byte[] encoded,int capacity)throws IOException {
        if(encoded==null)throw new IOException("PC1 encoded text is missing");
        int length=cLength(encoded);if(length>=capacity)throw new IOException("PC1 encoded text exceeds native"+(capacity==BODY_CAPACITY?"418090":"411D30")+" scratch");
        byte[] decoded=new byte[length];int end=length;
        for(int i=0;i<length;i++){decoded[i]=(byte)((encoded[i]&255)+32);if(decoded[i]==0&&end==length)end=i;}
        return Arrays.copyOf(decoded,end);
    }
    /** Body destination has1024 bytes including the two terminating zeros. */
    public static byte[] expand(byte[] decoded,Context context)throws IOException{return expand(decoded,context,BODY_CAPACITY);}
    public static byte[] expandSpeaker(byte[] decoded,Context context)throws IOException{return expand(decoded,context,SPEAKER_CAPACITY);}
    /** Input is a logical C string; bytes after its first NUL are not an owned
     *source-memory suffix. An instruction path reading beyond that terminator
     *is rejected instead of inventing contents of the native stack scratch. */
    public static byte[] expand(byte[] decoded,Context context,int destinationCapacity)throws IOException {
        if(decoded==null||context==null)throw new IOException("PC1 text expansion input is missing");
        if(destinationCapacity<2)throw new IOException("PC1 text destination has no double terminator");
        Input input=new Input(decoded);Output out=new Output(destinationCapacity);int p=0;
        while(input.at(p)!=0){
            int c=input.at(p);
            if(c!='$'){out.add(c);p++;continue;}
            c=input.at(++p);
            if(c=='n'){out.add(context.givenName());p++;}
            else if(c=='f'){out.add(context.familyName());p++;}
            else if(c=='t'||c=='m'){
                boolean text=c=='t';int index=input.atoi(++p);p=input.skipSignedDigits(p);
                int count=text?context.textCount():context.musicCount();
                if(index<count){
                    out.add(context.catalogLabel(text,index));
                }
                // 412580 decrements before the common increment: retain the
                //first byte not consumed by signed-char isdigit.
            }else if(signedDigit(c)){
                int bank=input.atoi(p);p=input.skipSignedDigits(p);
                if(input.at(p)==','){
                    int index=input.atoi(++p);p=input.skipSignedDigits(p);
                    out.add(Integer.toString(context.read(bank,index)).getBytes(StandardCharsets.US_ASCII));
                }else p++; // Missing comma consumes the first nondigit too.
            }else p++; // Unknown code drops both '$' and that code byte.
        }
        return out.bytes();
    }
    /** Explicit native output representation; expand() itself returns bytes
     *before NUL so that downstream layout can own their interpretation. */
    public static byte[] nativeBuffer(byte[] expanded)throws IOException {
        if(expanded==null)throw new IOException("PC1 expanded text is missing");
        if(cLength(expanded)!=expanded.length)throw new IOException("PC1 expanded text contains NUL");
        if(expanded.length>Integer.MAX_VALUE-2)throw new IOException("PC1 expanded text length");
        return Arrays.copyOf(expanded,expanded.length+2);
    }
    private static int cLength(byte[] bytes){int n=0;while(n<bytes.length&&bytes[n]!=0)n++;return n;}
    private static boolean unsignedDigit(int c){return c>='0'&&c<='9';}
    private static boolean space(int c){return c==32||c>=9&&c<=13;}
    /** MOVSX -> linked isdigit, MB_CUR_MAX1. Negative indices read the fixed
     *CRT data before4403CA; this unrelocatable image has no direct Ctype writer.
     *Do not replace this observed byte behavior with Character.isDigit. */
    private static boolean signedDigit(int c){
        if(unsignedDigit(c))return true;
        switch(c){case 0x87:case 0xaf:case 0xc7:case 0xc9:case 0xca:case 0xcc:case 0xe2:case 0xe4:case 0xfc:case 0xfe:return true;default:return false;}
    }
    private static final class Input {
        final byte[] bytes;final int length;
        Input(byte[] source){length=cLength(source);bytes=Arrays.copyOf(source,length);}
        int at(int p)throws IOException {if(p<0||p>length)throw new IOException("PC1 expansion reads beyond native source terminator");return p==length?0:bytes[p]&255;}
        int skipSignedDigits(int p)throws IOException {while(signedDigit(at(p)))p++;return p;}
        int atoi(int p)throws IOException {
            while(space(at(p)))p++;int c=at(p),sign=c;if(c=='-'||c=='+')c=at(++p);
            int value=0;while(unsignedDigit(c)){value=value*10+c-'0';c=at(++p);}
            return sign=='-'?-value:value;
        }
    }
    private static final class Output {
        final int limit;final ByteArrayOutputStream out=new ByteArrayOutputStream();
        Output(int capacity){limit=capacity-2;}
        void add(int b)throws IOException {if(out.size()>=limit)throw new IOException("PC1 expansion exceeds native destination with double terminator");out.write(b);}
        void add(byte[] bytes)throws IOException {if(bytes==null)throw new IOException("PC1 substitution bytes are missing");int n=cLength(bytes);if(n>limit-out.size())throw new IOException("PC1 expansion exceeds native destination with double terminator");out.write(bytes,0,n);}
        byte[] bytes(){return out.toByteArray();}
    }
}
