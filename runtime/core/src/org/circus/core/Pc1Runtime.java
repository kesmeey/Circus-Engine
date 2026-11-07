package org.circus.core;

import java.io.*;
import java.util.*;

/** PC1 native frame owners. System return, save candidate and live audio differ. */
public final class Pc1Runtime {
    private static final int FORMAT=0x50315259; //P1RY: MM neighborhood and explicit caller locals
    private final PcHost host;
    private final Pc1NativeFrame nativeFrame;
    private final Pc1NativeFrame workingFrame=Pc1NativeFrame.zeroBss(),systemFrame=Pc1NativeFrame.zeroBss();
    private final Pc1CodeBuffer codeBuffer=Pc1CodeBuffer.zeroBss();
    private final Pc1NativeMemory nativeMemory;
    private final Pc1DrawMemory drawMemory;
    private final Pc1GraphicsMemory graphicsMemory;
    private final Pc1VmGlobals vmGlobals;
    private final Pc1Effects.Memory effectsMemory;
    private final Pc1PresentationMemory presentationMemory;
    private final Pc1UiPage.Memory uiPageMemory;
    private final Pc1EventMemory eventMemory;
    private final Pc1CrxMemory crxMemory;
    private final Pc1CheckpointMemory checkpointMemory;
    private final Pc1EffectPeMemory effectPeMemory;
    private final Pc1CatalogMemory catalogMemory;
    private final Pc1StartupMemory startupMemory;
    private final Pc1Timer5.Memory timer5Memory;
    private final Pc1InputMemory inputMemory;
    private final Pc1WaitDispatcher.Memory waitMemory;
    private final Pc1ScriptMemory scriptMemory;
    private final Pc1SystemMemory systemMemory;
    private final Pc1WindowMemory windowMemory;
    private Pc1Variables nativeVariables;
    private Pc1TextExpander.Live nativeTextExpansion;
    private Pc1SettingsIo nativeSettingsIo;
    private Pc1RawDispatch nativeDispatch;
    private Pc1Fetch.Frame nativeDispatchFrame;
    private final Pc1NativeUi nativeUi;
    private final Pc1LayoutMemory layoutMemory;
    private final Pc1UiProducer uiProducer;
    private final Pc1PathGlobals pathGlobals;
    private final Pc1ResourceLiterals resourceLiterals;
    final Pc1TextLog textLog;
    private Pc1MessageGeometry geometry=new Pc1MessageGeometry();
    private Pc1TextBuffers textBuffers=new Pc1TextBuffers();
    private boolean initialScriptBound,dispatcherReentry=true,nativeDrawUsed,dispatching;
    private int pendingCondition,rawStringPointer;
    private int previousOpcode=-1,currentOpcode=-1,lastYieldOpcode=-1;
    private int fetchedAt=-1;
    private Pc1TextLayout.Aliases layoutAliases;
    private Pc1TextLayout canonicalLayout;
    private Pc1RenderContext nativeRenderContext;
    public interface NativeBoundary {void requireBoundary()throws IOException;}
    private final List<NativeBoundary> nativeBoundaries=new ArrayList<>();
    private Integer activeDictionaryCount;
    private final List<int[]> glyphBatches=new ArrayList<>(),textPatches=new ArrayList<>();
    private long redrawGeneration;
    private boolean layoutIsHistory;
    /** 9CFC70 story table, BB8450 prefix and9B8E1C full count are globals,
     *not1F44 or a saved menu/caller copy. Inactive suffix records survive. */
    private final List<Dc2Ui.Button> storyButtons=new ArrayList<>();
    private int storyPrefixCount,storyFullCount;
    private Pc1SystemContext system;
    private Pc1ReturnSurface returnSurface;
    private long returnPaintSerial=-1;
    private final Pc1SaveContext saves=new Pc1SaveContext();
    private Pc1Frame requestedSave;
    private int requestedSlot=-1;
    /** Portable leaves belong to Dc2Session, outside this native wire. These
     * transient notifications identify real producers, never metadata rebinding. */
    interface PortableCapture {void captured(int kind)throws IOException;}
    private PortableCapture portableCapture;
    private int pendingPortableCapture;
    void portableCapture(PortableCapture capture){portableCapture=capture;pendingPortableCapture=0;}
    void capturePortableBoundary()throws IOException{if(pendingPortableCapture!=0){int kind=pendingPortableCapture;if(portableCapture!=null)portableCapture.captured(kind);pendingPortableCapture=0;}}
    Pc1Runtime(final PcHost host){this(host,false);}
    Pc1Runtime(final PcHost host,boolean beforeWinMain){this.host=host;nativeFrame=beforeWinMain?Pc1NativeFrame.zeroBss():initialNativeFrame();textLog=Pc1TextLog.backed(nativeFrame);host.scene.bindCoordinates(new Dc2Scene.LiveCoordinates(){
        public int background(int axis){return nativeFrame.dword(axis==0?Pc1NativeSlot.Dword.BACKGROUND_X:Pc1NativeSlot.Dword.BACKGROUND_Y);}
        public void background(int axis,int value){nativeFrame.dword(axis==0?Pc1NativeSlot.Dword.BACKGROUND_X:Pc1NativeSlot.Dword.BACKGROUND_Y,value);}
        public int actor(int actor,int axis){return nativeFrame.dword(actor==0?(axis==0?Pc1NativeSlot.Dword.ACTOR1_X:Pc1NativeSlot.Dword.ACTOR1_Y):(axis==0?Pc1NativeSlot.Dword.ACTOR2_X:Pc1NativeSlot.Dword.ACTOR2_Y));}
        public void actor(int actor,int axis,int value){nativeFrame.dword(actor==0?(axis==0?Pc1NativeSlot.Dword.ACTOR1_X:Pc1NativeSlot.Dword.ACTOR1_Y):(axis==0?Pc1NativeSlot.Dword.ACTOR2_X:Pc1NativeSlot.Dword.ACTOR2_Y),value);}
    });nativeMemory=new Pc1NativeMemory(nativeFrame,codeBuffer,new Pc1NativeMemory.SettingsBytes(){
        public int read(int offset)throws IOException{return host.nativeSettingsByte(offset);}
        public void write(int offset,int value)throws IOException{host.nativeSettingsByte(offset,value);}
    });try{drawMemory=new Pc1DrawMemory(nativeMemory);for(final int address:new int[]{Pc1DrawProducer.ORIGIN_X,Pc1DrawProducer.ORIGIN_Y,Pc1DrawProducer.CONDITION,Pc1DrawProducer.LIST_OFFSET})drawMemory.bind(address,new Pc1DrawMemory.Dword(){public int read()throws IOException{return runtimeDword(address);}public void write(int value)throws IOException{runtimeDword(address,value);}});
        for(int n=0;n<13;n++){final int address=Pc1MessageGeometry.BODY_X+n*4;drawMemory.bind(address,new Pc1DrawMemory.Dword(){public int read(){return geometry.field(address);}public void write(int value){geometry.field(address,value);}});}
        drawMemory.bind(0x43e100,new Pc1DrawMemory.Dword(){public int read(){return host.nativeCrtState();}public void write(int value){host.nativeCrtState(value);}});
        drawMemory.bind(0xbb83fc,new Pc1DrawMemory.Dword(){public int read(){return rawStringPointer;}public void write(int value){rawStringPointer=value;}});
        drawMemory.bind(0x43d414,new Pc1DrawMemory.Dword(){public int read(){return lastYieldOpcode;}public void write(int value){lastYieldOpcode=value;}});
        nativeUi=new Pc1NativeUi(nativeMemory,new Pc1NativeUi.Counters(){
            public Pc1DrawMemory.Dword storyPrefixCount(){return new Pc1DrawMemory.Dword(){public int read(){return storyPrefixCount;}public void write(int value){storyPrefixCount=value;}};}
            public Pc1DrawMemory.Dword storyFullCount(){return new Pc1DrawMemory.Dword(){public int read(){return storyFullCount;}public void write(int value){storyFullCount=value;}};}
        });uiProducer=new Pc1UiProducer(nativeUi);pathGlobals=new Pc1PathGlobals(nativeMemory);resourceLiterals=new Pc1ResourceLiterals(nativeMemory);graphicsMemory=new Pc1GraphicsMemory(nativeMemory);vmGlobals=new Pc1VmGlobals(nativeMemory);effectsMemory=new Pc1Effects.Memory(nativeMemory);presentationMemory=new Pc1PresentationMemory(nativeMemory);uiPageMemory=new Pc1UiPage.Memory(nativeMemory);eventMemory=new Pc1EventMemory(nativeMemory);crxMemory=new Pc1CrxMemory(nativeMemory);checkpointMemory=new Pc1CheckpointMemory(nativeMemory);effectPeMemory=new Pc1EffectPeMemory(nativeMemory);catalogMemory=new Pc1CatalogMemory(nativeMemory);startupMemory=new Pc1StartupMemory(nativeMemory);timer5Memory=new Pc1Timer5.Memory(nativeMemory);inputMemory=new Pc1InputMemory(nativeMemory);waitMemory=new Pc1WaitDispatcher.Memory(nativeMemory);scriptMemory=new Pc1ScriptMemory(nativeMemory);systemMemory=new Pc1SystemMemory(nativeMemory);windowMemory=new Pc1WindowMemory(nativeMemory);saves.bindNativeMemory(nativeMemory);
        bindFrame(Pc1FrameLayout.WORKING_BASE,workingFrame);bindFrame(Pc1FrameLayout.SYSTEM_BASE,systemFrame);
        bindTextBuffer(0x9d3af8,Pc1TextBuffers.LIVE_BODY);bindTextBuffer(0x9ccaa0,Pc1TextBuffers.LIVE_SPEAKER);layoutMemory=new Pc1LayoutMemory(nativeMemory);
    }catch(IOException impossible){throw new AssertionError("PC1 fixed draw memory bindings",impossible);}}
    private int runtimeDword(int address)throws IOException{switch(address){case Pc1DrawProducer.ORIGIN_X:return host.ui.x;case Pc1DrawProducer.ORIGIN_Y:return host.ui.y;case Pc1DrawProducer.CONDITION:return pendingCondition;case Pc1DrawProducer.LIST_OFFSET:return host.listOffset();default:throw new IOException("Unknown PC1 bound draw scalar");}}
    private void runtimeDword(int address,int value)throws IOException{switch(address){case Pc1DrawProducer.ORIGIN_X:host.ui.x=value;break;case Pc1DrawProducer.ORIGIN_Y:host.ui.y=value;break;case Pc1DrawProducer.CONDITION:pendingCondition=value;break;case Pc1DrawProducer.LIST_OFFSET:host.listOffset(value);break;default:throw new IOException("Unknown PC1 bound draw scalar");}}
    private static Pc1NativeFrame initialNativeFrame(){try{return Pc1NativeFrame.initializedDefault();}catch(IOException impossible){throw new AssertionError(impossible);}}
    public Pc1NativeFrame nativeFrame(){return nativeFrame;}
    public Pc1NativeFrame workingFrame(){return workingFrame;}
    public Pc1NativeFrame systemFrame(){return systemFrame;}
    /**411480 is an explicit WinMain leaf, after the five CreateFont calls.
     *It neither clears working/candidate nor rebuilds external UI/history. */
    public void initializeFrame411480()throws IOException{nativeFrame.copyFrom(Pc1NativeFrame.initializedDefault());systemFrame.copyFrom(nativeFrame);}
    public Pc1SlotLoader.Owners nativeSlotOwners()throws IOException{return new Pc1SlotLoader.Owners(nativeFrame,workingFrame,systemFrame,saves.nativeCandidate());}
    private void bindFrame(int address,final Pc1NativeFrame owner)throws IOException{nativeMemory.bind(address,Pc1FrameLayout.FRAME_BYTES,new Pc1NativeMemory.Region(){public int read(int offset)throws IOException{return owner.readU8(offset);}public void write(int offset,int value)throws IOException{owner.writeU8(offset,value);}});}
    public Pc1CodeBuffer codeBuffer(){return codeBuffer;}
    public Pc1NativeMemory nativeMemory(){return nativeMemory;}
    public Pc1DrawMemory drawMemory(){return drawMemory;}
    public Pc1GraphicsMemory graphicsMemory(){return graphicsMemory;}
    public Pc1Effects.Memory effectsMemory(){return effectsMemory;}
    public Pc1CrxMemory crxMemory(){return crxMemory;}
    public Pc1CheckpointMemory checkpointMemory(){return checkpointMemory;}
    public Pc1EffectPeMemory effectPeMemory(){return effectPeMemory;}
    void bindNativeRenderContext(Pc1RenderContext value)throws IOException{if(value==null||nativeRenderContext!=null)throw new IOException("PC1 Runtime requires unique native render context");nativeRenderContext=value;}
    public void bindNativeBoundary(NativeBoundary boundary)throws IOException{if(boundary==null||nativeBoundaries.contains(boundary))throw new IOException("PC1 native boundary requires unique owner");nativeBoundaries.add(boundary);}
    private void requireNativeBoundary()throws IOException{scriptMemory.requireBoundary();systemMemory.requireBoundary();if(nativeRenderContext!=null)nativeRenderContext.requireBoundary();for(NativeBoundary boundary:nativeBoundaries)boundary.requireBoundary();}
    public Pc1CatalogMemory catalogMemory(){return catalogMemory;}
    public Pc1StartupMemory startupMemory(){return startupMemory;}
    public Pc1Timer5.Memory timer5Memory(){return timer5Memory;}
    public Pc1InputMemory inputMemory(){return inputMemory;}
    public Pc1WaitDispatcher.Memory waitMemory(){return waitMemory;}
    public Pc1ScriptMemory scriptMemory(){return scriptMemory;}
    public Pc1SystemMemory systemMemory(){return systemMemory;}
    public Pc1WindowMemory windowMemory(){return windowMemory;}
    public Pc1MmPhysicalMemory mmPhysicalMemory()throws IOException{return nativeMemory.mmPhysicalMemory();}
    public Pc1Variables nativeVariables(){return nativeVariables;}
    public Pc1TextExpander.Live nativeTextExpansion(){return nativeTextExpansion;}
    public void bindNativeTextExpansion(Pc1TextExpander.Live expansion)throws IOException{if(expansion==null||nativeTextExpansion!=null)throw new IOException("PC1 text expansion requires one canonical native consumer");expansion.requireBus(nativeMemory);expansion.requireBoundary();nativeTextExpansion=expansion;bindNativeBoundary(expansion::requireBoundary);}
    public void bindNativeVariables(Pc1Variables variables)throws IOException{if(variables==null||nativeVariables!=null)throw new IOException("PC1 variables require one canonical native consumer");variables.requireBus(nativeMemory);nativeVariables=variables;host.nativeSettingsRamOnly();bindNativeBoundary(variables::requireBoundary);}
    public Pc1SettingsIo nativeSettingsIo(){return nativeSettingsIo;}
    public Pc1RawDispatch nativeDispatch(){return nativeDispatch;}
    public Pc1Fetch.Frame nativeDispatchFrame(){return nativeDispatchFrame;}
    /**The caller supplies its actual known local-frame state. No numeric ESP
     *or unproved reuse of a previous invocation's native stack is inferred. */
    public void bindNativeDispatch(Pc1RawDispatch dispatcher,Pc1Fetch.Frame callerLocals)throws IOException{
        if(dispatcher==null||callerLocals==null||nativeDispatch!=null)throw new IOException("PC1 raw dispatcher requires one explicit caller-local owner");dispatcher.requireBus(nativeMemory);dispatcher.requireBoundary();requireDispatchFrameBoundary(callerLocals);nativeDispatch=dispatcher;nativeDispatchFrame=callerLocals;bindNativeBoundary(()->{dispatcher.requireBoundary();requireDispatchFrameBoundary(nativeDispatchFrame);});
    }
    private static void requireDispatchFrameBoundary(Pc1Fetch.Frame frame)throws IOException{if(frame!=null&&frame.active()&&!frame.atNextBoundary())throw new IOException("PC1 raw caller locals are inside an unresolved native continuation");}
    public void bindNativeSettingsIo(Pc1SettingsIo settings)throws IOException{if(settings==null||nativeSettingsIo!=null)throw new IOException("PC1 settings IO requires one canonical native consumer");settings.requireBus(nativeMemory);nativeSettingsIo=settings;host.nativeSettingsRamOnly();bindNativeBoundary(settings::requireBoundary);}
    public int prepareNativeScenarioLabel(byte[] target)throws IOException{return new Pc1ScenarioCatalog(catalogMemory).prepare(target);}
    public Pc1TextExpander.Context nativeCatalogTextContext()throws IOException{return catalogMemory.textContext(new Pc1CatalogMemory.LiveText(){public byte[] givenName()throws IOException{return host.nativeGivenName();}public byte[] familyName()throws IOException{return host.nativeFamilyName();}public int read(int bank,int index)throws IOException{return nativeVariable(bank,index);}}); }
    public long nextTextSourceEpoch()throws IOException{if(redrawGeneration==Long.MAX_VALUE)throw new IOException("PC1 symbolic text-source epoch exhausted");return ++redrawGeneration;}
    public Pc1TextExpander.Context textContext(){return textContext;}
    public int nativeCrtRandom()throws IOException{return host.nativeCrtRandom();}
    public int nativeVariable(int bank,int index)throws IOException{if(nativeVariables!=null)return nativeVariables.read(bank,index);if(host.variables==null)throw new IOException("PC1 live variable owner missing");return host.variables.read(bank,index);}
    public void nativeVariable(int bank,int index,int value)throws IOException{if(nativeVariables!=null){nativeVariables.write(bank,index,value);return;}if(host.variables==null)throw new IOException("PC1 live variable owner missing");host.variables.write(bank,index,value);}
    public Pc1NativeUi nativeUi(){return nativeUi;}
    public Pc1LayoutMemory layoutMemory(){return layoutMemory;}
    public Pc1UiProducer uiProducer(){return uiProducer;}
    public Pc1PathGlobals pathGlobals(){return pathGlobals;}
    private void bindTextBuffer(int address,final String owner)throws IOException{nativeMemory.bind(address,Pc1TextBuffers.capacity(owner),new Pc1NativeMemory.Region(){public int read(int at)throws IOException{return textBuffers.unsignedByte(owner,at);}public void write(int at,int value)throws IOException{textBuffers.writeKnownByte(owner,at,value);}});}
    void beforeRecordFetch(int opcode,int codeOffset){if(opcode>=0x2d&&opcode<0x4e)rawStringPointer=Pc1NativeMemory.CODE_BASE+codeOffset+1;}
    int rawStringPointer(){return rawStringPointer;}
    void expandUi(int source,int destination)throws IOException{
        if(nativeTextExpansion!=null)nativeTextExpansion.expand(offset->nativeMemory.readAddress(source+offset),destination);else expandUi(source,destination,textContext);
    }
    void expandNativeUi(int source,int destination)throws IOException{if(nativeTextExpansion!=null)nativeTextExpansion.expand(offset->nativeMemory.readAddress(source+offset),destination);else expandUi(source,destination,nativeCatalogTextContext());}
    private void expandUi(int source,int destination,Pc1TextExpander.Context context)throws IOException{
        byte[] expanded=Pc1TextExpander.expand(nativeUi.cstring(source),context,1024*1024);
        for(int n=0;n<expanded.length;n++)nativeMemory.writeAddress(destination+n,expanded[n]&255);
        nativeMemory.writeAddress(destination+expanded.length,0);nativeMemory.writeAddress(destination+expanded.length+1,0);
    }
    boolean dispatching(){return dispatching;}
    void instructionFinished(){dispatching=false;}
    boolean nativeDrawUsed(){return nativeDrawUsed;}
    private final Pc1DrawProducer.Code producerCode=this::producerByte;
    private int producerByte(int offset)throws IOException{return nativeMemory.readAddress(Pc1NativeMemory.CODE_BASE+offset);}
    void produceQuad53(int arg2,int arg3)throws IOException{nativeDrawUsed=true;Pc1DrawProducer.quad53(nativeFrame,drawMemory,producerCode,arg2,arg3);}
    void produceText5c(int pitch,int style)throws IOException{nativeDrawUsed=true;Pc1DrawProducer.text5c(nativeFrame,drawMemory,producerCode,pitch,style);}
    void produceOrigin5d(int mode)throws IOException{Pc1DrawProducer.origin5d(nativeFrame,drawMemory,mode);}
    void produceTemplate22()throws IOException{
        nativeDrawUsed=true;Pc1DrawProducer.template22(nativeFrame,drawMemory,new Pc1DrawProducer.Services(){
            public void geometryMode2(int selector)throws IOException{if(selector==1)headY(geometry.prepareBody(drawMemory.template(1),cachedScenarioRead(),host.read(99,18),headY()));else geometry.prepareName(drawMemory.template(2),cachedScenarioRead());}
            public void prepareUi(int a,int b)throws IOException{throw new IOException("PC1 template producer cannot substitute the4120C0 UI consumer");}
        });
    }
    public int cachedScenarioRead(){return nativeFrame.dword(Pc1NativeSlot.Dword.CACHED_SCENARIO_READ);}
    void cachedScenarioRead(int value){nativeFrame.dword(Pc1NativeSlot.Dword.CACHED_SCENARIO_READ,value);}
    int headY(){return nativeFrame.dword(Pc1NativeSlot.Dword.HEAD_Y);}
    void headY(int value){nativeFrame.dword(Pc1NativeSlot.Dword.HEAD_Y,value);}
    public int waitKind(){return nativeFrame.dword(Pc1NativeSlot.Dword.WAIT_KIND);}
    void waitKind(int value){nativeFrame.dword(Pc1NativeSlot.Dword.WAIT_KIND,value);}
    int argument0(){return nativeFrame.dword(Pc1NativeSlot.Dword.ARGUMENT0);}
    void argument0(int value){nativeFrame.dword(Pc1NativeSlot.Dword.ARGUMENT0,value);}
    int argument1(){return nativeFrame.dword(Pc1NativeSlot.Dword.ARGUMENT1);}
    void argument1(int value){nativeFrame.dword(Pc1NativeSlot.Dword.ARGUMENT1,value);}

