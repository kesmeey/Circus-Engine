package org.circus.core;

import java.io.*;
import java.util.*;

/** Spring Celebration 3B69 host, bound to the independently reviewed SC switch.
 * Evidence: CHS PE 11f0507f...13abc, 419E00 cases64/65/1C, 41E490,
 * 407760 service6 ->4074C0. Unreviewed SC services never use PC2's default return. */
public final class ScHost extends PcHost {
    private static final int SNAPSHOT=0x53434833;
    // Native DD80/DD84/DD88 are distinct from staged DD8C/DD90/DD94.
    // Initial live descriptors are BSS zero; actor load commits stage -> live.
    private final int[][] liveActors=new int[4][3];
    // Native419960 restores only saved stage AC30/AC34/AC38. A displayed
    // systemScene can differ after65 and does not own these saved bytes.
    private final int[][] savedActorStage=new int[4][3];
    private boolean savedActorStageValid;
    private boolean serviceGridBound;
    private boolean actorMoving,firstActorPoll;
    private int movingActor,targetX,targetY,actorPollPhase;

    public ScHost(CircusResources resources,TextCodec codec){this(resources,codec,(int)(System.nanoTime()/1000000L));}
    public ScHost(CircusResources resources,TextCodec codec,int startupTick){
        super(resources,codec,startupTick);
        if(!resources.profile.id.equals("dc2sc-3b69")||!second||pc1!=null)
            throw new IllegalArgumentException("SC host requires its own PC_BYTE profile and SC family binding");
        scene.bindActorProjection(this::projectActors);
    }

    /** SC51/410960 retains the raw native menu caption, separately from4B/4C
     * localized prose. The reviewed SC menu corpus keeps these captions CP932,
     * including scene replay dates/titles, while its story text is GBK. */
    @Override protected String buttonCaption(byte[] raw)throws IOException{return TextCodec.cp932(raw);}

    private void projectActors(Dc2Scene.Frame frame){
        for(int which=0;which<4;which++){
            System.arraycopy(liveActors[which],0,frame.actors[which],0,3);
            // 418740 reads pivot DD98/DD9C directly, separately from live zoom.
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
        if(args==null||args.length<1||bytes==null)throw new IOException("SC actor load requires a descriptor and name");
        int which=args[0];actor(which);
        if(bytes.length==2&&bytes[0]=='-'&&bytes[1]=='-'){
            if(!savedActorStageValid||systemScene==null)throw new IOException("SC actor restore has no captured saved stage");
            scene.slots[2+which]=systemScene.slots[2+which];
            // Preserve current pivot DD98/DD9C and all later fields. The
            // generic DM restore copies six displayed fields and is unsuitable.
            scene.characterPrefix(which,savedActorStage[which],3);commitActor(which);scene.revision++;return true;
        }
        boolean handled=super.command("pc.character",args,bytes);
        if(handled&&bytes.length>0)commitActor(which);
        return handled;
    }

    /** The active 4074C0 page contains five rows. Native slots are bound when the
     * service runs, rather than recomputed later from a possibly changed pager. */
    private void saveLoadPage(int mode)throws IOException {
        if(grid.size()!=5)throw new IOException("SC service6 requires five native grid rectangles");
        int page=read(99,0);
        if(page<0||page>255)throw new IOException("SC save/load page outside native byte range");
        saveGrid=mode==0;list.clear();
        for(int row=0;row<5;row++){
            int slot=page==0&&row<2?row:page*8+row;
            Dc2Ui.Button entry=grid.get(row);entry.binding=slot;
            boolean enabled=saveGrid?slot>=2:availableSaves.contains(slot);
            // Both native branches use the same string at456990. LOAD.mes
            // resolves its own selsave label; PC2's selsave2 is not an alias.
            entry.action=enabled?"selsave":"";
        }
        serviceGridBound=true;
    }

    @Override public int gridSlot(int index)throws IOException {
        if(serviceGridBound){if(index<0||index>=grid.size())throw new IOException("SC bound grid row");return grid.get(index).binding;}
        return super.gridSlot(index);
    }
    @Override public byte[] menuActionForGrid(int index)throws IOException {
        if(!serviceGridBound)return super.menuActionForGrid(index);
        if(!gridWaiting||index<0||index>=grid.size())throw new IOException("SC grid selection outside active page");
        Dc2Ui.Button entry=grid.get(index);if(entry.action.isEmpty())return null;
        registers.put(253,index);registers.put(254,entry.binding);registers.put(246,0);
        gridWaiting=ui.waiting=false;
        super.command("ui.music_end_action",new int[0],new byte[0]);
        if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);
        return entry.action.getBytes(Binary.SJIS);
    }

    private static void words(int[] args)throws IOException {
        if(args==null||args.length!=4)throw new IOException("SC actor operation requires four signed words");
        for(int value:args)if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IOException("SC actor argument outside signed native word");
    }
    private static void actor(int value)throws IOException {
        // The source has five descriptors; the common renderer exposes four.
        // No actor4 use was found in this holdout. Do not fold it into actor0.
        if(value<0||value>=4)throw new IOException("Unimplemented SC actor descriptor "+value);
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
        // Native65 stores only the target and selected descriptor. Wait41E490
        // changes displayed DD80/DD84, not staged DD8C/DD90; arg2 is ignored.
        movingActor=selector;targetX=args[0];targetY=args[1];actorPollPhase=0;firstActorPoll=true;actorMoving=true;
        remaining=0;presentationWaiting=textWaiting=false;ui.waiting=false;nextTicket();
    }

