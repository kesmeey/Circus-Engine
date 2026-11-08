package org.circus.core;

import java.io.*;
import java.util.*;

/** To You 5F69 host. Independently bound to Chinese PE 61570441...f505,
 * dispatcher41DAB0, loader41B5C0, actor load41D260 and wait4221A0.
 * Similar common primitives do not admit unreviewed native services. */
public final class TyHost extends PcHost {
    private static final int SNAPSHOT=0x54594833;
    public final TyScroll tyScroll=new TyScroll();
    public final TySelector tySelector=new TySelector();
    public UiCapture tySelectorUnderlay;
    private int selectorMusicState;
    private long selectorMusicStamp;
    // Native AD5078/7C/80 live differs from staged AD5084/88/8C.
    // Initial live descriptors are BSS zero; actor load commits stage -> live.
    private final int[][] liveActors=new int[4][3];
    // Native41D260 restores only saved stage AD1F28/2C/30. A displayed
    // systemScene can differ after65 and does not own these saved bytes.
    private final int[][] savedActorStage=new int[4][3];
    private boolean savedActorStageValid;
    private boolean serviceGridBound;
    private boolean actorMoving,firstActorPoll;
    private int movingActor,targetX,targetY,actorPollPhase;
    /** Native40A470 modifies only the loaded background bitmap. The platform
     * returns immutable baked pixels; existing UI-capture persistence owns them. */
    public interface MusicTitleBaker {UiCapture bake(String resource,UiCapture previous,String text,int size,int shadow,int foreground)throws IOException;}
    public MusicTitleBaker musicTitleBaker;
    private TyMusicNames musicNames;
    @Override public void copyPersistentFrom(Dc2Host other)throws IOException{super.copyPersistentFrom(other);musicTitleBaker=((TyHost)other).musicTitleBaker;}
    public TyMusicNames musicNames()throws IOException{if(musicNames==null)musicNames=TyMusicNames.load(resources);return musicNames;}
    private void musicTitle()throws IOException{
        if(musicTitleBaker==null)throw new IOException("TY music-name bitmap renderer is unavailable");
        TyMusicNames names=musicNames();int index=variables.read(3,5);byte[] raw=names.raw(index);String background=scene.slots[1];
        if(background.isEmpty())throw new IOException("TY music-name background bitmap is absent");
        UiCapture result=musicTitleBaker.bake(background,background.equals("@ui-capture")?uiCapture:null,names.text(index),TyMusicNames.fontSize(raw.length,variables.read(3,30),variables.read(3,31)),ui.palette[15],ui.palette[7]);
        if(result==null||result.kind!=UiCapture.ARGB)throw new IOException("TY music-name bake did not return immutable pixels");
        uiCapture=result;scene.slots[1]="@ui-capture";scene.revision++;
    }

    public TyHost(CircusResources resources,TextCodec codec){this(resources,codec,(int)(System.nanoTime()/1000000L));}
    public TyHost(CircusResources resources,TextCodec codec,int startupTick){
        super(resources,codec,startupTick);
        if(!resources.profile.id.equals("dc2ty-5f69")||!second||pc1!=null)
            throw new IllegalArgumentException("TY host requires its own PC_WIDE profile and TY family binding");
        Arrays.fill(ui.palette,13,63,0xff000000);scene.bindActorProjection(this::projectActors);
    }

    /** TY58/41EC16 keeps the original replay list captions in CP932;
     * translated4B/4C prose and other UI fields retain the selected codec. */
    @Override protected String gridCaption(byte[] raw)throws IOException{
        return TextCodec.cp932(raw).replace("$n",givenName).replace("$s",familyName).replace("$f",familyName);
    }

