package org.circus.core;

import java.io.*;

/** DC2PC 5769 actor descriptors: staged 64/65, load41BCE0, draw41A950,
 * and integer wait420C10. SC and FL bind their own actor consumers. */
public final class Pc2Host extends PcHost {
    private static final int SNAPSHOT=0x50324131;
    private final int[][] live=new int[4][3],systemStage=new int[4][3];
    private boolean systemStageValid,actorMoving,firstActorPoll;
    private int movingActor,targetX,targetY,pollPhase;

    public Pc2Host(CircusResources resources,TextCodec codec){this(resources,codec,(int)(System.nanoTime()/1000000L));}
    public Pc2Host(CircusResources resources,TextCodec codec,int startupTick){
        super(resources,codec,startupTick);
        if(!resources.profile.id.equals("dc2pc-5769")||!second||pc1!=null)
            throw new IllegalArgumentException("PC2 actor host requires dc2pc-5769");
        scene.bindActorProjection(this::projectLive);
    }
    private static void actor(int value)throws IOException {
        // The fifth descriptor has its own centred renderer. No64/65 selector4
        // appears in the reviewed PC2 corpus; reject instead of aliasing it.
        if(value<0||value>=4)throw new IOException("Unimplemented PC2 actor descriptor "+value);
    }
    private static void words(int[] args)throws IOException {
        if(args==null||args.length!=4)throw new IOException("PC2 actor operation requires four signed words");
        for(int value:args)if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IOException("PC2 actor argument outside signed native word");
    }
    /** A generic publish must not commit64's stage or undo65's live motion.
     * Native draw41A950 reads live XYZ and the current staged pivot fields. */
    private void projectLive(Dc2Scene.Frame frame){
        for(int n=0;n<4;n++){
            System.arraycopy(live[n],0,frame.actors[n],0,3);
            for(int field=3;field<6;field++)frame.actors[n][field]=scene.character(n,field);
        }
    }
    private void stageActor(int[] args)throws IOException {
        words(args);int selector=args[2];
        if(selector==255){String name=scene.slots[2];scene.slots[2]=scene.slots[3];scene.slots[3]=name;return;}
        actor(selector);scene.character(selector,0,args[0]);scene.character(selector,1,args[1]);scene.character(selector,2,100);
    }
    private void transformActor(int[] args)throws IOException {
        words(args);int selector=args[3];
        if(selector==255){if(args[0]!=-1)scene.character(0,2,args[0]);if(args[2]!=-1)scene.character(1,2,args[2]);return;}
        if(selector>=16&&selector<20){int n=selector-16;scene.character(n,2,args[0]);scene.character(n,3,args[1]);scene.character(n,4,args[2]);return;}
        actor(selector);movingActor=selector;targetX=args[0];targetY=args[1];pollPhase=0;firstActorPoll=true;actorMoving=true;
        // The third word is unused in this branch, including the live zoom.
        remaining=0;presentationWaiting=textWaiting=false;ui.waiting=false;nextTicket();
    }
    private boolean loadActor(int[] args,byte[] bytes)throws IOException {
        if(args==null||args.length<1)throw new IOException("PC2 actor load requires a descriptor");
        int n=args[0];actor(n);String name=new String(bytes,Binary.SJIS);
        if(name.equals("--")){
            if(systemScene==null)throw new IOException("PC2 actor restore has no saved system scene");
            scene.slots[2+n]=systemScene.slots[2+n];
            scene.characterPrefix(n,systemStageValid?systemStage[n]:systemScene.actors[n],3);
        }
        if(scene.character(n,2)==0)scene.character(n,2,100);
        boolean result=name.equals("--")||super.command("pc.character",args,bytes);
        if(!name.isEmpty())for(int field=0;field<3;field++)live[n][field]=scene.character(n,field);
        return result;
    }
    @Override public void enterSystem()throws IOException {
        for(int n=0;n<4;n++)for(int field=0;field<3;field++)systemStage[n][field]=scene.character(n,field);
        systemStageValid=true;super.enterSystem();
    }
    @Override public boolean command(String operation,int[] args,byte[] bytes)throws IOException {
        if(operation.equals("pc.actor_position")){stageActor(args);return true;}
        if(operation.equals("pc.actor_transform")){transformActor(args);return true;}
        if(operation.equals("pc.character")||operation.equals("pc.character_pair"))return loadActor(args,bytes);
        return super.command(operation,args,bytes);
    }
    @Override public boolean waiting(){return actorMoving||super.waiting();}
    public boolean actorMotionActive(){return actorMoving;}
    private static int approach(int value,int target,int distance){
        if(value<target)return (int)Math.min((long)target,(long)value+distance);
        if(value>target)return (int)Math.max((long)target,(long)value-distance);
        return value;
    }
    @Override public void elapse(long millis){
        super.elapse(millis);if(!actorMoving)return;
        long delta=Math.max(0,millis);boolean due=firstActorPoll||delta>=10||pollPhase+delta%10>=10;
        pollPhase=(int)((pollPhase+delta%10)%10);if(!due)return;firstActorPoll=false;
        if(live[movingActor][0]==targetX&&live[movingActor][1]==targetY){actorMoving=false;return;}
        int speed,rapidGate;try{speed=read(99,12);rapidGate=read(99,25);}catch(IOException failure){throw new IllegalStateException("PC2 actor speed",failure);}
        int distance=motionSkipPressed&&rapidGate==0||speed==3?64:speed==2?32:speed==1?16:8;
        live[movingActor][0]=approach(live[movingActor][0],targetX,distance);
        live[movingActor][1]=approach(live[movingActor][1],targetY,distance);
        try{scene.publish(ui,0,0);}catch(IOException failure){throw new IllegalStateException("PC2 actor redraw",failure);}
    }
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException {
        return !actorMoving&&super.signal(expected,signal);
    }
    @Override public boolean beforeTransfer(int mode,String name)throws IOException {
        boolean accepted=super.beforeTransfer(mode,name);
        if(accepted){actorMoving=false;firstActorPoll=false;pollPhase=0;}
        return accepted;
    }
    @Override public void save(DataOutput out)throws IOException {
        super.save(out);out.writeInt(SNAPSHOT);out.writeBoolean(systemStageValid);
        for(int[] actor:live)for(int value:actor)out.writeInt(value);
        for(int[] actor:systemStage)for(int value:actor)out.writeInt(value);
        out.writeBoolean(actorMoving);out.writeBoolean(firstActorPoll);out.writeInt(movingActor);out.writeInt(targetX);out.writeInt(targetY);out.writeInt(pollPhase);
    }
    @Override public void load(DataInput in)throws IOException {
        super.load(in);if(in.readInt()!=SNAPSHOT)throw new IOException("PC2 actor state version");
        systemStageValid=in.readBoolean();
        for(int[] actor:live)for(int field=0;field<3;field++)actor[field]=in.readInt();
        for(int[] actor:systemStage)for(int field=0;field<3;field++)actor[field]=in.readInt();
        actorMoving=in.readBoolean();firstActorPoll=in.readBoolean();movingActor=in.readInt();targetX=in.readInt();targetY=in.readInt();pollPhase=in.readInt();
        if(movingActor<0||movingActor>=4||pollPhase<0||pollPhase>=10||targetX<Short.MIN_VALUE||targetX>Short.MAX_VALUE||targetY<Short.MIN_VALUE||targetY>Short.MAX_VALUE)
            throw new IOException("PC2 saved actor motion");
        for(int[][] owner:new int[][][]{live,systemStage})for(int[] actor:owner)for(int value:actor)
            if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IOException("PC2 saved actor descriptor");
    }
}
