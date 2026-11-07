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