    private void projectActors(Dc2Scene.Frame frame){
        for(int which=0;which<4;which++){
            System.arraycopy(liveActors[which],0,frame.actors[which],0,3);
            // 41BED0 consumes stage pivot AD5090/94 separately from live zoom.
            frame.actors[which][3]=scene.character(which,3);frame.actors[which][4]=scene.character(which,4);
        }
    }
    private void commitActor(int which)throws IOException {
        actor(which);if(scene.character(which,2)==0)scene.character(which,2,100);
        for(int field=0;field<3;field++)liveActors[which][field]=scene.character(which,field);
    }
    public int displayedActor(int which,int field){return liveActors[which][field];}
    @Override public void enterSystem()throws IOException {
        for(int which=0;which<4;which++)for(int field=0;field<3;field++)savedActorStage[which][field]=scene.character(which,field);
        savedActorStageValid=true;super.enterSystem();
    }
    private boolean loadActor(int[] args,byte[] bytes)throws IOException {
        if(args==null||args.length<1||bytes==null)throw new IOException("TY actor load requires a descriptor and name");
        int which=args[0];actor(which);
        if(bytes.length==2&&bytes[0]=='-'&&bytes[1]=='-'){
            if(!savedActorStageValid||systemScene==null)throw new IOException("TY actor restore has no captured saved stage");
            scene.slots[2+which]=systemScene.slots[2+which];
            // Preserve current pivot AD5090/94 and all later fields. The
            // generic DM restore copies six displayed fields and is unsuitable.
            scene.characterPrefix(which,savedActorStage[which],3);commitActor(which);scene.revision++;return true;
        }
        boolean handled=super.command("pc.character",args,bytes);
        if(handled&&bytes.length>0)commitActor(which);
        return handled;
    }

    /** The active 4071D0 page contains five rows. Native slots are bound when the
     * service runs, rather than recomputed later from a possibly changed pager. */
    private void saveLoadPage(int mode)throws IOException {
        if(grid.size()!=5)throw new IOException("TY service6 requires five native grid rectangles");
        int page=read(99,0);
        if(page<0||page>255)throw new IOException("TY save/load page outside native byte range");
        saveGrid=mode==0;list.clear();
        for(int row=0;row<5;row++){
            int slot=page==0&&row<2?row:page*8+row;
            Dc2Ui.Button entry=grid.get(row);entry.binding=slot;
            boolean enabled=saveGrid?slot>=2:availableSaves.contains(slot);
            // 4071D0 uses selsave on SAVE and selsave2 on LOAD. Only
            // five entries are active although LOAD initializes ten descriptors.
            entry.action=enabled?(saveGrid?"selsave":"selsave2"):"";
        }
        serviceGridBound=true;
    }

