package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Required Win32 HWND/clock/queue and406ED0 physical bootstrap. The default
 *window is hidden and self-authored; no original EXE/window procedure runs.
 *Engine messages belong to the creating-thread NativeDispatcher. */
public final class Pc1WindowsWindow implements AutoCloseable {
    public interface NativeDispatcher {
        /** Called synchronously for every actual WndProc invocation, including
         *CreateWindowEx startup before this constructor/hwnd() has returned.
         *Use the supplied HWND. Known unimplemented engine branches must throw;
         *unhandled is for actual standard/default Windows behavior only. */
        Dispatch dispatch(long hwnd,int message,long wParam,long lParam)throws IOException;
        /** Exact canonical94C688 value, read AFTER the complete405710 drain.
         *WM_QUIT itself is not a made-up second quit flag. */
        int quitFlag()throws IOException;
    }
    public static final class Dispatch {
        final boolean handled;final long result;
        private Dispatch(boolean handled,long result){this.handled=handled;this.result=result;}
        public static Dispatch handled(long result){return new Dispatch(true,result);}
        public static Dispatch unhandled(){return new Dispatch(false,0);}
    }
    /** Live canonical flags/dirty/front providers. Each callback is invoked at
     *the proved native point, including flag rereads after BeginPaint. */
    public interface PaintHost {
        int global(int nativeAddress)throws IOException;
        byte[] frontBgr24()throws IOException;
        int[] dirtyForPresent()throws IOException; //4075E0, mutates canonical dirty
        void resetDirty()throws IOException; //407680, AFTER EndPaint
        /**403360, separate actual accelerated owner. EndPaint already happened
         *when this is called. Do not replace with normal GDI output. */
        void accelerated(long nativeDib,int[] osUpdateRect)throws IOException;
        /** True only when image0 actually borrows this same physical DIB. */
        default boolean sharesDib(long physicalAddress)throws IOException{return false;}
    }
    /** Whole native WM_PAINT branch. Call only from its actual WndProc
     *callback; return this handling decision to NativeDispatcher unchanged. */
    public synchronized Dispatch paintCurrent(PaintHost host)throws IOException{requireEngineReady();if(host==null)throw new IOException("PC1 native paint canonical host required");long[] result=paintNative(handle,host);if(result==null||result.length!=22)throw new IOException("PC1 native paint result packet");return result[0]==0?Dispatch.unhandled():Dispatch.handled(result[1]);}
    /** Last actual paint API results, even after a required-leaf fault. This
     *diagnostic journal is not a second canonical dirty/global owner. */
    public synchronized long[] lastPaintReport()throws IOException{requireOpen();return paintReportNative(handle);}
    private static Path loadedLibrary;
    private final Thread owner=Thread.currentThread();
    private final NativeDispatcher dispatcher;
    private long handle,window;
    private int callbacks;private boolean capsuleBackend,physicalRetired;private State stagedPhysical;private Pc1WindowsWindow adoptionSource;private boolean selectionsRestored;
    public interface FrontCreated {void created(NativeRgbImage actualDib)throws IOException;}
    public interface FrontAllocated {
        void allocated(Pc1HeapLease actualDib)throws IOException;
        /**401000 always publishes its known DWORD, even if CreateDIBSection
         *failed and its output parameter still contains the original HWND. */
        default void allocatedRaw(int rawBits,Pc1HeapLease actualDib)throws IOException{if(actualDib==null)throw new IOException("PC1 raw401000 publication requires canonical metadata consumer");allocated(actualDib);}
    }
    private FrontCreated pendingFront;
    private FrontAllocated pendingSharedFront;
    private Pc1HeapLease dibLease;
    private Pc1OsWords osWords;
    private final java.util.Map<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> osBindings=new java.util.LinkedHashMap<>();
    private long nextOsObject;private final Map<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> selectedBindings=new LinkedHashMap<>();
    private java.util.List<String> osJournal;
    /** Bounded, opt-in audit trace; never a second raw cell/physical owner. */
    public synchronized void traceOsWords(boolean enable)throws IOException{requireOpen();osJournal=enable?new java.util.ArrayList<>():null;}
    public synchronized java.util.List<String> osWordTrace()throws IOException{requireOpen();return osJournal==null?java.util.Collections.emptyList():new java.util.ArrayList<>(osJournal);}
    private void traceWord(String value)throws IOException{if(osJournal!=null){if(osJournal.size()>=10000)throw new IOException("PC1 explicit OS audit journal limit");osJournal.add(value);}}