    public boolean inSystem(){return system!=null;}
    public Pc1SystemContext system(){return system;}
    public boolean returning(){return returnSurface!=null&&!returnSurface.committed();}
    public int returnProgress(){return returnSurface==null?0:returnSurface.progress();}
    private Pc1ReturnSurface displayedReturn(){return returnSurface==null||returnSurface.committed()&&(host.scene.transitionSerial&0xffffffffL)!=returnPaintSerial?null:returnSurface;}
    public UiCapture returnFront()throws IOException{Pc1ReturnSurface surface=displayedReturn();return surface==null?null:surface.image();}
    public UiCapture.Pixels returnPixels(){Pc1ReturnSurface surface=displayedReturn();return surface==null?null:surface.frontPixels();}
    private void requireRetainableFront()throws IOException{
        if(host.scene.scrollStep>0||host.scene.particleKind!=0||host.scene.overlayKind!=0||host.scene.nativeMotion!=null||host.scene.transitionRemaining>0||host.pcCredits.active||host.credits.active())throw new IOException("PC1 wait-1 return has an unimplemented partial-paint/timer framebuffer owner");
    }
    /** A retained bitmap cannot silently absorb an unreviewed partial draw or
     *a Java publish which is only conditional in the native handler. */
    void beforeCommand(String operation,int[] args)throws IOException{
        Pc1ReturnSurface front=displayedReturn();if(front==null||!front.committed())return;
        if(operation.equals("pc.command")&&args.length>0&&args[0]==7)return; // Proven404EE0 default return.
        switch(operation){
        case "text.body":case "dm.ui.commit":case "pc.ui.wait":case "pc.choice.wait":case "pc.grid.wait":case "dm.ui.grid_wait":case "pc.confirm":
        case "pc.background":case "dm.scene.background":
        case "pc.character":case "pc.character_overlay":case "pc.character_pair":case "pc.portrait_pair":
        case "dm.scene.character":case "dm.scene.character_overlay":case "dm.scene.portrait":
        case "pc.wait":case "pc.scroll":case "dm.scene.scroll":case "pc.effect":case "dm.scene.effect":
        case "pc.transition":case "dm.scene.transition":case "pc.command":
            throw new IOException("PC1 retained return framebuffer: unimplemented partial-paint producer "+operation);
        default:break;
        }
    }
    public Pc1SaveContext saves(){return saves;}
    public int packedSoundRequest(){return nativeFrame.dword(Pc1NativeSlot.Dword.PACKED_SOUND);}
    public Pc1Frame requestedSave(){return requestedSave;}
    public int requestedSlot(){return requestedSlot;}
    public Pc1TextLog textLog(){return textLog;}
    public Pc1MessageGeometry geometry(){return geometry;}
    public Pc1TextBuffers textBuffers(){return textBuffers;}
    public int historySelection(){return presentationMemory.selectedHistory();}
    void historySelection(int value){presentationMemory.selectedHistory(value);}
    public boolean historyActive(){return historySelection()>=0;}
    public int nativeArgument0(){return argument0();}
    public int nativeArgument1(){return argument1();}
    public int previousOpcode(){return previousOpcode;}
    public int lastYieldOpcode(){return lastYieldOpcode;}
    public Pc1TextLayout.Snapshot textLayout(){if(layoutAliases==null&&canonicalLayout==null)return null;try{return builder().freezeChecked();}catch(IOException e){throw new UncheckedIOException(e);}}
    public boolean layoutIsHistory(){return layoutIsHistory;}
    public List<int[]> glyphBatches(){List<int[]> result=new ArrayList<>();for(int[] a:glyphBatches)result.add(a.clone());return result;}
    public List<int[]> textPatches(){List<int[]> result=new ArrayList<>();for(int[] a:textPatches)result.add(a.clone());return result;}
    public List<Dc2Ui.Button> storyPrefix(){return Collections.unmodifiableList(storyButtons.subList(0,storyPrefixCount));}
    void storeStoryPrefix(List<Dc2Ui.Button> prefix)throws IOException{List<Dc2Ui.Button> copy=Dc2Ui.copyButtons(prefix);for(int n=0;n<copy.size();n++)storyButton(n,copy.get(n));storyPrefixCount=copy.size();}
    void projectStoryPrefix(List<Dc2Ui.Button> prefix)throws IOException{List<Dc2Ui.Button> copy=Dc2Ui.copyButtons(prefix);for(int n=0;n<copy.size();n++)storyButton(n,copy.get(n));}
    private void storyButton(int index,Dc2Ui.Button button)throws IOException{if(index<0||index>storyButtons.size()||index>=50)throw new IOException("PC1 story button table exceeds the reviewed portable owner");if(index==storyButtons.size())storyButtons.add(button);else storyButtons.set(index,button);}
    void beginChoice(int mode)throws IOException{
        if(mode!=1)throw new IOException("PC1 native choice mode"+mode+" requires the unimplemented40B900 dynamic caption producer");
        if(host.ui.buttonTemplates.get(3).isEmpty())throw new IOException("PC1 choice panel template missing");
        host.choiceMode=mode;host.choiceCount=0;host.ui.buttons.clear();host.ui.buttons.addAll(Dc2Ui.copyButtons(storyPrefix()));
        Dc2Ui.Button panel=Dc2Ui.copyButtons(Collections.singletonList(host.ui.buttonTemplates.get(3).get(0))).get(0);storyButton(storyPrefixCount,panel);host.ui.buttons.add(panel);
    }
    void appendedChoice()throws IOException{int index=host.ui.buttons.size()-1;storyButton(index,Dc2Ui.copyButtons(Collections.singletonList(host.ui.buttons.get(index))).get(0));}
    void choiceWait(){storyFullCount=host.ui.buttons.size();}
    private void restoreStoryButtons()throws IOException{int count=waitKind()==9&&!historyActive()?storyFullCount:storyPrefixCount;if(count>storyButtons.size())throw new IOException("PC1 current story button backing is unavailable");host.ui.buttons.clear();host.ui.buttons.addAll(Dc2Ui.copyButtons(storyButtons.subList(0,count)));if(waitKind()==9)host.ui.waiting=true;}
    public List<Pc1TextLayout.Region> dictionaryRegions()throws IOException{return activeDictionaryCount==null||historyActive()||waitKind()!=7?Collections.<Pc1TextLayout.Region>emptyList():builder().regions(activeDictionaryCount);}
    boolean fetchedOpcode(int opcode){return currentOpcode==opcode&&fetchedAt==host.pc;}
    public void soundRequest(int index,int mode){nativeFrame.dword(Pc1NativeSlot.Dword.PACKED_SOUND,(index&255)==255?0:(index&255)|((mode&255)<<8));}
    public void scriptLoaded(int mode)throws IOException{scriptLoaded(mode,null);}
    public void scriptLoaded(int mode,String target)throws IOException{
        if(mode<0||mode>3)throw new IOException("PC1 native loader mode");
        int read=cachedScenarioRead();
        if(target!=null){
            read=prepareScenarioLabel(target);
            nativeFrame.writeCString(Pc1FrameLayout.SCRIPT,target.getBytes(Binary.SJIS));
        }else if(mode==1||mode==3)throw new IOException("PC1 cached read loader requires its target name");
        if(mode!=0){
            nativeFrame.dword(Pc1NativeSlot.Dword.RAW_CODE_PC,2);
            textLog.clearLog();headY(-1);
            nativeFrame.dword(Pc1NativeSlot.Dword.CURRENT_CHECKPOINT,0);waitKind(-1);
            if(mode==1||mode==3){
                cachedScenarioRead(read);
                nativeFrame.writeBytes(Pc1FrameLayout.ACTION0,new byte[Pc1FrameLayout.ACTION_CELL_BYTES]);
                nativeFrame.writeBytes(Pc1FrameLayout.ACTION1,new byte[Pc1FrameLayout.ACTION_CELL_BYTES]);
                host.ui.actions[0]=host.ui.actions[1]="";pendingCondition=0;
            }
        }
        saves.scriptLoaded(mode);
        lastYieldOpcode=-1; //411670 resets43D414 after every successful native load.
    }
    /**41AEA0 is called before native open. A failed raw36 load still changes
     *this label, while SCRIPT and cached read remain owned by the old caller. */
    int prepareScenarioLabel(String target)throws IOException{
        nativeFrame.writeU8(Pc1FrameLayout.SCENARIO_LABEL,0);int index=scenarioIndex(target);
        byte[] label=index<0?new byte[0]:catalogLabel("scenario_scripts",index);
        int seen=index<0?0:host.read(98,index);if(index>=0)nativeFrame.writeCString(Pc1FrameLayout.SCENARIO_LABEL,label);
        host.checkpointLabel=host.codec.decode(label);return seen;
    }
    /**41AE20's caller-only implicit writes, before its raw36 target load. */
    void markCurrentScenario()throws IOException{
        int index=scenarioIndex(nativeFrame.rawCString(Pc1FrameLayout.SCRIPT));
        if(index>=0){host.write(98,index,1);nativeFrame.writeBank90(index,1);}
    }
    void bindInitial(MesScript caller)throws IOException{
        if(initialScriptBound)return;scriptLoaded(1,caller.name);initialScriptBound=true;
    }
    void beginDispatcher(){if(dispatcherReentry){previousOpcode=currentOpcode=-1;waitKind(-1);dispatcherReentry=false;}}
    void fetched(MesScript.Instruction instruction){
        dispatching=true;
        previousOpcode=currentOpcode;currentOpcode=instruction.record.opcode;fetchedAt=instruction.record.offset;
        argument0(0);argument1(0);
        if(instruction.record.args.length!=0){argument0(instruction.record.args[0]);if(instruction.record.args.length>1)argument1(instruction.record.args[1]);}
        if(currentOpcode>=0x4a&&currentOpcode<0x4e)argument1(instruction.record.bytes.length);
    }
    void dispatched(MesScript.Instruction instruction){dispatching=false;if(returning())return;if(returnSurface!=null&&returnSurface.committed()&&(host.scene.transitionSerial&0xffffffffL)!=returnPaintSerial){returnSurface=null;returnPaintSerial=-1;}if(host.waiting()){lastYieldOpcode=instruction.record.opcode;dispatcherReentry=true;}}
    public void conditionalNext(){pendingCondition=1;}
    public int takeCondition(){int value=pendingCondition;pendingCondition=0;return value;}
    public boolean visible(Dc2Ui.Quad quad){return quad.nativeCondition==0||cachedScenarioRead()!=0;}
    /** 5C stores a code offset, not an already decoded or script-bound string.
     * Bytes beyond the currently owned script are not invented as stale code. */
    public String resolveDrawText(Dc2Ui.Quad quad)throws IOException{
        if(quad.nativeTextOffset<0)return quad.text;if(host.script==null)throw new IOException("PC1 draw text has no loaded code owner");
        long file=(long)host.script.versionOffset+quad.nativeTextOffset;if(file<=host.script.versionOffset||file>=host.script.length())throw new IOException("PC1 type10 text offset lies outside the current known loaded code");
        byte[] raw=host.script.stringPayload((int)file-1,host.pc).bytes;return host.codec.decodeUnmapped(Pc1TextExpander.expandDraw(raw,textContext));
    }
    /** Native41AEA0 cuts at the first dot, then compares the entire remaining
     *name; a directory prefix is not silently removed. */
    private int scenarioIndex(String target)throws IOException{
        return scenarioIndex(target.getBytes(Binary.SJIS));
    }
    private int scenarioIndex(byte[] raw)throws IOException{
        int end=0;while(end<raw.length&&raw[end]!='.')end++;
        List<NamesCatalog.Entry> names=host.resources.names.table("scenario_scripts");
        for(int n=0;n<names.size();n++){byte[] candidate=names.get(n).nameBytes;if(candidate.length!=end)continue;boolean equal=true;for(int i=0;i<end;i++){int a=raw[i]&255,b=candidate[i]&255;if(a>='a'&&a<='z')a-=32;if(b>='a'&&b<='z')b-=32;if(a!=b){equal=false;break;}}if(equal)return n;}return -1;
    }
    private byte[] catalogLabel(String category,int index)throws IOException{
        List<NamesCatalog.Entry> entries=host.resources.names.table(category);if(index<0||index>=entries.size())throw new IOException("PC1 raw catalog label index");byte[] raw=entries.get(index).extra;int end=0;while(end<raw.length&&raw[end]!=0)end++;if(end==raw.length)throw new IOException("PC1 raw catalog label lacks its owned NUL");return Arrays.copyOf(raw,end);
    }
    public final Pc1TextExpander.Context textContext=new Pc1TextExpander.Context(){
        public byte[] givenName()throws IOException{return host.nativeGivenName();}
        public byte[] familyName()throws IOException{return host.nativeFamilyName();}
        public int textCount()throws IOException{return host.resources.names.table("scenario_scripts").size();}
        public byte[] textLabel(int index)throws IOException{return Pc1Runtime.this.catalogLabel("scenario_scripts",index);}
        public int musicCount()throws IOException{return host.resources.names.table("music").size();}
        public byte[] musicLabel(int index)throws IOException{return Pc1Runtime.this.catalogLabel("music",index);}
        public int read(int bank,int index)throws IOException{if(host.variables==null)throw new IOException("PC1 expansion has no live variable owner");return host.variables.read(bank,index);}
    };
    byte[] expand(byte[] decoded,boolean speaker)throws IOException{return speaker?Pc1TextExpander.expandSpeaker(decoded,textContext):Pc1TextExpander.expand(decoded,textContext);}
    void prepareGeometry()throws IOException{
        headY(geometry.prepareBody(nativeDrawUsed?drawMemory.template(host.messageMode()==0?1:4):geometryEntries(host.ui.quadTemplates.get(host.messageMode()==0?1:4)),cachedScenarioRead(),host.read(99,18),headY()));
        geometry.prepareName(nativeDrawUsed?drawMemory.template(host.messageMode()==0?2:5):geometryEntries(host.ui.quadTemplates.get(host.messageMode()==0?2:5)),cachedScenarioRead());
    }
    private static List<Pc1MessageGeometry.Entry> geometryEntries(List<Dc2Ui.Quad> quads){
        List<Pc1MessageGeometry.Entry> result=new ArrayList<>();for(Dc2Ui.Quad q:quads)result.add(new Pc1MessageGeometry.Entry(q.x,q.y,q.type==2?10:-1,q.nativeCondition,q.sx,q.sy,q.width,q.height));return result;
    }
    private Pc1TextLayout.Config layoutConfig()throws IOException{
        return new Pc1TextLayout.Config(geometry.field(Pc1MessageGeometry.BODY_X),geometry.field(Pc1MessageGeometry.BODY_PITCH),geometry.field(Pc1MessageGeometry.NAME_X),geometry.field(Pc1MessageGeometry.NAME_Y),geometry.field(Pc1MessageGeometry.NAME_HISTORY_AUX),geometry.field(Pc1MessageGeometry.BODY_STYLE),Pc1TextLayout.CN_RUBY_STOP_WORD,Pc1TextLayout.CN_RUBY_SPLIT_WORD);
    }
    private Pc1TextLayout builder()throws IOException{if(canonicalLayout==null)canonicalLayout=new Pc1TextLayout(layoutConfig(),layoutMemory,layoutAliases);else canonicalLayout.configure(layoutConfig());return canonicalLayout;}
    /** Same mutable source-lifetime metadata as dispatcher/history layout.
     * Presentation callbacks must retain this owner instead of a detached DTO. */
    public Pc1TextLayout nativeTextLayout()throws IOException{return builder();}
    private Pc1TextLayout.Source source(String owner,String epoch)throws IOException{return new Pc1TextLayout.Source(owner+epoch,textBuffers.knownBytes(owner));}
    private void batch(Pc1TextLayout builder,Pc1TextLayout.Result result)throws IOException{
        int end=layoutMemory.readI32(Pc1LayoutMemory.GLYPH_COUNT);glyphBatches.add(new int[]{result.firstGlyph,end});for(Pc1TextLayout.Patch p:result.patches)textPatches.add(new int[]{result.firstGlyph,p.left,p.top,p.right,p.bottom,p.selector});
    }
    private byte[] expandedRecord(Pc1TextLog.RawRecord record,boolean speaker)throws IOException{
        return record==null?new byte[0]:expand(Pc1TextExpander.decodeEncoded(record.payload()),speaker);
    }
    /** The complete native journal is independent of portable reveal timing.
     * The journal owns raw glyphs; the platform alone rasterizes them. */
    void presentLiveText()throws IOException{
        String currentBody=host.body,currentSpeaker=host.speaker;prepareGeometry();Pc1TextLayout draw=builder();draw.resetRubyPhase();layoutAliases=draw.aliases();
        if(host.messageMode()!=0&&textLog.count()>0){redrawText(host.script,-2);draw=builder();}
        else{draw=builder();draw.clearGlyphs();draw.dictionaryCount(0);glyphBatches.clear();textPatches.clear();draw.position(geometry.field(Pc1MessageGeometry.BODY_X),geometry.field(Pc1MessageGeometry.BODY_Y),28);batch(draw,draw.speaker(source(Pc1TextBuffers.LIVE_SPEAKER,""),geometry.field(Pc1MessageGeometry.NAME_STYLE),argument1(),false));draw.position(geometry.field(Pc1MessageGeometry.BODY_X),geometry.field(Pc1MessageGeometry.BODY_Y),28);}
        byte[] body=textBuffers.read(Pc1TextBuffers.LIVE_BODY);Pc1TextLayout.Snapshot phase=draw.freeze();
        if(host.messageMode()!=0&&Pc1TextLog.beginsNewLine(body)&&phase.x!=geometry.field(Pc1MessageGeometry.BODY_X))draw.position(geometry.field(Pc1MessageGeometry.BODY_X),phase.y+geometry.field(Pc1MessageGeometry.BODY_PITCH),28);
        batch(draw,draw.body(source(Pc1TextBuffers.LIVE_BODY,""),geometry.field(Pc1MessageGeometry.BODY_STYLE),argument1()));layoutAliases=draw.aliases();host.body=currentBody;host.speaker=host.messageMode()==0?currentSpeaker:"";layoutIsHistory=false;activeDictionaryCount=null;
    }
    /** 418220 re-reads the current code and current substitutions. It never
     * changes the interpreter PC, ordinary bytes or the variable undo log. */
    public void redrawText(MesScript caller,int selection)throws IOException{
        Pc1TextLog.Replay replay=textLog.replay(caller,selection,host.messageMode(),host.choiceMode);prepareGeometry();
        textBuffers.beginRedraw();if(redrawGeneration==Long.MAX_VALUE)throw new IOException("PC1 redraw owner generation exhausted");String epoch="/"+nextTextSourceEpoch();
        Pc1TextLayout draw=builder();draw.clearGlyphs();draw.dictionaryCount(replay.currentPalette?0:-1);glyphBatches.clear();textPatches.clear();activeDictionaryCount=null;
        int selectedStyle=geometry.field(replay.currentPalette?Pc1MessageGeometry.BODY_STYLE:Pc1MessageGeometry.BODY_HISTORY_CONFIG),otherStyle=geometry.field(replay.currentPalette?Pc1MessageGeometry.BODY_HISTORY_STYLE:Pc1MessageGeometry.BODY_HISTORY_AUX),speakerStyle=geometry.field(replay.currentPalette?Pc1MessageGeometry.NAME_STYLE:Pc1MessageGeometry.NAME_HISTORY_STYLE);
        if(host.read(99,18)==0){selectedStyle&=0xffbf;otherStyle&=0xffbf;speakerStyle&=0xffbf;}
        if(replay.clearSpeakerFirst||replay.drawSpeaker){byte[] speaker=expandedRecord(replay.drawnSpeaker(),true);if(speaker.length==0)textBuffers.clearRedrawSpeakerHead();else textBuffers.writeExpanded(Pc1TextBuffers.REDRAW_SPEAKER,speaker);batch(draw,draw.speaker(source(Pc1TextBuffers.REDRAW_SPEAKER,epoch),speakerStyle,argument1(),host.messageMode()!=0));host.speaker=host.codec.decode(speaker);}
        else if(host.messageMode()!=0)host.speaker="";
        draw.position(geometry.field(Pc1MessageGeometry.BODY_X),geometry.field(Pc1MessageGeometry.BODY_Y),28);StringBuilder readable=new StringBuilder();
        for(Pc1TextLog.RawMessage message:replay.messages){
            byte[] body=expandedRecord(message.body,false);textBuffers.writeExpanded(Pc1TextBuffers.REDRAW_BODY,body);if(message.speaker!=null)textBuffers.writeExpanded(Pc1TextBuffers.REDRAW_SPEAKER,expandedRecord(message.speaker,true));
            Pc1TextLayout.Snapshot state=draw.freeze();boolean line=host.messageMode()!=0&&Pc1TextLog.beginsNewLine(body)&&state.x!=geometry.field(Pc1MessageGeometry.BODY_X);if(line){draw.position(geometry.field(Pc1MessageGeometry.BODY_X),state.y+geometry.field(Pc1MessageGeometry.BODY_PITCH),28);readable.append('\n');}
            readable.append(host.codec.decode(body));batch(draw,draw.body(source(Pc1TextBuffers.REDRAW_BODY,epoch),message.role==Pc1TextLog.Role.SELECTED?selectedStyle:otherStyle,argument1()));
        }
        layoutAliases=draw.aliases();host.body=readable.toString();host.presentationWaiting=false;host.textRendered=true;layoutIsHistory=selection>=0;
        if(waitKind()==7||waitKind()==9)activateDictionary();
    }
    void activateDictionary()throws IOException{
        if(waitKind()==7||waitKind()==9)restoreStoryButtons();
        if(layoutAliases==null&&canonicalLayout==null)return;activeDictionaryCount=waitKind()==7?Integer.valueOf(layoutMemory.readI32(Pc1LayoutMemory.DICT_COUNT)):null;Pc1TextLayout draw=builder();draw.dictionaryCount(-1);layoutAliases=draw.aliases();
    }
    public boolean historyPrevious()throws IOException{
        if(returning())return false;
        if(waitKind()!=7&&waitKind()!=9)throw new IOException("PC1 backlog requires a native07/09 wait");host.play(5,"",false);
        if(historySelection()==0){restoreStoryButtons();return false;} //418A00 refreshes controls without replaying the oldest bitmap.
        int selected=historySelection();int next=selected<0?textLog.count()-2:selected>0?selected-1:selected;historySelection(next);
        if(next>=0){redrawText(host.script,next);host.nextTicket();return true;}return false;
    }
    public boolean historyNext()throws IOException{
        if(returning())return false;
        host.play(5,"",false);if(historySelection()<0)return false;historySelection(historySelection()+1);
        if(historySelection()>=textLog.count()-1)historySelection(-1);redrawText(host.script,historySelection());host.nextTicket();return true;
    }
    public boolean closeHistory()throws IOException{
        if(returning())return false;
        if(historySelection()<0)return false;host.play(5,"",false);historySelection(-1);redrawText(host.script,-1);host.nextTicket();return true;
    }
    public void replayHistoryVoice()throws IOException{
        if(returning())return;
        if(historySelection()<0)return;host.play(5,"",false);Pc1TextLog.RawRecord voice=textLog.voiceForSelection(host.script,historySelection());if(voice!=null)host.playVoice(new String(voice.payload(),Binary.SJIS));
    }
    /** 419280 common right/Esc and toolbar entry are not opcode26. */
    void enterEvent(MesScript caller,int returnPc,byte[] rawTarget,int eventKind)throws IOException {
        if(eventKind<0||eventKind>2)throw new IOException("PC1 native system event kind");
        if(system!=null)throw new IOException("Nested native PC1 system context is not mapped");
        if(host.nativeFrameCapture==null)throw new IOException("Native PC1 system call requires a complete framebuffer capture adapter");
        if(eventKind!=0)host.play(5,"",false);
        if(saves.captureNativeEvent(host,caller,returnPc,packedSoundRequest())&&portableCapture!=null)portableCapture.captured(3);
        if(eventKind!=2)host.play(5,"",false);
        enter(caller,returnPc,0,rawTarget);
    }
    void enter(MesScript caller,int returnPc,int mode,byte[] rawTarget)throws IOException {
        if(system!=null)throw new IOException("Nested native PC1 system context is not mapped");
        if(host.nativeFrameCapture==null)throw new IOException("Native PC1 system call requires a complete framebuffer capture adapter");
        if(waitKind()==-1)requireRetainableFront();
        UiCapture framebuffer=host.nativeFrameCapture.capture();
        Pc1SystemContext next=Pc1SystemContext.capture(host,caller.name,returnPc,mode,rawTarget,framebuffer,packedSoundRequest());
        systemFrame.copyFrom(next.nativeFrame());next=next.bindNativeFrame(systemFrame);next.applyEntry(host);system=next;waitKind(-1);scriptLoaded(0,new String(rawTarget,Binary.SJIS));
    }
    MesVm.SystemReturn returnPoint(){
        if(system==null||returning())return null;Pc1SystemContext.ReturnPoint point=system.returnPoint();
        return new MesVm.SystemReturn(point.script,point.sha256,point.pc);
    }
    void leave()throws IOException {
        if(system==null)throw new IOException("Native PC1 return without a suspended caller");
        Pc1SystemContext.ReturnPoint point=system.returnPoint();MesScript caller=host.resources.scripts().script(Binary.safePath(point.script));
        if(!caller.sha256.equals(point.sha256)||!caller.profile.id.equals("dcpc-2063")||!knownCodePc(caller,point.pc))throw new IOException("Native PC1 suspended caller code changed");
        leave(caller);
    }
    void leave(MesScript verifiedCaller)throws IOException {
        if(system==null)throw new IOException("Native PC1 return without a suspended caller");
        if(returning())throw new IOException("PC1 native return transition already owns the caller");
        Pc1SystemContext before=system;int resumedWait=before.fields().waitKind();
        if(resumedWait!=-1&&resumedWait!=7&&resumedWait!=9)throw new IOException("PC1 native return display for wait"+resumedWait+" is not implemented");
        if(resumedWait==-1)requireRetainableFront();
        if(host.nativeFrameCapture==null)throw new IOException("Native PC1 return requires a complete framebuffer capture adapter");
        UiCapture oldFront=host.nativeFrameCapture.capture();
        before.applyReturn(host);checkpointMemory.loadSource(verifiedCaller);codeBuffer.load(verifiedCaller);MesScript activeCaller=verifiedCaller.nativeView(codeBuffer);host.script=activeCaller;scriptLoaded(0,before.returnPoint().script);
        if(waitKind()==7||waitKind()==9){redrawText(activeCaller,historySelection());if(waitKind()==9)restoreStoryButtons();}
        UiCapture target=waitKind()==-1?before.framebuffer():host.nativeFrameCapture.capture();
        returnSurface=Pc1ReturnSurface.begin(oldFront,target,host.read(99,12),host.motionSkipPressed);returnPaintSerial=host.scene.transitionSerial&0xffffffffL;
    }
    /** A late platform tick does not credit the next native40ms iteration.
     *41A0B0 samples a fresh clock after drawing the next feedback frame. */
    void elapseReturn(long milliseconds)throws IOException{
        if(returnSurface==null||!returnSurface.active())return;
        if(host.exitRequested){returnSurface.interrupt();return;}
        returnSurface.elapse(Math.min(Math.max(0,milliseconds),40-returnSurface.phaseElapsed()),host.read(99,12),host.motionSkipPressed);
        if(returnSurface.committed()){
            if(system==null)throw new IOException("PC1 return capture lost its suspended owner");
            if(system.restartsLoopingSound())returnSound(system.loopingSoundIndex());system=null;
            //07/09's target was fully rebuilt from the current scene/text/UI.
            //Normal presentation may now render its changing button states.
            //The wait-1 bitmap has no equivalent logical reconstruction.
            if(waitKind()==7||waitKind()==9){returnSurface=null;returnPaintSerial=-1;}
            if(host.waiting()){lastYieldOpcode=0x1c;dispatcherReentry=true;}
        }
    }
    private void returnSound(int index)throws IOException {
        // Return1C calls41C850 directly. FF is not the opcode05 stop sentinel here.
        if(host.read(99,15)==0||index>=host.resources.names.table("sound").size())return;
        host.play(1,host.resources.named("sound",index),true);
    }
    void prepareSave()throws IOException{saves.prepare(host,host.pc,packedSoundRequest());pendingPortableCapture=1;}
    void requestSave(int argument)throws IOException {
        Pc1Frame current=saves.needsCurrentFrame(argument)?Pc1Frame.capturePersistCurrent(host,host.pc,packedSoundRequest()):null;
        Pc1SaveContext.Selection selected=saves.selectForPersist4(argument,host.read(99,254),current);
        requestedSave=selected.frame;requestedSlot=selected.slot;host.saveRequest=selected.slot;host.nextTicket();
        if(current!=null)pendingPortableCapture=2;
    }
    public void completeSave(){requestedSave=null;requestedSlot=-1;}
    void restored(MesScript active)throws IOException {
        if(system!=null)validate(system.returnPoint(),active);
    }
    private static boolean knownCodePc(MesScript owner,int filePc){return filePc>=owner.versionOffset&&(long)filePc<(long)owner.versionOffset+Pc1CodeBuffer.CAPACITY;}
    private void validate(Pc1SystemContext.ReturnPoint point,MesScript active)throws IOException {
        MesScript caller=point.script.equals(active.name)?active:host.resources.scripts().script(Binary.safePath(point.script));
        if(!caller.profile.id.equals("dcpc-2063")||!caller.profile.canExecuteVersion(caller.version)||!caller.sha256.equals(point.sha256)||!knownCodePc(caller,point.pc))throw new IOException("Native PC1 suspended caller code changed");
    }
    void save(DataOutput out)throws IOException {
        requireNativeBoundary();
        if(canonicalLayout!=null)layoutAliases=canonicalLayout.aliases();
        out.writeInt(FORMAT);workingFrame.save(out);systemFrame.save(out);out.writeBoolean(system!=null);if(system!=null)system.saveBound(out);saves.save(out);
        out.writeInt(requestedSlot);out.writeBoolean(requestedSave!=null);if(requestedSave!=null){boolean shared=requestedSave.hasNativeFrame(saves.nativeCandidate());out.writeBoolean(shared);if(shared)requestedSave.saveBound(out);else requestedSave.save(out);}
        nativeFrame.save(out);codeBuffer.save(out);drawMemory.save(out);nativeUi.save(out);layoutMemory.save(out);pathGlobals.save(out);resourceLiterals.save(out);graphicsMemory.save(out);vmGlobals.save(out);effectsMemory.save(out);presentationMemory.save(out);uiPageMemory.save(out);eventMemory.save(out);crxMemory.save(out);checkpointMemory.save(out);effectPeMemory.save(out);catalogMemory.save(out);startupMemory.save(out);timer5Memory.save(out);inputMemory.save(out);waitMemory.save(out);scriptMemory.save(out);systemMemory.save(out);windowMemory.save(out);geometry.save(out);textBuffers.save(out);
        mmPhysicalMemory().save(out);
        out.writeBoolean(nativeDispatchFrame!=null);if(nativeDispatchFrame!=null)nativeDispatchFrame.save(out);
        out.writeBoolean(initialScriptBound);out.writeBoolean(dispatcherReentry);out.writeInt(pendingCondition);out.writeBoolean(nativeDrawUsed);out.writeInt(rawStringPointer);
        out.writeInt(previousOpcode);out.writeInt(currentOpcode);out.writeInt(lastYieldOpcode);out.writeInt(fetchedAt);
        out.writeBoolean(layoutAliases!=null);if(layoutAliases!=null)layoutAliases.save(out);out.writeBoolean(activeDictionaryCount!=null);if(activeDictionaryCount!=null)out.writeInt(activeDictionaryCount);
        out.writeLong(redrawGeneration);out.writeBoolean(layoutIsHistory);out.writeInt(glyphBatches.size());for(int[] a:glyphBatches){out.writeInt(a[0]);out.writeInt(a[1]);}out.writeInt(textPatches.size());for(int[] a:textPatches)for(int value:a)out.writeInt(value);
        out.writeInt(storyPrefixCount);out.writeInt(storyFullCount);Dc2Ui.saveButtons(out,storyButtons);
        out.writeLong(returnPaintSerial);out.writeBoolean(returnSurface!=null);if(returnSurface!=null)returnSurface.save(out);
    }
    void load(DataInput in)throws IOException {
        requireNativeBoundary();
        if(in.readInt()!=FORMAT)throw new IOException("Native PC1 runtime state format");
        Pc1NativeFrame loadedWorking=Pc1NativeFrame.load(in),loadedSystem=Pc1NativeFrame.load(in);Pc1SystemContext next=in.readBoolean()?Pc1SystemContext.loadBound(in,loadedSystem):null;Pc1SaveContext nextSaves=new Pc1SaveContext();nextSaves.load(in);
        int slot=in.readInt();boolean hasRequested=in.readBoolean(),requestedShared=hasRequested&&in.readBoolean();Pc1Frame frame=!hasRequested?null:requestedShared?Pc1Frame.loadBound(in,nextSaves.nativeCandidate()):Pc1Frame.load(in);
        if(slot< -1||(slot>=0)!=(frame!=null)||slot!=host.saveRequest)throw new IOException("Native PC1 pending save state");
        Pc1NativeFrame loadedFrame=Pc1NativeFrame.load(in);Pc1CodeBuffer loadedCode=Pc1CodeBuffer.load(in);Pc1DrawMemory.Image loadedDraw=Pc1DrawMemory.load(in);Pc1NativeUi.Image loadedUi=Pc1NativeUi.load(in);Pc1LayoutMemory.Image loadedLayout=Pc1LayoutMemory.load(in);byte[] loadedPaths=Pc1PathGlobals.loadImage(in);byte[][] loadedLiterals=Pc1ResourceLiterals.loadImage(in);Pc1GraphicsMemory.Image loadedGraphics=Pc1GraphicsMemory.load(in);byte[] loadedVmGlobals=Pc1VmGlobals.loadImage(in);Pc1Effects.Memory.Image loadedEffects=Pc1Effects.Memory.load(in);byte[] loadedPresentation=Pc1PresentationMemory.loadImage(in);Pc1UiPage.Memory.Image loadedUiPage=Pc1UiPage.Memory.load(in);byte[] loadedEvents=Pc1EventMemory.loadImage(in);byte[] loadedCrx=Pc1CrxMemory.loadImage(in);Pc1CheckpointMemory.Image loadedCheckpoints=Pc1CheckpointMemory.loadImage(in);Pc1EffectPeMemory.Image loadedEffectPe=Pc1EffectPeMemory.loadImage(in);byte[] loadedCatalog=Pc1CatalogMemory.loadImage(in);Pc1StartupMemory.Image loadedStartup=Pc1StartupMemory.loadImage(in);byte[] loadedTimer5=Pc1Timer5.Memory.loadImage(in);Pc1InputMemory.Image loadedInput=Pc1InputMemory.load(in);byte[] loadedWait=Pc1WaitDispatcher.Memory.loadImage(in);Pc1ScriptMemory.Image loadedScript=Pc1ScriptMemory.loadImage(in);Pc1SystemMemory.Image loadedSystemMemory=Pc1SystemMemory.loadImage(in);Pc1WindowMemory.Image loadedWindowMemory=Pc1WindowMemory.loadImage(in);Pc1MessageGeometry nextGeometry=Pc1MessageGeometry.load(in);Pc1TextBuffers buffers=Pc1TextBuffers.load(in);
        Pc1MmPhysicalMemory.Image loadedPhysical=Pc1MmPhysicalMemory.loadImage(in);
        Pc1Fetch.Frame loadedDispatchFrame=in.readBoolean()?Pc1Fetch.Frame.load(in):null;
        boolean bound=in.readBoolean(),reentry=in.readBoolean();int conditional=in.readInt();boolean drawUsed=in.readBoolean();int rawPointer=in.readInt(),previous=in.readInt(),current=in.readInt(),yielded=in.readInt(),at=in.readInt();
        if(previous< -1||previous>255||current< -1||current>255||at< -1)throw new IOException("Native PC1 saved dispatcher/history state");
        Pc1TextLayout.Aliases aliases=in.readBoolean()?Pc1TextLayout.Aliases.load(in):null;Integer dictionaryCount=in.readBoolean()?Integer.valueOf(in.readInt()):null;
        long epoch=in.readLong();boolean historic=in.readBoolean();int batches=in.readInt();if(epoch<0||batches<0||batches>Pc1CodeBuffer.CAPACITY)throw new IOException("PC1 text draw batch count");List<int[]> nextBatches=new ArrayList<>();
        for(int n=0;n<batches;n++){int first=in.readInt(),end=in.readInt();nextBatches.add(new int[]{first,end});}
        int patchCount=in.readInt();if(patchCount<0||patchCount>Pc1CodeBuffer.CAPACITY)throw new IOException("PC1 text patch count");List<int[]> patches=new ArrayList<>();for(int n=0;n<patchCount;n++){int[] a=new int[6];for(int j=0;j<a.length;j++)a[j]=in.readInt();if(a[5]!=0)throw new IOException("PC1 text patch range");patches.add(a);}
        int prefix=in.readInt(),full=in.readInt();List<Dc2Ui.Button> story=Dc2Ui.loadButtons(in);
        long returnRevision=in.readLong();Pc1ReturnSurface surface=in.readBoolean()?Pc1ReturnSurface.load(in):null;
        if(surface==null?returnRevision!=-1:returnRevision<0||returnRevision>0xffffffffL||!surface.committed()&&next==null||surface.committed()&&next!=null)throw new IOException("PC1 return surface lifecycle");
        mmPhysicalMemory().requireRestoreImage(loadedPhysical);
        if((loadedDispatchFrame!=null)!=(nativeDispatchFrame!=null))throw new IOException("PC1 saved raw caller locals require the same explicitly constructed dispatcher graph");requireDispatchFrameBoundary(loadedDispatchFrame);
        // Publish only a complete decoded owner. The host/VM pair itself is still unpublished.
        workingFrame.copyFrom(loadedWorking);systemFrame.copyFrom(loadedSystem);system=next==null?null:next.bindNativeFrame(systemFrame);saves.copyFrom(nextSaves);requestedSlot=slot;requestedSave=requestedShared?frame.bindNativeFrame(saves.nativeCandidate()):frame;nativeFrame.copyFrom(loadedFrame);codeBuffer.copyFrom(loadedCode);drawMemory.restore(loadedDraw);nativeUi.restore(loadedUi);layoutMemory.restore(loadedLayout);pathGlobals.restore(loadedPaths);resourceLiterals.restore(loadedLiterals);graphicsMemory.restore(loadedGraphics);vmGlobals.restore(loadedVmGlobals);effectsMemory.restore(loadedEffects);presentationMemory.restore(loadedPresentation);uiPageMemory.restore(loadedUiPage);eventMemory.restore(loadedEvents);crxMemory.restore(loadedCrx);checkpointMemory.restore(loadedCheckpoints);effectPeMemory.restore(loadedEffectPe);catalogMemory.restore(loadedCatalog);startupMemory.restore(loadedStartup);timer5Memory.restore(loadedTimer5);inputMemory.restore(loadedInput);waitMemory.restore(loadedWait);scriptMemory.restore(loadedScript);systemMemory.restore(loadedSystemMemory);windowMemory.restore(loadedWindowMemory);geometry=nextGeometry;textBuffers=buffers;
        mmPhysicalMemory().restore(loadedPhysical);
        if(nativeDispatchFrame!=null)nativeDispatchFrame.restoreFrom(loadedDispatchFrame);
        initialScriptBound=bound;dispatcherReentry=reentry;pendingCondition=conditional;nativeDrawUsed=drawUsed;rawStringPointer=rawPointer;dispatching=false;previousOpcode=previous;currentOpcode=current;lastYieldOpcode=yielded;fetchedAt=at;pendingPortableCapture=0;
        layoutAliases=aliases;canonicalLayout=null;activeDictionaryCount=dictionaryCount;redrawGeneration=epoch;layoutIsHistory=historic;glyphBatches.clear();glyphBatches.addAll(nextBatches);textPatches.clear();textPatches.addAll(patches);
        storyPrefixCount=prefix;storyFullCount=full;storyButtons.clear();storyButtons.addAll(story);
        returnSurface=surface;returnPaintSerial=returnRevision;
    }
}
