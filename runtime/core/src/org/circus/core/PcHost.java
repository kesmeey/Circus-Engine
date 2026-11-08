package org.circus.core;

import java.io.*;
import java.util.*;

/** Earlier 800x600 CIRCUS host. Numeric opcodes stay in independently reviewed profiles.
 * It shares rendering primitives with DM, not DM's variable layout or system commands. */
public class PcHost extends Dc2Host implements MesVm.ScriptSystemHost,MesVm.NativePcHost {
    public final Pc1Runtime pc1;
    private Pc1NativeSettings pc1Settings;
    private boolean pc1SettingsInitialized;
    private boolean rawNativeSession;
    private boolean nativeDefaultsVerified;
    private Pc1NativeSettings.NativeIo nativeSettingsIo;
    private String nativeSettingsPath="system.dat",nativeSettingsSource="";
    private Pc1NativeSettings.ReadResult nativeSettingsRead;
    private Pc1NativeSettings.WriteResult nativeSettingsWrite;
    private int pc1BgmMode;
    public interface NativeSettingsAudioEffects {void apply(Pc1NativeSettings.Effect effect,Pc1NativeSettings settings)throws IOException;}
    private NativeSettingsAudioEffects nativeSettingsAudio;
    private static final Pc1NativeSettings.NativeIo UNAVAILABLE_SETTINGS_IO=new Pc1NativeSettings.NativeIo(){
        public int open(String path,int flags,int mode){return -1;}
        public int read(int descriptor,byte[] destination,int count)throws IOException{throw new IOException("No runtime-owned PC1 settings descriptor");}
        public int write(int descriptor,byte[] source,int count)throws IOException{throw new IOException("No runtime-owned PC1 settings descriptor");}
        public int close(int descriptor)throws IOException{throw new IOException("No runtime-owned PC1 settings descriptor");}
    };
    /** Bind only a runtime-owned destination. Original GameResources stay read-only.
     *An unbound sink is an observable native open failure, never a successful write. */
    public void bindNativeSettingsIo(Pc1NativeSettings.NativeIo io,String runtimePath)throws IOException{requireNativeSettings();if(io==null||runtimePath==null||runtimePath.isEmpty())throw new IOException("PC1 native settings storage binding");nativeSettingsIo=io;nativeSettingsPath=runtimePath;}
    /** A profile stamp/codec is not evidence of another executable's defaults. */
    public void bindNativeSettingsDefaults(Pc1NativeSettings template,String peSha256)throws IOException{requireNativeSettings();if(template==null||!Pc1NativeSettings.CURRENT_CHS_PE_SHA256.equals(peSha256)||!Pc1NativeSettings.CURRENT_CHS_DEFAULT_SHA256.equals(Binary.sha256(template.nativeBytes())))throw new IOException("PC1 default template has no verified PE identity binding");nativeDefaultsVerified=true;}
    public void bindNativeSettingsAudioEffects(NativeSettingsAudioEffects effects)throws IOException{requireNativeSettings();nativeSettingsAudio=effects;}
    public boolean nativeSettingsIoBound(){return nativeSettingsIo!=null;}
    public Pc1NativeSettings.ReadResult nativeSettingsReadResult(){return nativeSettingsRead;}
    public Pc1NativeSettings.WriteResult nativeSettingsWriteResult(){return nativeSettingsWrite;}
    public String nativeSettingsSource(){return nativeSettingsSource;}
    public Pc1NativeSettings nativeSettings()throws IOException{ensureNativeSettings();return pc1Settings;}
    public Pc1NativeSettings nativeSettingsRaw()throws IOException{if(pc1Settings==null)throw new IOException("PC1 native settings owner unavailable");return pc1Settings;}
    public int nativeSettingsByte(int offset)throws IOException{return nativeSettingsRaw().nativeByte(offset);}
    public void nativeSettingsByte(int offset,int value)throws IOException{nativeSettingsRaw().nativeByte(offset,value);}
    /** Native402890 read from the explicitly bound runtime-owned file. A short
     *owned file never falls through to a different original game file. */
    public Pc1NativeSettings.ReadResult reloadNativeSettingsFromBoundIo()throws IOException{
        requireNativeSettings();if(nativeSettingsIo==null)throw new IOException("PC1 native settings storage is not bound");
        Pc1NativeSettings.ReadResult result=Pc1NativeSettings.nativeReadCurrent(nativeSettingsIo,nativeSettingsPath,this::nativeDefaultTemplate);
        nativeSettingsRead=result;nativeSettingsSource=nativeSettingsPath;requireBoundDefault(result);pc1Settings=result.settings;pc1SettingsInitialized=true;refreshNativeNameCache();return result;
    }
    /** Whole memory-image installation (native read/platform import/fixture),
     *not411B10. No setter audio/file side effects or host/frame reset is invented. */
    public void loadNativeSettings(byte[] bytes)throws IOException{requireNativeSettings();pc1Settings=Pc1NativeSettings.parse(bytes);pc1SettingsInitialized=true;refreshNativeNameCache();}
    private void requireNativeSettings()throws IOException{if(pc1==null)throw new IOException("Native PC1 settings require dcpc-2063");}
    /** Native consumers take authority over the existing RAM without loading,
     *resetting or replacing it through the legacy convenience importer. */
    void nativeSettingsRamOnly(){rawNativeSession=true;}
    private void ensureNativeSettings()throws IOException{
        requireNativeSettings();if(pc1SettingsInitialized||rawNativeSession)return;
        List<String> sources=NativeProgress.sourcePaths(resources.files,resources.profileId);if(sources.size()>1)throw new IOException("Multiple PC1 system.dat sources require an explicit selected memory image");nativeSettingsSource=sources.isEmpty()?"":sources.get(0);
        final String source=nativeSettingsSource;Pc1NativeSettings.NativeIo readOnly=new Pc1NativeSettings.NativeIo(){
            private Archive.Reader input;
            public int open(String path,int flags,int mode){if(source.isEmpty())return -1;try{input=resources.files.source(source).openReader();return 1;}catch(IOException failure){return -1;}}
            public int read(int descriptor,byte[] destination,int requested){try{int count=(int)Math.min(requested,input.size());byte[] bytes=input.read(0,count);if(bytes.length!=count)return -1;System.arraycopy(bytes,0,destination,0,count);return count;}catch(IOException failure){return -1;}}
            public int write(int descriptor,byte[] bytes,int requested)throws IOException{throw new IOException("Original PC1 system.dat is read-only");}
            public int close(int descriptor){try{input.close();input=null;return 0;}catch(IOException failure){input=null;return -1;}}
        };
        nativeSettingsRead=Pc1NativeSettings.nativeReadCurrent(readOnly,source,this::nativeDefaultTemplate);
        requireBoundDefault(nativeSettingsRead);
        pc1Settings=nativeSettingsRead.settings;pc1SettingsInitialized=true;refreshNativeNameCache();
    }
    private Pc1NativeSettings nativeDefaultTemplate()throws IOException{return Pc1NativeSettings.parse(pc1.effectPeMemory().defaultSettingsBytes());}
    private void requireBoundDefault(Pc1NativeSettings.ReadResult result)throws IOException{
        if(result.defaulted&&!nativeDefaultsVerified){
            if(resources.files.explicitExecutable()){byte[] pe=resources.files.read(resources.files.executable,4*1024*1024);if(Pc1NativeSettings.CURRENT_CHS_PE_SHA256.equals(Binary.sha256(pe)))bindNativeSettingsDefaults(Pc1NativeSettings.currentChsDefaults(),Pc1NativeSettings.CURRENT_CHS_PE_SHA256);}
            if(!nativeDefaultsVerified)throw new IOException("PC1 native system.dat default_unbound: missing/short source requires an explicitly verified PE template or explicit raw memory seed");
        }
    }
    /** Display caches have no authority over raw names. A malformed raw name
     *remains preserved; its real consuming UI/macro reports a bounded failure. */
    private void refreshNativeNameCache(){if(pc1==null)return;try{familyName=codec.decodeUnmapped(pc1Settings.rawCString(Pc1NativeSettings.Name.FAMILY));givenName=codec.decodeUnmapped(pc1Settings.rawCString(Pc1NativeSettings.Name.GIVEN));}catch(IOException unavailable){/* raw source retained; opening the editor validates it */}}
    public Pc1NativeSettings.WriteResult writeNativeSettings()throws IOException{ensureNativeSettings();nativeSettingsWrite=pc1Settings.nativeWrite(nativeSettingsIo==null?UNAVAILABLE_SETTINGS_IO:nativeSettingsIo,nativeSettingsPath);return nativeSettingsWrite;}
    private void applyNativeSettingsWrite(int bank,int index,int value)throws IOException{
        ensureNativeSettings();Pc1NativeSettings.ScriptWrite write=pc1Settings.withScriptWrite(bank,index,value,new Pc1NativeSettings.WriteContext(logicalMusic(),pc1BgmMode));pc1Settings=write.settings;
        for(Pc1NativeSettings.Effect effect:write.effects){if(effect.kind==Pc1NativeSettings.EffectKind.WRITE_SYSTEM_4027E0)writeNativeSettings();else{if(nativeSettingsAudio==null)throw new IOException("Unsupported PC1 native settings audio effect "+effect.kind+"; native PCM/MCI/timer adapter is not bound");nativeSettingsAudio.apply(effect,pc1Settings);}}
    }
    /** Selective phone-progress import is a direct memory merge, not execution
     *of the native bank100 setter. Caller commits the final account atomically. */
    public void importNativeProgressByte(int bank,int index,int value)throws IOException{
        ensureNativeSettings();int start=bank==100?Pc1NativeSettings.GLOBAL_OFFSET:bank==97?Pc1NativeSettings.CG_OFFSET:bank==98?Pc1NativeSettings.SCENE_OFFSET:-1;long at=(long)start+index;if(start<0||at<0||at>=Pc1NativeSettings.SIZE||value<0||value>255)throw new IOException("PC1 native progress import cell");byte[] next=pc1Settings.originalBytes();next[(int)at]=(byte)value;pc1Settings=Pc1NativeSettings.parse(next);
    }
    /** Native text macros read raw persistent C strings on every invocation.
     *No Unicode roundtrip is inserted into the PC1 source-memory path. */
    public byte[] nativeFamilyName()throws IOException{if(pc1==null)return encodeName(familyName);ensureNativeSettings();return pc1Settings.rawCString(Pc1NativeSettings.Name.FAMILY);}
    public byte[] nativeGivenName()throws IOException{if(pc1==null)return encodeName(givenName);ensureNativeSettings();return pc1Settings.rawCString(Pc1NativeSettings.Name.GIVEN);}
    /** Raw prefix excludes NUL. Native/UI input constraints belong to the
     *input owner; the PC1 source bytes do not roundtrip through a String cache. */
    public void setNativeName(Pc1NativeSettings.Name field,byte[] raw)throws IOException{
        if(field==null||raw==null)throw new IOException("Native player name field/prefix is unavailable");
        if(pc1!=null){ensureNativeSettings();pc1Settings=pc1Settings.withNameCString(field,raw);refreshNativeNameCache();return;}
        String value=codec.decode(raw);if(field==Pc1NativeSettings.Name.FAMILY)familyName=value;else givenName=value;
    }
    public void setNativeNames(String family,String given)throws IOException{
        byte[] familyRaw=encodeName(family),givenRaw=encodeName(given);if(pc1!=null){ensureNativeSettings();pc1Settings=pc1Settings.withNameCString(Pc1NativeSettings.Name.FAMILY,familyRaw).withNameCString(Pc1NativeSettings.Name.GIVEN,givenRaw);}familyName=family;givenName=given;
    }
    private byte[] encodeName(String value)throws IOException{
        if(value==null)throw new IOException("Native player name is unavailable");try{java.nio.ByteBuffer encoded=codec.charset.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));byte[] raw=new byte[encoded.remaining()];encoded.get(raw);return raw;}catch(java.nio.charset.CharacterCodingException invalid){throw new IOException("Native player name cannot be represented by "+codec.charset.name(),invalid);}
    }
    @Override public void loadedInitial(MesScript s,int nextOffset)throws IOException{script=s;pc=nextOffset;if(pc1!=null){ensureNativeSettings();pc1.bindInitial(s);}if(pc1==null)cacheOriginalScenarioRead(s.name);}
    @Override protected boolean nativeScalarFrame(){return pc1!=null;}
    @Override public void beforeNativeScriptLoad(int opcode,String target)throws IOException{if(pc1!=null&&opcode==0x36){pc1.markCurrentScenario();pc1.prepareScenarioLabel(target);}}
    @Override public void writeNativeArgument0(int value){if(pc1==null)throw new IllegalStateException("Native PC1 argument requires PC1");pc1.argument0(value);}
    @Override public int readNativeCodePc(){if(pc1==null)throw new IllegalStateException("Native PC1 cursor requires PC1");return pc1.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC);}
    @Override public Pc1CodeBuffer nativeCodeBuffer(){return pc1==null?null:pc1.codeBuffer();}
    @Override public void nativeHeaderLoaded(MesScript original)throws IOException{if(pc1==null)throw new IOException("PC1 native checkpoint owner unavailable");pc1.checkpointMemory().loadSource(original);}
    @Override public boolean ownsNativeProducerPc(int opcode){return pc1!=null&&(opcode==0x4f||opcode==0x50||opcode==0x51||opcode==0x53||opcode==0x54||opcode==0x56||opcode==0x57||opcode==0x5c);}
    @Override public void beforeNativeRecordFetch(int opcode,int codeOffset){if(pc1!=null)pc1.beforeRecordFetch(opcode,codeOffset);}
    @Override public void writeNativeCodePc(int value){if(pc1==null)throw new IllegalStateException("Native PC1 cursor requires PC1");pc1.nativeFrame().dword(Pc1NativeSlot.Dword.RAW_CODE_PC,value);}
    @Override public int logicalMusic(){return pc1==null?super.logicalMusic():pc1.nativeFrame().dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC);}
    @Override public void logicalMusic(int value){if(pc1==null)super.logicalMusic(value);else pc1.nativeFrame().dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC,value);}
    @Override public int messageMode(){return pc1==null?super.messageMode():pc1.nativeFrame().dword(Pc1NativeSlot.Dword.PARAGRAPH_MODE);}
    @Override public void messageMode(int value){if(pc1==null)super.messageMode(value);else pc1.nativeFrame().dword(Pc1NativeSlot.Dword.PARAGRAPH_MODE,value);}
    @Override public Variables.NativeByteMemory nativeByteMemory(){return pc1==null?null:pc1.nativeMemory();}
    @Override public boolean signedNativeIndex(int bank){return pc1!=null&&(bank==90||bank==97||bank==98||bank==99||bank==100);}
    public final PcNameInput nameInput=new PcNameInput();public String familyName,givenName;public final boolean second,spring,fall,dreamXmas,toYou;public final Pc2Map map=new Pc2Map();public final Dc2Map dc2Map=new Dc2Map();public final Dc2BorrowedState dc2Borrowed=new Dc2BorrowedState();public final Dc2OriginalCredits dc2Credits=new Dc2OriginalCredits();public UiCapture dc2CreditsUnderlay;public final Dc2CharacterEnding dc2CharacterEnding=new Dc2CharacterEnding();public UiCapture dc2EndingUnderlay;public final Dc2ActorMotion dc2ActorMotion=new Dc2ActorMotion();public final Dc2ActorChange dc2ActorChange=new Dc2ActorChange();public final PcCredits pcCredits=new PcCredits();
    private final List<String> voiceGroup=new ArrayList<>();
    private final byte[] globals,cg,seen,voiceEnabled,scratch;
    private final byte[][] legacyPcTails={new byte[0],new byte[0],new byte[0],new byte[0],new byte[0]};
    public final ScriptList list=new ScriptList();private boolean gridTarget;private String musicEndAction="";
    private int spriteFlags;private int randomState=0x63c25769;private boolean timedInput;
    private boolean pendingRegionFlag;
    /** Original D.C.II (2006) shares the PC host but keeps its own draw-batch caps. */
    private final boolean original;
    public interface LocalHour {int read();}
    public LocalHour dc2LocalHour=()->new java.util.GregorianCalendar().get(java.util.Calendar.HOUR_OF_DAY);
    // 0 = proven clear; 1 = native pending composition; -1 = an unreviewed producer.
    // Character/text frame producers also depend on native batch/history fields.
    private int originalCompositePending;
    /** Physical Ctrl / the user's native skip latch; the native 8:18 disable byte is checked per frame. */
    public boolean motionSkipPressed;
    /** system.dat+38: independent native voice availability, not script bank99:5. */
    private int originalVoiceEnabled=1;
    private float originalPlaybackGain=1;
    private int originalWaitKind=-1,originalInputMode;
    private int originalDrawDirty;
    private boolean originalResetBatch;
    private UiCapture originalBatchInitial;
    private String originalNextSpeaker="";private int originalSpeakerEnd=-1;
    public interface NativeFrameCapture {UiCapture capture()throws IOException;}
    public NativeFrameCapture nativeFrameCapture;
    public interface NativeMessageTemplateCapture {Dc2MessageWindow capture()throws IOException;}
    public NativeMessageTemplateCapture nativeMessageTemplateCapture;
    private static final class DeferredScene {
        int kind,pc;String script="",body="",speaker="",voice="";
        void save(DataOutput out)throws IOException{out.writeInt(kind);out.writeInt(pc);out.writeUTF(script);out.writeUTF(body);out.writeUTF(speaker);out.writeUTF(voice);}
        static DeferredScene load(DataInput in)throws IOException{DeferredScene value=new DeferredScene();value.kind=in.readInt();value.pc=in.readInt();value.script=in.readUTF();value.body=in.readUTF();value.speaker=in.readUTF();value.voice=in.readUTF();if(value.kind!=1&&value.kind!=2)throw new IOException("DC2 deferred scene command");return value;}
    }
    private DeferredScene originalDeferred;
    public boolean sceneCommandDeferred(){return original&&(originalDeferred!=null||dc2ActorChange.preparing);}
    public boolean actorMotionVisible(){return original&&dc2ActorMotion.drawn&&dc2ActorMotion.sceneSerial==scene.transitionSerial&&scene.nativeMotion==null&&originalDrawDirty==0;}
    public boolean actorChangeVisible(){return original&&!dc2ActorChange.preparing&&(dc2ActorChange.active||dc2ActorChange.drawn)&&dc2ActorChange.sceneSerial==scene.transitionSerial&&scene.nativeMotion==null&&originalDrawDirty==0;}
    @Override public void restored(MesScript s,int nextOffset)throws IOException{super.restored(s,nextOffset);if(pc1!=null)pc1.restored(s);}
    @Override public void fetched(MesScript.Instruction instruction)throws IOException{if(pc1!=null)pc1.fetched(instruction);}
    @Override public void dispatched(MesScript.Instruction instruction)throws IOException{if(pc1!=null)pc1.dispatched(instruction);}
    @Override public void instructionFinished(){if(pc1!=null)pc1.instructionFinished();}
    public void enterSystemContext(MesScript caller,int returnPc,int mode,byte[] rawTarget)throws IOException{if(pc1==null)throw new IOException("Explicit native system context requires PC1");pc1.enter(caller,returnPc,mode,rawTarget);musicEndAction="";}
    public void enterSystemEvent(MesScript caller,int returnPc,byte[] rawTarget,int eventKind)throws IOException{if(pc1==null)throw new IOException("Native system event requires PC1");pc1.enterEvent(caller,returnPc,rawTarget,eventKind);musicEndAction="";}
    public MesVm.SystemReturn systemReturnPoint(){return pc1==null?null:pc1.returnPoint();}
    public void leaveSystemContext()throws IOException{if(pc1==null)throw new IOException("Explicit native system return requires PC1");pc1.leave();musicEndAction="";}
    public void leaveSystemContext(MesScript verifiedCaller)throws IOException{if(pc1==null)throw new IOException("Explicit native system return requires PC1");pc1.leave(verifiedCaller);musicEndAction="";}
    @Override public void completeSave(){super.completeSave();if(pc1!=null)pc1.completeSave();}
    public UiCapture frozenOriginalBatch(){return original&&originalDrawDirty==1?originalBatchInitial:null;}
    // PC1 4304F8 / PC2 500F30 defaults (native bytes are G,R,B,0). START assigns later indices separately.
    private static final int[] PC_PALETTE={0xff000000,0xff0000ff,0xffff0000,0xffff00ff,0xff00ff00,0xff00ffff,0xffffff00,0xffffffff,0xff854e28,0xffccccff,0xff774400,0xffff7f7f,0xffcccccc};
    public final int[] gridBounds=new int[8];
    /** Platform list position is not the PC1 script getter/setter99:246 (both unmapped). */
    public int listOffset()throws IOException{return pc1!=null?registers.getOrDefault(246,0):read(99,246);}
    public void listOffset(int value)throws IOException{if(pc1!=null)registers.put(246,value);else write(99,246,value);}
    public int gridSlot(int index)throws IOException{return read(99,0)*(second||original?8:5)+index;}
    private void gridSelection(int index,int slot)throws IOException{
        if(pc1!=null){pc1.nativeUi().writeInt(0x4305fc,index);pc1.nativeUi().writeInt(0x968a00,slot);}
        else{registers.put(253,index);registers.put(254,slot);}
    }
    @Override public byte[] menuActionForGrid(int index)throws IOException{
        if(pc1!=null&&pc1.returning())return null;
        if(list.active()){ScriptList.Item item=list.at(listOffset(),index,grid.size());if(item==null||item.action.isEmpty())return null;gridSelection(index,item.id);gridWaiting=ui.waiting=false;musicEndAction="";if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);return item.action.getBytes(Binary.SJIS);}
        if(!gridWaiting||index<0||index>=grid.size())throw new IOException("PC grid selection outside page");
        // D.C.II 404CD0 binds slot IDs and enabled labels when configuring the page;
        // the PC1/PC2 generic grid computes them at activation instead.
        if(original){Dc2Ui.Button entry=grid.get(index);if(entry.action.isEmpty())return null;gridSelection(index,entry.binding);gridWaiting=ui.waiting=false;if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);return entry.action.getBytes(Binary.SJIS);}
        int slot=gridSlot(index);
        if(saveGrid&&slot<2||!saveGrid&&!availableSaves.contains(slot))return null;
        gridSelection(index,slot);registers.put(246,0);gridWaiting=ui.waiting=false;
        if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);
        return (saveGrid?"selsave":"selsave2").getBytes(Binary.SJIS);
    }
    @Override public byte[] pollMenuAction(){if(pc1!=null&&pc1.returning())return null;if(ui.waiting&&!audioActive[0]&&!musicEndAction.isEmpty()){byte[] a=musicEndAction.getBytes(Binary.SJIS);musicEndAction="";ui.waiting=gridWaiting=false;ui.actions[0]=ui.actions[1]="";return a;}return null;}
    public boolean hasTimedInput(){return timedInput&&remaining!=0;}
    public boolean hasOriginalBackgroundInputWait(){return original&&(hasTimedInput()||originalWaitKind==10&&remaining!=0);}
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException{if(pc1!=null&&pc1.returning())return false;if(original&&signal==MesVm.HostSignal.ADVANCE&&(hasTimedInput()&&(originalInputMode&1)==0||originalWaitKind==10&&originalInputMode==1&&remaining!=0))return false;return super.signal(expected,signal);}
    /** 4189D0 / 422DA0: older engines browse one message in the original message window. */
    public void openHistory()throws IOException{if(pc1!=null){pc1.historyPrevious();return;}history.active=true;history.selected=Math.max(0,history.entries.size()-2);ui.waiting=true;ui.buttons.clear();ui.buttons.addAll(Dc2Ui.copyButtons(ui.buttonTemplates.get(messageMode()==0?1:4)));showHistory();nextTicket();}
    private void showHistory(){if(history.entries.isEmpty())return;Dc2History.Entry e=history.entries.get(history.selected);speaker=e.speaker;body=e.body;}
    public static boolean supports(Profile p){return p.id.equals("dc2pc-5769")||p.id.equals("dcpc-2063")||p.id.equals("dc2-3363")||p.id.equals("dc2sc-3b69")||p.id.equals("dc2fl-9c69")||p.id.equals("dcdx-7769")||p.id.equals("dc2ty-5f69");}
    public PcHost(CircusResources r,TextCodec c){this(r,c,(int)(System.nanoTime()/1000000L));}
    /** Portable monotonic startup tick; explicit injection enables native seed-matched replays. */
    public PcHost(CircusResources r,TextCodec c,int startupTick){this(r,c,startupTick,false);}
    /**Explicit native process entry: frame PE/BSS and verified43E100=1 before WinMain.*/
    public static PcHost pc1BeforeWinMain(CircusResources r,TextCodec c)throws IOException{if(r==null||!r.profile.id.equals("dcpc-2063"))throw new IOException("PC1 native WinMain requires current verified PC1 profile");return new PcHost(r,c,1,true);}
    private PcHost(CircusResources r,TextCodec c,int startupTick,boolean beforeWinMain){
        super(r,c);spring=r.profile.id.equals("dc2sc-3b69");fall=r.profile.id.equals("dc2fl-9c69");dreamXmas=r.profile.id.equals("dcdx-7769");toYou=r.profile.id.equals("dc2ty-5f69");second=r.profile.id.equals("dc2pc-5769")||spring||fall||dreamXmas||toYou;original=r.profile.id.equals("dc2-3363");
        globals=new byte[100];cg=new byte[second?(spring?1024:fall||dreamXmas?640:toYou?512:1500):1600];seen=new byte[second?(spring||fall||dreamXmas||toYou?1024:1500):1600];voiceEnabled=new byte[second?(spring?80:fall?152:dreamXmas?256:100):128];scratch=new byte[second?(spring||fall||dreamXmas||toYou?1024:1500):100];
        rawNativeSession=beforeWinMain;pc1Settings=!second&&!original?Pc1NativeSettings.initialZeroImage():null;pc1=!second&&!original?new Pc1Runtime(this,beforeWinMain):null;
        if(pc1!=null){ui.quadLimit=60;randomState=startupTick;}
        if(original){randomState=startupTick;scene.nativeParticles=new Dc2Particles(this::nextRandom);ui.quadLimit=60;int[] defaults={1,1,25,25,29,1,8,1,0,1,1,8,1,1};for(int n=0;n<defaults.length;n++)registers.put(5+n,defaults[n]);registers.put(20,1);}
        if(second){int[] defaults={1,1,25,25,29,1,8,1,0,1,1,8,spring?1:0,1};for(int n=0;n<defaults.length;n++)registers.put(5+n,defaults[n]);registers.put(20,1);}
        System.arraycopy(PC_PALETTE,0,ui.palette,0,PC_PALETTE.length);boolean cn=c.charset.name().equalsIgnoreCase("GBK");familyName=second?(cn?"樱内":"桜内"):(cn?"朝仓":"朝倉");givenName=second?(cn?"义之":"義之"):(cn?"纯一":"純一");gridHoverSound=gridClickSound=255;
        if(!second)Arrays.fill(voiceEnabled,(byte)1);registers.put(13,0);registers.put(19,0);registers.put(25,0);registers.put(26,8);registers.put(27,8);if(pc1!=null)clearNativeSettingsRegisterMirrors();
    }
    @Override public void elapse(long ms){
        if(!original){if(pc1!=null)try{pc1.elapseReturn(ms);}catch(IOException failure){throw new IllegalStateException("PC1 native return transition: "+failure.getMessage(),failure);}super.elapse(ms);pcCredits.elapse(ms);if(map.active)map.elapsed+=Math.max(0,ms);return;}
        if(dc2ActorChange.active&&!dc2ActorChange.preparing){super.elapse(ms);dc2ActorChange.advance(ms,registers.getOrDefault(12,0),originalMotionSkip());return;}
        if(dc2ActorMotion.active){super.elapse(ms);scene.nativeParticles.elapse(ms,registers.getOrDefault(20,0)!=0,false,originalDrawDirty!=0);Dc2ActorMotion.Step move=dc2ActorMotion.advance(ms,scene.character(dc2ActorMotion.actor,0),scene.character(dc2ActorMotion.actor,1),registers.getOrDefault(12,0),originalMotionSkip());if(move.drawn){scene.character(dc2ActorMotion.actor,0,move.x);scene.character(dc2ActorMotion.actor,1,move.y);try{dc2ActorMotion.template=originalCompositePending>0?actorWindow():null;scene.publish(ui,0,0);dc2ActorMotion.sceneSerial=scene.transitionSerial;}catch(IOException failure){throw new IllegalStateException("DC2 actor motion publish: "+failure.getMessage(),failure);}}return;}
        boolean characterActive=dc2CharacterEnding.active;dc2CharacterEnding.advance(ms);if(characterActive){super.elapse(ms);if(!dc2CharacterEnding.active)try{finishOriginalCharacterEnding();}catch(IOException failure){throw new IllegalStateException("DC2 character ending front capture: "+failure.getMessage(),failure);}return;}
        if(dc2Credits.active){super.elapse(ms);dc2Credits.advance(ms);if(!dc2Credits.active)try{finishOriginalCredits();}catch(IOException failure){throw new IllegalStateException("DC2 ending front capture: "+failure.getMessage(),failure);}return;}if(dc2Map.active){super.elapse(ms);dc2Map.elapse(ms);return;}long left=Math.max(0,ms);do{long step=left;if(scene.nativeParticles.timerActive())step=Math.min(step,scene.nativeParticles.untilTick());if(scene.nativeMotion!=null&&scene.nativeMotion.active)step=Math.min(step,scene.nativeMotion.untilFrame());if(scene.transitionRemaining>0)step=Math.min(step,scene.transitionRemaining);
            boolean busy=scene.transitionRemaining>0||scene.nativeMotion!=null&&scene.nativeMotion.preparing;
            super.elapse(step);pcCredits.elapse(step);if(scene.nativeMotion!=null&&scene.nativeMotion.active){int prior=scene.nativeMotion.draws().size();scene.nativeMotion.elapse(step,registers.getOrDefault(12,0),originalMotionSkip());if(prior!=scene.nativeMotion.draws().size())syncOriginalMotion();}
            if(originalDrawDirty<0&&scene.nativeParticles.kind()!=0)throw new IllegalStateException("DC2 particle timer: native9DA2D0 batch state unresolved at "+(script==null?"?":script.name)+"@0x"+Integer.toHexString(pc));
            scene.nativeParticles.elapse(step,registers.getOrDefault(20,0)!=0,busy,originalDrawDirty!=0);left-=step;
        }while(left>0);
        if(dc2ActorChange.preparing&&scene.nativeMotion!=null&&!scene.nativeMotion.active)try{finishOriginalActorPreparation();}catch(IOException failure){throw new IllegalStateException("DC2 actor preparation: "+failure.getMessage(),failure);}
    }
    @Override public boolean waiting(){return dc2ActorChange.active||dc2ActorMotion.active||dc2CharacterEnding.active||dc2Credits.active||pc1!=null&&pc1.returning()||pcCredits.active||original&&scene.nativeMotion!=null&&scene.nativeMotion.active||super.waiting();}
    private boolean originalMotionSkip(){try{return motionSkipPressed&&variables!=null&&variables.read(8,18)!=1;}catch(IOException impossible){throw new IllegalStateException(impossible);}}
    private void originalActorMotion(int[] a)throws IOException{
        if(a.length!=4)throw originalUnsupported("61 requires four native words");
        if(a[2]==-1){String actor=scene.slots[2];scene.slots[2]=scene.slots[3];scene.slots[3]=actor;return;}
        if(originalDrawDirty!=0)throw originalUnsupported("61 interleaved with a pending native draw batch");
        boolean retained=actorMotionVisible();Dc2MessageWindow previousTemplate=dc2ActorMotion.template;dc2ActorMotion.start(a[2],a[0],a[1]);if(retained){dc2ActorMotion.drawn=true;dc2ActorMotion.sceneSerial=scene.transitionSerial;dc2ActorMotion.template=previousTemplate;}else if(originalCompositePending>0)dc2ActorMotion.template=actorWindow();remaining=0;timedInput=false;presentationWaiting=textWaiting=false;ui.waiting=false;originalWaitKind=-1;nextTicket();
    }
    private void commitOriginalActorPosition(){scene.character(0,0,dc2ActorChange.staged[0]);scene.character(0,1,dc2ActorChange.staged[1]);scene.character(1,0,dc2ActorChange.staged[2]);scene.character(1,1,dc2ActorChange.staged[3]);}
    private UiCapture actorScene(Dc2Scene.Frame frame)throws IOException{return SceneCaptures.legacy(copyFrame(frame),scene,ui,variables,true,uiCapture);}
    private Dc2MessageWindow actorWindow()throws IOException{return nativeMessageTemplateCapture==null?Dc2MessageWindow.capture(this):nativeMessageTemplateCapture.capture();}
    /**32 peeks at the optional45 second actor, which is still fetched later
     * as a native no-effect record. Only the following02 defers its wait. */
    private boolean originalActorChange(byte[] first)throws IOException{
        if(originalCompositePending<0||messageMode()!=0&&originalCompositePending!=0)throw originalUnsupported("32 retained NVL glyph/history composition is not reconstructed");
        MesScript.Record following=script.fetchRecord(script.instruction(pc).next);byte[] secondActor=following.opcode==0x45?following.bytes:new byte[0];
        boolean batch=originalDrawDirty!=0,wasVisible=!scene.slots[2].isEmpty()||!scene.slots[3].isEmpty();int prior=originalCompositePending;
        UiCapture old=batch?originalBatchInitial:actorScene(scene.shown);Dc2MessageWindow oldWindow=!batch&&prior!=0?actorWindow():null;
        boolean removeFirst=first.length==1&&first[0]=='-',removeSecond=secondActor.length==1&&secondActor[0]=='-';
        super.command("dm.scene.character",new int[]{0},first);super.command("dm.scene.character",new int[]{1},secondActor);dc2ActorChange.clear();
        if(removeFirst&&removeSecond&&!wasVisible)return true;
        if(prior>0&&!batch)originalCompositePending=messageMode()==0?2:1;
        if(following.opcode==2){commitOriginalActorPosition();return true;}
        if(batch&&scene.slots[2].isEmpty()&&scene.slots[3].isEmpty())return true;
        remaining=0;timedInput=false;presentationWaiting=textWaiting=false;ui.waiting=false;originalWaitKind=-1;originalResetBatch=true;
        if(batch){
            Dc2Scene.Frame source=originalFrame();UiCapture prepared=actorScene(source);Dc2Scene.Frame target=copyFrame(source);target.actors[0][0]=dc2ActorChange.staged[0];target.actors[0][1]=dc2ActorChange.staged[1];target.actors[1][0]=dc2ActorChange.staged[2];target.actors[1][1]=dc2ActorChange.staged[3];
            dc2ActorChange.start(old,actorScene(target),null,null,scene.transitionSerial,true);clearOriginalDirty();originalCompositePending=0;scene.nativeMotion=Dc2Motion.preparation(source,old,prepared,read(99,12),originalMotionSkip());
        }else{
            commitOriginalActorPosition();scene.publish(ui,0,0);Dc2MessageWindow nextWindow=originalCompositePending!=0?actorWindow():null;dc2ActorChange.start(old,actorScene(scene.shown),oldWindow,nextWindow,scene.transitionSerial,false);
        }
        nextTicket();return true;
    }
    private void finishOriginalActorPreparation()throws IOException{
        UiCapture initial=scene.nativeMotion.prepared;scene.nativeMotion=null;originalCompositePending=0;commitOriginalActorPosition();scene.publish(ui,0,0);dc2ActorChange.afterPreparation(initial,actorScene(scene.shown),scene.transitionSerial);
    }
    private void syncOriginalMotion(){Dc2Motion motion=scene.nativeMotion;scene.backgroundX(motion.x);scene.backgroundY(motion.y);scene.shown.x=motion.x;scene.shown.y=motion.y;scene.revision++;}
    private static Dc2Scene.Frame copyFrame(Dc2Scene.Frame frame)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();frame.save(new DataOutputStream(bytes));Dc2Scene.Frame copy=new Dc2Scene.Frame();copy.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));return copy;}
    private void originalMotion(int[] a)throws IOException{
        if(a.length!=4)throw new IOException("D.C.II 5E requires four native operands");
        if(a[2]!=0){scene.backgroundX(a[0]);scene.backgroundY(a[1]);return;}
        for(int n=0;n<50;n++)if(scene.nativeParticles.slot(n)[2]!=0)throw originalUnsupported("5E moving feedback interleaved with active particle timer has no reviewed render journal");
        if(originalCompositePending<0)throw new IOException("Unsupported D.C.II 5E at "+(script==null?"?":script.name)+"@0x"+Integer.toHexString(pc)+": native BE8BC8 pending composition is unresolved after a text/character frame producer (41A020/418EA0; batch/history fields required)");
        if(a[3]<0||a[3]>16)throw new IOException("Unsupported D.C.II 5E feedback weight "+a[3]+" at "+(script==null?"?":script.name)+"@0x"+Integer.toHexString(pc)+" (native 408160 packed over-range arithmetic)");
        Dc2Scene.Frame source=new Dc2Scene.Frame();source.slots=scene.slots.clone();for(int n=0;n<4;n++)source.actors[n]=scene.character(n);source.quads=Dc2Ui.copyQuads(ui.quads);source.x=scene.backgroundX();source.y=scene.backgroundY();
        Dc2Motion previous=scene.nativeMotion;UiCapture initial=previous==null?(nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(copyFrame(scene.shown),scene,ui,variables,read(99,10)!=0,uiCapture)):null;
        scene.nativeMotion=new Dc2Motion(source,initial,previous,a[0],a[1],a[3],originalCompositePending!=0,read(99,12),originalMotionSkip());
        if(originalCompositePending!=0){scene.nativeMotion.prepared=SceneCaptures.legacy(copyFrame(source),scene,ui,variables,true,uiCapture);if(nativeMessageTemplateCapture!=null)scene.nativeMotion.fullTemplate=nativeMessageTemplateCapture.capture();}
        syncOriginalMotion();nextTicket();
    }
    @Override public boolean internalMenuAction(long expected,int button)throws IOException{
        if(pc1!=null&&pc1.returning())return false;
        if(nameInput.active&&expected==ticket){String a=button<0?"@name:cancel":ui.buttons.get(button).action;nameAction(a);return true;}
        if(history.active&&expected==ticket){String a=button<0?"@log:exit":button<ui.buttons.size()?ui.buttons.get(button).action:"";
            if(a.equals(":up")){history.selected=Math.max(0,history.selected-1);showHistory();}
            else if(a.equals(":down")){if(history.selected>=history.entries.size()-2){history.active=ui.waiting=false;returnRequested=true;}else{history.selected++;showHistory();}}
            else if(a.equals("@log:exit")){history.active=ui.waiting=false;returnRequested=true;}
            else if(a.equals(":close")||a.equals(":change"))return false;
            else return true;return true;
        }
        if(dc2Map.active){
            if(expected!=ticket)return false;
            if(button<0){if(button!=-1)return true;boolean accepted=dc2Map.right(variables);if(accepted){dc2Map.buttons(ui);if(!dc2Map.active)ui.waiting=false;}return true;}
            if(button>=ui.buttons.size())throw new IOException("DC2 map button index");String action=ui.buttons.get(button).action;
            if(action.equals("@dc2map:yes"))dc2Map.confirm(variables);else if(action.equals("@dc2map:no"))dc2Map.cancel();else if(action.startsWith("@dc2map:"))dc2Map.select(Integer.parseInt(action.substring(8)));else throw new IOException("Unknown DC2 map action");
            dc2Map.buttons(ui);play(1,resources.named("sound",220),false);return true;
        }
        if(!map.active)return super.internalMenuAction(expected,button);if(expected!=ticket)return false;
        if(button<0){if(map.selected>=0){map.selected=-1;map.buttons(ui);}return true;}
        if(button>=ui.buttons.size())throw new IOException("Map button index");String action=ui.buttons.get(button).action;
        if(action.equals("@map:yes")){variables.write(5,40,map.selected);map.active=false;ui.buttons.clear();ui.waiting=false;}
        else if(action.equals("@map:no")){map.selected=-1;map.buttons(ui);}
        else if(action.startsWith("@map:")){map.selected=Integer.parseInt(action.substring(5));map.buttons(ui);}
        else throw new IOException("Unknown native map action");play(1,resources.named("sound",223),false);return true;
    }
    public void nameAction(String action)throws IOException{int result=nameInput.action(action);if(result==1)setNativeNames(nameInput.family.trim(),nameInput.given.trim());nameInput.build(ui);nextTicket();}
    @Override public boolean beforeInstruction(Profile p,int op)throws IOException{
        if(!p.id.equals(resources.profile.id))throw new IOException("PC host profile mismatch");
        if(original&&originalDeferred!=null){
            if(scene.nativeMotion!=null&&scene.nativeMotion.active)return true;
            DeferredScene pending=originalDeferred;originalDeferred=null;scene.nativeMotion=null;originalCompositePending=0;scene.publish(ui,0,0);
            commitOriginal(pending);
        }
        if(waiting())return true;
        if(pc1!=null){pc1.bindInitial(script);pc1.beginDispatcher();}
        // 414735 is reached when the native dispatcher is re-entered after a real wait,
        // never merely because MesVm.run exhausted its caller's instruction budget.
        if(original&&originalResetBatch){clearOriginalDirty();originalResetBatch=false;}
        return false;
    }
    private Dc2Scene.Frame originalFrame()throws IOException{Dc2Scene.Frame frame=new Dc2Scene.Frame();frame.slots=scene.slots.clone();for(int n=0;n<4;n++)frame.actors[n]=scene.character(n);frame.quads=Dc2Ui.copyQuads(ui.quads);frame.x=scene.backgroundX();frame.y=scene.backgroundY();return frame;}
    private void dirtyOriginal()throws IOException{
        if(originalDrawDirty<0)throw originalUnsupported("9DA2D0 draw-batch producer is unresolved");
        if(originalDrawDirty==0){originalBatchInitial=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(copyFrame(scene.shown),scene,ui,variables,true,uiCapture);originalDrawDirty=1;}
    }
    private void clearOriginalDirty(){originalDrawDirty=0;originalBatchInitial=null;}
    /** Typed normal-text import projection; raw Windows frame bytes remain
     * with Dc2NativeSlot. Logical buffers restart through the real checkpoint. */
    void applyDc2NativeSlot(Dc2NativeSlot slot,Dc2NativeSlot.Plan plan)throws IOException{
        if(!original)throw new IOException("DC2 native slot profile required");
        byte[] ordinary=slot.ordinaryBytes();for(int n=0;n<ordinary.length;n++)variables.write(n/100,n%100,ordinary[n]&255);
        Dc2NativeSlot.StringField[] fields={Dc2NativeSlot.StringField.BACKGROUND,Dc2NativeSlot.StringField.ACTOR1,Dc2NativeSlot.StringField.ACTOR2,Dc2NativeSlot.StringField.OVERLAY1,Dc2NativeSlot.StringField.OVERLAY2,Dc2NativeSlot.StringField.RESOURCE3,Dc2NativeSlot.StringField.RESOURCE4,Dc2NativeSlot.StringField.RESOURCE5};
        int[] target={1,2,3,6,7,10,11,12};for(int n=0;n<fields.length;n++){String resource=slot.resource(fields[n]);if(!resource.isEmpty())resources.image(resource);scene.slots[target[n]]=resource;}
        scene.backgroundX(slot.dword(Dc2NativeSlot.Dword.BACKGROUND_X));scene.backgroundY(slot.dword(Dc2NativeSlot.Dword.BACKGROUND_Y));
        scene.character(0,0,slot.dword(Dc2NativeSlot.Dword.ACTOR1_X));scene.character(0,1,slot.dword(Dc2NativeSlot.Dword.ACTOR1_Y));scene.character(1,0,slot.dword(Dc2NativeSlot.Dword.ACTOR2_X));scene.character(1,1,slot.dword(Dc2NativeSlot.Dword.ACTOR2_Y));
        dc2ActorChange.staged[0]=slot.dword(Dc2NativeSlot.Dword.ACTOR1_STAGE_X);dc2ActorChange.staged[1]=slot.dword(Dc2NativeSlot.Dword.ACTOR1_STAGE_Y);dc2ActorChange.staged[2]=slot.dword(Dc2NativeSlot.Dword.ACTOR2_STAGE_X);dc2ActorChange.staged[3]=slot.dword(Dc2NativeSlot.Dword.ACTOR2_STAGE_Y);
        messageMode(slot.dword(Dc2NativeSlot.Dword.PARAGRAPH_MODE));checkpoint=plan.checkpointIndex;int cachedRead=slot.dword(Dc2NativeSlot.Dword.CACHED_SCENARIO_READ);if(cachedRead<0||cachedRead>255)throw new IOException("DC2 native cached scenario read byte");registers.put(251,cachedRead);
        ui.clearCurrent();grid.clear();gridWaiting=confirming=false;systemScene=null;remaining=0;audioMask=0;textWaiting=presentationWaiting=false;body=speaker="";speakerPending=false;lineVoice="";
        originalDrawDirty=0;originalBatchInitial=null;originalResetBatch=false;originalDeferred=null;originalCompositePending=0;originalWaitKind=-1;originalInputMode=0;originalNextSpeaker="";originalSpeakerEnd=-1;scene.nativeMotion=null;dc2Map.reset();dc2Borrowed.reset();
        ui.actions[0]=slot.text(Dc2NativeSlot.StringField.ACTION0);ui.actions[1]=slot.text(Dc2NativeSlot.StringField.ACTION1);scene.publish(ui,0,0);
        int music=slot.dword(Dc2NativeSlot.Dword.LOGICAL_MUSIC);command("dm.audio.music",new int[]{music<0?255:music,1},new byte[0]);
        int packed=slot.dword(Dc2NativeSlot.Dword.PACKED_SOUND),sound=packed&255,mode=packed>>>8&255;if(mode!=0)command("pc.sound8",new int[]{sound,1},new byte[0]);else play(1,"",false);
        command("pc.effect",new int[]{slot.dword(Dc2NativeSlot.Dword.EFFECT_KIND),slot.dword(Dc2NativeSlot.Dword.EFFECT_STRENGTH)},new byte[0]);
        for(int word:slot.activeLogWords())readCheckpoints.add(plan.script+":"+(word&0x7fff));
    }
    /** 41D340 samples the previous scene-read byte; loader413946 caches it.
     * The value is not a catalog index and must not change when body marks seen. */
    private void cacheOriginalScenarioRead(String name)throws IOException{int index=catalogIndex("scenario_scripts",name);registers.put(251,index>=0&&index<seen.length?seen[index]&255:0);}
    // Out-of-band portable event key; it is not a native99 register address.
    private static final int DC2_RAPID_RESET_EVENT=0x44435252;
    public int dc2RapidResetGeneration(){return registers.getOrDefault(DC2_RAPID_RESET_EVENT,0);}
    /** system40 writes BE8BA8=0: reset current rapid input, not skip permission. */
    public void resetOriginalRapid()throws IOException{if(!original||dc2RapidResetGeneration()==Integer.MAX_VALUE)throw new IOException("DC2 rapid reset event counter");motionSkipPressed=false;registers.put(DC2_RAPID_RESET_EVENT,dc2RapidResetGeneration()+1);}
    public void prepareOriginalCharacterEnding(int variant)throws IOException{
        if(!original||variant<0||variant>5||dc2Credits.active)throw new IOException("DC2 character ending requires original profile and variant0..5");
        int[] seconds=new int[24];for(int n=0;n<seconds.length;n++)seconds[n]=variables.read(6,50+n);
        UiCapture front=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);if(front==null)throw new IOException("DC2 character ending initial front missing");
        dc2CharacterEnding.prepare(variant,seconds);dc2EndingUnderlay=front;
    }
    public void beginOriginalCharacterEnding()throws IOException{
        if(!original||dc2EndingUnderlay==null||dc2Credits.active)throw new IOException("DC2 character ending11 requires its prepared front");dc2CharacterEnding.begin();remaining=0;timedInput=false;presentationWaiting=textWaiting=false;ui.waiting=false;originalWaitKind=-1;nextTicket();
    }
    private void finishOriginalCharacterEnding()throws IOException{
        UiCapture front=nativeFrameCapture==null?null:nativeFrameCapture.capture();
        Arrays.fill(scene.slots,0,21,"");ui.quads.clear();ui.buttons.clear();scene.backgroundX(0);scene.backgroundY(0);scene.scrollStep=0;scene.nativeMotion=null;scene.nativeParticles.command(0,0,true);
        if(front!=null){uiCapture=front;scene.slots[1]="@ui-capture";}else scene.slots[1]="BG/black";
        clearOriginalDirty();originalDeferred=null;originalResetBatch=false;scene.publish(ui,0,0);dc2EndingUnderlay=null;
    }
    public void openOriginalCredits(int variant)throws IOException{
        if(!original||variant<0||variant>1)throw new IOException("DC2 credits require original profile and variant0/1");
        UiCapture underlay=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);if(underlay==null)throw new IOException("DC2 credits initial front missing");
        dc2Credits.start(variant);dc2CreditsUnderlay=underlay;remaining=0;timedInput=false;presentationWaiting=textWaiting=false;ui.waiting=false;originalWaitKind=-1;nextTicket();
    }
    private void finishOriginalCredits()throws IOException{
        UiCapture front=nativeFrameCapture==null?null:nativeFrameCapture.capture();
        Arrays.fill(scene.slots,0,21,"");ui.quads.clear();ui.buttons.clear();scene.backgroundX(0);scene.backgroundY(0);scene.scrollStep=0;scene.nativeMotion=null;scene.nativeParticles.command(0,0,true);
        if(front!=null){uiCapture=front;scene.slots[1]="@ui-capture";}else scene.slots[1]=dc2Credits.image(Dc2OriginalCredits.PAGES-1);
        clearOriginalDirty();originalDeferred=null;originalResetBatch=false;scene.publish(ui,0,0);dc2CreditsUnderlay=null;
    }
    public void openOriginalMap()throws IOException {if(!original)throw new IOException("DC2 map requires original profile");dc2Map.open(variables);clearOriginalDirty();originalDeferred=null;originalResetBatch=false;scene.nativeMotion=null;originalWaitKind=-1;originalInputMode=0;timedInput=false;remaining=0;presentationWaiting=textWaiting=gridWaiting=confirming=false;dc2Map.buttons(ui);nextTicket();}
    private IOException originalUnsupported(String detail){return new IOException("Unsupported D.C.II at "+(script==null?"?":script.name)+"@0x"+Integer.toHexString(pc)+": "+detail);}
    private DeferredScene originalCommand(int kind,String value){DeferredScene pending=new DeferredScene();pending.kind=kind;pending.pc=pc;pending.script=script==null?"":script.name;if(kind==1)pending.voice=value;else{pending.body=value;pending.speaker=originalSpeakerEnd==pc&&messageMode()==0?originalNextSpeaker:"";pending.voice=lineVoice;originalNextSpeaker="";originalSpeakerEnd=-1;}return pending;}
    private void submitOriginal(DeferredScene pending)throws IOException{
        if(originalDrawDirty<0)throw originalUnsupported("34/4B requires unresolved native9DA2D0 producer");
        if(originalDrawDirty==0){commitOriginal(pending);return;}
        // 41791x / 416E9x free native actor handles 1 and 2 before baking the background.
        for(int slot:new int[]{2,3,6,7})scene.slots[slot]="";
        Dc2Scene.Frame frame=originalFrame();UiCapture prepared=SceneCaptures.legacy(copyFrame(frame),scene,ui,variables,true,uiCapture);
        UiCapture initial=originalBatchInitial;clearOriginalDirty();
        scene.nativeMotion=Dc2Motion.preparation(frame,initial,prepared,read(99,12),originalMotionSkip());
        originalDeferred=pending;nextTicket();
    }
    private void commitOriginal(DeferredScene pending)throws IOException{
        if(pending.kind==1){lineVoice=pending.voice;playVoice(pending.voice);return;}
        scene.publish(ui,0,0);
        body=pending.body;speaker=pending.speaker;speakerPending=false;
        unlock("scenario_scripts",pending.script,98);
        if(!body.isEmpty())history.append(speaker,body,pending.voice,pending.script,pending.pc);
        lineVoice="";presentationWaiting=true;textRendered=false;originalCompositePending=messageMode()==0?1:-1;
        originalResetBatch=true;pc=pending.pc;nextTicket();
    }
    @Override public byte[] menuAction(long expected,int button)throws IOException{
        if(pc1!=null&&pc1.returning())return null;
        if(confirming&&ui.waiting&&expected==ticket){String a=button<0?":n":button<ui.buttons.size()?ui.buttons.get(button).action:"";if(a.equals(":y"))a=ui.actions[2];else if(a.equals(":n"))a=ui.actions[3];return a.isEmpty()?null:a.getBytes(Binary.SJIS);}
        // PC1 419310..419379 / PC2 423A4C..423AB5: action0 is right/Esc,
        // action1 is left/Enter; these are separate from confirmation slots 2/3.
        if(button<0&&(ui.waiting||hasTimedInput())&&expected==ticket){
            if(original&&!hasTimedInput()&&(button==-2||originalWaitKind!=10&&originalWaitKind!=11))return null;
            String action=ui.actions[button==-2?1:0];if(action.isEmpty())return null;byte[] label=action.getBytes(Binary.SJIS);
            if(original&&(script==null||script.label(label)==null))return null;
            return label;
        }
        if(pc1!=null&&(pc1.waitKind()==7||pc1.waitKind()==9)&&button==0&&expected==ticket&&!ui.buttons.isEmpty()&&!ui.buttons.get(0).action.startsWith(":"))return null;
        if(button<0)return null;return super.menuAction(expected,button);
    }
    @Override public boolean ignoreMissingMenuLabel(){return pc1!=null&&(pc1.waitKind()==7||pc1.waitKind()==9);}
    @Override public void acceptMenuAction(int button)throws IOException{if(pc1!=null&&(pc1.waitKind()==7||pc1.waitKind()==9)&&button>0&&!ui.buttons.get(button).action.startsWith(":")){pc1.textLog().discardLastForLabelAction();pc1.waitKind(-1);}if(button<0&&!confirming)ui.actions[button==-2?1:0]="";timedInput=false;musicEndAction="";super.acceptMenuAction(button);}
    @Override public int read(int b,int i)throws IOException{
        if(pc1!=null&&pc1.nativeVariables()!=null)return pc1.nativeVariables().read(b,i);
        if(b==90&&pc1!=null)return pc1.nativeMemory().readBank90(i);
        if(pc1!=null&&(b==97||b==98||b==100)){ensureNativeSettings();return pc1Settings.readByteBank(b,i);}
        if(pc1!=null&&b==99){
            if(i<0)return 0;if(Pc1NativeSettings.owns99(i)){ensureNativeSettings();return pc1Settings.read99(i);}
            switch(i){
            case 0:return pc1.drawMemory().readInt(Pc1VmGlobals.RESULT0);
            case 2:return pc1.drawMemory().readInt(Pc1VmGlobals.RANDOM_RESULT);
            case 3:return pc1.drawMemory().readInt(0x43028c);
            case 4:return pc1.drawMemory().readInt(0x94c61c);
            case 19:return 0; // No411910 branch for the write-only9B9F1C.
            case 22:return pc1.drawMemory().readInt(Pc1VmGlobals.VALUE22);
            case 243:return pc1.drawMemory().readInt(Pc1VmGlobals.SELECTION243);
            case 247:return pc1.drawMemory().readInt(Pc1VmGlobals.DEVICE247)==0?1:0;
            case 252:return pc1.drawMemory().readInt(Pc1VmGlobals.VALUE252);
            case 253:return pc1.drawMemory().readInt(0x4305fc);
            case 254:return pc1.drawMemory().readInt(0x968a00);
            default:break;
            }
        }
        if(pc1!=null&&b!=99)return 0;
        if(i<0)throw new IOException("Negative PC variable index");
        if(second&&((b==100&&i>=globals.length)||(b==97&&i>=cg.length)||(b==98&&i>=seen.length)||(b==95&&i>=voiceEnabled.length)||(b==90&&i>=scratch.length)))throw new IOException("Unreviewed native byte-bank span "+b+":"+i);
        if(b==100)return at(globals,i);if(b==97)return at(cg,i);if(b==98)return at(seen,i);if(b==95)return second?at(voiceEnabled,i):1;if(b==90)return at(scratch,i);
        if(b!=99)return 0;
        if(pc1==null&&i==251)return registers.getOrDefault(251,0);
        if(pc1==null&&i==21){int hour=dc2LocalHour.read();if(hour<0||hour>23)throw new IOException("PC local hour outside native SYSTEMTIME range");return hour;}
        switch(i){case 3:return 1;case 4:return fullscreen?1:0;case 242:return 0;case 243:return pc1!=null?registers.getOrDefault(243,-1):catalogIndex("images",scene.slots[1]);case 244:return pc1!=null?0:logicalMusic();
        case 245:return pc1!=null?0:ui.hovered;case 246:return pc1!=null||original?0:list.active()?registers.getOrDefault(246,0):gridWaiting?2:0;case 247:return 0;case 248:return audioActive[1]?0:1;case 249:return audioActive[5]?0:1;case 250:return audioActive[0]?0:1;
        case 251:return pc1!=null?pc1.cachedScenarioRead():script==null?-1:catalogIndex("scenario_scripts",script.name);case 255:return pc1!=null?0:registers.getOrDefault(255,0);
        default:return pc1!=null&&i!=0&&i!=2&&i!=21&&i!=22&&i!=252&&i!=253&&i!=254?0:registers.getOrDefault(i,0);}
    }
    private static int at(byte[] b,int i){return i<b.length?b[i]&255:0;}
    @Override public void write(int b,int i,int v)throws IOException{
        if(pc1!=null&&pc1.nativeVariables()!=null){pc1.nativeVariables().write(b,i,v);return;}
        if(b==90&&pc1!=null){pc1.nativeMemory().writeBank90(i,v);return;}
        if(pc1!=null&&(b==97||b==98||b==100)){applyNativeSettingsWrite(b,i,v);return;}
        if(pc1!=null&&b==99){if(i<0)return;if(Pc1NativeSettings.owns99(i)){applyNativeSettingsWrite(b,i,v);return;}}
        if(pc1!=null){
            //411B10 has only these external DWORD stores after the raw settings cases.
            //Unknown indices, including the old portable pager246, have no effect.
            if(b==99){int address=i==0?Pc1VmGlobals.RESULT0:i==19?Pc1VmGlobals.SET19:i==243?Pc1VmGlobals.SELECTION243:i==254?0x968a00:0;
                if(address!=0)pc1.drawMemory().writeInt(address,i==243&&v==255?-1:v);}
            return;
        }
        if(i<0)throw new IOException("Negative PC variable index");
        byte[] target=b==100?globals:b==97?cg:b==98?seen:b==95&&second?voiceEnabled:b==90?scratch:null;
        if(target!=null){if(second&&i>=target.length)throw new IOException("Unreviewed native byte-bank span "+b+":"+i);if(i<target.length)target[i]=(byte)v;return;}
        if(b==99&&i<256){registers.put(i,v);if(original&&i==7&&audioActive[5])originalPlaybackGain=Dc2Voice.gain(0,read(99,9));} // Native setters for unknown banks have no effect.
    }
    @Override protected void unlock(String table,String name,int bank)throws IOException{int n=catalogIndex(table,name);if(n>=0&&(pc1==null||!table.equals("scenario_scripts"))){write(bank,n,1);if(second&&table.equals("scenario_scripts"))write(90,n,1);}}
    @Override public boolean beforeTransfer(int mode,String name)throws IOException{
        if(mode<1||mode>3)throw new IOException("PC transfer mode "+mode);
        if(pc1!=null)pc1.scriptLoaded(mode,name);
        ui.clearCurrent();grid.clear();list.clear();gridTarget=false;musicEndAction="";gridWaiting=confirming=false;remaining=0;body=speaker="";textWaiting=presentationWaiting=false;
        if(original){dc2Borrowed.loaded(mode);if(mode==1||mode==3){dc2Map.reset();dc2Credits.clear();dc2CreditsUnderlay=null;dc2CharacterEnding.clear();dc2EndingUnderlay=null;dc2ActorMotion.clear();dc2ActorChange.clear();}originalWaitKind=-1;originalInputMode=0;clearOriginalDirty();originalResetBatch=false;originalDeferred=null;originalNextSpeaker="";originalSpeakerEnd=-1;}
        if(pc1==null&&(mode==1||mode==3))cacheOriginalScenarioRead(name);
        if(mode!=2){ui.actions[0]=ui.actions[1]="";}
        if(mode==1){scene.reset();ui.quads.clear();}return true;
    }
    /** Resource selector codes are not texture slots; both generations use distinct maps. */
    private int slot(int value)throws IOException{
        if(second){if(value==0)return 1;if(value>=1&&value<=4)return value+1;if(value>=6&&value<=8)return value+4;if(value>=11&&value<=14)return value+10;}
        else {if(value==0)return 1;if(value>=1&&value<=3)return value+9;if(value==4)return 21;if(pc1!=null&&(value==5||value==6))throw new IOException("Unmodelled PC1 native bitmap cache selector "+value+"; it is not an opcode25 named-resource handle");if(value==5)return 23;if(value==6)return 24;}
        throw new IOException("Unimplemented PC texture selector "+value);
    }
    private void quad(int[] a,boolean sprite)throws IOException{
        if(a.length!=8)throw new IOException("PC quad extension missing");
        if(pc1!=null&&!sprite&&ui.quads.size()>=60)return;
        int[] q=a.clone();
        // PC1 5/7 and PC2 20/21 describe a slider's variables/detents, not texture coordinates.
        if(sprite&&((!second&&(a[7]==5||a[7]==7))||(second&&(a[7]==20||a[7]==21)))){q[7]=a[7]==20?5:a[7]==21?7:a[7];ui.quad(q,true);return;}
        if(sprite&&second){if(q[7]==22)q[7]=12;else if(q[7]==23)q[7]=6;}
        boolean fill=!sprite&&(q[6]&64)!=0;
        if(!fill){q[2]=sprite?a[0]-a[2]:a[2];q[3]=sprite?a[1]-a[3]:a[3];if(!sprite){q[4]=a[4]-a[2];q[5]=a[5]-a[3];}q[6]=(q[6]&~63)|slot(q[6]&63);}
        if(sprite){q[6]|=spriteFlags;spriteFlags=0;if(q[7]==9)q[7]=99;}
        if(sprite&&gridTarget&&!grid.isEmpty()){Dc2Ui.Quad quad=ScriptList.sprite(q);quad.x+=ui.x;quad.y+=ui.y;grid.get(grid.size()-1).sprites.add(quad);}else{Dc2Ui.Quad created=ui.quad(q,sprite);if(pc1!=null&&!sprite)created.nativeCondition=pc1.takeCondition();}
    }
    /** Presentation projection of an already produced raw record. It does not
     *write PC/count/condition or feed converted UI fields back into the frame. */
    private Dc2Ui.Quad nativeDrawProjection(int[] d)throws IOException{
        if(d[2]==-1)return null;Dc2Ui.Quad q=new Dc2Ui.Quad();q.x=d[0];q.y=d[1];q.nativeCondition=d[3];q.width=d[6];q.height=d[7];
        if(d[2]==10){q.type=2;q.sx=d[4];q.sy=d[5];q.nativeTextOffset=d[4];return q;}
        q.type=(d[2]&64)!=0?1:0;q.sx=q.x-d[4];q.sy=q.y-d[5];q.flags=q.type==1?d[2]:(d[2]&~127)|slot(d[2]&127);return q;
    }
    private void projectProducedRecord(int index)throws IOException{
        if(index<0||index>=60)return;int[] raw=new int[8];for(int n=0;n<8;n++)raw[n]=pc1.nativeFrame().pendingDword(index,n);Dc2Ui.Quad q=nativeDrawProjection(raw);if(q!=null)ui.quads.add(q);
    }
    private void projectNativeTemplate(int selector)throws IOException{
        if(selector>0&&selector<8){List<Dc2Ui.Quad> target=ui.quadTemplates.get(selector);target.clear();for(Pc1MessageGeometry.Entry entry:pc1.drawMemory().template(selector)){Dc2Ui.Quad q=nativeDrawProjection(entry.snapshot());if(q!=null)target.add(q);}}
        ui.quads.clear();
    }
    /** Temporary presentation DTOs. Addressed UI records remain authoritative. */
    private List<Dc2Ui.Button> nativeButtonProjection(int base,int count)throws IOException{
        List<Dc2Ui.Button> result=new ArrayList<>();if(count<0||count>Pc1NativeUi.MAIN_CAPACITY)return result;
        Pc1NativeUi raw=pc1.nativeUi();
        for(int n=0;n<count;n++){
            int at=base+n*Pc1NativeUi.BUTTON_BYTES;Dc2Ui.Button b=new Dc2Ui.Button();
            b.flags=raw.readInt(at);b.binding=raw.readInt(at+4);b.left=raw.readInt(at+12);b.top=raw.readInt(at+16);b.right=raw.readInt(at+20);b.bottom=raw.readInt(at+24);
            b.hoverSound=raw.readInt(at+0x1fc);b.clickSound=raw.readInt(at+0x200);b.action=TextCodec.cp932(raw.cstring(at+0x204));
            for(int f=0;f<12;f++){
                int c=at+0x1c+40*f,flag=raw.readInt(c);if(flag==0)continue;Dc2Ui.Quad q=new Dc2Ui.Quad();q.state=flag;q.x=raw.readInt(c+4);q.y=raw.readInt(c+8);q.flags=raw.readInt(c+12);q.sx=raw.readInt(c+20);q.sy=raw.readInt(c+24);q.width=raw.readInt(c+28);q.height=raw.readInt(c+32);int source=raw.readInt(c+36);
                if(source!=0){q.type=2;q.text=codec.decodeUnmapped(Pc1TextExpander.expand(raw.cstring(source),pc1.textContext,2048));}
                b.sprites.add(q);
            }
            result.add(b);
        }
        return result;
    }
    private void projectNativeButtons()throws IOException{
        Pc1NativeUi raw=pc1.nativeUi();if(raw.readInt(Pc1NativeUi.BUTTON_POINTER)!=Pc1NativeUi.MAIN_BUTTONS)return;
        ui.buttons.clear();ui.buttons.addAll(nativeButtonProjection(Pc1NativeUi.MAIN_BUTTONS,raw.readInt(Pc1NativeUi.BUTTON_COUNT)));
    }
    private void produceNativeButtonTemplate(int selector)throws IOException{
        pc1.uiProducer().template17(selector);if(selector>=1&&selector<=4){
            int[] base={0,0x9cb908,0x9b8e20,0x9b9f20,0x9ccba8},counts={0,0xbb846c,0xbb8478,0xbb845c,0xbb8468};
            List<Dc2Ui.Button> target=ui.buttonTemplates.get(selector);target.clear();target.addAll(nativeButtonProjection(base[selector],pc1.nativeUi().readInt(counts[selector])));
            if(selector==1)pc1.projectStoryPrefix(target);
        }ui.buttons.clear();ui.hovered=ui.pressed=-1;
    }
    private void templates(int selector,boolean buttons)throws IOException{
        if(buttons){gridTarget=false;if(selector<0||selector>4)throw new IOException("PC button template "+selector);int[] cap={0,8,4,10,8};if(selector!=0){List<Dc2Ui.Button> t=ui.buttonTemplates.get(selector);t.clear();t.addAll(Dc2Ui.copyButtons(ui.buttons.subList(0,Math.min(cap[selector],ui.buttons.size()))));if(pc1!=null&&selector==1){for(Dc2Ui.Button b:t)b.flags=3;pc1.storeStoryPrefix(t);}}ui.buttons.clear();ui.hovered=ui.pressed=-1;return;}
        if(selector==255&&second){ui.quads.clear();return;}int[] cap={0,10,5,second?30:20,10,5,second?30:20,second?30:20};
        // D.C.II 2006 415F20..41614C clamps each batch at 10/5/30/10/5/30/30 for selectors 1..7.
        if(original)cap=new int[]{0,10,5,30,10,5,30,30};
        if(selector<0||selector>255)throw new IOException("PC draw template "+selector);if(selector>0&&selector<cap.length){List<Dc2Ui.Quad> t=ui.quadTemplates.get(selector);t.clear();t.addAll(Dc2Ui.copyQuads(ui.quads.subList(0,Math.min(cap[selector],ui.quads.size()))));}ui.quads.clear();if(pc1!=null&&(selector==1||selector==2||selector==4||selector==5))pc1.prepareGeometry();
    }
    @Override public void addChoice(byte[] label,byte[] caption)throws IOException{
        List<Dc2Ui.Button> templates=ui.buttonTemplates.get(3);int n=++choiceCount;if(n>=templates.size())throw new IOException("PC choice template missing");Dc2Ui.Button b=Dc2Ui.copyButtons(Collections.singletonList(templates.get(n))).get(0);b.action=TextCodec.cp932(label);
        for(Dc2Ui.Quad q:b.sprites)if(q.type==2){q.text=codec.decode(caption);q.width=caption.length*14+1;}ui.buttons.add(b);if(pc1!=null)pc1.appendedChoice();
    }
    /** PC2 412860 stores text state 9; 4132E3 makes it visible at rest, like image state 9. */
    private void normalizeButtonText(Dc2Ui.Quad q){if(second&&q.type==2&&q.state==9)q.state=99;}
    private void normalizeButtonText(List<Dc2Ui.Button> buttons){for(Dc2Ui.Button b:buttons)for(Dc2Ui.Quad q:b.sprites)normalizeButtonText(q);}
    @Override public boolean command(String op,int[] a,byte[] bytes)throws IOException{
        if(pc1!=null)pc1.beforeCommand(op,a);
        if(pc1!=null&&op.equals("audio.music")&&a.length>=2&&a[0]!=255){if(a[1]==0||a[1]==1&&logicalMusic()!=a[0])pc1BgmMode=0;else if(a[1]==2)pc1BgmMode=1;}
        // DC2's service default is an actual native return; do not clear generic PC
        // wait/input state before the version-specific dispatcher can classify it.
        if(original&&op.equals("native.unmodelled")&&script!=null){int opcode=script.fetchRecord(pc).opcode;if(opcode==0x61){originalActorMotion(a);return true;}if(opcode==0x22)return command("pc.random",a,bytes);}
        if(original&&op.equals("dm.system.command")||op.equals("native.unmodelled")||op.equals("native.no_effect"))return super.command(op,a,bytes);
        if(original&&op.equals("pc.effect")){if(a[0]==2)throw originalUnsupported("2A kind2 rain kernel412380/4124C0 remains unreviewed");scene.nativeParticles.command(a[0],a[1],read(99,20)!=0);if(scene.nativeMotion!=null&&!scene.nativeMotion.active){scene.nativeMotion=null;if(originalDrawDirty==0)scene.publish(ui,0,0);}return true;}
        if(op.equals("pc.effect")&&(a[0]==5||a[0]==6))return super.command("dm.scene.effect",a,bytes);
        timedInput=false;
        String text=new String(bytes,Binary.SJIS);
        switch(op){
        case "dc2.scene.motion":if(!original)throw new IOException("D.C.II 2006 motion requires dc2-3363");originalMotion(a);return true;
        case "dc2.ui.button_voice":case "dc2.ui.button_hover_voice":{
            if(!original)throw new IOException("D.C.II region voice requires dc2-3363");
            if(ui.buttons.isEmpty())return true;Dc2Ui.Button button=ui.buttons.get(ui.buttons.size()-1);
            if(op.equals("dc2.ui.button_voice")){button.voice=text;button.clickSound=255;}else{button.hoverVoice=text;button.hoverSound=255;}return true;
        }
        case "dm.audio.voice":if(original&&!text.equals("--")){submitOriginal(originalCommand(1,text));return true;}lineVoice=(original?text.equals("--"):voiceStops(text))?"":text;playVoice(text);return true;
        case "ui.music_end_action":musicEndAction=text;return true;
        case "pc.button_rect_payload":{
            if(pc1==null||!pc1.dispatching())return command("host.command_with_string",a,bytes);
            pc1.uiProducer().rectangle4f(pc1.nativeFrame(),a[2],a[3]);gridTarget=false;projectNativeButtons();return true;
        }
        case "host.command_with_string":{
            gridTarget=false;super.command(op,a,bytes);
            // D.C.II 29h sets BE8BAC=1 and 4Eh consumes it on the region it just created.
            if(pendingRegionFlag){ui.last().flags|=1;pendingRegionFlag=false;}
            return true;
        }
        case "dm.ui.grid_rect":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().gridRect55(pc1.nativeFrame(),a[2],a[3]);return true;}gridTarget=true;return super.command(op,a,bytes);
        case "dm.ui.button_sound":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().sounds(a[0],a[1]);projectNativeButtons();return true;}return super.command(op,a,bytes);
        // 414EF1 sets BE8BAC=1; 414F61 then consumes it to flag the region the next 4Eh creates.
        case "pc.region_flag":pendingRegionFlag=true;return true;
        case "pc.grid.item":gridTarget=false;list.add(a,slot(a[6]));return true;
        case "grid.item_action":list.last().action=text;return true;
        // 41D696 writes the preceding entry's x/y and normal/hover palettes,
        // consuming the next raw string record even when there is no entry.
        case "pc.grid.caption":{
            if(!second||a.length!=4)throw new IOException("PC2 list caption arguments");
            if(list.items.isEmpty())return true;
            ScriptList.Item item=list.last();item.sprites.removeIf(q->q.type==2);
            String caption=gridCaption(bytes);
            for(int n=0;n<2;n++){Dc2Ui.Quad q=new Dc2Ui.Quad();q.type=2;q.x=a[0];q.y=a[1];q.flags=a[2+n];q.state=n==0?99:1;q.text=caption;q.width=bytes.length*14;q.height=28;item.sprites.add(q);}
            return true;
        }
        case "text.speaker":if(original){originalNextSpeaker=prose(bytes);originalSpeakerEnd=script==null?pc+bytes.length+2:script.instruction(pc).next;return true;}if(pc1!=null){byte[] expanded=pc1.expand(bytes,true);pc1.textBuffers().writeExpanded(Pc1TextBuffers.LIVE_SPEAKER,expanded);speaker=codec.decode(expanded);}else speaker=prose(bytes);speakerPending=true;return true;
        case "text.body":if(original){submitOriginal(originalCommand(2,prose(bytes)));return true;}scene.publish(ui,0,0);if(pc1!=null){if((pc1.fetchedOpcode(0x4c)?pc1.previousOpcode()!=0x4b:!speakerPending)){pc1.textBuffers().clearLiveSpeakerHead();speaker="";}if(messageMode()!=0)speaker="";byte[] expanded=pc1.expand(bytes,false);pc1.textBuffers().writeExpanded(Pc1TextBuffers.LIVE_BODY,expanded);body=codec.decode(expanded);pc1.historySelection(-1);pc1.waitKind(0x4c);pc1.presentLiveText();}else{if(!speakerPending||messageMode()!=0)speaker="";body=prose(bytes);if(!body.isEmpty())history.append(speaker,body,lineVoice,script.name,pc);}speakerPending=false;unlock("scenario_scripts",script.name,98);lineVoice="";presentationWaiting=true;textRendered=false;nextTicket();return true;
        case "dm.wait.message":if(pc1!=null){pc1.textLog.onWait07(a[0],messageMode());if(pc1.lastYieldOpcode()==0x4c){pc1.historySelection(-1);pc1.waitKind(7);pc1.activateDictionary();textWaiting=true;nextTicket();}return true;}return super.command(op,a,bytes);
        case "ui.button_text":case "dm.ui.text":{
            if(pc1!=null&&pc1.dispatching()&&op.equals("ui.button_text")){pc1.uiProducer().text51(pc1.nativeFrame(),a[2],a[3]);projectNativeButtons();return true;}
            if(pc1!=null&&pc1.dispatching()&&op.equals("dm.ui.text")&&pc1.fetchedOpcode(0x5c)){int count=pc1.nativeFrame().dword(Pc1NativeSlot.Dword.PENDING_COUNT);pc1.produceText5c(a[2],a[3]);if(count>=0&&count<60)projectProducedRecord(count);return true;}
            boolean sprite=op.equals("ui.button_text");if(pc1!=null&&!sprite&&ui.quads.size()>=60)return true;ui.text(a,new byte[0],sprite);Dc2Ui.Quad q=sprite?ui.last().sprites.get(ui.last().sprites.size()-1):ui.quads.get(ui.quads.size()-1);
            if(pc1!=null&&!sprite){q.nativeCondition=pc1.takeCondition();if(pc1.fetchedOpcode(0x5c)){q.nativeTextOffset=script.instruction(pc).stringPayload.offset+1-script.versionOffset;return true;}}
            // The 2006 scene-replay lists prefix every label with "@s" (4674 of 4685 cases) and the
            // native sprite keeps the marker inside its 14-pitch width. D.C.II P.C stores the same
            // labels without it, so draw the bare text and keep the native width.
            byte[] label=bytes;
            if(sprite&&label.length>2&&label[0]=='@'&&label[1]=='s')label=java.util.Arrays.copyOfRange(label,2,label.length);
            q.text=sprite?buttonCaption(label):codec.decode(label);if(sprite){q.width=bytes.length*14+1;normalizeButtonText(q);}return true;}
        case "pc.movie":{
            // D.C.II writes "advdata\movie\name.mpg"; normalise before the prefix check so the
            // path is not prefixed twice.
            String path=text.replace('\\','/');
            if(!path.toLowerCase(Locale.ROOT).startsWith("advdata/"))path="AdvData/"+path;
            movie=Binary.safePath(path);movieGeneration++;play(0,"",false);nextTicket();return true;
        }
        case "pc.history":case "pc.checkpoint":checkpoint=a[0]|a[1]<<8;if(pc1!=null){if(op.equals("pc.history"))pc1.textLog.onCheckpoint03(checkpoint);else pc1.textLog.onCheckpoint04(checkpoint);}if(op.equals("pc.history"))readCheckpoints.add(script.name+":"+checkpoint);return true;
        case "pc.reset":variables.clearOrdinary();variables.clearHistory();return true;
        case "pc.save_context":if(pc1!=null)pc1.prepareSave();return true;
        case "pc.fullscreen":windowRequest=a[0];fullscreen=a[0]!=0;return true; // Android owns the full-screen surface.
        case "pc.sprite_opaque":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().opaque19();return true;}spriteFlags=128;return true;
        case "pc.button_hover_voice":return true; // Hover sounds have no corresponding hover event on a touchscreen.
        case "pc.rapid_binding":if(pc1!=null)pc1.conditionalNext();return true;
        case "pc.button_template":if(pc1!=null&&pc1.dispatching()){produceNativeButtonTemplate(a[0]);return true;}templates(a[0],true);return true;
        case "pc.quad_template":if(pc1!=null&&pc1.dispatching()){pc1.produceTemplate22();projectNativeTemplate(a[0]);return true;}if(original&&a[0]==0)dirtyOriginal();templates(a[0],false);return true;
        case "pc.resource":case "pc.resource_payload":if(pc1!=null&&a[0]!=0&&a[0]!=2&&a[0]!=3)return true;if(a[0]<0||a[0]>3)throw new IOException("PC message resource slot");scene.slots[21+a[0]]=text;return true;
        // Original D.C.II 414B9D switches on the argument into three fixed handles 45E808/45E80C/
        // 45E810, i.e. 0 -> 21, 2 -> 22, 3 -> 23. There is no handle for 1, so the PC slot formula
        // above does not apply to this profile.
        case "dm.system.resource_handle":{
            int slot=a[0]==0?21:a[0]==2?22:a[0]==3?23:-1;
            if(slot<0)throw new IOException("DM resource handle "+a[0]);
            scene.slots[slot]=text;return true;
        }
        case "pc.image6":case "pc.image7":case "pc.image8":scene.slots[10+op.charAt(op.length()-1)-'6']=text.equals("-")?"":text;return true;
        case "pc.background":if(original)dirtyOriginal();super.command("dm.scene.background",a,bytes);if(original)originalCompositePending=0;return super.command("dm.scene.preload",new int[]{1},bytes);
        case "pc.character_pair":return super.command("dm.scene.character",a,bytes);
        case "pc.portrait_pair":scene.slots[18+a[0]]=text.equals("-")?"":text;return true;
        // Original D.C.II 416896 encodes no slot byte: the string record alone picks the actor,
        // so 32h drives slot 0 and 33h drives the overlay slot.
        case "pc.character":case "pc.character_overlay":{
            boolean overlay=op.equals("pc.character_overlay");
            if(original&&!overlay&&a.length==0)return originalActorChange(bytes);
            int priorComposition=originalCompositePending;
            if(original&&!overlay&&originalDrawDirty!=0&&(script==null||script.instruction(pc).next>=script.length()||script.rawOpcode(script.instruction(pc).next)!=2))throw originalUnsupported("32 actor coroutine must flush native9DA2D0 through413E70/41C440; only its02 deferred batch producer is reviewed");
            int[] slot=a.length>0?a:new int[]{overlay?1:0};
            boolean loaded=super.command(overlay?"dm.scene.character_overlay":"dm.scene.character",slot,bytes);
            if(original&&!overlay&&priorComposition>0&&originalDrawDirty==0)originalCompositePending=messageMode()==0?2:1;
            return loaded;
        }
        case "pc.quad":if(pc1!=null&&pc1.dispatching()){int count=pc1.nativeFrame().dword(Pc1NativeSlot.Dword.PENDING_COUNT);pc1.produceQuad53(a[2],a[3]);if(count>=0&&count<60)projectProducedRecord(count);return true;}quad(a,false);return true;
        // Original D.C.II 416151 records the 51h companion as {sx, sy, right, bottom}: the unflagged
        // form stores right-sx/bottom-sy, while 40h keeps both raw and reuses the low bits as a colour.
        // That is the same contract quad() already applies for pc.quad.
        case "dc2.quad":if(a.length!=8)throw new IOException("D.C.II region needs the 51h extension");if(original)dirtyOriginal();quad(a,false);if(original)originalCompositePending=0;return true;
        case "pc.button_sprite":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().sprite50(pc1.nativeFrame(),a[2],a[3]);projectNativeButtons();return true;}quad(a,true);return true;
        case "pc.origin":if(pc1!=null&&pc1.dispatching()){pc1.produceOrigin5d(a[2]);return true;}ui.x=a[2]==0?a[0]:ui.x+a[0];ui.y=a[2]==0?a[1]:ui.y+a[1];return true;
        case "pc.action0":case "pc.action1":case "pc.action2":case "pc.action3":ui.actions[op.charAt(op.length()-1)-'0']=text;return true;
        case "pc.ui.wait":if(pc1!=null)pc1.waitKind(10);if(original){originalWaitKind=10;originalInputMode=a[1];originalResetBatch=true;}if(a[1]==1){delay(200L*a[0]);return true;}if(original){originalCompositePending=0;clearOriginalDirty();}ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "dm.ui.grid_wait":if(original){originalCompositePending=0;clearOriginalDirty();originalResetBatch=true;originalWaitKind=11;originalInputMode=a.length>1?a[1]:0;}return super.command(op,a,bytes);
        case "pc.choice.begin":if(pc1!=null){pc1.beginChoice(a[0]);return true;}choiceMode=a[0];choiceCount=0;ui.buttons.clear();return true;
        case "pc.choice.wait":if(pc1!=null){pc1.choiceWait();pc1.waitKind(9);pc1.redrawText(script,-1);}if(original){originalWaitKind=9;originalResetBatch=true;if(originalDrawDirty!=0)throw originalUnsupported("09 choice wait with unflushed native draw batch");}ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "pc.wait":if(pc1!=null)pc1.waitKind(18);if(original){originalWaitKind=18;originalInputMode=a[1];clearOriginalDirty();originalResetBatch=true;}timedInput=true;scene.publish(ui,0,0);delay(a[0]==0?-1:200L*a[0]);return true;
        case "pc.effect":
            if(!second&&(a[0]==1||a[0]==3||a[0]==4)){scene.slots[16]="system/"+(a[0]==3?"YUKI01":a[0]==1?"SACRA01":"SACRA02");scene.effect(1,a[1]);return true;}
            if(second&&(a[0]==1||a[0]==3||a[0]==4||a[0]==11||a[0]==12)){scene.slots[16]="system/"+(a[0]==12?"SAKU_YUKI02":a[0]==11?"SAKU_YUKI01":a[0]==3?"YUKI01":a[0]==1?"SACRA01":"SACRA02");scene.effect(1,a[1]);return true;}
            return super.command("dm.scene.effect",a,bytes);
        case "pc.transition":if(original&&(a[0]==1||a[0]==2)){clearOriginalDirty();originalResetBatch=true;}if(original&&(a[0]==0||a[0]==13)){originalCompositePending=0;clearOriginalDirty();originalResetBatch=true;}if(a[0]==13){if(a[1]!=0&&a[1]!=1&&a[1]!=2)throw new IOException("PC2 mask variant "+a[1]);int minimum=a[1]==0?0:a[1]==1?(second?-191:-199):(second?-340:-348);int gain=0,ticks=1;while(minimum+gain<16){ticks++;gain+=Math.min(16,ticks);}scene.publish(ui,130+a[1],ticks*40L);delay(scene.transitionDuration);return true;}if(a[0]==7){return true;} // Native creates/releases a temporary transition bitmap, not a timed transition.
            if(second&&(a[0]==21||a[0]==22)){int[] rates={64,64,32,16,8,4,2,1};int duration=a[1]<8?40*(256+rates[a[1]]-1)/rates[a[1]]:(a[1]*5-35)*8*256;scene.publish(ui,200+a[0],duration);delay(duration);return true;}
            if(a[0]==9){captureUi();return true;} // Captured caller is a logical frame instead of a native render target.
            if(a[0]==10||a[0]==12||a[0]==14){scene.publish(ui,0,200L*Math.max(1,a[1]));delay(scene.transitionDuration);return true;}return super.command("dm.scene.transition",a,bytes);
        case "pc.scroll":if(a[2]==2){scene.portraitX=a[0];scene.portraitY=a[1];return true;}return super.command("dm.scene.scroll",a,bytes);
        case "pc.actor_position":if(original){System.arraycopy(a,0,dc2ActorChange.staged,0,4);return true;}if(pc1!=null&&pc1.dispatching()){Pc1DrawProducer.stageActor60(pc1.nativeFrame(),a[2],a[3]);return true;}if(second){int n=a[2];if(n>=0&&n<4){scene.character(n,0,a[0]);scene.character(n,1,a[1]);scene.character(n,2,100);return true;}}else{scene.character(0,0,a[0]);scene.character(0,1,a[1]);scene.character(1,0,a[2]);scene.character(1,1,a[3]);return true;}throw new IOException("PC actor position "+Arrays.toString(a));
        case "pc.actor_transform":if(second&&(a[3]&16)!=0)return super.command("dm.scene.character_transform",a,bytes);if(second&&a[3]<4){int n=a[3];scene.character(n,0,a[0]);scene.character(n,1,a[1]);scene.character(n,2,a[2]);scene.publish(ui,0,200);delay(200);return true;}throw new IOException("PC actor transform "+Arrays.toString(a));
        case "pc.sound8":if(pc1!=null)pc1.soundRequest(a[0],a[1]);return super.command("dm.audio.sound",new int[]{a[0]==255?-1:a[0],a[1]},bytes);
        case "pc.save_caption":checkpointLabel=text;return true;
        case "pc.persist":switch(a[0]){
            case 0:loadRequest=read(99,254);nextTicket();return true;
            case 8:windowRequest=a[1];fullscreen=a[1]!=0;return true;
            case 2:case 3:case 5:case 10:case 12:case 17:return true;
            case 4:if(pc1!=null){pc1.requestSave(a[1]);return true;}if(original){if(a[1]==1&&dc2Borrowed.flag==0)dc2Borrowed.produce(script,pc,script.fetchRecord(pc).end,2);saveRequest=a[1]==1?0:read(99,254);nextTicket();return true;}if(a[1]==1)autosaveGeneration++;else{saveRequest=read(99,254);nextTicket();}return true;
            case 6:if(pc1!=null)pc1.textLog.clearLog();else variables.clearHistory();return true;case 7:skipAllowed=a[1]!=0;return true;case 11:messageMode(a[1]);if(pc1!=null){pc1.textLog.clearLog();pc1.prepareGeometry();}return true;case 13:inputMode=a[1];return true;
            default:throw new IOException("Unimplemented PC persistence "+Arrays.toString(a));}
        case "pc.wait.media_mask":if((a[0]&~7)!=0)throw new IOException("PC native media timer mask "+a[0]);return super.command("wait.audio",a,bytes); // 41ED22 / 4230E0: music=1, voice=2, SE=4.
        case "pc.voice_group.add":if(voiceGroup.size()<10)voiceGroup.add(text);return true;
        case "pc.voice_group.play":
            if(a[0]==2){voiceGroup.clear();return true;}if(voiceGroup.isEmpty())return true;
            if(a[0]==0){lineVoice=voiceGroup.get(nextRandom()%voiceGroup.size());playVoice(lineVoice);}
            // 41CC10 uses device channels 3..9; 427680 uses 4..9. Group
            // playback checks the global voice switch, not individual mutes.
            else if(a[0]==1){lineVoice=MixedVoice.reference(voiceGroup.subList(0,Math.min(voiceGroup.size(),second?6:7)));voicePan=0;play(5,lineVoice,false);}return true;
        // PC1 414DC0 / PC2 41EDA4 store rand()%range in 99:2. A zero range
        // stores zero without calling rand; 99:255 is an independent result field.
        case "pc.random":{int value=a[0]==0?0:nextRandom()%a[0];if(pc1!=null)pc1.drawMemory().writeInt(Pc1VmGlobals.RANDOM_RESULT,value);else registers.put(2,value);return true;}
        case "pc.grid.begin":grid.clear();return true;
        case "pc.grid.configure":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().grid54(pc1.nativeFrame(),a[2],a[3]);grid.clear();list.clear();gridTarget=false;gridWaiting=false;return true;}if(a.length!=8)throw new IOException("PC grid extension missing");System.arraycopy(a,0,gridBounds,0,8);grid.clear();list.clear();gridTarget=false;gridWaiting=false;gridSelection(-1,-1);return true;
        case "pc.grid.caption_payload":if(pc1==null)throw new IOException("PC1 grid caption owner unavailable");pc1.uiProducer().gridCaption57(pc1.nativeFrame(),a[2],pc1::expandUi);return true;
        case "pc.grid.item_payload":if(pc1==null)throw new IOException("PC1 grid item owner unavailable");pc1.uiProducer().gridItem56(pc1.nativeFrame(),a[2],a[3]);return true;
        case "pc.grid.caption_bounds":if(pc1==null)throw new IOException("PC1 grid caption bounds owner unavailable");pc1.uiProducer().gridCaptionBounds5f(a[0],a[1],a[2],a[3]);return true;
        case "pc.grid.wait":if(pc1!=null)pc1.waitKind(11);if(list.active()){list.columns=grid.size()>1&&grid.get(0).top==grid.get(1).top?2:1;listOffset(list.clamp(listOffset(),grid.size()));}gridWaiting=ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "pc.grid.sound":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().gridSounds14(a[0],a[1]);return true;}gridHoverSound=a[0];gridClickSound=a[1];return true;
        case "pc.grid.voice":if(pc1!=null&&pc1.dispatching()){pc1.uiProducer().sealGrid43(pc1.rawStringPointer());return true;}if(!grid.isEmpty())grid.get(grid.size()-1).voice=text;return true;
        case "pc.confirm_position":confirmX=a[0];confirmY=a[1];confirmKind=a[3];return true;
        case "pc.prompt_position":confirmX=a[0]*8;confirmY=a[1]*8;return true;
        case "pc.confirm":{
            List<Dc2Ui.Button> list=Dc2Ui.copyButtons(ui.buttonTemplates.get(2));if(list.size()<3)throw new IOException("PC original confirmation template missing");Dc2Ui.Button panel=list.get(0);int dx=confirmX-panel.left,dy=confirmY-panel.top;boolean caption=false;
            for(Dc2Ui.Button b:list){b.left+=dx;b.right+=dx;b.top+=dy;b.bottom+=dy;for(Dc2Ui.Quad q:b.sprites){q.x+=dx;q.y+=dy;if(b==panel&&q.type==2){q.text=codec.decode(bytes);q.width=Math.max(1,bytes.length*11);caption=true;}}}
            if(!caption)throw new IOException("PC confirmation text anchor missing");ui.buttons.clear();ui.buttons.addAll(list);confirming=ui.waiting=true;gridWaiting=false;nextTicket();return true;
        }
        case "pc.command":
            if(pc1!=null&&a[0]==7)return true; //404EE0 selector7 takes the native default return.
            if(!second&&a[0]==8){nameInput.prepare(resources,codec);if(pc1!=null){familyName=codec.decodeUnmapped(nativeFamilyName());givenName=codec.decodeUnmapped(nativeGivenName());}nameInput.open(familyName,givenName);nameInput.build(ui);scene.publish(ui,0,0);nextTicket();return true;}if(second&&a[0]==9)return true; /* 407350 selects the generic music list renderer. */
            if(second&&a[0]==7){list.textStyle=4;return true;} // 408911 -> 407340: Scene list renderer; argument 1 is ignored.
            if(second&&a[0]==20){map.open(variables);map.buttons(ui);scene.publish(ui,0,0);nextTicket();return true;}if(a[0]==6){saveGrid=a[1]==0;return true;}
            if(second&&a[0]==10){endingClock=0;return true;} // 408960: records timeGetTime for the ending service.
            if(second&&a[0]==11){if(a[1]==0)pcCredits.active=false;else{pcCredits.start(a[1],endingClock);scene.publish(ui,0,0);nextTicket();}return true;}
            if(!second&&a[0]==10){pcCredits.preparePc1();return true;} // 405180 prepares twelve original 800x1200 strips.
            if(!second&&a[0]==11){pcCredits.active=true;scene.publish(ui,0,0);nextTicket();return true;}
            if(a[0]==1){calendar(a[1]);return true;}
            if(a[0]==2&&(a[1]==0||a[1]==1)){rank(a[1]);return true;}
            if(a[0]!=7&&a[0]!=8&&a[0]!=9&&a[0]!=10&&a[0]!=11&&a[0]!=100&&a[0]!=101)return true; // Explicit default return in 404EE0 / 408560.
            throw new IOException("Unimplemented PC native system command "+Arrays.toString(a));
        default:return super.command(op,a,bytes);
        }
    }
    /** The native Scene renderer indexes the scenario catalog by the list entry id.
     * Empty actions are locked entries and must not reveal their date or decoration. */
    public String sceneListName(ScriptList.Item item)throws IOException{
        if(!second||list.textStyle!=4||item.action.isEmpty())return "";
        List<NamesCatalog.Entry> entries=resources.names.table("scenario_scripts");
        return item.id>=0&&item.id<entries.size()?entries.get(item.id).name:"";
    }
    /** 407360: this specific set receives the original special-scene background. */
    static boolean sceneListSpecial(String name){
        String[] names={"ind_0110_e1_rno","fst_1224_f1_snb","ind_0102_f1_mhr","ind_0121_e2_ais","ind_0120_f2_erk","ind_0118_f2_erk","ind_0128_f2_myk","ind_0122_f3_myk","ind_0124_f1_mya","ind_0120_f3_mya","ind_0211_f1_akn","ind_0115_f2_akn","ind_0129_f2_anz","ind_0120_f1_mnt","ind_0116_f1_mnt","ind_0127_f1_nnk","ind_0116_f1_nnk","ind_0127_f3_kko","ind_0128_a1_yme","ind_0121_f2_yme","ind_0120_f3_yme","ind_0401_f1_otm"};
        for(String value:names)if(value.equalsIgnoreCase(name))return true;return false;
    }
    /** 4078B0 / 407F48: date or original route-period label, independent of the title. */
    static String sceneListDate(String name){
        if(name.isEmpty())return "";
        if(name.charAt(0)=='0'||name.charAt(0)=='1')return " dacapo";
        int separator=name.indexOf('_');String value=separator<0?"":name.substring(separator+1);int date=0;
        for(int i=0;i<value.length()&&value.charAt(i)>='0'&&value.charAt(i)<='9';i++)date=date*10+value.charAt(i)-'0';
        if(date==0||date>1300||date==400||value.equals("0216_b1_anz")||value.equals("0305_b1_akn")||value.equals("0125_a1_rno"))return " epilogue";
        if(date/100==4)return " alfine";
        if(date==1)return " dacapo";if(date==2)return " alfine";if(date==3)return " epilogue";
        return date>=100?String.format(Locale.ROOT,"%2d月%2d日",date/100,date%100):"";
    }
    /** Native 404EE0 / 408560; calendar fields are script-visible route state. */
    private void calendar(int mode)throws IOException{
        if(mode!=0&&mode!=1)return;
        if(mode==0){String n=script.name.replace('\\','/');n=n.substring(n.lastIndexOf('/')+1).toUpperCase(Locale.ROOT);
            if(second){
                if(n.length()<9||!Character.isDigit(n.charAt(4))){variables.write(9,0,0);variables.write(9,1,0);variables.write(9,3,0);variables.write(9,20,5);}
                else{int month=Integer.parseInt(n.substring(4,6)),day=Integer.parseInt(n.substring(6,8));variables.write(9,0,month);variables.write(9,1,day);variables.write(9,3,n.charAt(8)-'A');
                    int season=month==7&&day<22?(n.startsWith("S_GM0721E")||n.startsWith("S_GM0721F")?1:0):n.startsWith("S_AS")||n.startsWith("S_HS")?0:n.startsWith("S_GM")?1:2;
                    variables.write(9,80,season);variables.write(9,20,n.startsWith("S_AS")?3:n.startsWith("S_GM")?4:n.startsWith("S_TM")||n.startsWith("S_TT")?0:n.startsWith("S_MM")?2:n.startsWith("S_KN")||n.startsWith("S_KH")?1:5);
                }
            }else{if(n.length()<5)throw new IOException("PC calendar filename too short: "+n);variables.write(9,0,(n.charAt(0)-'0')*10+n.charAt(1)-'0');variables.write(9,1,(n.charAt(2)-'0')*10+n.charAt(3)-'0');char period=n.charAt(4);variables.write(9,3,period=='B'?1:period=='C'||period=='D'?2:0);}
            if(!second)return;
        }
        int days=variables.read(9,1),month=variables.read(9,0);if(second&&month>12){month%=10;days+=365;}
        int[] lengths={31,28,31,30,31,30,31,31,30,31,30};for(int n=0;n<lengths.length&&n+1<month;n++)days+=lengths[n];variables.write(9,2,(days+(second?5:1))%7);
    }
    private void rank(int mode)throws IOException{
        // PC2's native loop reads thirteen words, including the adjacent constant bytes.
        // Preserve the observed binary's comparison exactly; do not invent new route priorities.
        int[] priorities=second?new int[]{6,5,4,3,2,1,1654170256,-1417526131,1351985282,27799,0,0,0}:new int[]{12,13,11,10,7,8,0,5,4,3,6,9,0};
        int score=mode==0?0:2099,index=mode==0?0:15;for(int n=0;n<priorities.length;n++){int value=variables.read(0,n)*100+priorities[n];if(value>score){score=value;index=n;}}variables.write(9,10,index);
    }
    private void playOriginalVoice(String source,boolean button)throws IOException{
        if(source.isEmpty()||originalVoiceEnabled==0||button&&read(99,15)==0)return;
        int character=Dc2Voice.character(source);if(!Dc2Voice.forced(source)&&read(100,60+character)!=0)return;
        voiceCharacter=character;voicePan=0;originalPlaybackGain=Dc2Voice.gain(0,read(99,9));play(5,source,false);
    }
    @Override protected void playButtonVoice(String source)throws IOException{if(original)playOriginalVoice(source,true);else super.playButtonVoice(source);}
    public void hoverButton(Dc2Ui.Button button)throws IOException{if(button.hoverSound!=255)play(1,resources.named("sound",button.hoverSound),false);if(original&&!button.hoverVoice.isEmpty())playButtonVoice(button.hoverVoice);}
    @Override public void hoverMenu(int button)throws IOException{if(dc2Map.active){if(dc2Map.hover(ui,button))play(1,resources.named("sound",221),false);return;}if(!original){super.hoverMenu(button);return;}if(button==ui.hovered)return;if(button< -1||button>=ui.buttons.size())throw new IOException("D.C.II region hover index");ui.hovered=button;if(button>=0){registers.put(245,button);hoverButton(ui.buttons.get(button));}}
    @Override protected void playVoice(String source)throws IOException{if(original){if(source.equals("--")){play(5,"",false);return;}playOriginalVoice(source,false);return;}if(voiceStops(source)){play(5,"",false);return;}String name=source;voicePan=name.startsWith("<")?-1000:name.startsWith(">")?1000:0;if(name.startsWith("<")||name.startsWith(">")||name.startsWith("="))name=name.substring(1);voiceCharacter=PcVoice.character(name,second);play(5,PcVoice.forced(name,second)||read(100,60+voiceCharacter)==0?name:"",false);}
    @Override public float voiceGain()throws IOException{if(original)return originalPlaybackGain;return audio[5].startsWith("mix:")||PcVoice.forced(audio[5],second)||read(100,60+PcVoice.character(audio[5],second))==0?1:0;}
    /** PC1 420862 / PC2 42B54B: shared CRT stream, including signed-seed wraparound. */
    private int nextRandom(){randomState=randomState*214013+2531011;return(randomState>>>16)&32767;}
    int nativeCrtState(){return randomState;}
    void nativeCrtState(int value){randomState=value;}
    /** Shared43E100 CRT stream, including the discarded WM_TIMER1 call. */
    public int nativeCrtRandom()throws IOException{if(pc1==null)throw new IOException("PC1 CRT owner unavailable");return nextRandom();}
    /** Menu caption bytes can have a different source encoding from localized prose. */
    protected String buttonCaption(byte[] raw)throws IOException{return codec.decode(raw);}
    /** List captions are retained native UI fields in some translated games. */
    protected String gridCaption(byte[] raw)throws IOException{return prose(raw).replace("$f",familyName);}
    private String prose(byte[] raw)throws IOException{return codec.decode(raw).replace("$n",pc1==null?givenName:codec.decode(nativeGivenName())).replace("$s",pc1==null?familyName:codec.decode(nativeFamilyName()));}
    @Override public void copyPersistentFrom(Dc2Host other)throws IOException{
        PcHost p=(PcHost)other;nativeFrameCapture=p.nativeFrameCapture;nativeMessageTemplateCapture=p.nativeMessageTemplateCapture;
        if(pc1!=null){
            if(p.pc1==null)throw new IOException("PC1 persistent owner/profile mismatch");
            p.ensureNativeSettings();pc1Settings=Pc1NativeSettings.parse(p.pc1Settings.originalBytes());pc1SettingsInitialized=true;
            nativeDefaultsVerified=p.nativeDefaultsVerified;nativeSettingsIo=p.nativeSettingsIo;nativeSettingsPath=p.nativeSettingsPath;nativeSettingsAudio=p.nativeSettingsAudio;
            nativeSettingsSource=p.nativeSettingsSource;nativeSettingsRead=p.nativeSettingsRead;nativeSettingsWrite=p.nativeSettingsWrite;
            availableSaves.clear();availableSaves.addAll(p.availableSaves);saveLabels.clear();saveLabels.putAll(p.saveLabels);readCheckpoints.addAll(p.readCheckpoints);refreshNativeNameCache();return;
        }
        super.copyPersistentFrom(other);familyName=p.familyName;givenName=p.givenName;System.arraycopy(p.globals,0,globals,0,globals.length);System.arraycopy(p.cg,0,cg,0,cg.length);System.arraycopy(p.seen,0,seen,0,seen.length);System.arraycopy(p.voiceEnabled,0,voiceEnabled,0,voiceEnabled.length);if(original)originalVoiceEnabled=p.originalVoiceEnabled;if(second)for(int n=0;n<legacyPcTails.length;n++)legacyPcTails[n]=p.legacyPcTails[n].clone();
    }
    /** Old register defaults are not another authority for PC1 system.dat. */
    private void clearNativeSettingsRegisterMirrors(){for(int index:Pc1NativeSettings.indices99())registers.remove(index);for(int index:new int[]{0,2,19,22,243,247,252,253,254})registers.remove(index);}
    private void saveNativeSettingsImage(DataOutput out)throws IOException{
        ensureNativeSettings();byte[] bytes=pc1Settings.originalBytes();if(bytes.length>16*1024*1024)throw new IOException("PC1 portable native settings backing exceeds16MiB");out.writeInt(bytes.length);out.write(bytes);
    }
    private static Pc1NativeSettings readNativeSettingsImage(DataInput in)throws IOException{
        int size=in.readInt();if(size<Pc1NativeSettings.SIZE||size>16*1024*1024)throw new IOException("PC1 portable native settings backing length");byte[] bytes=new byte[size];in.readFully(bytes);return Pc1NativeSettings.parse(bytes);
    }
    private byte[][] pcBanks(){return new byte[][]{globals,cg,seen,voiceEnabled,scratch};}
    private void savePc(DataOutput o)throws IOException{
        if(pc1!=null){o.writeInt(0x50435034);saveNativeSettingsImage(o);o.writeInt(pc1BgmMode);o.writeInt(spriteFlags);o.writeInt(randomState);return;}
        o.writeInt(second?0x50435033:0x50435032);if(second){for(byte[] bytes:pcBanks()){o.writeInt(bytes.length);o.write(bytes);}for(byte[] bytes:legacyPcTails){o.writeInt(bytes.length);o.write(bytes);}}else{o.write(globals);o.write(cg);o.write(seen);o.write(voiceEnabled);o.write(scratch);}o.writeInt(spriteFlags);o.writeInt(randomState);o.writeUTF(familyName);o.writeUTF(givenName);
    }
    private void loadPc(DataInput i)throws IOException{
        int format=i.readInt();
        if(pc1!=null){if(format!=0x50435034)throw new IOException("PC1 pre-PCP4 state has no lossless native system.dat owner");Pc1NativeSettings next=readNativeSettingsImage(i);int mode=i.readInt(),sprites=i.readInt(),random=i.readInt();pc1Settings=next;pc1SettingsInitialized=true;pc1BgmMode=mode;spriteFlags=sprites;randomState=random;clearNativeSettingsRegisterMirrors();refreshNativeNameCache();return;}
        if(format!=0x50435031&&format!=0x50435032&&format!=0x50435033)throw new IOException("PC state format");
        if(format==0x50435033){if(!second)throw new IOException("PCP3 requires its reviewed PC2/SC/FL family");for(byte[] bytes:pcBanks()){int length=i.readInt();if(length!=bytes.length)throw new IOException("Native PC byte-bank layout mismatch");i.readFully(bytes);}for(int n=0;n<legacyPcTails.length;n++){int length=i.readInt();if(length<0||length>1600)throw new IOException("Legacy PC byte-bank tail limit");legacyPcTails[n]=new byte[length];i.readFully(legacyPcTails[n]);}}
        else{int[] oldLengths={100,1600,1600,128,100};byte[][] banks=pcBanks();for(int n=0;n<banks.length;n++){byte[] old=new byte[oldLengths[n]];i.readFully(old);Arrays.fill(banks[n],(byte)0);System.arraycopy(old,0,banks[n],0,Math.min(old.length,banks[n].length));legacyPcTails[n]=old.length>banks[n].length?Arrays.copyOfRange(old,banks[n].length,old.length):new byte[0];}}
        spriteFlags=i.readInt();randomState=i.readInt();if(format>=0x50435032){familyName=i.readUTF();givenName=i.readUTF();if(familyName.length()>4||givenName.length()>4)throw new IOException("Saved player name too long");}
    }
    @Override public void save(DataOutput o)throws IOException{super.save(o);o.writeInt(original?0x50434841:pc1!=null?0x5043483b:0x50434835);savePc(o);o.writeBoolean(timedInput);for(int n:gridBounds)o.writeInt(n);map.save(o);pcCredits.save(o);o.writeInt(voiceGroup.size());for(String voice:voiceGroup)o.writeUTF(voice);list.save(o);o.writeBoolean(gridTarget);o.writeUTF(musicEndAction);nameInput.save(o);if(original){o.writeInt(originalCompositePending);o.writeInt(originalVoiceEnabled);o.writeFloat(originalPlaybackGain);o.writeInt(originalWaitKind);o.writeInt(originalInputMode);o.writeBoolean(scene.nativeMotion!=null);if(scene.nativeMotion!=null)scene.nativeMotion.save(o);scene.nativeParticles.save(o);o.writeInt(originalDrawDirty);o.writeBoolean(originalResetBatch);UiCapture.save(o,originalBatchInitial);o.writeUTF(originalNextSpeaker);o.writeInt(originalSpeakerEnd);o.writeBoolean(originalDeferred!=null);if(originalDeferred!=null)originalDeferred.save(o);}if(original){dc2Map.save(o);dc2Borrowed.save(o);dc2Credits.save(o);UiCapture.save(o,dc2CreditsUnderlay);dc2CharacterEnding.save(o);UiCapture.save(o,dc2EndingUnderlay);dc2ActorMotion.save(o);dc2ActorChange.save(o);}if(pc1!=null)pc1.save(o);}
    @Override public void load(DataInput i)throws IOException{super.load(i);int format=i.readInt();if(format!=0x50434832&&format!=0x50434833&&format!=0x50434834&&format!=0x50434835&&format!=0x50434836&&format!=0x50434837&&format!=0x50434838&&format!=0x50434839&&format!=0x5043483a&&format!=0x5043483b&&format!=0x5043483c&&format!=0x5043483d&&format!=0x5043483e&&format!=0x5043483f&&format!=0x50434840&&format!=0x50434841)throw new IOException("PC host state version");if(original&&format!=0x50434837&&format!=0x5043483c&&format!=0x5043483d&&format!=0x5043483e&&format!=0x5043483f&&format!=0x50434840&&format!=0x50434841)throw new IOException("D.C.II pre-PCH7 snapshot has no native particle/batch state; restore requires a PCH7 snapshot");if((format==0x50434836||format==0x50434837||(format==0x5043483c||format==0x5043483d||(format==0x5043483e||(format==0x5043483f||(format==0x50434840||format==0x50434841)))))&&!original)throw new IOException("D.C.II motion state requires original profile");if(pc1!=null&&format!=0x5043483b)throw new IOException("PC1 pre-PCH11 snapshot has no canonical live scene coordinates");if((format==0x50434838||format==0x50434839||format==0x5043483a||format==0x5043483b)&&pc1==null)throw new IOException("PCH8/PCH9/PCH10/PCH11 requires PC1");loadPc(i);timedInput=i.readBoolean();for(int n=0;n<8;n++)gridBounds[n]=i.readInt();map.load(i);voiceGroup.clear();if(format>=0x50434833){pcCredits.load(i);int count=i.readInt();if(count<0||count>10)throw new IOException("PC voice group count");while(count-->0)voiceGroup.add(i.readUTF());}if(format>=0x50434834){list.load(i);gridTarget=i.readBoolean();musicEndAction=i.readUTF();}if(format>=0x50434835){nameInput.load(i);if(nameInput.active)nameInput.prepare(resources,codec);}originalCompositePending=0;scene.nativeMotion=null;motionSkipPressed=false;originalWaitKind=-1;originalInputMode=0;originalVoiceEnabled=1;originalPlaybackGain=Dc2Voice.gain(0,registers.getOrDefault(9,29));if(format==0x50434836||format==0x50434837||(format==0x5043483c||format==0x5043483d||(format==0x5043483e||(format==0x5043483f||(format==0x50434840||format==0x50434841))))){originalCompositePending=i.readInt();originalVoiceEnabled=i.readInt();originalPlaybackGain=i.readFloat();originalWaitKind=i.readInt();originalInputMode=i.readInt();if(originalWaitKind!= -1&&originalWaitKind!=9&&originalWaitKind!=10&&originalWaitKind!=11&&originalWaitKind!=18)throw new IOException("D.C.II saved input wait");if(Float.isNaN(originalPlaybackGain)||originalPlaybackGain<0||originalPlaybackGain>1)throw new IOException("D.C.II saved voice gain");if(originalCompositePending< -1||originalCompositePending>2)throw new IOException("D.C.II pending composition state");if(i.readBoolean())scene.nativeMotion=Dc2Motion.load(i);}if(format==0x50434837||(format==0x5043483c||format==0x5043483d||(format==0x5043483e||(format==0x5043483f||(format==0x50434840||format==0x50434841))))){scene.nativeParticles=Dc2Particles.load(i,this::nextRandom);originalDrawDirty=i.readInt();originalResetBatch=i.readBoolean();originalBatchInitial=UiCapture.load(i);originalNextSpeaker=i.readUTF();originalSpeakerEnd=i.readInt();originalDeferred=i.readBoolean()?DeferredScene.load(i):null;if(originalDrawDirty< -1||originalDrawDirty>1||originalDrawDirty==1&&originalBatchInitial==null||originalDrawDirty!=1&&originalBatchInitial!=null||originalDeferred!=null&&(scene.nativeMotion==null||!scene.nativeMotion.prepareOnly))throw new IOException("D.C.II saved batch/preparation state");}
        if(original){if(format==0x5043483c||format==0x5043483d||(format==0x5043483e||(format==0x5043483f||(format==0x50434840||format==0x50434841))))dc2Map.load(i);else dc2Map.reset();if(format==0x5043483d||(format==0x5043483e||(format==0x5043483f||(format==0x50434840||format==0x50434841))))dc2Borrowed.load(i);else dc2Borrowed.reset();if((format==0x5043483e||(format==0x5043483f||(format==0x50434840||format==0x50434841)))){dc2Credits.load(i);dc2CreditsUnderlay=UiCapture.load(i);if(dc2Credits.active&&dc2CreditsUnderlay==null)throw new IOException("DC2 credits front is absent");}else{dc2Credits.clear();dc2CreditsUnderlay=null;}if((format==0x5043483f||(format==0x50434840||format==0x50434841))){dc2CharacterEnding.load(i);dc2EndingUnderlay=UiCapture.load(i);if(dc2CharacterEnding.prepared&&dc2EndingUnderlay==null)throw new IOException("DC2 character ending front is absent");}else{dc2CharacterEnding.clear();dc2EndingUnderlay=null;}if((format==0x50434840||format==0x50434841))dc2ActorMotion.load(i);else dc2ActorMotion.clear();if(format==0x50434841)dc2ActorChange.load(i);else dc2ActorChange.clear();}if(pc1!=null)pc1.load(i);
        // Older phone snapshots contain the unconverted native text state in both the active menu and START templates.
        normalizeButtonText(ui.buttons);for(List<Dc2Ui.Button> template:ui.buttonTemplates)normalizeButtonText(template);
        // Before native defaults were initialized, all thirteen entries were white. Preserve any nondefault saved palette.
        boolean missing=true;for(int n=0;n<PC_PALETTE.length;n++)missing&=ui.palette[n]==0xffffffff;if(missing)System.arraycopy(PC_PALETTE,0,ui.palette,0,PC_PALETTE.length);
    }
    @Override public void savePersistent(DataOutput o)throws IOException{
        if(pc1==null){super.savePersistent(o);savePc(o);return;}
        //Phone account envelope, not native4027E0 output or a story snapshot.
        o.writeInt(0x50315331);saveNativeSettingsImage(o);o.writeInt(readCheckpoints.size());for(String key:readCheckpoints)o.writeUTF(key);
    }
    @Override public void loadPersistent(DataInput i)throws IOException{
        if(pc1==null){super.loadPersistent(i);loadPc(i);return;}
        if(i.readInt()!=0x50315331)throw new IOException("PC1 old phone progress has no lossless native system.dat owner; original bytes retained");
        Pc1NativeSettings next=readNativeSettingsImage(i);int count=i.readInt();if(count<0||count>100000)throw new IOException("PC1 portable read checkpoint count");Set<String> reads=new TreeSet<>();while(count-->0)reads.add(i.readUTF());
        pc1Settings=next;pc1SettingsInitialized=true;readCheckpoints.clear();readCheckpoints.addAll(reads);refreshNativeNameCache();
    }
}