    /** Association only: no eager global read, OS call or manufactured MOV. */
    public synchronized void bindOsWords(Pc1OsWords words)throws IOException{requireOpen();if(words==null||osWords!=null&&osWords!=words)throw new IOException("PC1 window OS words require a unique canonical owner");try{bindWordsNative(handle);}catch(UnsatisfiedLinkError missing){throw new IOException("PC1 bound Window requires matching OS-word JNI backend",missing);}osWords=words;}
    /** Explicit staged-world association after its typed registry/layout has
     *been validated. Physical HWND/DC/DIB objects are retained, not recreated. */
    public synchronized void rebindOsWords(Pc1OsWords staged)throws IOException{requireSnapshotBoundary();if(staged==null||osWords==null)throw new IOException("PC1 staged OS words rebind requires prior bound provider");osWords=staged;}
    public synchronized long selectedBitmap(long physicalDc)throws IOException{requireOpen();return objectNative(handle,4,physicalDc,0);}
    public synchronized Pc1OsRegistry.Binding rebindOsObject(Pc1OsRegistry.SavedObject saved)throws IOException{requireOpen();Pc1OsRegistry.Binding binding=osBindings.get(saved.owner);if(binding==null)throw new IOException("PC1 Window saved OS lifetime is not available: "+saved.owner.id);return binding;}
    public synchronized long currentMainWindow()throws IOException{requireEngineReady();return osWords==null?window:resolveRawNative(osWords.rawWord(Pc1OsWords.HWND),Pc1OsRegistry.Domain.USER.ordinal());}
    public synchronized long currentModuleInstance()throws IOException{requireEngineReady();return osWords==null?moduleNative(handle):resolveRawNative(osWords.rawWord(Pc1OsWords.INSTANCE),Pc1OsRegistry.Domain.MODULE.ordinal());}
    /** Caller owns the original MOV point; this method never guesses one from
     *an earlier API return (notably INSTANCE and CreateWindow). */
    public synchronized int publishOsWord(int address,Pc1OsRegistry.Kind kind,long physical)throws IOException{requireEngineReady();if(kind==null)throw new IOException("PC1 Window publication kind missing");return publishAtNative(address,kind.ordinal(),physical);}
    private int rawAtNative(int address)throws IOException{if(osWords==null)throw new IOException("PC1 window canonical OS words required");int value=osWords.rawWord(address);traceWord("read:"+Integer.toHexString(address)+":"+Integer.toHexString(value));return value;}
    private void storeAtNative(int address,int value,int bytes)throws IOException{if(osWords==null||bytes<1||bytes>4)throw new IOException("PC1 window native raw store owner missing");for(int n=0;n<bytes;n++)osWords.nativeMemory().writeAddress(address+n,value>>>(8*n));traceWord("store:"+Integer.toHexString(address)+":"+bytes+":"+Integer.toHexString(value));}
    private long resolveRawNative(int raw,int domain)throws IOException{
        if(osWords==null||domain<0||domain>=Pc1OsRegistry.Domain.values().length)throw new IOException("PC1 window scalar domain missing");traceWord("resolve:"+domain+":"+Integer.toHexString(raw));Pc1OsRegistry.Resolution value=osWords.registry().lookup(raw,Pc1OsRegistry.Domain.values()[domain]);
        if(value.status==Pc1OsRegistry.Status.NULL)return 0;if(value.status==Pc1OsRegistry.Status.SENTINEL)return -1;
        if(value.status==Pc1OsRegistry.Status.LIVE)return value.physicalHandle();
        // A captured HWND may be destroyed by the preceding DispatchMessage.
        // PeekMessage still receives that exact numeric value, never NULL.
        if(value.status==Pc1OsRegistry.Status.CLOSED&&domain==Pc1OsRegistry.Domain.USER.ordinal())return value.object.binding().physicalHandle();
        if(value.status==Pc1OsRegistry.Status.CLOSED&&domain==Pc1OsRegistry.Domain.GDI.ordinal())return value.object.binding().physicalHandle(); // exact retained numeric, never fakeNULL (NULL is valid to some GDI APIs)
        throw new IOException("PC1 window required scalar "+value.status+" raw0x"+Integer.toHexString(raw));
    }
    private final class WindowBinding implements Pc1OsRegistry.GdiDcBinding {
        final long physical;final int role;final Pc1OsRegistry.Kind kind;final Pc1GuestAddressSpace.ExternalOwner key;boolean deleted;
        WindowBinding(long physical,Pc1OsRegistry.Kind kind,Pc1GuestAddressSpace.ExternalOwner key)throws IOException{this(physical,kind,key,capsuleBackend?capsuleRoleOfNative(handle,physical,kind.domain.ordinal()):0,false);}
        WindowBinding(long physical,Pc1OsRegistry.Kind kind,Pc1GuestAddressSpace.ExternalOwner key,int role,boolean deleted){this.physical=physical;this.kind=kind;this.key=key;this.role=role;this.deleted=deleted;}
        public long physicalHandle(){return physical;}
        public boolean live()throws IOException{if(deleted||handle==0)return false;boolean actual=objectNative(handle,0,physical,kind.ordinal())!=0;if(capsuleBackend&&!actual)deleted=true;return actual;}
        public String contractId(){return "PC1.Win32.owned.scalar."+(capsuleBackend?"v2/":"v1/")+kind.name();}
        public byte[] restorationProof()throws IOException{
            if(capsuleBackend)return physicalProof(this);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(kind.ordinal());out.writeLong(physical);out.writeLong(handle);out.writeBoolean(deleted);
            if(live()&&kind==Pc1OsRegistry.Kind.DC){for(int operation=5;operation<=8;operation++){long selected=objectNative(handle,operation,physical,0);out.writeLong(selected);Pc1OsRegistry.Scalar ref=osWords.registry().findPhysical(Pc1OsRegistry.Domain.GDI,selected);out.writeBoolean(ref!=null);if(ref!=null){out.writeUTF(ref.owner.type);out.writeUTF(ref.owner.id);}byte[] descriptor=objectDescriptorNative(handle,selected);out.writeInt(descriptor.length);out.write(descriptor);}}
            if(live()&&kind==Pc1OsRegistry.Kind.BITMAP){byte[] proof=bitmapProofNative(handle,physical);try{out.write(java.security.MessageDigest.getInstance("SHA-256").digest(proof));}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}}
            return bytes.toByteArray();
        }
        public boolean deleteObject()throws IOException{requireOpen();if(deleted)return false;boolean result=objectNative(handle,1,physical,kind.ordinal())!=0;if(result&&((capsuleBackend&&role>=2&&role<=5)||objectNative(handle,0,physical,kind.ordinal())==0))deleted=true;if(result&&kind==Pc1OsRegistry.Kind.BITMAP&&dibLease!=null&&!objectNativeLiveDib()){dibLease.retireBorrowedView();}return result;}
        public boolean deleteDc()throws IOException{requireOpen();if(deleted)return false;boolean result=objectNative(handle,2,physical,kind.ordinal())!=0;if(result)deleted=true;return result;}
    }
    private void releasedDcNative(long physical)throws IOException{for(Pc1OsRegistry.Binding binding:osBindings.values())if(binding instanceof WindowBinding){WindowBinding value=(WindowBinding)binding;if(value.kind==Pc1OsRegistry.Kind.DC&&value.physical==physical)value.deleted=true;}if(osWords!=null)osWords.registry().refreshClosed();}
    private boolean objectNativeLiveDib()throws IOException{return objectNative(handle,3,0,0)!=0;}
    private int publishAtNative(int address,int kindOrdinal,long physical)throws IOException{
        if(osWords==null)throw new IOException("PC1 window publication requires OS words");
        if(physical==0||physical==-1){osWords.rawWord(address,(int)physical);traceWord("publish:"+Integer.toHexString(address)+":"+Integer.toHexString((int)physical));return(int)physical;}
        Pc1OsRegistry.Kind kind=Pc1OsRegistry.Kind.values()[kindOrdinal];Pc1OsRegistry registry=osWords.registry();Pc1OsRegistry.Scalar scalar=registry.findPhysical(kind.domain,physical);
        if(scalar==null){Pc1GuestAddressSpace.ExternalOwner key=new Pc1GuestAddressSpace.ExternalOwner("PC1.Win32.Window",Long.toHexString(handle)+"/"+(++nextOsObject));WindowBinding binding=new WindowBinding(physical,kind,key);osBindings.put(key,binding);scalar=osWords.publish(address,kind,key,binding);}else osWords.rawWord(address,scalar.rawWord);
        traceWord("publish:"+Integer.toHexString(address)+":"+Integer.toHexString(scalar.rawWord));return scalar.rawWord;
    }
    private int wordForPhysicalNative(long physical,int kind)throws IOException{
        if(physical==0||physical==-1)return(int)physical;if(osWords==null)throw new IOException("PC1 window scalar association required");Pc1OsRegistry.Kind type=Pc1OsRegistry.Kind.values()[kind];Pc1OsRegistry.Scalar existing=osWords.registry().findPhysical(type.domain,physical);if(existing!=null)return existing.rawWord;
        Pc1GuestAddressSpace.ExternalOwner key=new Pc1GuestAddressSpace.ExternalOwner("PC1.Win32.Window",Long.toHexString(handle)+"/"+(++nextOsObject));WindowBinding binding=new WindowBinding(physical,type,key);osBindings.put(key,binding);return osWords.registry().register(type,key,binding).rawWord;
    }
    private void frontRawCreatedNative(int priorRaw,long actualBits,boolean untouched)throws IOException{
        if(pendingSharedFront==null)throw new IOException("PC1 raw front callback without401000 consumer");callbacks++;try{Pc1HeapLease lease=null;int raw;
            if(untouched)raw=priorRaw;else if(actualBits==0)raw=0;else {lease=borrowDib();Pc1GuestAddressSpace space=osWords.registry().guestAddressSpace();raw=space.containsLease(lease)?space.addressOf(lease):space.mapBorrowed(lease,new Pc1GuestAddressSpace.ExternalOwner("PC1.Win32.DIB",Long.toHexString(handle)));}
            pendingSharedFront.allocatedRaw(raw,lease);
        }finally{callbacks--;}
    }

    public Pc1WindowsWindow(Path dll,NativeDispatcher dispatcher)throws IOException{
        this(dll,dispatcher,false);
    }
    /** Creates only the JNI/creating-thread owner. No class, HWND, DC, bitmap,
     *ShowWindow or startup call has happened when this returns. */
    public static Pc1WindowsWindow deferred(Path dll,NativeDispatcher dispatcher)throws IOException{return new Pc1WindowsWindow(dll,dispatcher,true);}
    private Pc1WindowsWindow(Path dll,NativeDispatcher dispatcher,boolean deferred)throws IOException{
        if(dll==null||dispatcher==null)throw new IOException("PC1 window native library/dispatcher required");this.dispatcher=dispatcher;Path selected=dll.toAbsolutePath().normalize();
        synchronized(Pc1WindowsWindow.class){if(loadedLibrary==null){if(!Files.isRegularFile(selected))throw new FileNotFoundException(selected.toString());try{System.load(selected.toString());}catch(UnsatisfiedLinkError failure){throw new IOException("PC1 window native library load failed",failure);}loadedLibrary=selected;}else if(!loadedLibrary.equals(selected))throw new IOException("PC1 window native library already loaded from a different path");}
        handle=deferred?deferredNative(this):openNative(this);if(handle==0)throw new IOException("PC1 window owner creation failed");try{capsuleBackend=capsuleAbiNative()==1;}catch(UnsatisfiedLinkError legacy){capsuleBackend=false;}boolean accepted=false;try{window=hwndNative(handle);accepted=true;}finally{if(!accepted){closeNative(handle);handle=0;}}
    }
    private static byte[] nativeString(byte[] value)throws IOException{if(value==null)throw new IOException("PC1 Windows raw C string missing");int n=0;while(n<value.length&&value[n]!=0)n++;if(n>=1024*1024)throw new IOException("PC1 Windows raw C string host bound");return java.util.Arrays.copyOf(value,n+1);}
    public synchronized long moduleInstance()throws IOException{requireOpen();return moduleNative(handle);}
    public synchronized long loadIcon(long resourceInstance,int ordinal)throws IOException{requireOpen();return resourceNative(handle,0,resourceInstance,ordinal);}
    public synchronized long loadCursor(long resourceInstance,int ordinal)throws IOException{requireOpen();return resourceNative(handle,1,resourceInstance,ordinal);}
    public synchronized int registerClass(long instance,byte[] rawClass,long icon,long cursor)throws IOException{requireEngineReady();return registerNative(handle,instance,nativeString(rawClass),icon,cursor);}
    /** Native failure0 is returned, preserving the subsequent menu calls. */
    public synchronized long createWindow(int extendedStyle,byte[] rawClass,byte[] rawTitle,int style,int x,int y,int width,int height,long instance)throws IOException{requireEngineReady();window=createNative(handle,extendedStyle,nativeString(rawClass),nativeString(rawTitle),style,x,y,width,height,instance);return window;}
    public synchronized int metric(int index)throws IOException{requireOpen();return metricNative(handle,index);}
    public synchronized int[] adjustWindowRect(int l,int t,int r,int b,int style,boolean menu)throws IOException{requireOpen();return adjustNative(handle,l,t,r,b,style,menu);}
    public synchronized long systemMenu(long hwnd,boolean revert)throws IOException{requireOpen();return systemMenuNative(handle,hwnd,revert);}
    public synchronized int appendMenu(long menu,int flags,int id,byte[] rawText)throws IOException{requireEngineReady();return appendNative(handle,menu,flags,id,nativeString(rawText));}
    public synchronized int showWindow(long hwnd,int command)throws IOException{requireEngineReady();return showNative(handle,hwnd,command);}
    public synchronized int updateWindow(long hwnd)throws IOException{requireEngineReady();return updateAtNative(handle,hwnd);}
    public synchronized void mainWindow(long hwnd)throws IOException{requireEngineReady();mainNative(handle,hwnd);window=hwnd;if(osWords!=null)publishAtNative(Pc1OsWords.HWND,Pc1OsRegistry.Kind.WINDOW.ordinal(),hwnd);}
    public synchronized int enableIme(long hwnd,int enable)throws IOException{requireEngineReady();return imeNative(handle,hwnd,enable);}
    /**406ED0 is separate and occurs AFTER font/audio/optional DD startup. */
    public synchronized void initializeFront()throws IOException{requireEngineReady();initializeFrontNative(handle,false);}
    /** Publish401000's actual DIB-backed image0 before406ED0's black DDB
     *tail. Consumer failures preserve the already-created physical objects. */
    public synchronized void initializeFront(FrontCreated consumer)throws IOException{requireEngineReady();if(consumer==null||pendingFront!=null)throw new IOException("PC1 front publication requires unique consumer");pendingFront=consumer;try{initializeFrontNative(handle,true);}finally{pendingFront=null;}}
    private void frontCreatedNative(byte[] bytes)throws IOException{if(pendingFront==null)throw new IOException("PC1 native front callback without401000 consumer");callbacks++;try{pendingFront.created(NativeRgbImage.fromBgr24(800,600,2400,bytes));}finally{callbacks--;}}
    public synchronized void initializeFrontShared(FrontAllocated consumer)throws IOException{requireEngineReady();if(consumer==null||pendingSharedFront!=null||pendingFront!=null)throw new IOException("PC1 shared front publication requires unique consumer");pendingSharedFront=consumer;try{initializeFrontSharedNative(handle);}finally{pendingSharedFront=null;}}
    public synchronized void initializeFrontSharedAt(long capturedHwnd,FrontAllocated consumer)throws IOException{requireEngineReady();if(consumer==null||pendingSharedFront!=null||pendingFront!=null)throw new IOException("PC1 shared front publication requires unique consumer");pendingSharedFront=consumer;try{initializeFrontSharedAtNative(handle,capturedHwnd);}finally{pendingSharedFront=null;}}
    private void frontLeaseCreatedNative()throws IOException{if(pendingSharedFront==null)throw new IOException("PC1 native DIB lease callback without401000 consumer");callbacks++;try{pendingSharedFront.allocated(borrowDib());}finally{callbacks--;}}
    public synchronized int lastNativeError()throws IOException{requireOpen();return errorNative(handle);}
    private void requireEngineReady()throws IOException{requireOpen();if(stagedPhysical!=null)throw new IOException("PC1 staged Window world is not activated for engine calls");}
    private void requireOpen()throws IOException{if(Thread.currentThread()!=owner)throw new IOException("PC1 window services require their creating thread");if(handle==0)throw new IOException("PC1 window is not yet constructed or has closed; startup callback must use supplied HWND");if(physicalRetired)throw new IOException("PC1 physical window context retired after explicit HWND adoption");}
    private long[] dispatchNative(long hwnd,int message,long wParam,long lParam)throws IOException{if(Thread.currentThread()!=owner)throw new IOException("PC1 WndProc callback on wrong thread");callbacks++;try{Dispatch result=dispatcher.dispatch(hwnd,message,wParam,lParam);if(result==null)throw new IOException("PC1 native dispatcher returned no handling decision");return new long[]{result.handled?1:0,result.result};}finally{callbacks--;}}
    private int quitNative()throws IOException{callbacks++;try{return dispatcher.quitFlag();}finally{callbacks--;}}
    public synchronized long hwnd()throws IOException{requireOpen();return window;}
    /** Actual GetTickCount DWORD, including wrap; no nanosecond/reveal clock. */
    /** Actual SHORT bits, including the low pressed-since-query bit. No cache
     *or GetKeyState substitution; canonical rapid latch remains elsewhere. */
    public synchronized int asyncKeyState(int virtualKey)throws IOException{requireEngineReady();return asyncKeyNative(handle,virtualKey);}
    public synchronized int[] systemTime()throws IOException{requireOpen();return timeNative(handle);}
    /**4118F0 reads only SYSTEMTIME.wHour, not a cached preference/register. */
    public synchronized int localHour()throws IOException{return systemTime()[4];}
    public synchronized int tickCount()throws IOException{requireOpen();return tickNative(handle);}
    public synchronized int setTimer(int id,int rawInterval)throws IOException{requireEngineReady();return timerNative(handle,0,id,rawInterval);}
    /** Startup405A85 passes its captured CreateWindow return, rather than
     *reading the mutable main-window global again. */
    public synchronized int setTimerAt(long capturedHwnd,int id,int rawInterval)throws IOException{requireEngineReady();return timerAtNative(handle,capturedHwnd,id,rawInterval);}
    public synchronized int killTimer(int token)throws IOException{requireEngineReady();return timerNative(handle,1,token,0);}
    /**40AEC0 uses this same main HWND. Preserve the prior HWND and raw BOOL;
     *neither result is an engine-side success gate. */
    public synchronized long setCapture()throws IOException{requireEngineReady();return captureNative(handle,0);}
    public synchronized int releaseCapture()throws IOException{requireEngineReady();return (int)captureNative(handle,1);}
    public synchronized long captureWindow()throws IOException{requireEngineReady();return captureNative(handle,2);}
    public synchronized int updateWindow()throws IOException{requireEngineReady();return updateNative(handle);}
    public synchronized int invalidate(boolean erase)throws IOException{requireEngineReady();return invalidateNative(handle,erase);}
    /** Native407000 passes the actual 4075E0 RECT; do not widen it to NULL. */
    public synchronized int invalidate(int left,int top,int right,int bottom,boolean erase)throws IOException{requireEngineReady();return invalidateRectNative(handle,left,top,right,bottom,erase);}
    public synchronized int postMessage(int message,long wParam,long lParam)throws IOException{requireEngineReady();return postNative(handle,message,wParam,lParam);}
    /**405420 uses the all-thread GetMessageA(NULL,0,0), separately from
     *405710's main-HWND PeekMessage drain. Return -1 is preserved. */
    public synchronized int getMessage(Pc1Startup.Message output)throws IOException{requireEngineReady();if(output==null)throw new IOException("PC1 GetMessage output missing");long[] prior=null;try{prior=output.physical();}catch(IOException unknown){/*First native stack MSG is not initialized by the engine.*/}long[] packet=getMessageNative(handle,prior);if(packet==null||packet.length!=9)throw new IOException("PC1 physical GetMessage result packet");if(packet[1]!=0)output.physical(java.util.Arrays.copyOfRange(packet,2,9));else output.invalidatePhysical();return (int)packet[0];}
    public synchronized int translateMessage(Pc1Startup.Message message)throws IOException{requireEngineReady();if(message==null)throw new IOException("PC1 TranslateMessage input missing");return (int)messageActionNative(handle,0,message.physical());}
    public synchronized long dispatchMessage(Pc1Startup.Message message)throws IOException{requireEngineReady();if(message==null)throw new IOException("PC1 DispatchMessage input missing");return messageActionNative(handle,1,message.physical());}
    public synchronized int postThreadMessage(int message,long wParam,long lParam)throws IOException{requireEngineReady();return postThreadNative(handle,message,wParam,lParam);}
    public synchronized void postQuitMessage(int code)throws IOException{requireEngineReady();quitMessageNative(handle,code);}
    /** Native405710(hwnd): filter0 means this main HWND, not every thread
     *window. Each Translate/Dispatch completes before the next PeekMessage. */
    public synchronized int pumpMessages(long filterHwnd)throws IOException{requireEngineReady();return pumpNative(handle,filterHwnd);}
    public synchronized int pumpMessages()throws IOException{return pumpMessages(0);}
    /** Actual GetCursorPos and ScreenToClient results, with unscaled signed
     *coordinates. Packet:screenBOOL,screenX,screenY,clientBOOL,clientX,clientY. */
    public synchronized int[] cursorClient()throws IOException{requireEngineReady();return cursorNative(handle);}
    public synchronized int[] windowInfo()throws IOException{requireOpen();return infoNative(handle);}
    public synchronized long[] bootstrapTail()throws IOException{requireOpen();return tailNative(handle);}
    /** Actual selectedDDB readback converted by GDI to bottom-up24bpp; this
     *does not pretend hidden-window pixels are visible screen output. */
    public synchronized NativeRgbImage compatibleRgb()throws IOException{requireOpen();return NativeRgbImage.fromBgr24(800,600,2400,compatibleRgbNative(handle));}
    public synchronized int compatiblePixel(int x,int y)throws IOException{requireOpen();return compatiblePixelNative(handle,x,y);}
    /**40F810 GetDC(mainHWND), held in the external94BDC0 OS owner until
     *endNativePaint. The returned HDC is opaque and is never a native32 ID. */
    public synchronized long beginNativePaint()throws IOException{requireEngineReady();return beginDirectNative(handle);}
    /**406E40 copies precisely this signed RECT from canonical image0 through
     *the selectedDDB to that GetDC HDC. No WM_PAINT gates, dirty getter/reset,
     *invalidation, or accelerated branch belong to this native leaf.
     *Diagnostic packet:firstBlt,firstFlush,windowBlt,secondFlush,HDC,l,t,r,b. */
    public synchronized long[] paintRectangle(int left,int top,int right,int bottom,byte[] canonicalFrontBgr24)throws IOException{requireEngineReady();if(osWords==null&&(canonicalFrontBgr24==null||canonicalFrontBgr24.length!=1440000))throw new IOException("PC1 effect paint requires complete canonical800x600x24 front backing");return directRectangleNative(handle,left,top,right,bottom,canonicalFrontBgr24);}
    /**40F8C6 ReleaseDC only. The enclosing effects owner performs407680. */
    public synchronized int endNativePaint()throws IOException{requireEngineReady();return endDirectNative(handle);}
    public synchronized long[] bootstrapDetails()throws IOException{requireOpen();return detailsNative(handle);}
    public synchronized int[] clientRect()throws IOException{requireOpen();return rectNative(handle);}
    /** Actual independent DIB bytes, still NOT selected into the compatibleDC.
     *Explicitly import these bytes into the canonical compositor at bootstrap. */
    public synchronized NativeRgbImage dibRgb()throws IOException{requireOpen();return NativeRgbImage.fromBgr24(800,600,2400,dibNative(handle));}
    /** The actual DIB bytes, with lifetime checks on every access. No heap
     *guard is invented and the native64 address is never a32-bit object ID. */
    public synchronized Pc1HeapLease borrowDib()throws IOException{
        requireOpen();if(dibLease!=null){if(!dibLease.live())throw new IOException("PC1 borrowed DIB view has retired");return dibLease;}final long context=handle;final ByteBuffer bytes=dibBufferNative(handle);final long address=dibPointerNative(handle);if(bytes==null||!bytes.isDirect()||bytes.capacity()<=0||address==0)throw new IOException("PC1 actual DIB direct storage unavailable");
        final int extent=bytes.capacity();Pc1HeapLease.Storage storage=new Pc1HeapLease.Storage(){private void ready()throws IOException{requireOpen();if(handle!=context)throw new IOException("PC1 DIB context lifetime changed");}public int length(){return extent;}public int read(int offset)throws IOException{ready();return bytes.get(offset)&255;}public void write(int offset,int value)throws IOException{ready();bytes.put(offset,(byte)value);}};
        dibLease=Pc1HeapLease.borrowedExternal64(address,storage,"Win32 CreateDIBSection image0; owned window lifetime");return dibLease;
    }
    public synchronized boolean isDibLease(Pc1HeapLease lease)throws IOException{requireOpen();return lease!=null&&lease==dibLease&&lease.live();}
    public synchronized long[] paintRectangleShared(int l,int t,int r,int b,Pc1HeapLease image0)throws IOException{requireEngineReady();if(osWords==null&&!isDibLease(image0))throw new IOException("PC1 shared paint requires this exact live DIB lease");return directSharedNative(handle,l,t,r,b);}
    /** Exact406E40 consumes OS bitmap/DC DWORDs, not image0 metadata. */
    public synchronized long[] paintNativeRectangle(int l,int t,int r,int b)throws IOException{requireEngineReady();if(osWords==null)throw new IOException("PC1 actual OS-word paint requires bound provider");return directSharedNative(handle,l,t,r,b);}
    synchronized int readDibByte(int offset)throws IOException{requireOpen();return dibByteNative(handle,offset,0,false);}
    synchronized void writeDibByte(int offset,int value)throws IOException{requireOpen();dibByteNative(handle,offset,value,true);}
    /** Explicit upload only; no implicit paint/invalidation or invented native
     *WM_PAINT copy. Engine display copy remains a required mapped consumer. */
    public synchronized void uploadDib(NativeRgbImage image)throws IOException{requireEngineReady();if(image==null||image.width!=800||image.height!=600||image.stride!=2400)throw new IOException("PC1 actual front DIB requires800x600x24/stride2400");uploadNative(handle,image.bgr24());}
    /** HWND, two DCs, DIB, selected DDB, actual current selected bitmap. Opaque
     *platform IDs are diagnostic, not portable native32 engine pointers. */
    public synchronized long[] bootstrapHandles()throws IOException{requireOpen();return bootstrapNative(handle);}
    /** Actual DestroyWindow may synchronously dispatch nested destroy messages.
     *Native context remains alive until close, which is physical cleanup only. */
    public synchronized int destroyWindow()throws IOException{requireEngineReady();return destroyNative(handle);}
    public synchronized void requireSnapshotBoundary()throws IOException{requireOpen();if(callbacks!=0)throw new IOException("PC1 window snapshot during active native callback stack");idleNative(handle);}
    @Override public synchronized void close()throws IOException{if(handle==0)return;if(Thread.currentThread()!=owner||callbacks!=0)throw new IOException("PC1 window close requires idle creating thread; use raw destroy service inside dispatch");if(dibLease!=null)dibLease.retireBorrowedView();closeNative(handle);handle=window=0;}
    /** Explicit same-thread HWND adoption. It preserves this external HWND,
     *not its original message queue/menu/timer state. Commit transfers dispatch
     *and destruction responsibility; abort never destroys the adopted HWND. */
    public static final class HwndAdoption {private final Pc1WindowsWindow source;private final long hwnd;private HwndAdoption(Pc1WindowsWindow source,long hwnd){this.source=source;this.hwnd=hwnd;}}
    public synchronized HwndAdoption adoptHwnd()throws IOException{requireSnapshotBoundary();if(window==0||windowInfo()[0]==0)throw new IOException("PC1 physical capsule requires explicit live HWND adoption");return new HwndAdoption(this,window);}
    public interface ObjectRebinder {Pc1OsRegistry.Binding binding(Pc1GuestAddressSpace.ExternalOwner owner)throws IOException;}
    private static final class ObjectRef {
        final int mode,role;final Pc1GuestAddressSpace.ExternalOwner owner;final byte[] proof;
        ObjectRef(int mode,int role,Pc1GuestAddressSpace.ExternalOwner owner,byte[] proof)throws IOException{if(mode<0||mode>3||mode==1!=(owner!=null)||mode==2&&(role<10||role>=18)||mode==3&&(role!=4&&role!=5)||proof==null||proof.length>8192||mode==0&&proof.length!=0)throw new IOException("PC1 Window selected-object reference");this.mode=mode;this.role=role;this.owner=owner;this.proof=proof.clone();}
        void save(DataOutput out)throws IOException{out.writeByte(mode);out.writeInt(role);if(mode==1){out.writeUTF(owner.type);out.writeUTF(owner.id);}blob(out,proof);}
        static ObjectRef load(DataInput in)throws IOException{int mode=in.readUnsignedByte(),role=in.readInt();Pc1GuestAddressSpace.ExternalOwner key=mode==1?new Pc1GuestAddressSpace.ExternalOwner(in.readUTF(),in.readUTF()):null;return new ObjectRef(mode,role,key,blob(in,8192));}
    }
    private static final class ObjectImage {
        final Pc1GuestAddressSpace.ExternalOwner key;final Pc1OsRegistry.Kind kind;final int role;final boolean deleted;
        ObjectImage(Pc1GuestAddressSpace.ExternalOwner key,Pc1OsRegistry.Kind kind,int role,boolean deleted)throws IOException{if(key==null||kind==null||role!=0&&role!=1&&role!=2&&role!=3&&role!=4&&role!=5&&role!=20&&(role<10||role>=18))throw new IOException("PC1 Window scalar role descriptor");this.key=key;this.kind=kind;this.role=role;this.deleted=deleted;}
        void save(DataOutput out)throws IOException{out.writeUTF(key.type);out.writeUTF(key.id);out.writeByte(kind.ordinal());out.writeInt(role);out.writeBoolean(deleted);}
        static ObjectImage load(DataInput in)throws IOException{Pc1GuestAddressSpace.ExternalOwner key=new Pc1GuestAddressSpace.ExternalOwner(in.readUTF(),in.readUTF());int kind=in.readUnsignedByte();if(kind>=Pc1OsRegistry.Kind.values().length)throw new IOException("PC1 Window scalar kind");return new ObjectImage(key,Pc1OsRegistry.Kind.values()[kind],in.readInt(),in.readBoolean());}
    }
    /** Physical graphics capsule only. Pool owns a referenced DIB payload once;
     *orphanDib owns it only when absent from the Pool allocation registry. */
    public static final class State {
        private final long hwnd,nextObject;private final String wordsHash,dibHash;private final int bornMask,liveMask,frontAttempt,poolIndex,dibLength;
        private final byte[] dibInfo,ddbInfo,dibDescriptor,ddbPixels,orphanDib;private final byte[][] dc;
        private final ObjectRef[][] selected;private final List<ObjectImage> objects;private final Pc1GuestAddressSpace.ExternalOwner dibExternal;
        private State(long hwnd,long next,String wordsHash,int born,int live,int front,int poolIndex,int dibLength,String dibHash,byte[] dibInfo,byte[] dibDescriptor,byte[] ddbInfo,byte[] ddbPixels,byte[] orphan,byte[][] dc,ObjectRef[][] selected,List<ObjectImage> objects,Pc1GuestAddressSpace.ExternalOwner external)throws IOException{
            if(hwnd==0||hwnd==-1||next<0||next>100000||!hash(wordsHash)||!hash(dibHash)||(born&~15)!=0||(live&~born)!=0||front<0||front>1||poolIndex< -1||dibLength<0||dibLength>64*1024*1024||dibInfo.length!=40||dibDescriptor.length!=24||ddbInfo.length!=24||ddbPixels.length>64*1024*1024||orphan.length>64*1024*1024||dc.length!=2||selected.length!=2||objects.size()>100000)throw new IOException("PC1 Window physical capsule descriptor");
            if((born&4)!=0){long extent=bitmapBytes(dibDescriptor);if(little(dibInfo,4)<1||little(dibInfo,8)==0||Math.abs((long)little(dibInfo,8))!=little(dibDescriptor,8)||little(dibInfo,4)!=little(dibDescriptor,4)||((live&4)!=0&&extent!=dibLength))throw new IOException("PC1 Window DIB creation/payload extent mismatch");long row=(((long)little(dibInfo,4)*((dibInfo[14]&255)|(dibInfo[15]&255)<<8)+31)/32)*4;if(row<1||row*Math.abs((long)little(dibInfo,8))>64*1024*1024)throw new IOException("PC1 Window DIB creation allocation bound");}if((born&8)!=0){long extent=bitmapBytes(ddbInfo);if((live&8)!=0&&extent!=ddbPixels.length)throw new IOException("PC1 Window DDB payload extent mismatch");}
            boolean dib=(live&4)!=0;if(dib!=(dibLength>0)||!dib&&(poolIndex!=-1||orphan.length!=0||external!=null)||dib&&(poolIndex>=0?orphan.length!=0:orphan.length!=dibLength)||((live&8)==0&&ddbPixels.length!=0))throw new IOException("PC1 Window physical payload authority");for(int n=0;n<2;n++)if(dc[n]==null||dc[n].length>1024*1024+172||((live&(1<<n))!=0)!=(dc[n].length>0)||selected[n]==null||selected[n].length!=4)throw new IOException("PC1 Window owned DC capsule");
            Set<Pc1GuestAddressSpace.ExternalOwner> keys=new HashSet<>();for(ObjectImage object:objects)if(!keys.add(object.key))throw new IOException("PC1 Window duplicate object lifetime key");this.hwnd=hwnd;nextObject=next;this.wordsHash=wordsHash;bornMask=born;liveMask=live;frontAttempt=front;this.poolIndex=poolIndex;this.dibLength=dibLength;this.dibHash=dibHash;this.dibInfo=dibInfo.clone();this.dibDescriptor=dibDescriptor.clone();this.ddbInfo=ddbInfo.clone();this.ddbPixels=ddbPixels.clone();orphanDib=orphan.clone();this.dc=new byte[][]{dc[0].clone(),dc[1].clone()};this.selected=new ObjectRef[][]{selected[0].clone(),selected[1].clone()};for(ObjectRef[] refs:this.selected)for(ObjectRef ref:refs)if(ref==null)throw new IOException("PC1 Window missing selection reference");this.objects=new ArrayList<>(objects);dibExternal=external;
        }
        public int dibPoolLeaseIndex(){return poolIndex;}public int dibByteLength(){return dibLength;}public String dibSha256(){return dibHash;}public boolean ownsOrphanDibPayload(){return orphanDib.length!=0;}public int compatibleByteLength(){return ddbPixels.length;}
        public void save(DataOutput out)throws IOException{out.writeInt(0x50315731);out.writeLong(hwnd);out.writeLong(nextObject);out.writeUTF(wordsHash);out.writeInt(bornMask);out.writeInt(liveMask);out.writeInt(frontAttempt);out.writeInt(poolIndex);out.writeInt(dibLength);out.writeUTF(dibHash);out.write(dibInfo);out.write(dibDescriptor);out.write(ddbInfo);blob(out,ddbPixels);blob(out,orphanDib);for(int n=0;n<2;n++){blob(out,dc[n]);for(ObjectRef ref:selected[n])ref.save(out);}out.writeInt(objects.size());for(ObjectImage object:objects)object.save(out);out.writeBoolean(dibExternal!=null);if(dibExternal!=null){out.writeUTF(dibExternal.type);out.writeUTF(dibExternal.id);}}
        public static State load(DataInput in)throws IOException{if(in.readInt()!=0x50315731)throw new IOException("PC1 Window capsule P1W1 required");long hwnd=in.readLong(),next=in.readLong();String words=in.readUTF();int born=in.readInt(),live=in.readInt(),front=in.readInt(),index=in.readInt(),length=in.readInt();String hash=in.readUTF();byte[] dib=new byte[40],descriptor=new byte[24],ddb=new byte[24];in.readFully(dib);in.readFully(descriptor);in.readFully(ddb);byte[] pixels=blob(in,64*1024*1024),orphan=blob(in,64*1024*1024);byte[][] dc=new byte[2][];ObjectRef[][] refs=new ObjectRef[2][4];for(int n=0;n<2;n++){dc[n]=blob(in,1024*1024+172);for(int k=0;k<4;k++)refs[n][k]=ObjectRef.load(in);}int count=in.readInt();if(count<0||count>100000)throw new IOException("PC1 Window object count");List<ObjectImage> objects=new ArrayList<>();for(int n=0;n<count;n++)objects.add(ObjectImage.load(in));Pc1GuestAddressSpace.ExternalOwner external=in.readBoolean()?new Pc1GuestAddressSpace.ExternalOwner(in.readUTF(),in.readUTF()):null;return new State(hwnd,next,words,born,live,front,index,length,hash,dib,descriptor,ddb,pixels,orphan,dc,refs,objects,external);}
    }
    private static int little(byte[] raw,int at){return(raw[at]&255)|(raw[at+1]&255)<<8|(raw[at+2]&255)<<16|raw[at+3]<<24;}
    private static long bitmapBytes(byte[] descriptor)throws IOException{int width=little(descriptor,4),height=little(descriptor,8),stride=little(descriptor,12);long size=(long)height*stride;if(width<1||height<1||stride<1||size<1||size>64*1024*1024)throw new IOException("PC1 Window bitmap creation extent bound");return size;}
    private static boolean hash(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    private static void blob(DataOutput out,byte[] raw)throws IOException{out.writeInt(raw.length);out.write(raw);}
    private static byte[] blob(DataInput in,int max)throws IOException{int size=in.readInt();if(size<0||size>max)throw new IOException("PC1 Window capsule byte bound");byte[] raw=new byte[size];in.readFully(raw);return raw;}
    private static byte[] leaseBytes(Pc1HeapLease lease)throws IOException{byte[] raw=new byte[lease.length()];for(int n=0;n<raw.length;n++)raw[n]=(byte)lease.readU8(n);return raw;}
    private String wordsHash()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();osWords.save(new DataOutputStream(bytes));return Binary.sha256(bytes.toByteArray());}
    private void requireCapsule()throws IOException{requireSnapshotBoundary();if(!capsuleBackend||osWords==null)throw new IOException("PC1 Window physical capsule requires matching bound JNI ABI1");}
    private Pc1GuestAddressSpace.ExternalOwner ownerOfPhysical(long physical)throws IOException{Pc1OsRegistry.Scalar registered=osWords.registry().findPhysical(Pc1OsRegistry.Domain.GDI,physical);if(registered!=null)return registered.owner;for(Map.Entry<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> entry:osBindings.entrySet())if(entry.getValue().live()&&entry.getValue().physicalHandle()==physical)return entry.getKey();for(Map.Entry<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> entry:selectedBindings.entrySet())if(entry.getValue().live()&&entry.getValue().physicalHandle()==physical)return entry.getKey();return null;}
    private ObjectRef[] selections(int role)throws IOException{
        ObjectRef[] refs=new ObjectRef[4];long dc=capsuleRoleNative(handle,role,false);for(int n=0;n<4;n++){if(dc==0){refs[n]=new ObjectRef(0,0,null,new byte[0]);continue;}long object=objectNative(handle,5+n,dc,0);Pc1GuestAddressSpace.ExternalOwner key=ownerOfPhysical(object);if(key!=null){refs[n]=new ObjectRef(1,0,key,new byte[0]);continue;}int own=capsuleRoleOfNative(handle,object,Pc1OsRegistry.Domain.GDI.ordinal());if(own>=10&&own<18)refs[n]=new ObjectRef(2,own,null,capsuleObjectProofNative(handle,object));else if(own==4||own==5)refs[n]=new ObjectRef(3,own,null,new byte[0]);else throw new IOException("PC1 Window selected object requires explicit external/retained-deleted reconstruction");}return refs;
    }
    private byte[] physicalProof(WindowBinding object)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeUTF(object.key.type);out.writeUTF(object.key.id);out.writeInt(object.kind.ordinal());out.writeInt(object.role);boolean live=object.live();out.writeBoolean(live);
        if(object.role==0||object.role==1||object.role==20)out.writeLong(object.physical); //explicit same-process adoption, not new numeric identity
        if(live&&object.role>=2&&object.role<=3){blob(out,capsuleDcNative(handle,object.role));for(ObjectRef ref:selections(object.role))ref.save(out);}
        else if(live&&object.role==4){out.write(capsuleBitmapInfoNative(handle,4));out.write(capsuleDibInfoNative(handle));}
        else if(live&&object.role==5){out.write(capsuleBitmapInfoNative(handle,5));out.writeUTF(Binary.sha256(capsuleDdbBytesNative(handle)));}
        else if(live&&object.role>=10&&object.role<18)blob(out,capsuleObjectProofNative(handle,object.physical));
        else if(live&&object.kind.domain==Pc1OsRegistry.Domain.GDI){blob(out,objectDescriptorNative(handle,object.physical));if(object.kind==Pc1OsRegistry.Kind.BITMAP)out.writeUTF(Binary.sha256(bitmapProofNative(handle,object.physical)));}
        return bytes.toByteArray();
    }
    public synchronized State snapshotPhysical(Pc1ImagePool pool)throws IOException{
        requireCapsule();if(stagedPhysical!=null||pool==null||pool.guestAddressSpace()!=osWords.registry().guestAddressSpace())throw new IOException("PC1 Window capsule needs the current sole Pool/Guest world");long[] flags=capsuleFlagsNative(handle);if(flags[3]!=0||flags[4]!=0||window==0||windowInfo()[0]==0)throw new IOException("PC1 Window capsule needs live adopted HWND and no unreleased GetDC lease");if(flags[2]!=0&&flags[2]!=window)throw new IOException("PC1 Window front device belongs to another HWND; explicit device adoption required");int born=0,live=0;for(int role=2;role<=5;role++){if(capsuleRoleNative(handle,role,true)!=0)born|=1<<(role-2);if(capsuleRoleNative(handle,role,false)!=0)live|=1<<(role-2);}byte[][] dc=new byte[2][];ObjectRef[][] refs=new ObjectRef[2][];for(int n=0;n<2;n++){dc[n]=(live&(1<<n))!=0?capsuleDcNative(handle,n+2):new byte[0];refs[n]=selections(n+2);}int index=-1,length=0;byte[] orphan=new byte[0];String digest=Binary.sha256(orphan);Pc1GuestAddressSpace.ExternalOwner external=null;
        if((live&4)!=0){Pc1HeapLease dib=borrowDib();byte[] actual=leaseBytes(dib);length=actual.length;digest=Binary.sha256(actual);index=pool.snapshotLeaseIndex(dib);if(index<0){orphan=actual;if(osWords.registry().guestAddressSpace().containsLease(dib))external=new Pc1GuestAddressSpace.ExternalOwner("PC1.Win32.DIB",Long.toHexString(handle));}}
        List<ObjectImage> objects=new ArrayList<>();for(Map.Entry<Pc1GuestAddressSpace.ExternalOwner,Pc1OsRegistry.Binding> entry:osBindings.entrySet()){if(!(entry.getValue() instanceof WindowBinding))throw new IOException("PC1 Window capsule has foreign binding authority");WindowBinding object=(WindowBinding)entry.getValue();objects.add(new ObjectImage(entry.getKey(),object.kind,object.role,!object.live()));}
        return new State(window,nextOsObject,wordsHash(),born,live,(int)flags[0],index,length,digest,capsuleDibInfoNative(handle),capsuleBitmapInfoNative(handle,4),capsuleBitmapInfoNative(handle,5),(live&8)!=0?capsuleDdbBytesNative(handle):new byte[0],orphan,dc,refs,objects,external);
    }
    public static Pc1WindowsWindow stagePhysical(Path dll,NativeDispatcher dispatcher,Pc1OsWords words,State saved,HwndAdoption adoption)throws IOException{
        if(saved==null||words==null||adoption==null)throw new IOException("PC1 Window physical stage requires capsule and explicit HWND adoption");Pc1WindowsWindow source=adoption.source;source.requireSnapshotBoundary();if(source.window!=adoption.hwnd||saved.hwnd!=adoption.hwnd||source.windowInfo()[0]==0)throw new IOException("PC1 Window adopted HWND does not match saved external identity");Pc1WindowsWindow out=deferred(dll,dispatcher);boolean accepted=false;try{out.bindOsWords(words);if(!out.capsuleBackend)throw new IOException("PC1 Window physical stage requires matching JNI ABI1");out.stagedPhysical=saved;out.adoptionSource=source;out.nextOsObject=saved.nextObject;capsuleStageNative(out.handle,source.handle,saved.bornMask,saved.liveMask,saved.dibInfo,saved.ddbInfo,saved.ddbPixels,saved.frontAttempt);out.window=adoption.hwnd;if((saved.liveMask&4)!=0){Pc1HeapLease dib=out.borrowDib();if(dib.length()!=saved.dibLength)throw new IOException("PC1 Window staged DIB extent mismatch");if(saved.poolIndex<0)for(int n=0;n<dib.length();n++)dib.writeU8(n,saved.orphanDib[n]&255);}for(ObjectImage object:saved.objects){if(object.role==0){if(object.deleted){Pc1OsRegistry.Binding known=source.osBindings.get(object.key);if(!(known instanceof Pc1WindowsWindow.WindowBinding)||known.live())throw new IOException("PC1 closed external scalar needs its explicit original same-process owner");out.osBindings.put(object.key,out.new WindowBinding(known.physicalHandle(),object.kind,object.key,0,true));}continue;}if(object.deleted&&object.role>=10&&object.role<18)throw new IOException("PC1 Window retired stock/default object requires external adoption");long physical=capsuleRoleNative(out.handle,object.role,true);if(physical==0)throw new IOException("PC1 Window physical role not reconstructed");out.osBindings.put(object.key,out.new WindowBinding(physical,object.kind,object.key,object.role,object.deleted));}accepted=true;return out;}finally{if(!accepted)out.close();}
    }
    public synchronized void completePhysicalRestore(ObjectRebinder external)throws IOException{
        requireCapsule();State state=stagedPhysical;if(state==null)throw new IOException("PC1 Window capsule has not been staged");for(int n=0;n<2;n++)if((state.liveMask&(1<<n))!=0){long[] physical=new long[4];for(int k=0;k<4;k++){ObjectRef ref=state.selected[n][k];if(ref.mode==1){Pc1OsRegistry.Binding binding=osBindings.get(ref.owner);if(binding==null&&external!=null)binding=external.binding(ref.owner);if(binding==null||!binding.live())throw new IOException("PC1 Window selected object needs its explicit live rebound owner");selectedBindings.put(ref.owner,binding);physical[k]=binding.physicalHandle();}else if(ref.mode==2||ref.mode==3){physical[k]=capsuleRoleNative(handle,ref.role,false);if(physical[k]==0||ref.mode==2&&!Arrays.equals(ref.proof,capsuleObjectProofNative(handle,physical[k])))throw new IOException("PC1 Window stock/default selected object identity changed");}else throw new IOException("PC1 live Window DC cannot have a NULL saved selection");}capsuleSelectNative(handle,n+2,physical);capsuleRestoreDcNative(handle,n+2,state.dc[n]);if(!Arrays.equals(state.dc[n],capsuleDcNative(handle,n+2)))throw new IOException("PC1 Window supported DC properties did not reconstruct exactly");}selectionsRestored=true;
    }
    /** DIB Pool payload is adopted by index, never another copy in the capsule. */
    public synchronized Pc1HeapLease rebindDib(Pc1ImagePool.Image image,Pc1ImagePool.SavedLease allocation)throws IOException{requireCapsule();State state=stagedPhysical;if(state==null||state.poolIndex<0||image==null||image.savedLease(state.poolIndex)!=allocation)throw new IOException("PC1 Window DIB requires exact saved Pool allocation identity");byte[] bytes=allocation.backingBytes();if(bytes.length!=state.dibLength||!Binary.sha256(bytes).equals(state.dibHash))throw new IOException("PC1 Window Pool DIB saved payload differs");Pc1HeapLease dib=borrowDib();if(dib.length()!=bytes.length)throw new IOException("PC1 Window rebound Pool DIB extent differs");for(int n=0;n<bytes.length;n++)dib.writeU8(n,bytes[n]&255);return dib;}
    public synchronized void stageExternal(Pc1GuestAddressSpace.Image layout)throws IOException{requireCapsule();State state=stagedPhysical;if(state==null)throw new IOException("PC1 Window external stage requires P1W1");if(state.dibExternal!=null)osWords.registry().guestAddressSpace().stageExternal(layout,state.dibExternal,borrowDib());}
    /** Last commit only: raw owner bytes, Pool layout/bytes, providers and all
     *other consumers must already be restored. Does not redraw/uploadimage0. */
    public synchronized void activateRestored(Pc1ImagePool pool)throws IOException{
        requireCapsule();State state=stagedPhysical;if(state==null||!selectionsRestored||pool==null)throw new IOException("PC1 Window physical capsule is not ready for commit");if(!state.wordsHash.equals(wordsHash()))throw new IOException("PC1 Window saved OS-word owner differs");for(ObjectImage object:state.objects){Pc1OsRegistry.Scalar associated=osWords.registry().findOwner(object.key);Pc1OsRegistry.Binding own=osBindings.get(object.key);if(associated==null||associated.kind!=object.kind||associated.live()==object.deleted||object.role!=0&&associated.binding()!=own)throw new IOException("PC1 Window scalar registry associations have not been restored");}if((state.liveMask&4)!=0){Pc1HeapLease dib=borrowDib();if(!Binary.sha256(leaseBytes(dib)).equals(state.dibHash)||pool.snapshotLeaseIndex(dib)!=state.poolIndex)throw new IOException("PC1 Window DIB payload/Pool authority not restored");if(state.dibExternal!=null&&!osWords.registry().guestAddressSpace().containsLease(dib))throw new IOException("PC1 Window orphan DIB Guest layout not adopted");}if(!Arrays.equals(state.ddbPixels,(state.liveMask&8)!=0?capsuleDdbBytesNative(handle):new byte[0]))throw new IOException("PC1 Window independent native DDB payload differs");for(int n=0;n<2;n++)if((state.liveMask&(1<<n))!=0){if(!Arrays.equals(state.dc[n],capsuleDcNative(handle,n+2)))throw new IOException("PC1 Window DC state changed before commit");ByteArrayOutputStream a=new ByteArrayOutputStream(),b=new ByteArrayOutputStream();for(ObjectRef ref:state.selected[n])ref.save(new DataOutputStream(a));for(ObjectRef ref:selections(n+2))ref.save(new DataOutputStream(b));if(!Arrays.equals(a.toByteArray(),b.toByteArray()))throw new IOException("PC1 Window selected objects changed before commit");}capsuleCommitNative(handle,adoptionSource.handle);adoptionSource.physicalRetired=true;stagedPhysical=null;adoptionSource=null;
    }
    /** Actual native-format DDB observation, unlike the RGB24 diagnostic. */
    public synchronized byte[] compatibleNativePixels()throws IOException{requireOpen();if(!capsuleBackend)throw new IOException("PC1 native DDB observation needs capsule JNI");return capsuleDdbBytesNative(handle);}
    void selectOwnDibCapsuleAudit()throws IOException{requireSnapshotBoundary();long[] selected=ownedDcSelections(2);selected[1]=capsuleRoleNative(handle,4,false);capsuleSelectNative(handle,2,selected);}
    void configureDcCapsuleAudit(int role)throws IOException{requireSnapshotBoundary();capsuleConfigureDcAuditNative(handle,role);}
    long[] ownedDcSelections(int role)throws IOException{requireOpen();long dc=capsuleRoleNative(handle,role,false);long[] selected=new long[4];for(int n=0;n<4;n++)selected[n]=objectNative(handle,5+n,dc,0);return selected;}
    public synchronized Pc1OsRegistry.Binding bindingForOwner(Pc1GuestAddressSpace.ExternalOwner key)throws IOException{requireOpen();Pc1OsRegistry.Binding binding=osBindings.get(key);if(binding==null)throw new IOException("PC1 Window physical owner not reconstructed: "+key.type+"/"+key.id);return binding;}
    void importCompatibleNativePixels(byte[] payload)throws IOException{requireSnapshotBoundary();if(!capsuleBackend)throw new IOException("PC1 native DDB import requires capsule JNI");capsuleSetDdbNative(handle,payload);}
    private static native void capsuleConfigureDcAuditNative(long context,int role)throws IOException;
    private static native void capsuleSetDdbNative(long context,byte[] payload)throws IOException;
    private static native int capsuleAbiNative();
    private static native long capsuleRoleNative(long context,int role,boolean birth)throws IOException;
    private static native int capsuleRoleOfNative(long context,long physical,int domain)throws IOException;
    private static native byte[] capsuleDcNative(long context,int role)throws IOException;
    private static native void capsuleRestoreDcNative(long context,int role,byte[] properties)throws IOException;
    private static native byte[] capsuleBitmapInfoNative(long context,int role)throws IOException;
    private static native byte[] capsuleDibInfoNative(long context)throws IOException;
    private static native byte[] capsuleDdbBytesNative(long context)throws IOException;
    private static native byte[] capsuleObjectProofNative(long context,long object)throws IOException;
    private static native long[] capsuleFlagsNative(long context)throws IOException;
    private static native void capsuleSelectNative(long context,int role,long[] selected)throws IOException;
    private static native void capsuleStageNative(long target,long source,int bornMask,int liveMask,byte[] dibInfo,byte[] ddbInfo,byte[] ddbPixels,int frontAttempt)throws IOException;
    private static native void capsuleCommitNative(long target,long source)throws IOException;
    private static native byte[] bitmapProofNative(long context,long physical)throws IOException;
    private static native byte[] objectDescriptorNative(long context,long physical)throws IOException;
    private static native void bindWordsNative(long context)throws IOException;
    private static native long objectNative(long context,int operation,long physical,int kind)throws IOException;
    private static native long captureNative(long context,int operation)throws IOException;
    private static native long[] getMessageNative(long context,long[] prior)throws IOException;
    private static native long messageActionNative(long context,int operation,long[] packet)throws IOException;
    private static native int postThreadNative(long context,int message,long wParam,long lParam)throws IOException;
    private static native void quitMessageNative(long context,int code)throws IOException;
    private static native byte[] compatibleRgbNative(long context)throws IOException;
    private static native long[] paintNative(long context,PaintHost host)throws IOException;
    private static native long[] paintReportNative(long context)throws IOException;
    private static native long openNative(Pc1WindowsWindow owner)throws IOException;
    private static native long deferredNative(Pc1WindowsWindow owner)throws IOException;
    private static native long moduleNative(long context)throws IOException;
    private static native long resourceNative(long context,int kind,long instance,int ordinal)throws IOException;
    private static native int registerNative(long context,long instance,byte[] rawClass,long icon,long cursor)throws IOException;
    private static native long createNative(long context,int extendedStyle,byte[] rawClass,byte[] rawTitle,int style,int x,int y,int width,int height,long instance)throws IOException;
    private static native int metricNative(long context,int index)throws IOException;
    private static native int[] adjustNative(long context,int l,int t,int r,int b,int style,boolean menu)throws IOException;
    private static native long systemMenuNative(long context,long hwnd,boolean revert)throws IOException;
    private static native int appendNative(long context,long menu,int flags,int id,byte[] rawText)throws IOException;
    private static native int showNative(long context,long hwnd,int command)throws IOException;
    private static native int updateAtNative(long context,long hwnd)throws IOException;
    private static native void mainNative(long context,long hwnd)throws IOException;
    private static native int imeNative(long context,long hwnd,int enable)throws IOException;
    private static native void initializeFrontNative(long context,boolean publish)throws IOException;
    private static native void initializeFrontSharedAtNative(long context,long capturedHwnd)throws IOException;
    private static native void initializeFrontSharedNative(long context)throws IOException;
    private static native int errorNative(long context)throws IOException;
    private static native long hwndNative(long context)throws IOException;
    private static native int asyncKeyNative(long context,int virtualKey)throws IOException;
    private static native int[] timeNative(long context)throws IOException;
    private static native int tickNative(long context)throws IOException;
    private static native int timerNative(long context,int operation,int id,int interval)throws IOException;
    private static native int timerAtNative(long context,long capturedHwnd,int id,int interval)throws IOException;
    private static native int updateNative(long context)throws IOException;
    private static native int invalidateNative(long context,boolean erase)throws IOException;
    private static native int invalidateRectNative(long context,int left,int top,int right,int bottom,boolean erase)throws IOException;
    private static native int postNative(long context,int message,long wParam,long lParam)throws IOException;
    private static native int pumpNative(long context,long filter)throws IOException;
    private static native int[] cursorNative(long context)throws IOException;
    private static native int[] infoNative(long context)throws IOException;
    private static native long[] detailsNative(long context)throws IOException;
    private static native int[] rectNative(long context)throws IOException;
    private static native byte[] dibNative(long context)throws IOException;
    private static native ByteBuffer dibBufferNative(long context)throws IOException;
    private static native long dibPointerNative(long context)throws IOException;
    private static native int dibByteNative(long context,int offset,int value,boolean write)throws IOException;
    private static native long[] directSharedNative(long context,int left,int top,int right,int bottom)throws IOException;
    private static native void uploadNative(long context,byte[] bytes)throws IOException;
    private static native long[] bootstrapNative(long context)throws IOException;
    private static native int destroyNative(long context)throws IOException;
    private static native long[] tailNative(long context)throws IOException;
    private static native int compatiblePixelNative(long context,int x,int y)throws IOException;
    private static native long beginDirectNative(long context)throws IOException;
    private static native long[] directRectangleNative(long context,int left,int top,int right,int bottom,byte[] front)throws IOException;
    private static native int endDirectNative(long context)throws IOException;
    private static native void idleNative(long context)throws IOException;
    private static native void closeNative(long context)throws IOException;
}
