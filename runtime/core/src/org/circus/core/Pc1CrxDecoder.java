package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Current CHS4015D0/4012E0/4011E0. Native BGR/ABGR bytes, borrowed
 * persistent LZ state, real allocation initial bytes and native read counts.
 * No generic CRX mode/alpha conversion, ARGB flattening or padding fill. */
public final class Pc1CrxDecoder {
    /** Shared only for independently machine-verified profiles. Field keys
     *are PC1 constants; returned addresses and pointer DWORD values belong
     *to the actual profile's canonical bus, including its known neighbors. */
    public interface NativeState {
        int fieldAddress(int pc1FieldAddress)throws IOException;
        int read(int actualAddress)throws IOException;
        void write(int actualAddress,int value)throws IOException;
        int word(int actualAddress)throws IOException;
        int dword(int actualAddress)throws IOException;
        void dword(int actualAddress,int value)throws IOException;
        void begin()throws IOException;
        void end();
        boolean active();
    }
    public interface Input {
        int descriptor();
        /** CRT read: -1 on ordinary OS failure,0 at EOF. Preserve suffix. */
        int read(byte[] destination,int offset,int requested)throws IOException;
    }
    public interface Allocation extends AutoCloseable {
        /** Same known mutable allocation throughout its lifetime. */
        byte[] bytes();
        void close()throws IOException;
    }
    public interface Allocator {
        /**401050 requests HeapAlloc(GetProcessHeap(),0,bytes). Null=failure. */
        Allocation image(int bytes)throws IOException;
        /** Native CRT malloc, distinct from image allocation. Null=failure. */
        Allocation temporary(int bytes)throws IOException;
    }
    public static final class Decoded {
        public final int width,height,stride,format;
        private final byte[] storage;
        private Decoded(int w,int h,int stride,int format,byte[] storage){width=w;height=h;this.stride=stride;this.format=format;this.storage=storage.clone();}
        public byte[] storage(){return storage.clone();}
        public Pc1GraphicsResources.Source source()throws IOException{return format==0?Pc1GraphicsResources.Source.rgb(NativeRgbImage.fromBgr24(width,height,stride,storage)):Pc1GraphicsResources.Source.alpha(NativeInverseAlphaImage.fromAbgr32(width,height,stride,storage));}
    }
    private static final int MAX_ALLOCATION=64*1024*1024;
    private final NativeState memory;
    public Pc1CrxDecoder(Pc1CrxMemory memory)throws IOException{this((NativeState)memory);}
    public Pc1CrxDecoder(NativeState memory)throws IOException{if(memory==null)throw new IOException("Native CRX globals missing");this.memory=memory;}
    private int address(int pc1Field)throws IOException{return memory.fieldAddress(pc1Field);}
    private int read(Input input,int address,int count)throws IOException{
        if(count<0||count>MAX_ALLOCATION)throw new IOException("PC1 CRX read exceeds known host allocation bound");byte[] raw=new byte[count];int got=input.read(raw,0,count);
        if(got< -1||got>count)throw new IOException("PC1 CRT read returned invalid count");for(int n=0;n<got;n++)memory.write(address+n,raw[n]&255);return got;
    }
    private int encoded(Input input)throws IOException{
        int left=memory.dword(address(Pc1CrxMemory.BUFFER_COUNT));
        if(left==0){left=read(input,address(Pc1CrxMemory.BUFFER),Pc1CrxMemory.BUFFER_BYTES);memory.dword(address(Pc1CrxMemory.BUFFER_POINTER),address(Pc1CrxMemory.BUFFER));}
        int at=memory.dword(address(Pc1CrxMemory.BUFFER_POINTER));memory.dword(address(Pc1CrxMemory.BUFFER_COUNT),left-1);int value=memory.read(at);memory.dword(address(Pc1CrxMemory.BUFFER_POINTER),at+1);return value;
    }
    private int encodedWord(Input input)throws IOException{return encoded(input)|encoded(input)<<8;}
    private static byte[] allocation(Allocation value,int length)throws IOException{if(value==null)return null;byte[] raw=value.bytes();if(raw==null||raw.length!=length)throw new IOException("PC1 allocator did not supply exact known backing");return raw;}
    private static void put(byte[] raw,int at,int value)throws IOException{if(at<0||at>=raw.length)throw new IOException("PC1 CRX pixel store reaches unowned allocation at"+at);raw[at]=(byte)value;}
    private static int get(byte[] raw,int at)throws IOException{if(at<0||at>=raw.length)throw new IOException("PC1 CRX predictor reads unowned allocation at"+at);return raw[at]&255;}
    private static Pc1HeapLease.Storage array(final byte[] bytes){return new Pc1HeapLease.Storage(){public int length(){return bytes.length;}public int read(int offset){return bytes[offset]&255;}public void write(int offset,int value){bytes[offset]=(byte)value;}};}
    private static void put(Pc1HeapLease.Storage memory,int at,int value)throws IOException{if(memory.length()>=0&&(at<0||at>=memory.length()))throw new IOException("PC1 CRX pixel store reaches unowned allocation at"+at);memory.write(at,value&255);}
    private static int get(Pc1HeapLease.Storage memory,int at)throws IOException{if(memory.length()>=0&&(at<0||at>=memory.length()))throw new IOException("PC1 CRX predictor reads unowned allocation at"+at);return memory.read(at)&255;}
    private interface RowDone{void next()throws IOException;}
    private void lz(Input input,Pc1HeapLease.Storage image,int w,int h,int stride,int line,Pc1ImagePool pool)throws IOException{
        memory.dword(address(Pc1CrxMemory.DESCRIPTOR),input.descriptor());memory.dword(address(Pc1CrxMemory.BUFFER_COUNT),0);memory.dword(address(Pc1CrxMemory.WINDOW_CURSOR),0);
        int flags=0,row=0;
        while(row<h){flags>>=1;if((flags&256)==0)flags=encoded(input)|0xff00;boolean literal=(flags&1)!=0;int count=1,source=0,value=0;
            if(literal)value=encoded(input);else{int control=encoded(input),offset;if(control>=192){offset=((control&3)<<8)|encoded(input);count=4+((control>>2)&15);}else if((control&128)!=0){offset=control&31;count=((control>>5)&3)+2;if(offset==0)offset=encoded(input);}else if(control==127){count=encodedWord(input)+2;offset=encodedWord(input);}else{count=control+4;offset=encodedWord(input);}source=memory.dword(address(Pc1CrxMemory.WINDOW_CURSOR))-offset;}
            for(int n=0;n<count;n++){int cursor=memory.dword(address(Pc1CrxMemory.WINDOW_CURSOR));if(!literal)value=memory.read(address(Pc1CrxMemory.WINDOW)+(source++&65535));memory.write(address(Pc1CrxMemory.WINDOW)+cursor,value);cursor=cursor+1&65535;memory.dword(address(Pc1CrxMemory.WINDOW_CURSOR),cursor);int left=memory.dword(address(Pc1CrxMemory.ROW_LEFT))-1;memory.dword(address(Pc1CrxMemory.ROW_LEFT),left);
                if(left==0){int sourceRow=cursor-line&65535,output=(h-1-row)*stride;Pc1HeapLease.Storage rowTarget=pool==null?image:pool.rowStorage();for(int b=0;b<line;b++)put(rowTarget,(pool==null?output:0)+b,memory.read(address(Pc1CrxMemory.WINDOW)+(sourceRow+b&65535)));if(pool!=null)pool.advanceRow(-stride);row++;memory.dword(address(Pc1CrxMemory.ROW_LEFT),line);memory.dword(address(Pc1CrxMemory.ROWS_LEFT),h-row);if(row==h)return;}
            }
        }
    }
    private static int inflate(byte[] output,byte[] encoded,int length)throws IOException{
        Inflater inflater=new Inflater();try{inflater.setInput(encoded,0,length);while(!inflater.finished()&&!inflater.needsInput()&&!inflater.needsDictionary()&&inflater.getBytesWritten()<output.length){int at=(int)inflater.getBytesWritten();int got;try{got=inflater.inflate(output,at,output.length-at);}catch(DataFormatException nativeError){break;}if(got==0)break;}return (int)inflater.getBytesWritten();}finally{inflater.end();}
    }
    private static void predict(byte[] stream,Pc1HeapLease.Storage image,int w,int h,int stride,int ps,RowDone done)throws IOException{
        int p=0,line=w*ps;
        for(int row=0;row<h;row++){int output=(h-1-row)*stride,previous=output+stride;
            if(ps==1){for(int b=0;b<line;b++)put(image,output+b,get(stream,p++));if(done!=null)done.next();continue;}
            int control=get(stream,p++);
            if(control==0){for(int b=0;b<ps;b++)put(image,output+b,get(stream,p++));for(int b=ps;b<line;b++)put(image,output+b,get(image,output+b-ps)+get(stream,p++));}
            else if(control==1){for(int b=0;b<line;b++)put(image,output+b,(row==0?unknownPrevious():get(image,previous+b))+get(stream,p++));}
            else if(control==2){for(int b=0;b<ps;b++)put(image,output+b,get(stream,p++));for(int b=ps;b<line;b++)put(image,output+b,get(stream,p++)+(row==0?unknownPrevious():get(image,previous+b-ps)));}
            else if(control==3){for(int b=0;b<line-ps;b++)put(image,output+b,get(stream,p++)+(row==0?unknownPrevious():get(image,previous+b+ps)));for(int b=line-ps;b<line;b++)put(image,output+b,get(stream,p++));}
            else if(control==4){int at=output;for(int channel=0;channel<ps;channel++){int left=w;while(left>0){int value=get(stream,p++);put(image,at,value);at+=ps;if(--left==0)break;if(value==get(stream,p)){int repeated=get(stream,p+1);p+=2;left-=repeated;for(int n=0;n<repeated;n++){put(image,at,value);at+=ps;}}}at=at-w*ps+1;}}
            if(done!=null)done.next();
            // Other controls consume their byte but do not paint this row.
        }
    }
    private static int unknownPrevious()throws IOException{throw new IOException("PC1 CRX first-row predictor reads native null/unknown previous row");}
    private static final class Destination {
        final int handle,w,h,stride,format,length;final Pc1HeapLease.Storage storage;final Allocation detached;final Pc1HeapLease lease;
        Destination(int handle,int w,int h,int stride,int format,int length,Pc1HeapLease.Storage storage,Allocation detached,Pc1HeapLease lease){this.handle=handle;this.w=w;this.h=h;this.stride=stride;this.format=format;this.length=length;this.storage=storage;this.detached=detached;this.lease=lease;}
    }
    private Destination destination(int w,int h,int format,int mode,Allocator allocator,Pc1ImagePool pool)throws IOException{
        int stride=Pc1ImagePool.nativeStride(w,format),length=stride*h;
        if(pool!=null){int handle=pool.allocate(w,h,format,mode);if(handle==-1)return null;Pc1HeapLease lease=pool.allocation(handle);return new Destination(handle,w,h,stride,format,length,pool.pixelStorage(handle),null,lease);}
        Allocation allocation=allocator.image(length+1);if(allocation==null)return null;byte[] data=allocation(allocation,length+1);return new Destination(-1,w,h,stride,format,length,array(data),allocation,null);
    }
    private static byte[] snapshot(Destination image)throws IOException{byte[] bytes=new byte[image.length];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)get(image.storage,n);return bytes;}
    private static void releaseDetached(Destination destination)throws IOException{if(destination!=null&&destination.detached!=null)destination.detached.close();}
    /** Explicit detached algorithm projection for legacy/DC2 vectors. It does
     *not claim to own native pool publication or addressable image pointers. */
    public Decoded decode(Input input,Allocator allocator)throws IOException{memory.begin();Destination result=null;try{result=decodeCore(input,allocator,null,0);return result==null?null:new Decoded(result.w,result.h,result.stride,result.format,snapshot(result));}finally{try{releaseDetached(result);}finally{memory.end();}}}
    /**4015D0 production path: image bytes/metadata publish BEFORE CRT malloc
     *and remain in the same pool on an ordinary later return−1. */
    public int decodeInto(Input input,Allocator allocator,Pc1ImagePool pool,int mode)throws IOException{if(pool==null)throw new IOException("PC1 CRX requires canonical image pool");pool.beginNativeCall();try{memory.begin();try{Destination result=decodeCore(input,allocator,pool,mode);return result==null?-1:result.handle;}finally{memory.end();}}finally{pool.endNativeCall();}}
    private Destination decodeCore(Input input,Allocator allocator,Pc1ImagePool pool,int mode)throws IOException{
        if(input==null||allocator==null)throw new IOException("PC1 CRX native IO/allocation leaf missing");Destination first=null,converted=null;boolean success=false;
        try{
            if(read(input,address(Pc1CrxMemory.HEADER),20)!=20)return null;
            int comp=(short)memory.word(address(Pc1CrxMemory.HEADER)+12),flags=(short)memory.word(address(Pc1CrxMemory.HEADER)+14),depth=(short)memory.word(address(Pc1CrxMemory.HEADER)+16);
            if(memory.dword(address(Pc1CrxMemory.HEADER))!=memory.dword(address(Pc1CrxMemory.MAGIC))||(comp!=1&&comp!=2)||flags>1)return null;
            if(depth>1&&read(input,address(Pc1CrxMemory.PALETTE),depth*3)!=depth*3)return null;
            int w=(short)memory.word(address(Pc1CrxMemory.HEADER)+8),h=(short)memory.word(address(Pc1CrxMemory.HEADER)+10),ps=depth==0?3:depth==1?4:1;
            long storage=(long)((w*ps+3)&~3)*h;if(w<=0||h<=0||storage+1>MAX_ALLOCATION||pool==null&&depth<0)throw new IOException("PC1 CRX signed geometry/format cannot be represented by known image owner");int stride=(w*ps+3)&~3,line=w*ps;
            first=destination(w,h,depth,depth>1?0:mode,allocator,pool);if(first==null)return null;
            memory.dword(address(Pc1CrxMemory.STRIDE),stride);if(pool!=null)pool.pointRow(first.lease,(h-1)*stride);memory.dword(address(Pc1CrxMemory.ROW_BYTES),line);
            if(comp==1){memory.dword(address(Pc1CrxMemory.ROW_LEFT),line);memory.dword(address(Pc1CrxMemory.ROWS_LEFT),h);lz(input,first.storage,w,h,stride,line,pool);}
            else{
                int inflatedLength=(line+1)*h+100,encodedLength=line*h+10000;Allocation decoded=allocator.temporary(inflatedLength);
                if(decoded==null){if(pool==null)throw new IOException("PC1 CRX native malloc failed after image-pool allocation: detached projection cannot own orphan slot");return null;}
                Allocation encoded=null;try{byte[] stream=allocation(decoded,inflatedLength);encoded=allocator.temporary(encodedLength);
                    if(encoded==null){if(pool==null)throw new IOException("PC1 CRX second native malloc failed after image-pool allocation: detached projection cannot own orphan slot");return null;}
                    byte[] packed=allocation(encoded,encodedLength);int got=input.read(packed,0,encodedLength);if(got< -1||got>encodedLength)throw new IOException("PC1 CRT read returned invalid count");if(got>12){inflate(stream,packed,got);Pc1HeapLease.Storage target=first.storage;if(pool!=null){final Pc1HeapLease.Storage row=pool.rowStorage();final int initial=(h-1)*stride;target=new Pc1HeapLease.Storage(){public int length(){return -1;}public int read(int at)throws IOException{return row.read(at-initial);}public void write(int at,int value)throws IOException{row.write(at-initial,value);}};}predict(stream,target,w,h,stride,ps,pool==null?null:()->pool.advanceRow(-stride));}
                }finally{decoded.close();if(encoded!=null)encoded.close();}
            }
            if(depth<=1){success=true;return first;}
            converted=destination(w,h,0,mode,allocator,pool);
            if(converted==null)throw new IOException("PC1 CRX palette allocation−1 enters native401230 adjacent metadata/pointer consumer; no reviewed destination owner");
            int rgbStride=converted.stride;for(int row=0;row<h;row++)for(int x=0;x<w;x++){int index=get(first.storage,row*stride+x),entry=address(Pc1CrxMemory.PALETTE)+index*3,at=row*rgbStride+x*3;put(converted.storage,at,memory.read(entry+2));put(converted.storage,at+1,memory.read(entry+1));put(converted.storage,at+2,memory.read(entry));}
            if(pool!=null)pool.free(first.handle);else releaseDetached(first);first=null;success=true;return converted;
        }finally{if(!success){releaseDetached(first);releaseDetached(converted);}}
    }
    public boolean active(){return memory.active();}
}