    @Override public int gridSlot(int index)throws IOException {
        if(serviceGridBound){if(index<0||index>=grid.size())throw new IOException("TY bound grid row");return grid.get(index).binding;}
        return super.gridSlot(index);
    }
    @Override public byte[] menuActionForGrid(int index)throws IOException {
        if(!serviceGridBound)return super.menuActionForGrid(index);
        if(!gridWaiting||index<0||index>=grid.size())throw new IOException("TY grid selection outside active page");
        Dc2Ui.Button entry=grid.get(index);if(entry.action.isEmpty())return null;
        registers.put(253,index);registers.put(254,entry.binding);registers.put(246,0);
        gridWaiting=ui.waiting=false;
        super.command("ui.music_end_action",new int[0],new byte[0]);
        if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);
        return entry.action.getBytes(Binary.SJIS);
    }

    private static void words(int[] args)throws IOException {
        if(args==null||args.length!=4)throw new IOException("TY actor operation requires four signed words");
        for(int value:args)if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IOException("TY actor argument outside signed native word");
    }
    private static void actor(int value)throws IOException {
        // The source has five descriptors; the common renderer exposes four.
        // No actor4 use was found in this holdout. Do not fold it into actor0.
        if(value<0||value>=4)throw new IOException("Unimplemented TY actor descriptor "+value);
    }
    private void stageActor(int[] args)throws IOException {
        words(args);int selector=args[2];
        if(selector==255){String name=scene.slots[2];scene.slots[2]=scene.slots[3];scene.slots[3]=name;return;}
        actor(selector);scene.character(selector,0,args[0]);scene.character(selector,1,args[1]);scene.character(selector,2,100);
    }
    private void transformActor(int[] args)throws IOException {
        words(args);int selector=args[3];
        if(selector==255){if(args[0]!=-1)scene.character(0,2,args[0]);if(args[1]!=-1)scene.character(1,2,args[1]);return;}
        if(selector>=16&&selector<20){int which=selector-16;scene.character(which,2,args[0]);scene.character(which,3,args[1]);scene.character(which,4,args[2]);return;}
        actor(selector);
        // Native65 stores only the target and selected descriptor. Wait4221A0
        // changes displayed AD5078/7C, not staged AD5084/88; arg2 is ignored.
        movingActor=selector;targetX=args[0];targetY=args[1];actorPollPhase=0;firstActorPoll=true;actorMoving=true;
        remaining=0;presentationWaiting=textWaiting=false;ui.waiting=false;nextTicket();
    }

    public boolean tyScrollVisible(){return tyScroll.visible&&tyScroll.sourceSerial==scene.transitionSerial;}
    public boolean tySelectorVisible(){return tySelector.visible&&tySelector.sourceSerial==scene.transitionSerial;}
    private static boolean captureReference(String[] slots){for(String name:slots)if(name.equals("@ui-capture"))return true;return false;}
    private void releaseReplacedScroll(){
        if(!tyScroll.active&&tyScroll.visible&&!tyScrollVisible())tyScroll.clear();
        if(!tySelector.active&&tySelector.visible&&!tySelectorVisible()){tySelector.clear();tySelectorUnderlay=null;}
        if(uiCapture!=null&&!captureReference(scene.slots)&&!captureReference(scene.shown.slots)&&!(scene.transitionRemaining>0&&captureReference(scene.previous.slots))&&!(systemScene!=null&&captureReference(systemScene.slots)))uiCapture=null;
    }
    private void refreshSelector(){ui.buttons.clear();ui.buttons.addAll(tySelector.controls());ui.waiting=tySelector.interactive();ui.hovered=tySelector.focused();ui.pressed=-1;}
    private void startSelector()throws IOException{
        if(variables==null)throw new IOException("TY selector has no ordinary variable owner");
        UiCapture old=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);
        scene.publish(ui,0,0);tySelector.start(variables.read(3,33),variables.read(3,34),variables.read(3,35),scene.transitionSerial);tySelectorUnderlay=old;selectorMusicState=0;selectorMusicStamp=0;
        remaining=0;presentationWaiting=textWaiting=false;refreshSelector();nextTicket();
    }
    private void advanceSelector(long millis)throws IOException{
        tySelector.advance(millis);
        // Native40B360 waits until99:249 (voice complete), then strictly more
        // than400ms before4282E0(76,0,3): the looping music76 catalog resource.
        long now=tySelector.elapsedMillis();if(read(99,249)==1&&selectorMusicState!=1){if(selectorMusicState==0){selectorMusicStamp=now;selectorMusicState=-1;}if(((now-selectorMusicStamp)&0xffffffffL)>400){super.command("dm.audio.music",new int[]{76,0},new byte[0]);selectorMusicState=1;}}
        Integer sound;while((sound=tySelector.takeSound())!=null)play(1,resources.named("sound",sound),false);
        scene.slots[1]=tySelector.currentBackground();
        Integer result=tySelector.takeResult();if(result!=null)variables.write(3,32,result);
        refreshSelector();
    }
    public boolean nativeSelectorKey(int key)throws IOException{if(!tySelector.active)return false;tySelector.key(key);advanceSelector(0);return true;}
    public boolean nativeSelectorPointer(int x,int y)throws IOException{if(!tySelector.active)return false;tySelector.pointer(x,y);advanceSelector(0);return true;}
    public boolean nativeSelectorClick(int x,int y)throws IOException{if(!tySelector.active)return false;tySelector.click(x,y);advanceSelector(0);return true;}
    @Override public boolean internalMenuAction(long expected,int button)throws IOException{
        if(!tySelector.active)return super.internalMenuAction(expected,button);if(expected!=waitTicket())return false;
        if(button<0)return nativeSelectorKey(button==-2?13:27);if(button>=ui.buttons.size())return false;Dc2Ui.Button b=ui.buttons.get(button);return nativeSelectorClick((b.left+b.right)/2,(b.top+b.bottom)/2);
    }
    @Override public void hoverMenu(int button)throws IOException{
        if(!tySelector.active){super.hoverMenu(button);return;}if(button<0){nativeSelectorPointer(-1,-1);return;}if(button>=ui.buttons.size())return;Dc2Ui.Button b=ui.buttons.get(button);nativeSelectorPointer((b.left+b.right)/2,(b.top+b.bottom)/2);
    }
    private void startScroll(int[] args)throws IOException{
        if(scene.slots[1].equals("@ui-capture"))throw new IOException("TY weighted scroll of a captured background has no reviewed raw bitmap owner");
        UiCapture old=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);
        scene.scrollStep=0;scene.publish(ui,0,0);tyScroll.start(old,scene.slots[1],scene.backgroundX(),scene.backgroundY(),args[0],args[1],args[3],scene.transitionSerial);remaining=0;presentationWaiting=textWaiting=ui.waiting=false;nextTicket();
    }
    @Override protected void captureUi()throws IOException{
        if(nativeFrameCapture==null){super.captureUi();return;}
        UiCapture front=nativeFrameCapture.capture();uiCapture=front;scene.slots[1]="@ui-capture";Arrays.fill(scene.slots,2,10,"");scene.backgroundX(0);scene.backgroundY(0);scene.publish(ui,0,0);
    }
    @Override public boolean waiting(){return tySelector.active||tyScroll.active||actorMoving||super.waiting();}
    public boolean actorMotionActive(){return actorMoving;}
    private static int approach(int value,int target,int step){
        if(value<target)return (int)Math.min((long)target,(long)value+step);
        if(value>target)return (int)Math.max((long)target,(long)value-step);
        return value;
    }
    @Override public void elapse(long millis){
        super.elapse(millis);
        releaseReplacedScroll();
        if(tySelector.active){try{advanceSelector(millis);}catch(IOException failure){throw new IllegalStateException("TY native story selector",failure);}return;}
        if(tyScroll.active){
            try{
                boolean drawn=tyScroll.advance(millis,read(99,12),motionSkipPressed&&read(99,25)==0,(x,y)->{
                    scene.backgroundX(x);scene.backgroundY(y);scene.publish(ui,0,0);tyScroll.sourceSerial=scene.transitionSerial;
                    return SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);
                });
                if(drawn&&tyScroll.weight>0){scene.backgroundX(tyScroll.x);scene.backgroundY(tyScroll.y);scene.publish(ui,0,0);tyScroll.sourceSerial=scene.transitionSerial;}
            }catch(IOException failure){throw new IllegalStateException("TY native scroll",failure);}
            return;
        }
        if(!actorMoving)return;
        long delta=Math.max(0,millis);boolean due=firstActorPoll||delta>=10||actorPollPhase+delta%10>=10;
        actorPollPhase=(int)((actorPollPhase+delta%10)%10);if(!due)return;firstActorPoll=false;
        int[] displayed=liveActors[movingActor];
        // Reaching the target still draws and returns1. Only a later poll
        // returns0 and lets the dispatcher continue (424439 wait65 thunk).
        if(displayed[0]==targetX&&displayed[1]==targetY){actorMoving=false;return;}
        int speed;try{speed=read(99,12);}catch(IOException failure){throw new IllegalStateException("TY actor speed read",failure);}
        int rapidGate;try{rapidGate=read(99,25);}catch(IOException failure){throw new IllegalStateException("TY Ctrl gate",failure);}
        int distance=motionSkipPressed&&rapidGate==0||speed==3?64:speed==2?32:speed==1?16:8;
        displayed[0]=approach(displayed[0],targetX,distance);displayed[1]=approach(displayed[1],targetY,distance);
        try{scene.publish(ui,0,0);}catch(IOException failure){throw new IllegalStateException("TY actor frame publish",failure);}
    }
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException {
        // Dialogue ADVANCE is not a native modal key. Desktop keyboard events
        // use nativeSelectorKey, including genuine Enter during interactive UI.
        if(tySelector.active)return false;
        if(actorMoving||tyScroll.active)return false;
        return super.signal(expected,signal);
    }
    // The native4C body does not write scenario flags. Wait2E calls426D30
    // on the old script before the loader samples the target via426DB0.
    @Override protected void unlock(String table,String name,int bank)throws IOException {
        if(!table.equals("scenario_scripts"))super.unlock(table,name,bank);
    }
    @Override public boolean beforeTransfer(int mode,String name)throws IOException {
        if(mode!=2&&script!=null)super.unlock("scenario_scripts",script.name,98);
        boolean accepted=super.beforeTransfer(mode,name);
        if(accepted){actorMoving=false;firstActorPoll=false;actorPollPhase=0;serviceGridBound=false;}
        return accepted;
    }

    @Override public boolean command(String operation,int[] args,byte[] bytes)throws IOException {
        if(operation.equals("ty.actor_position")){stageActor(args);return true;}
        if(operation.equals("ty.actor_transform")){transformActor(args);return true;}
        if(operation.equals("pc.character"))return loadActor(args,bytes);
        if(operation.equals("pc.character_overlay")&&args.length>0&&args[0]>=0&&args[0]<4&&TextCodec.cp932(bytes).equals("--")){
            int which=args[0];scene.slots[6+which]=systemScene==null?"":systemScene.slots[6+which];scene.revision++;return true;
        }
        if(operation.equals("ty.scroll")){
            words(args);if(args[2]==0){startScroll(args);return true;}
            if(args[2]==1){scene.backgroundX(args[0]);scene.backgroundY(args[1]);scene.scrollStep=0;return true;}
            if(args[2]==2){scene.portraitX=args[0];scene.portraitY=args[1];return true;}
            if(args[2]>=4&&args[2]<=7)return true; // Independently observed default with no store.
            throw new IOException("Unimplemented TY native scroll "+Arrays.toString(args));
        }
        if(operation.equals("ty.command")){
            if(args==null||args.length!=2)throw new IOException("TY system command requires two bytes");
            if(args[0]==6&&args[1]>=0&&args[1]<=255){saveLoadPage(args[1]);return true;}
            if(args[0]==7){list.textStyle=4;return true;}
            if(args[0]==9){musicTitle();return true;}
            if(args[0]==12){startSelector();return true;}
            throw new IOException("Unimplemented TY native system service "+Arrays.toString(args));
        }
        if(operation.equals("native.unmodelled")||operation.startsWith("ty.")||operation.equals("pc.command")||operation.equals("pc.actor_position")||operation.equals("pc.actor_transform"))
            throw new IOException("Unimplemented TY native operation "+operation+" "+Arrays.toString(args));
        if(operation.equals("pc.grid.configure")||operation.equals("pc.grid.begin"))serviceGridBound=false;
        return super.command(operation,args,bytes);
    }

    @Override public void save(DataOutput out)throws IOException {
        releaseReplacedScroll();super.save(out);out.writeInt(SNAPSHOT);out.writeBoolean(serviceGridBound);out.writeBoolean(actorMoving);
        out.writeBoolean(firstActorPoll);out.writeInt(movingActor);out.writeInt(targetX);out.writeInt(targetY);out.writeInt(actorPollPhase);
        for(int[] descriptor:liveActors)for(int value:descriptor)out.writeInt(value);
        out.writeBoolean(savedActorStageValid);for(int[] descriptor:savedActorStage)for(int value:descriptor)out.writeInt(value);tyScroll.save(out);tySelector.save(out);UiCapture.save(out,tySelectorUnderlay);out.writeInt(selectorMusicState);out.writeLong(selectorMusicStamp);
    }
    @Override public void load(DataInput in)throws IOException {
        super.load(in);if(in.readInt()!=SNAPSHOT)throw new IOException("TY host state version");
        boolean bound=in.readBoolean(),moving=in.readBoolean(),first=in.readBoolean();
        int which=in.readInt(),x=in.readInt(),y=in.readInt(),phase=in.readInt();
        if(which<0||which>=4||phase<0||phase>=10||bound&&grid.size()!=5||x<Short.MIN_VALUE||x>Short.MAX_VALUE||y<Short.MIN_VALUE||y>Short.MAX_VALUE)
            throw new IOException("TY saved actor/grid state");
        serviceGridBound=bound;actorMoving=moving;firstActorPoll=first;movingActor=which;targetX=x;targetY=y;actorPollPhase=phase;
        for(int[] descriptor:liveActors)for(int field=0;field<3;field++)descriptor[field]=in.readInt();
        savedActorStageValid=in.readBoolean();for(int[] descriptor:savedActorStage)for(int field=0;field<3;field++)descriptor[field]=in.readInt();
        for(int[][] owner:new int[][][]{liveActors,savedActorStage})for(int[] descriptor:owner)for(int value:descriptor)
            if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IOException("TY saved actor descriptor outside native signed word");
        tyScroll.load(in);tySelector.load(in);tySelectorUnderlay=UiCapture.load(in);selectorMusicState=in.readInt();selectorMusicStamp=in.readLong();
        if((tyScroll.active?1:0)+(actorMoving?1:0)+(tySelector.active?1:0)>1||tySelector.visible&&tySelectorUnderlay==null||selectorMusicState< -1||selectorMusicState>1||selectorMusicStamp<0||selectorMusicStamp>0xffffffffL)throw new IOException("TY saved modal owner state");
    }
}
