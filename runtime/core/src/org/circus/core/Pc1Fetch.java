package org.circus.core;

import java.io.*;

/** CurrentCHS4125B0 entry/fetch and41570F tail, without executing a handler.
 * Code reads use the live addressed bus; no immutable MES identity is consulted.
 * The1024-byte copied string and the two preserved arguments are native locals,
 * not a second allocator or invented native stack virtual address. */
public final class Pc1Fetch {
    public static final int PC=0x9d8a14,WAIT=0x9d8a20,ARGUMENT0=0x9da4c0,ARGUMENT1=0x9da4c4;
    public static final int RAW_STRING=0xbb83fc,LAST_OPCODE=0x43d414,CACHED_BACKGROUND=0x9553b4;
    public static final int CODE_BASE=0x9a077c,DEVICE=0x9689ec,BACKGROUND=0x43d420;
    public interface State {int read(int address)throws IOException;void write(int address,int value)throws IOException;}
    private final State state;
    public Pc1Fetch(State state)throws IOException{if(state==null)throw new IOException("PC1 fetch requires live native memory");this.state=state;}
    public Pc1Fetch(final Pc1NativeMemory memory)throws IOException{this(memory==null?null:new State(){public int read(int address)throws IOException{return memory.readAddress(address);}public void write(int address,int value)throws IOException{memory.writeAddress(address,value);}});}
    private int byteAt(int address)throws IOException{return state.read(address)&255;}
    private int dword(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=byteAt(address+n)<<(8*n);return value;}
    private void dword(int address,int value)throws IOException{for(int n=0;n<4;n++)state.write(address+n,value>>>(8*n));}
    /**4118D0 reads the HIGH byte first. Its result is signed16 LE. */
    public int signedWord(int address)throws IOException{int high=byteAt(address+1),low=byteAt(address);return(short)((high<<8)|low);}
    public static final class Frame {
        public static final int BYTES=0x41c,ARG2=0,ARG3=4,PREVIOUS=8,LOCAL_POINTER=12,OPCODE=16,BUFFER=28,BUFFER_BYTES=1024;
        private static final int FORMAT=0x50315131,VERSION=1;
        private static final int IDLE=0,ENTERING=1,READY=2,FETCHING=3,DISPATCH=4,EXITED=5,INTERRUPTED=6;
        private final byte[] raw=new byte[BYTES],known=new byte[BYTES];
        private int phase,cursor,sourcePc,arg0,arg1,device;
        private boolean stringSource;
        private Frame(){}
        /** Unknown native stack bytes, rather than Java zero initialization. */
        public static Frame unknown(){return new Frame();}
        /** A caller may reuse only bytes actually known at its own ABI point. */
        public static Frame fromKnown(byte[] raw,byte[] mask)throws IOException{if(raw==null||mask==null||raw.length!=BYTES||mask.length!=BYTES)throw new IOException("PC1 fetch locals require1052 raw/known bytes");Frame frame=new Frame();System.arraycopy(raw,0,frame.raw,0,BYTES);for(int n=0;n<BYTES;n++){if(mask[n]!=0&&mask[n]!=1)throw new IOException("PC1 fetch known mask");frame.known[n]=mask[n];}return frame;}
        private static int at(int offset)throws IOException{if(offset<0||offset>=BYTES)throw new IOException("PC1 fetch reaches unknown caller ABI outside locals at+0x"+Integer.toHexString(offset));return offset;}
        public int unsignedByte(int offset)throws IOException{int at=at(offset);if(known[at]==0)throw new IOException("PC1 fetch reads unknown native local byte at+0x"+Integer.toHexString(offset));return raw[at]&255;}
        public void writeByte(int offset,int value)throws IOException{int at=at(offset);raw[at]=(byte)value;known[at]=1;}
        public int dword(int offset)throws IOException{int value=0;for(int n=0;n<4;n++)value|=unsignedByte(offset+n)<<(8*n);return value;}
        public void dword(int offset,int value)throws IOException{for(int n=0;n<4;n++)writeByte(offset+n,value>>>(8*n));}
        public void unknownByte(int offset)throws IOException{known[at(offset)]=0;}
        public int argument2()throws IOException{return dword(ARG2);}public int argument3()throws IOException{return dword(ARG3);}
        public void setArgument2(int value)throws IOException{dword(ARG2,value);}public void setArgument3(int value)throws IOException{dword(ARG3,value);}
        public int previousOpcode()throws IOException{return dword(PREVIOUS);}public void setPreviousOpcode(int value)throws IOException{dword(PREVIOUS,value);}
        /** Live local bytes, including known stale suffix/earlier locals if an
         *explicit consumer uses an offset before BUFFER. Never zero fills. */
        public Pc1GlyphCache.Source source(final int localOffset){return offset->{long at=(long)localOffset+offset;if(at<Integer.MIN_VALUE||at>Integer.MAX_VALUE)throw new IOException("PC1 local source offset overflow");return unsignedByte((int)at);};}
        public Pc1GlyphCache.Source source(){return source(BUFFER);}
        public byte[] rawBytes(){return raw.clone();}public byte[] knownMask(){return known.clone();}
        public boolean active(){return phase==ENTERING||phase==READY||phase==FETCHING||phase==DISPATCH;}
        public boolean atDispatch(){return phase==DISPATCH;}
        public byte[] localCString()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int at=BUFFER;;at++){int value=unsignedByte(at);if(value==0)return out.toByteArray();out.write(value);}}
        /** Preserve already-borrowed Frame.Source aliases across a portable
         *restore; this copies no global native memory or OS object. */
        public void restoreFrom(Frame image)throws IOException{if(image==null)throw new IOException("PC1 fetch frame image missing");System.arraycopy(image.raw,0,raw,0,BYTES);System.arraycopy(image.known,0,known,0,BYTES);phase=image.phase;cursor=image.cursor;sourcePc=image.sourcePc;arg0=image.arg0;arg1=image.arg1;device=image.device;stringSource=image.stringSource;}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);out.writeInt(phase);out.writeInt(cursor);out.writeInt(sourcePc);out.writeInt(arg0);out.writeInt(arg1);out.writeInt(device);out.writeBoolean(stringSource);out.write(raw);out.write(known);}
        public static Frame load(DataInput in)throws IOException{if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 fetch locals format");Frame f=new Frame();f.phase=in.readInt();f.cursor=in.readInt();f.sourcePc=in.readInt();f.arg0=in.readInt();f.arg1=in.readInt();f.device=in.readInt();f.stringSource=in.readBoolean();if(f.phase<IDLE||f.phase>INTERRUPTED)throw new IOException("PC1 fetch locals phase");in.readFully(f.raw);in.readFully(f.known);for(byte value:f.known)if(value!=0&&value!=1)throw new IOException("PC1 fetch known mask");return f;}
    }
    public static final class Dispatch {
        public final int signedOpcode,sourcePc,device;public final boolean stringRecord;
        private final int arg0,arg1;private final Frame frame;
        private Dispatch(Frame frame,int opcode){this.frame=frame;signedOpcode=opcode;sourcePc=frame.sourcePc;device=frame.device;arg0=frame.arg0;arg1=frame.arg1;stringRecord=frame.stringSource;}
        public int argument(int index)throws IOException{switch(index){case 0:return arg0;case 1:return arg1;case 2:return frame.argument2();case 3:return frame.argument3();default:throw new IOException("PC1 dispatch argument index");}}
        public boolean nativeDefault(){return signedOpcode<0||signedOpcode>0x62;}
        public Pc1GlyphCache.Source source(){return frame.source();}
        public byte[] localCString()throws IOException{return frame.localCString();}
    }
    /** False corresponds to WAIT14's direct native return0, with no local/global
     *writes. Re-entering the same Frame explicitly preserves its native stale
     *stack bytes; callers choose whether that ABI identity is actually known. */
    public boolean enter(Frame frame)throws IOException{
        if(frame==null||frame.active())throw new IOException("PC1 fetch entry requires inactive locals");
        if(dword(WAIT)==14)return false;frame.phase=Frame.ENTERING;
        int background=dword(BACKGROUND);frame.cursor=dword(PC);
        dword(CACHED_BACKGROUND,background);dword(WAIT,-1);frame.setPreviousOpcode(-1);dword(0x9cca8c,0);dword(0x9b9f18,0);frame.phase=Frame.READY;return true;
    }
    public Dispatch next(Frame frame)throws IOException{
        if(frame==null||frame.phase!=Frame.READY)throw new IOException("PC1 native fetch is not at its next-record boundary");frame.phase=Frame.FETCHING;frame.sourcePc=frame.cursor;frame.stringSource=false;
        int opcode=(byte)byteAt(CODE_BASE+frame.cursor);frame.device=dword(DEVICE);frame.dword(Frame.OPCODE,opcode);
        frame.arg0=frame.arg1=0;dword(ARGUMENT1,0);dword(ARGUMENT0,0);
        if(opcode<0x2d){int first=byteAt(CODE_BASE+frame.cursor+1),second=byteAt(CODE_BASE+frame.cursor+2);frame.arg0=first;dword(ARGUMENT0,first);frame.arg1=second;dword(ARGUMENT1,second);if(opcode!=0){frame.cursor+=3;dword(PC,frame.cursor);}}
        else if(opcode<0x4e){
            int pc=dword(PC),source=CODE_BASE+pc+1;frame.stringSource=true;
            //LEA identifies the typed local source. There is no proved native
            //ESP numeric here, so its DWORD bytes cannot be manufactured.
            for(int n=0;n<4;n++)frame.unknownByte(Frame.LOCAL_POINTER+n);
            dword(RAW_STRING,source);long count=0;for(long remaining=0xffffffffL;remaining!=0;remaining--){int value=byteAt(source+(int)count);count++;if(value==0)break;}
            int bytes=(int)count,from=source,to=Frame.BUFFER;
            for(int n=0;n<(bytes>>>2);n++){int word=dword(from);frame.dword(to,word);from+=4;to+=4;}
            for(int n=0;n<(bytes&3);n++){frame.writeByte(to++,byteAt(from++));}
            int length=0;while(frame.unsignedByte(Frame.BUFFER+length)!=0)length++;
            frame.cursor=pc+length+2;dword(PC,frame.cursor);
            boolean encodedNonempty=false;
            if(opcode>=0x4a){int at=Frame.BUFFER,value=frame.unsignedByte(at),decodedCount=0;if(value!=0){encodedNonempty=true;do{frame.writeByte(at,value+0x20);value=frame.unsignedByte(at+1);at++;decodedCount++;}while(value!=0);frame.arg1=decodedCount;}}
            //The common string epilogue reloads ESI from live PC, unlike its
            //earlier EBX source-position value used to calculate the advance.
            frame.cursor=dword(PC);frame.device=dword(DEVICE);if(encodedNonempty)dword(ARGUMENT1,frame.arg1);
        }else{
            int first=signedWord(CODE_BASE+frame.cursor+1);dword(ARGUMENT0,first);
            int second=signedWord(CODE_BASE+dword(PC)+3);dword(ARGUMENT1,second);
            int third=signedWord(CODE_BASE+dword(PC)+5),pcBeforeFourth=dword(PC);frame.setArgument2(third);int fourth=signedWord(CODE_BASE+pcBeforeFourth+7);
            frame.cursor=dword(PC);frame.arg0=dword(ARGUMENT0);frame.arg1=dword(ARGUMENT1);frame.device=dword(DEVICE);frame.cursor+=9;frame.setArgument3(fourth);dword(PC,frame.cursor);
        }
        frame.phase=Frame.DISPATCH;return new Dispatch(frame,opcode);
    }
    /** Separate412647 branch/jump-table read, on the live mutable native bus. */
    public int jumpTarget(Dispatch dispatch)throws IOException{if(dispatch==null)throw new IOException("PC1 dispatch missing");return dispatch.nativeDefault()?0x41570f:dword(0x415754+dispatch.frame.dword(Frame.OPCODE)*4);}
    /** True means native415722 loops within this SAME4125B0 invocation. */
    public boolean commonTail(Frame frame)throws IOException{
        if(frame==null||frame.phase!=Frame.DISPATCH)throw new IOException("PC1 common tail requires dispatched locals");int wait=dword(WAIT),opcode=frame.dword(Frame.OPCODE);frame.setPreviousOpcode(opcode);
        if(wait==-1){frame.cursor=dword(PC);frame.phase=Frame.READY;return true;}
        dword(LAST_OPCODE,opcode);frame.phase=Frame.EXITED;return false;
    }
    /**415731 return1 bypasses prev/43D414 publication completely. */
    public void interruptedReturn(Frame frame)throws IOException{if(frame==null||frame.phase!=Frame.DISPATCH)throw new IOException("PC1 interrupted return requires dispatched locals");frame.phase=Frame.INTERRUPTED;}
}