    @Override public boolean waiting(){return actorMoving||super.waiting();}
    public boolean actorMotionActive(){return actorMoving;}
    private static int approach(int value,int target,int step){
        if(value<target)return (int)Math.min((long)target,(long)value+step);
        if(value>target)return (int)Math.max((long)target,(long)value-step);
        return value;
    }
    @Override public void elapse(long millis){
        super.elapse(millis);
        if(!actorMoving)return;
        long delta=Math.max(0,millis);boolean due=firstActorPoll||delta>=10||actorPollPhase+delta%10>=10;
        actorPollPhase=(int)((actorPollPhase+delta%10)%10);if(!due)return;firstActorPoll=false;
        int[] displayed=liveActors[movingActor];
        // Reaching the target still draws and returns1. Only a later poll
        // returns0 and lets the dispatcher continue (4206B0 case65).
        if(displayed[0]==targetX&&displayed[1]==targetY){actorMoving=false;return;}
        int speed;try{speed=read(99,12);}catch(IOException failure){throw new IllegalStateException("SC actor speed read",failure);}
        int distance=motionSkipPressed||speed==3?64:speed==2?32:speed==1?16:8;
        displayed[0]=approach(displayed[0],targetX,distance);displayed[1]=approach(displayed[1],targetY,distance);
        try{scene.publish(ui,0,0);}catch(IOException failure){throw new IllegalStateException("SC actor frame publish",failure);}
    }
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException {
        if(actorMoving)return false;
        return super.signal(expected,signal);
    }
    @Override public boolean beforeTransfer(int mode,String name)throws IOException {
        boolean accepted=super.beforeTransfer(mode,name);
        if(accepted){actorMoving=false;firstActorPoll=false;actorPollPhase=0;serviceGridBound=false;}
        return accepted;
    }

    @Override public boolean command(String operation,int[] args,byte[] bytes)throws IOException {
        if(operation.equals("sc.actor_position")){stageActor(args);return true;}
        if(operation.equals("sc.actor_transform")){transformActor(args);return true;}
        if(operation.equals("pc.character"))return loadActor(args,bytes);
        if(operation.equals("sc.command")){
            if(args==null||args.length!=2)throw new IOException("SC system command requires two bytes");
            if(args[0]==6&&args[1]>=0&&args[1]<=255){saveLoadPage(args[1]);return true;}
            throw new IOException("Unimplemented SC native system service "+Arrays.toString(args));
        }
        if(operation.equals("native.unmodelled")||operation.startsWith("sc.")||operation.equals("pc.command")||operation.equals("pc.actor_position")||operation.equals("pc.actor_transform"))
            throw new IOException("Unimplemented SC native operation "+operation+" "+Arrays.toString(args));
        if(operation.equals("pc.grid.configure")||operation.equals("pc.grid.begin"))serviceGridBound=false;
        return super.command(operation,args,bytes);
    }

    @Override public void save(DataOutput out)throws IOException {
        super.save(out);out.writeInt(SNAPSHOT);out.writeBoolean(serviceGridBound);out.writeBoolean(actorMoving);
        out.writeBoolean(firstActorPoll);out.writeInt(movingActor);out.writeInt(targetX);out.writeInt(targetY);out.writeInt(actorPollPhase);
        for(int[] descriptor:liveActors)for(int value:descriptor)out.writeInt(value);
        out.writeBoolean(savedActorStageValid);for(int[] descriptor:savedActorStage)for(int value:descriptor)out.writeInt(value);
    }
    @Override public void load(DataInput in)throws IOException {
        super.load(in);if(in.readInt()!=SNAPSHOT)throw new IOException("SC host state version");
        boolean bound=in.readBoolean(),moving=in.readBoolean(),first=in.readBoolean();
        int which=in.readInt(),x=in.readInt(),y=in.readInt(),phase=in.readInt();
        if(which<0||which>=4||phase<0||phase>=10||bound&&grid.size()!=5||x<Short.MIN_VALUE||x>Short.MAX_VALUE||y<Short.MIN_VALUE||y>Short.MAX_VALUE)
            throw new IOException("SC saved actor/grid state");
        serviceGridBound=bound;actorMoving=moving;firstActorPoll=first;movingActor=which;targetX=x;targetY=y;actorPollPhase=phase;
        for(int[] descriptor:liveActors)for(int field=0;field<3;field++)descriptor[field]=in.readInt();
        savedActorStageValid=in.readBoolean();for(int[] descriptor:savedActorStage)for(int field=0;field<3;field++)descriptor[field]=in.readInt();
        for(int[][] owner:new int[][][]{liveActors,savedActorStage})for(int[] descriptor:owner)for(int value:descriptor)
            if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IOException("SC saved actor descriptor outside native signed word");
    }
}
