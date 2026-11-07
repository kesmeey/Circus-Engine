package org.circus.core;

import java.io.*;

/** Complete current CHS411670 and4115A0 control/byte order. File reads write
 * canonical destinations immediately; short/error results do not clear stale
 * tails or select invented failure branches. Source identity admission is an
 * outer VM concern after this actual native consumer has finished. */
public final class Pc1ScriptLoader {
    public interface Services {
        int prepareScenario41AEA0(int rawName)throws IOException;
        default int prepareScenario41AEA0(Pc1ByteSource liveName)throws IOException{throw new IOException("PC1 scenario41AEA0 typed live-source consumer required");}
        void error411050(int rawMessage)throws IOException;
        void resetEffects40F560(int first,int second)throws IOException;
        void releaseGraphics411080(int slot,int mode)throws IOException;
    }
    public interface NativeIo {
        /** Legacy real raw-name input. Its local path has no native VA. */
        Opened open(AddressedPath path)throws IOException;
        default Opened open(TypedPath path)throws IOException{throw new IOException("PC1 script40E5A0/open8000 typed path provider required");}
    }
    public interface TypedIo {Opened open(TypedPath path)throws IOException;}
    public static NativeIo typedIo(final TypedIo io)throws IOException{if(io==null)throw new IOException("PC1 typed script IO missing");return new NativeIo(){public Opened open(AddressedPath path)throws IOException{return io.open(path);}public Opened open(TypedPath path)throws IOException{return io.open(path);}};}
    public interface Opened {
        int descriptor();
        /** The unsigned32 count is passed as its raw Java int bits. Emit
         * only the actual ordered written prefix, including error-side data. */
        int read(Destination destination,int requested)throws IOException;
        int close()throws IOException;
    }
    /** Typed local260 plus the original live name owner; no numeric overlay. */
    public static class TypedPath {
        public final int category=Pc1ScriptMemory.CATEGORY,extension=Pc1ScriptMemory.EXTENSION,pathCapacity=260;
        protected final Pc1NativeMemory bus;private final Pc1ByteSource nameSource;
        private final byte[] local=new byte[260];private final boolean[] known=new boolean[260];
        private final Pc1ByteSource.Pointer destination=new Pc1ByteSource.Pointer(){private int at(int offset)throws IOException{if(offset<0||offset>=260)throw new IOException("PC1 script path reaches unknown caller ABI outside local260");return offset;}public int unsignedByte(int offset)throws IOException{int n=at(offset);if(!known[n])throw new IOException("PC1 script path reads unknown local260 suffix");return local[n]&255;}public void writeByte(int offset,int value)throws IOException{int n=at(offset);local[n]=(byte)value;known[n]=true;}};
        private TypedPath(Pc1NativeMemory bus,Pc1ByteSource source){this.bus=bus;nameSource=source;}
        public Pc1ByteSource nameSource(){return nameSource;}
        public Pc1ByteSource.Pointer destination(){return destination;}
        public void buildTyped(Pc1NativePaths.SourceAttributes attributes)throws IOException{Pc1NativePaths.buildAsset(Pc1NativePaths.memory(bus),destination,Pc1ByteSource.addressed(bus,category),nameSource,Pc1ByteSource.addressed(bus,extension),attributes);}
        public void build(Pc1NativePaths.Attributes attributes)throws IOException{buildTyped(attributes==null?null:source->attributes.querySource(source));}
        public byte[] pathBytes()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int b=destination.unsignedByte(n);if(b==0)return out.toByteArray();out.write(b);}}
        public byte[] localBytes(){return local.clone();}public boolean[] knownBytes(){return known.clone();}
    }
    /** Real raw-name convenience. All numeric accesses remain actual bus
     *accesses; E0200000 is no longer reserved or intercepted as a local. */
    public static final class AddressedPath extends TypedPath implements Pc1NativePaths.Memory {
        public final int name;
        private AddressedPath(Pc1NativeMemory bus,int name){super(bus,Pc1ByteSource.addressed(bus,name));this.name=name;}
        public int read(int address)throws IOException{return bus.readAddress(address);}
        public void write(int address,int value)throws IOException{bus.writeAddress(address,value);}
        public int readInt32(int address)throws IOException{return bus.readInt32(address);}
        public void writeInt32(int address,int value)throws IOException{bus.writeInt32(address,value);}
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
    private void copyString(int dest,Pc1ByteSource source)throws IOException{Pc1NativePaths.copy(Pc1ByteSource.addressed(bus,dest),source);}
    private void zeroDwords(int at,int count)throws IOException{for(int n=0;n<count;n++)d(at+n*4,0);}
    private int read(Opened file,int address,int requested)throws IOException{return file.read(new Destination(bus,address,requested),requested);}
    /** Native4115A0 is a label jump, not another file loader. */
    public void jumpLabel(int rawName)throws IOException{memory.begin();try{int found=labels.find(rawName);if(found==-1){services.error411050(Pc1ScriptMemory.ERROR_LABEL);d(0x9d8a20,1);}else d(0x9d8a14,found);}finally{memory.end();}}
    public void jumpLabel(Pc1ByteSource name)throws IOException{memory.begin();try{int found=labels.find(name);if(found==-1){services.error411050(Pc1ScriptMemory.ERROR_LABEL);d(0x9d8a20,1);}else d(0x9d8a14,found);}finally{memory.end();}}
    public Result load(int rawName,int mode)throws IOException{return load(Pc1ByteSource.addressed(bus,rawName),mode,Integer.valueOf(rawName));}
    public Result load(Pc1ByteSource name,int mode)throws IOException{return load(name,mode,null);}
    private Result load(Pc1ByteSource name,int mode,Integer rawName)throws IOException{
        if(name instanceof Pc1ByteSource.Addressed&&((Pc1ByteSource.Addressed)name).bus()!=bus)throw new IOException("PC1 script source belongs to another native bus");
        memory.begin();try{
            int cachedRead=rawName==null?services.prepareScenario41AEA0(name):services.prepareScenario41AEA0(rawName.intValue());
            Opened file=rawName==null?io.open(new TypedPath(bus,name)):io.open(new AddressedPath(bus,rawName.intValue()));
            if(file==null||file.descriptor()==-1){services.error411050(Pc1ScriptMemory.ERROR_OPEN);d(0x9d8a20,1);return new Result(-1,false,false,0,0);}
            int codeRead,closed;
            read(file,0x9cca9c,4);read(file,0x9d2b54,d(0x9cca9c)*4);codeRead=read(file,Pc1CodeBuffer.BASE,100000);d(Pc1ScriptMemory.CODE_READ_RESULT,codeRead);
            closed=file.close();int first=b(Pc1CodeBuffer.BASE);d(0x9689ec,0);d(0x43d414,-1);d(0xbb84bc,0);
            if(first!=0x63||b(Pc1CodeBuffer.BASE+1)!=0x20){services.error411050(first!=0x63?Pc1ScriptMemory.ERROR_MAGIC:Pc1ScriptMemory.ERROR_VERSION);d(0x9d8a20,1);return new Result(0,true,false,codeRead,closed);}
            copyString(0x9d858c,name);
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
    public void copyDeferredPath(int rawSource)throws IOException{copyDeferredPath(Pc1ByteSource.addressed(bus,rawSource));}
    public void copyDeferredPath(Pc1ByteSource source)throws IOException{if(source instanceof Pc1ByteSource.Addressed&&((Pc1ByteSource.Addressed)source).bus()!=bus)throw new IOException("PC1 deferred source belongs to another native bus");memory.begin();try{copyString(Pc1ScriptMemory.DEFERRED_PATH,source);}finally{memory.end();}}
}
