package org.circus.core;
import java.io.*;
import java.util.*;

/** 405420 WinMain. Completed native calls keep their original failure prefix;
 *there is no invented cleanup on early returns. Physical APIs and unknown
 *engine leaves are required and run synchronously on their owning thread. */
public final class Pc1Startup {
    /** Pure staged validation/rebinding, never a second CreateWindow/open.
     *The surrounding bundle commits physical/canonical owners atomically. */
    public interface Adoption {long adopt(String kind,long savedHandle)throws IOException;}
    static long adopt(Adoption adoption,String kind,long handle)throws IOException{if(adoption==null)throw new IOException("PC1 startup requires physical "+kind+" adoption");long adopted=adoption.adopt(kind,handle);if(adopted==0)throw new IOException("PC1 startup physical "+kind+" adoption failed");return adopted;}
    static long adoptNonzero(Adoption adoption,String kind,long handle)throws IOException{return handle==0?0:adopt(adoption,kind,handle);}
    public interface Services extends Pc1StartupRegistry.Services,Pc1StartupScreens.Services,Pc1StartupWindow.Services {
        long createMutex(int rawName,boolean initialOwner)throws IOException;
        int lastError()throws IOException;
        void mutex(long value)throws IOException;long mutex()throws IOException;
        void closeHandle(long value)throws IOException;
        int uninstall()throws IOException; //complete40EF50, including its file/registry consumers
        int messageBox(long parent,int rawText,int rawTitle,int flags)throws IOException;
        long loadIcon(long instance,int resource)throws IOException;
        long loadCursor(long instance,int resource)throws IOException;
        /** WNDCLASS:style0/proc405E50/extra0+0/background0/menu0. */
        int registerClass(long instance,long icon,long cursor,int rawClass)throws IOException;
        int coInitialize()throws IOException;
        /** Actual CRT exit(1); this method must not return. */
        void fatalExit(int code)throws IOException;
        /** Actual GetModuleFileNameA(NULL,destination,capacity), no guessedroot. */
        void moduleFileName(int destination,int capacity)throws IOException;
        void currentDirectory(int rawDirectory)throws IOException;
        long processHeap()throws IOException;void heap(long heap)throws IOException;
        /**40ED00 _lcreat(saveRoot+\\savetest,0), close, DeleteFile; failure
         *MessageBox30. This may only target explicitly owned runtime storage. */
        int testSaveDirectory(long parent)throws IOException;
        int loadNames()throws IOException; //41ABF0, raw catalog owner
        /**CreateFontA exact common values:esc/orient/weight/italic/underline/
         *strike0,charset134,out4,clip32,quality0,pitch4,live face bytes. */
        long createFont(int height,int width,int rawFace)throws IOException;
        void font(int nativeAddress,long handle)throws IOException;long font(int nativeAddress)throws IOException;
        void instance(long handle)throws IOException;
        void initializeFrame()throws IOException; //411480
        /**All-thread GetMessageA(NULL,0,0). Write actual MSG output, including
         *partial/absent output on -1. The native caller tests only ==0. */
        int getMessage(Message output)throws IOException;
        void translateMessage(Message message)throws IOException;
        void dispatchMessage(Message message)throws IOException;
        void closeAudio()throws IOException; //41CDA0
        void deleteObject(long handle)throws IOException;
        void coUninitialize()throws IOException;
    }
    /** Known native 28-byte x86 MSG input/output. It is not a saved Windows
     *queue or a fabricated pointer. Physical HWND is handled separately. */
    public static final class Message {
        private final byte[] bytes=new byte[28],known=new byte[28];
        private long hwnd,wideWParam,wideLParam;private boolean hwndKnown,wideWKnown,wideLKnown;
        /** Actual x64 Windows adapters supply HWND here, never by truncating
         *it into the original x86 MSG DWORD. NULL is a valid thread message. */
        public void window(long value){hwnd=value;hwndKnown=true;Arrays.fill(known,0,4,(byte)0);Arrays.fill(bytes,0,4,(byte)0);}
        public long window()throws IOException{if(!hwndKnown)throw new IOException("PC1 GetMessage unknown physical HWND");return hwnd;}
        /** Offsets4..27 are the x86 payload. A complete supplied DWORD0 is
         *accepted only as an explicitly observed x86 physical HWND fixture. */
        public void write(int offset,byte[] raw,int from,int count)throws IOException{if(raw==null||offset<0||count<0||offset>28-count||from<0||from>raw.length-count)throw new IOException("PC1 MSG output bounds");if(count>0&&offset<4){hwndKnown=false;hwnd=0;}if(count>0&&offset<12&&offset+count>8){wideWKnown=false;wideWParam=0;}if(count>0&&offset<16&&offset+count>12){wideLKnown=false;wideLParam=0;}System.arraycopy(raw,from,bytes,offset,count);Arrays.fill(known,offset,offset+count,(byte)1);if(offset<4&&known[0]!=0&&known[1]!=0&&known[2]!=0&&known[3]!=0){hwnd=((bytes[0]&255L)|(bytes[1]&255L)<<8|(bytes[2]&255L)<<16|(bytes[3]&255L)<<24);hwndKnown=true;}}
        public void put(int offset,int value)throws IOException{for(int n=0;n<4;n++)write(offset+n,new byte[]{(byte)(value>>>(n*8))},0,1);}
        public int get(int offset)throws IOException{if(offset<0||offset>24)throw new IOException("PC1 MSG field bounds");int result=0;for(int n=0;n<4;n++){if(known[offset+n]==0)throw new IOException("PC1 GetMessage unknown MSG byte "+(offset+n));result|=(bytes[offset+n]&255)<<(8*n);}return result;}
        public byte[] bytes(){return bytes.clone();}public byte[] known(){return known.clone();}
        /** Physical ABI transport. Wide parameters have no fabricated x86
         *DWORD projection; raw get(8/12) stays unknown until explicitly mapped.
         *The installed Windows SDK exposes HWND/message/parameters/time/POINT. */
        public void physical(long[] packet)throws IOException{
            if(packet==null||packet.length!=7)throw new IOException("PC1 physical MSG packet");
            for(int at:new int[]{1,4})if((packet[at]&0xffffffffL)!=packet[at])throw new IOException("PC1 physical MSG unsigned field");for(int at:new int[]{5,6})if(packet[at]!=(int)packet[at])throw new IOException("PC1 physical MSG signed point");
            window(packet[0]);Arrays.fill(known,0,4,(byte)0);Arrays.fill(bytes,0,4,(byte)0);put(4,(int)packet[1]);
            if(packet[2]==(packet[2]&0xffffffffL))put(8,(int)packet[2]);else{wideWKnown=true;wideWParam=packet[2];Arrays.fill(known,8,12,(byte)0);Arrays.fill(bytes,8,12,(byte)0);}
            if(packet[3]==(long)(int)packet[3])put(12,(int)packet[3]);else{wideLKnown=true;wideLParam=packet[3];Arrays.fill(known,12,16,(byte)0);Arrays.fill(bytes,12,16,(byte)0);}
            put(16,(int)packet[4]);put(20,(int)packet[5]);put(24,(int)packet[6]);
        }
        public long[] physical()throws IOException{return new long[]{window(),get(4)&0xffffffffL,wideWKnown?wideWParam:get(8)&0xffffffffL,wideLKnown?wideLParam:(long)get(12),get(16)&0xffffffffL,get(20),get(24)};}
        /**PostQuitMessage takes an int. Its sign-extended physical WPARAM
         *stays intact for OS transport while the engine returns that int.*/
        public int exitCode()throws IOException{if(wideWKnown&&get(4)==0x12&&wideWParam==(long)(int)wideWParam)return (int)wideWParam;return get(8);}
        public void invalidatePhysical(){Arrays.fill(bytes,(byte)0);Arrays.fill(known,(byte)0);hwnd=wideWParam=wideLParam=0;hwndKnown=wideWKnown=wideLKnown=false;}
        void save(DataOutput out)throws IOException{out.write(bytes);out.write(known);out.writeBoolean(hwndKnown);out.writeLong(hwnd);out.writeBoolean(wideWKnown);out.writeLong(wideWParam);out.writeBoolean(wideLKnown);out.writeLong(wideLParam);}
        void load(DataInput in,Adoption adoption)throws IOException{in.readFully(bytes);in.readFully(known);for(byte b:known)if(b!=0&&b!=1)throw new IOException("PC1 MSG known mask");hwndKnown=in.readBoolean();hwnd=in.readLong();if(hwndKnown)hwnd=adoptNonzero(adoption,"message-window",hwnd);wideWKnown=in.readBoolean();wideWParam=in.readLong();wideLKnown=in.readBoolean();wideLParam=in.readLong();if(!wideWKnown&&wideWParam!=0||!wideLKnown&&wideLParam!=0)throw new IOException("PC1 physical MSG absent field payload");for(int n=8;n<16;n++)if((n<12?wideWKnown:wideLKnown)&&known[n]!=0)throw new IOException("PC1 wide MSG parameter has conflicting DWORD authority");if(wideWKnown||wideLKnown){if(adoption==null)throw new IOException("PC1 wide MSG requires physical parameter adoption");if(wideWKnown)wideWParam=adoption.adopt("message-wparam",wideWParam);if(wideLKnown)wideLParam=adoption.adopt("message-lparam",wideLParam);}}
    }
    private static final int[] FONT_ADDRESS={0x94c648,0x94c640,0x94c644,0x94c64c,0x94c650};
    private static final int[] FONT_HEIGHT={44,56,24,64,80},FONT_WIDTH={22,28,12,32,40};
    private final Pc1NativeMemory memory;private final Services io;private final Pc1StartupRegistry registry;private final Pc1StartupScreens screens;private final Pc1StartupWindow window;
    public Pc1Startup(Pc1NativeMemory memory,Services services)throws IOException{if(memory==null||services==null)throw new IOException("PC1 startup requires canonical bus and every native service");this.memory=memory;io=services;registry=new Pc1StartupRegistry(memory,io);screens=new Pc1StartupScreens(memory,io);window=new Pc1StartupWindow(memory,io);}
    public Frame begin(long instance,boolean previousInstance,byte[] rawCommandLine,int show)throws IOException{return new Frame(instance,previousInstance,rawCommandLine,show);}
    public final class Frame {
        private int phase,show,result,fontIndex;private boolean previous,busy;private long instance;private byte[] command;
        private Pc1StartupRegistry.Frame registryFrame;private Pc1StartupScreens.Frame screenFrame;private Pc1StartupWindow.Frame windowFrame;private Message message=new Message();
        private Frame(long instance,boolean previous,byte[] command,int show)throws IOException{if(command==null||command.length==0||command.length>1_048_576||command[command.length-1]!=0)throw new IOException("PC1 startup command line needs known NUL-terminated backing");this.instance=instance;this.previous=previous;this.command=command.clone();this.show=show;}
        public boolean active(){return phase>=0&&phase<24;}public int phase(){return phase;}public boolean runningMessages(){return phase>=18&&phase<=20;}
        public int result()throws IOException{if(phase!=24)throw new IOException("PC1 startup has not returned");return result;}
        public void step()throws IOException{
            if(!active()||busy)throw new IOException("PC1 startup frame not active/reentrant");busy=true;
            try{switch(phase){
            case 0:long mutex=io.createMutex(0x43030c,false);io.mutex(mutex);if(mutex==0||io.lastError()==183)finish(0);else phase=1;break;
            case 1:if(uninstallCommand()){int value=io.uninstall();if(value==-1)io.messageBox(0,0x4302cc,0x4302f4,0);io.closeHandle(io.mutex());finish(0);}else phase=2;break;
            case 2:if(!previous){long icon=io.loadIcon(instance,105);long cursor=io.loadCursor(0,32512);if((io.registerClass(instance,icon,cursor,0x430314)&65535)==0){finish(0);break;}}phase=3;break;
            case 3:if(io.coInitialize()<0){io.fatalExit(1);throw new IOException("PC1 native exit(1) unexpectedly returned");}phase=4;break;
            case 4:io.moduleFileName(Pc1StartupMemory.MODULE,256);phase=5;break;
            case 5:byte[] path=cString(memory,Pc1StartupMemory.MODULE);int last=-1;for(int n=0;n<path.length;n++)if(path[n]=='\\')last=n;if(last<0)throw new IOException("PC1 module path strrchr returned NULL before native write");memory.writeAddress(Pc1StartupMemory.MODULE+last,0);io.currentDirectory(Pc1StartupMemory.MODULE);phase=6;break;
            case 6:io.heap(io.processHeap());registryFrame=registry.begin();phase=7;break;
            case 7:if(registryFrame.active())registryFrame.step();else{registryFrame.result();registryFrame=null;phase=8;}break;
            case 8:if(io.testSaveDirectory(0)==0)finish(0);else{screenFrame=screens.begin();phase=9;}break;
            //405532..5546 currentCHS patch leaves EAX=0x9727, always nonzero.
            case 9:if(screenFrame.active())screenFrame.step();else{int checked=screenFrame.result();screenFrame=null;if(checked!=0)finish(0);else phase=10;}break;
            case 10:if(io.loadNames()!=0){io.messageBox(0,0x4302a0,0x4302f4,0);finish(0);}else{fontIndex=0;phase=11;}break;
            case 11:io.font(FONT_ADDRESS[fontIndex],io.createFont(FONT_HEIGHT[fontIndex],FONT_WIDTH[fontIndex],0x430290));if(++fontIndex==5)phase=12;break;
            case 12:io.instance(instance);io.initializeFrame();windowFrame=window.begin(instance,show);phase=13;break;
            case 13:if(windowFrame.active())windowFrame.step();else{int started=windowFrame.result();windowFrame=null;if(started==0)finish(0);else phase=18;}break;
            case 18:if(io.getMessage(message)==0)phase=21;else phase=19;break;
            case 19:io.translateMessage(message);phase=20;break;
            case 20:io.dispatchMessage(message);phase=18;break;
            case 21:io.closeAudio();fontIndex=0;phase=22;break;
            case 22:io.deleteObject(io.font(FONT_ADDRESS[fontIndex]));if(++fontIndex==5)phase=23;break;
            case 23:io.coUninitialize();finish(message.exitCode());break; //MSG.wParam
            default:throw new IOException("PC1 startup invalid phase");
            }}catch(IOException|RuntimeException failure){phase=-1;throw failure;}finally{busy=false;}
        }
        private boolean uninstallCommand()throws IOException{for(int n=0;n<2;n++){int input=n<command.length?command[n]&255:0;int expected=memory.readAddress(0x430308+n);if(input!=expected)return false;if(input==0)return true;}return true;}
        private void finish(int value){result=value;phase=24;}
        /** Snapshot local engine frame only. The caller must also atomically
         *save canonical memory and stage/adopt its physical platform owners. */
        public void save(DataOutput out)throws IOException{if(busy||phase<0)throw new IOException("PC1 startup outside known snapshot boundary");out.writeInt(0x50315354);out.writeInt(3);out.writeInt(phase);out.writeInt(show);out.writeInt(result);out.writeInt(fontIndex);out.writeBoolean(previous);out.writeLong(instance);out.writeInt(command.length);out.write(command);message.save(out);out.writeBoolean(registryFrame!=null);if(registryFrame!=null)registryFrame.save(out);out.writeBoolean(screenFrame!=null);if(screenFrame!=null)screenFrame.save(out);out.writeBoolean(windowFrame!=null);if(windowFrame!=null)windowFrame.save(out);}
    }
    public Frame load(DataInput in,Adoption adoption)throws IOException{
        if(in.readInt()!=0x50315354||in.readInt()!=3)throw new IOException("PC1 startup frame format");int phase=in.readInt(),show=in.readInt(),result=in.readInt(),fontIndex=in.readInt();boolean previous=in.readBoolean();long instance=adoptNonzero(adoption,"instance",in.readLong());int size=in.readInt();if(size<1||size>1_048_576)throw new IOException("PC1 startup command backing size");byte[] command=new byte[size];in.readFully(command);Frame f=new Frame(instance,previous,command,show);f.phase=phase;f.result=result;f.fontIndex=fontIndex;f.message.load(in,adoption);if(in.readBoolean())f.registryFrame=registry.load(in,adoption);if(in.readBoolean())f.screenFrame=screens.load(in,adoption);if(in.readBoolean())f.windowFrame=window.load(in,adoption);
        if(phase<0||phase>24||phase>=14&&phase<=17||fontIndex<0||fontIndex>5||((phase==11||phase==22)&&fontIndex==5)||(phase==7)!=(f.registryFrame!=null)||(phase==9)!=(f.screenFrame!=null)||(phase==13)!=(f.windowFrame!=null))throw new IOException("PC1 startup saved phase/child contract");return f;
    }
    static int get(Pc1NativeMemory memory,int address)throws IOException{return memory.readAddress(address)|memory.readAddress(address+1)<<8|memory.readAddress(address+2)<<16|memory.readAddress(address+3)<<24;}
    static void put(Pc1NativeMemory memory,int address,int value)throws IOException{for(int n=0;n<4;n++)memory.readAddress(address+n);for(int n=0;n<4;n++)memory.writeAddress(address+n,value>>>(n*8));}
    static byte[] cString(Pc1NativeMemory memory,int address)throws IOException{return cString(memory,address,1_048_576);}
    static byte[] cString(Pc1NativeMemory memory,int address,int maximum)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<maximum;n++){int value=memory.readAddress(address+n);if(value==0)return out.toByteArray();out.write(value);}throw new IOException("PC1 native string passes known local backing");}
    static void copyCString(Pc1NativeMemory memory,int target,int source)throws IOException{int length=cString(memory,source).length+1;copyForward(memory,target,source,length);}
    static void appendCString(Pc1NativeMemory memory,int target,int source)throws IOException{copyCString(memory,target+cString(memory,target).length,source);}
    // Native REP MOVSD reads a whole four-byte unit before its four writes;
    //overlapping admitted aliases therefore are not a Java memcpy temporary.
    private static void copyForward(Pc1NativeMemory memory,int target,int source,int length)throws IOException{int n=0;for(;n+4<=length;n+=4){int value=get(memory,source+n);put(memory,target+n,value);}for(;n<length;n++)memory.writeAddress(target+n,memory.readAddress(source+n));}
}
