package org.circus.core;

import java.io.*;
import java.util.*;

/** PH full-width ruby records, from41FBE0 and43E490. Native syntax is
 * U+FF5B annotation U+FF0F body U+FF5D, encoded withCP932 two-byte markers.
 * The portable dialogue currently renders the base text; annotations remain
 * edition-owned snapshot data for a later small-glyph renderer. */
public final class Dc4Ruby {
    public static final class Span {
        public final int startByte,endByte;private final byte[] annotation;
        private Span(int start,int end,byte[] note){startByte=start;endByte=end;annotation=note;}
        public byte[] annotationRaw(){return annotation.clone();}
    }
    private final List<Span> spans=new ArrayList<>();
    public int spanCount(){return spans.size();}
    public Span span(int index){return spans.get(index);}
    private static boolean word(byte[] b,int p,int second){return p+1<b.length&&(b[p]&255)==0x81&&(b[p+1]&255)==second;}
    private static int width(byte[] b,int p)throws IOException{int c=b[p]&255;if(c==0)throw new IOException("Embedded NUL in PH ruby text");if(c>=0x81&&c<=0x9f||c>=0xe0&&c<=0xfc){if(p+1>=b.length)throw new IOException("Truncated PH ruby character");return 2;}return 1;}
    public byte[] parse(byte[] source)throws IOException{
        require(source.length<=8191,"ruby text buffer");ByteArrayOutputStream out=new ByteArrayOutputStream();List<Span> next=new ArrayList<>();
        for(int p=0;p<source.length;){
            if(!word(source,p,0x6f)){int n=width(source,p);out.write(source,p,n);p+=n;continue;}
            int noteAt=p+2,separator=-1,close=-1;p=noteAt;
            while(p<source.length){
                require(!word(source,p,0x6f),"nested ruby");
                if(word(source,p,0x5e)){require(separator<0,"duplicate ruby separator");separator=p;p+=2;continue;}
                if(word(source,p,0x70)){close=p;break;}
                p+=width(source,p);
            }
            require(separator>=noteAt&&close>separator+2,"ruby delimiters/base");
            byte[] note=Arrays.copyOfRange(source,noteAt,separator);int baseAt=separator+2,baseLength=close-baseAt;
            require(note.length>0&&note.length<=1024&&next.size()<64,"ruby annotation bounds");
            boolean dots=note.length%2==0;for(int i=0;dots&&i<note.length;i+=2)dots=word(note,i,0x45);
            // 41FBE0 expands/truncates emphasis dots to the base byte width.
            if(dots){note=new byte[((baseLength+1)/2)*2];for(int i=0;i<note.length;i+=2){note[i]=(byte)0x81;note[i+1]=0x45;}}
            int start=out.size();out.write(source,baseAt,baseLength);next.add(new Span(start,out.size(),note));p=close+2;
        }
        spans.clear();spans.addAll(next);return out.toByteArray();
    }
    public void save(DataOutput out)throws IOException{out.writeInt(0x50485231);out.writeInt(spans.size());for(Span s:spans){out.writeInt(s.startByte);out.writeInt(s.endByte);out.writeInt(s.annotation.length);out.write(s.annotation);}}
    public void load(DataInput in)throws IOException{
        require(in.readInt()==0x50485231,"ruby snapshot version");int count=in.readInt();require(count>=0&&count<=64,"ruby snapshot count");List<Span> next=new ArrayList<>();int previous=0;
        for(int i=0;i<count;i++){int start=in.readInt(),end=in.readInt(),length=in.readInt();require(start>=previous&&end>start&&end<=8191&&length>0&&length<=8192,"ruby snapshot span");byte[] note=new byte[length];in.readFully(note);next.add(new Span(start,end,note));previous=end;}
        spans.clear();spans.addAll(next);
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
