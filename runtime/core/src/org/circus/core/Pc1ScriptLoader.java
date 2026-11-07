package org.circus.core;

import java.io.*;

/** Complete current CHS411670 and4115A0 control/byte order. File reads write
 * canonical destinations immediately; short/error results do not clear stale
 * tails or select invented failure branches. Source identity admission is an
 * outer VM concern after this actual native consumer has finished. */
public final class Pc1ScriptLoader {
    public interface Services {
        int prepareScenario41AEA0(int rawName)throws IOException;
        void error411050(int rawMessage)throws IOException;
        void resetEffects40F560(int first,int second)throws IOException;
        void releaseGraphics411080(int slot,int mode)throws IOException;
    }
    public interface NativeIo {
        /** Must execute40E5A0 then_open8000 over this addressed transient
         * local260. Null represents exactly the native descriptor−1. */
        Opened open(AddressedPath path)throws IOException;
    }
    public interface Opened {
        int descriptor();
        /** The unsigned32 count is passed as its raw Java int bits. Emit
         * only the actual ordered written prefix, including error-side data. */
        int read(Destination destination,int requested)throws IOException;
        int close()throws IOException;
    }
    /** A managed local260 address, explicitly NOT the original Windows ESP.
     * Unwritten stack suffix is unknown. Global aliases use the same bus. */
    public static final class AddressedPath implements Pc1NativePaths.Memory {
        public final int category=Pc1ScriptMemory.CATEGORY,name,extension=Pc1ScriptMemory.EXTENSION,pathCapacity=260,path=0xe0200000;
        private final Pc1NativeMemory bus;
        private final byte[] local=new byte[260];private final boolean[] known=new boolean[260];
        private AddressedPath(Pc1NativeMemory bus,int rawName){this.bus=bus;name=rawName;}
        private int offset(int address){long n=(address&0xffffffffL)-(path&0xffffffffL);return n>=0&&n<260?(int)n:-1;}
        public int read(int address)throws IOException{int at=offset(address);if(at<0)return bus.readAddress(address);if(!known[at])throw new IOException("PC1 script path reads unknown local260 suffix");return local[at]&255;}
        public void write(int address,int value)throws IOException{int at=offset(address);if(at<0){bus.writeAddress(address,value);return;}local[at]=(byte)value;known[at]=true;}
        public void build(Pc1NativePaths.Attributes attributes)throws IOException{Pc1NativePaths.buildAsset(this,path,category,name,extension,attributes);}
        public byte[] pathBytes()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<260;n++){int b=read(path+n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 script local260 path reaches unknown caller stack");}
    }
    public static final class Destination {
        private final Pc1NativeMemory bus;private final int address;private final long limit;private int written;
        private Destination(Pc1NativeMemory bus,int address,int requested){this.bus=bus;this.address=address;limit=requested&0xffffffffL;}
        public int address(){return address;}
        public int written(){return written;}
        public void write(int offset,int value)throws IOException{if(offset!=written||offset<0||(offset&0xffffffffL)>=limit)throw new IOException("PC1 script read must emit an ordered requested prefix");bus.writeAddress(address+offset,value);written++;}
    }
    public static final class Result {
        public final int nativeResult,codeReadResult,closeResult;public final boolean opened,markerAccepted;
        private Result(int value,boolean opened,boolean accepted,int read,int close){nativeResult=value;this.opened=opened;markerAccepted=accepted;codeReadResult=read;closeResult=close;}
    }
    private final Pc1NativeMemory bus;private final Pc1ScriptMemory memory;private final NativeIo io;private final Services services;private final Pc1LabelScanner labels;
    public Pc1ScriptLoader(Pc1NativeMemory bus,Pc1ScriptMemory memory,NativeIo io,Services services)throws IOException{if(bus==null||memory==null||io==null||services==null)throw new IOException("PC1 script loader requires canonical memory and explicit services");memory.requireBus(bus);this.bus=bus;this.memory=memory;this.io=io;this.services=services;labels=new Pc1LabelScanner(bus);}
    private int b(int at)throws IOException{return bus.readAddress(at);}
    private int d(int at)throws IOException{return b(at)|b(at+1)<<8|b(at+2)<<16|b(at+3)<<24;}
    private void d(int at,int value)throws IOException{for(int n=0;n<4;n++)bus.writeAddress(at+n,value>>>(n*8));}
    private int length(int at)throws IOException{for(int n=0;n<1024*1024;n++)if(b(at+n)==0)return n;throw new IOException("PC1 script strlen exceeds known host scan bound");}
    private void copyString(int dest,int source)throws IOException{int count=length(source)+1,n=0;for(;n+4<=count;n+=4)d(dest+n,d(source+n));for(;n<count;n++)bus.writeAddress(dest+n,b(source+n));}
    private void zeroDwords(int at,int count)throws IOException{for(int n=0;n<count;n++)d(at+n*4,0);}
    private int read(Opened file,int address,int requested)throws IOException{return file.read(new Destination(bus,address,requested),requested);}
    /** Native4115A0 is a label jump, not another file loader. */
    public void jumpLabel(int rawName)throws IOException{memory.begin();try{int found=labels.find(rawName);if(found==-1){services.error411050(Pc1ScriptMemory.ERROR_LABEL);d(0x9d8a20,1);}else d(0x9d8a14,found);}finally{memory.end();}}
    public Result load(int rawName,int mode)throws IOException{
        memory.begin();try{
            int cachedRead=services.prepareScenario41AEA0(rawName);
            Opened file=io.open(new AddressedPath(bus,rawName));
            if(file==null){services.error411050(Pc1ScriptMemory.ERROR_OPEN);d(0x9d8a20,1);return new Result(-1,false,false,0,0);}
            int codeRead,closed;
            read(file,0x9cca9c,4);read(file,0x9d2b54,d(0x9cca9c)*4);codeRead=read(file,Pc1CodeBuffer.BASE,100000);d(Pc1ScriptMemory.CODE_READ_RESULT,codeRead);
            closed=file.close();int first=b(Pc1CodeBuffer.BASE);d(0x9689ec,0);d(0x43d414,-1);d(0xbb84bc,0);
            if(first!=0x63||b(Pc1CodeBuffer.BASE+1)!=0x20){services.error411050(first!=0x63?Pc1ScriptMemory.ERROR_MAGIC:Pc1ScriptMemory.ERROR_VERSION);d(0x9d8a20,1);return new Result(0,true,false,codeRead,closed);}
            copyString(0x9d858c,rawName);
            if(mode==2||mode==1||mode==3){if(mode!=2)d(0x9da4a8,cachedRead);d(0x9d8a14,2);d(0x9d8a1c,0);bus.writeAddress(0x9d8a24,0);bus.writeAddress(0x9d8a25,0);d(0x9da4b0,-1);d(0x9d8a18,0);d(0x9d8a20,-1);
                if(mode!=2){services.resetEffects40F560(0,0);zeroDwords(0x9d95dc,8);d(0x94c698,0);zeroDwords(0x9d95fc,8);if(mode==1)services.releaseGraphics411080(-1,0);d(0xbb8418,0);d(0xbb843c,0);d(0xbb8440,0);d(0xbb842c,0);d(0xbb8430,0);}
                else d(0x94c698,0);
            }
            if(b(0xbb847c)!=0)jumpLabel(0xbb847c);
            zeroDwords(0xbb849c,8);zeroDwords(0xbb847c,8);return new Result(0,true,true,codeRead,closed);
        }finally{memory.end();}
    }
    /**414DAA/415317 producer: REP MOVSD thenMOVSB, includingNUL. Caller
     *owns its wait/mode stores and source local; this never infers a capacity. */
    public void copyDeferredPath(int rawSource)throws IOException{memory.begin();try{copyString(Pc1ScriptMemory.DEFERRED_PATH,rawSource);}finally{memory.end();}}
}
