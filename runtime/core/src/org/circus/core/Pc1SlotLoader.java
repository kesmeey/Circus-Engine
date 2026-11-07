package org.circus.core;

import java.io.*;

/** Current CHS41A620, including its two419E40/419D30 continuations.
 * This is reconstruction orchestration, not a platform renderer/audio backend.
 * Every external native service is a required callback; an unsupported owner
 * must throw rather than quietly treating a frame copy as a complete load. */
public final class Pc1SlotLoader {
    private static final int FORMAT=0x5031534c,VERSION=1; // P1SL
    public enum Phase { OUT,IN,COMPLETE,INTERRUPTED,FAILED }
    public enum Geometry { BODY,NAME }
    /** Only globals directly used by41A620. They are external to1F44. */
    public enum Global {
        BGM_FADE_ACTIVE(0xbc1d1c),SELECTED_HISTORY(0x43d410),HOVERED_BUTTON(0x94fa28),
        CANDIDATE_FLAG(0xbb8418),DRAW_TARGET(0x43d418),ACTOR_ORIGIN_X(0xbb8404),ACTOR_ORIGIN_Y(0xbb8408),
        ACTOR1_IMAGE(0x9b9be4),ACTOR1_MASK(0x97176c),ACTOR1_OVERLAY(0x971770),
        ACTOR2_IMAGE(0x9b9be8),ACTOR2_MASK(0x9a0778),ACTOR2_OVERLAY(0x971768),MUSIC_RESTART_LATCH(0xbb8438);
        public final int nativeAddress;
        Global(int address){nativeAddress=address;}
    }
    /** The read service has already copied the admitted working image into
     * suspended.41A620 deliberately copies it BACK only after releasing old
     * graphics, whose411080 effects may have mutated live frame bytes. */
    public static final class Owners {
        public final Pc1NativeFrame live,working,suspended,candidate;
        public Owners(Pc1NativeFrame live,Pc1NativeFrame working,Pc1NativeFrame suspended,Pc1NativeFrame candidate)throws IOException{
            if(live==null||working==null||suspended==null||candidate==null||live==working||live==suspended||live==candidate||working==suspended||working==candidate||suspended==candidate)throw new IOException("PC1 slot reconstruction requires four distinct native frame owners");
            this.live=live;this.working=working;this.suspended=suspended;this.candidate=candidate;
        }
    }
    public interface Host {
        /**411080(-1,0): six graphics, masks/overlays and its native resets. */
        void releaseGraphics(int slot,int preserveOverlayName)throws IOException;
        /**41CBE0: channel2 stop plus41CCD0 channels3..9 stop/release. */
        void stopVoices()throws IOException;
        /** Read/write these actual external globals, with no simulated pump. */
        int global(Global field)throws IOException;
        void global(Global field,int value)throws IOException;
        void stopMusic(int mode)throws IOException; //41C650(0)
        void ensureAudioTimer()throws IOException; //4070E0
        /** Actual411670(name,1), including its loader effects and code/header
         * ownership. Native ignores its return: supply the currently active
         * code/header view even if a native failed open left the prior one.
         * No frozen source-file decoder may stand in for the loaded buffer. */
        MesScript loadScript(byte[] nativeName,int mode)throws IOException;
        /**4111A0: required mask/overlay derivation and handle/name updates,
         * not just an archive read. Return values are ignored by41A570. */
        void loadGraphic(int slot,byte[] nativeName)throws IOException;
        /**415D50 /416220, exactly(0,0,0,0,2); native global templates remain
         * live. The BODY callback may update the restored frame headY. */
        void prepareGeometry(Geometry kind,int x,int y,int width,int height,int mode)throws IOException;
        void restoreControls(int draw)throws IOException; //412260(0)
        void bakePending()throws IOException; //411D30, all native pending records
        void copyImage(int x,int y,int sourceHandle)throws IOException; //407880
        void compositeActor(int x,int y,int imageHandle,int maskHandle)throws IOException; //4085A0
        void overlayActor(int x,int y,int overlayHandle)throws IOException; //407EE0
        void playMusic(int track,int mode)throws IOException; //41C4B0(track,0)
        void playSound(int sound,int loop)throws IOException; //41C850(sound,1)
        void redrawText(int selected)throws IOException; //418220(-1), raw log/current vars
        void activateText()throws IOException; //412320, native region/toolbar owner
        void ensureMainTimer()throws IOException; //407040
        void startEffect(int kind,int strength)throws IOException; //40F560
        /** Opaque captures, tick epoch, flags, pump and rendering remain host
         * owned. The returned service's argument1 methods are deliberately
         * not used: this helper binds that alias directly to Owners.live.C4.
         * Enclosing snapshots must retain the capture and all external state. */
        Pc1LoadFade.Host fadeHost()throws IOException;
    }
    private Phase phase=Phase.OUT;
    private Pc1LoadFade fade;
    private boolean serviceBranch,busy;
    private Pc1SlotLoader(){}
    public static Pc1SlotLoader begin(Owners owners,Host host)throws IOException{
        require(owners,host);Pc1SlotLoader result=new Pc1SlotLoader();result.busy=true;
        try{
            host.releaseGraphics(-1,0);owners.working.copyFrom(owners.suspended);host.stopVoices();
            if(host.global(Global.BGM_FADE_ACTIVE)==0){host.stopMusic(0);host.ensureAudioTimer();}
            result.fade=Pc1LoadFade.begin(Pc1LoadFade.Direction.OUT,fadeHost(owners,host));return result;
        }catch(IOException|RuntimeException failure){result.phase=Phase.FAILED;throw failure;}
        finally{result.busy=false;}
    }
    public Phase phase(){return phase;}
    public boolean active(){return phase==Phase.OUT||phase==Phase.IN;}
    public boolean complete(){return phase==Phase.COMPLETE;}
    /** Native41A620 returns0 after a fade interrupt as well as completion. */
    public int nativeResult(){if(phase==Phase.COMPLETE||phase==Phase.INTERRUPTED)return 0;throw new IllegalStateException("PC1 slot reconstruction has no native result");}
    public Pc1LoadFade.Direction fadeDirection(){return fade.direction();}
    public Pc1LoadFade.Phase fadePhase(){return fade.phase();}
    public int capturedHandle(){return fade.capturedHandle();}
    /** At most one original fade pump per step. Completing OUT executes the
     * ordered middle block and begins IN, without also pumping IN. A late
     * tick therefore cannot consume multiple native fade iterations. */
    public Phase step(Owners owners,Host host)throws IOException{
        require(owners,host);if(busy)throw new IOException("PC1 slot reconstruction reentry");if(phase==Phase.FAILED)throw new IOException("PC1 failed slot reconstruction cannot resume");if(!active())return phase;
        busy=true;
        try{
            Pc1LoadFade.Phase next=fade.active()?fade.step(fadeHost(owners,host)):fade.phase();
            if(next==Pc1LoadFade.Phase.INTERRUPTED){phase=Phase.INTERRUPTED;return phase;}
            if(next!=Pc1LoadFade.Phase.COMPLETE)return phase;
            if(phase==Phase.OUT){middle(owners,host);fade=Pc1LoadFade.begin(Pc1LoadFade.Direction.IN,fadeHost(owners,host));phase=Phase.IN;}
            else{finish(owners,host);phase=Phase.COMPLETE;}
            return phase;
        }catch(IOException|RuntimeException failure){phase=Phase.FAILED;throw failure;}
        finally{busy=false;}
    }
    private void middle(Owners o,Host h)throws IOException{
        MesScript caller=h.loadScript(o.working.rawCString(Pc1FrameLayout.SCRIPT),1);
        o.live.writeCString(Pc1FrameLayout.OVERLAY1,o.working.rawCString(Pc1FrameLayout.OVERLAY1));
        o.live.writeCString(Pc1FrameLayout.OVERLAY2,o.working.rawCString(Pc1FrameLayout.OVERLAY2));
        int[] names={Pc1FrameLayout.BACKGROUND,Pc1FrameLayout.ACTOR1,Pc1FrameLayout.ACTOR2,Pc1FrameLayout.RESOURCE3,Pc1FrameLayout.RESOURCE4,Pc1FrameLayout.RESOURCE5};
        for(int slot=0;slot<names.length;slot++)if(o.working.readU8(names[slot])!=0)h.loadGraphic(slot,o.working.rawCString(names[slot]));
        h.global(Global.SELECTED_HISTORY,-1);h.global(Global.HOVERED_BUTTON,-1);
        o.live.dword(Pc1NativeSlot.Dword.ARGUMENT0,0);o.live.dword(Pc1NativeSlot.Dword.ARGUMENT1,0);h.global(Global.CANDIDATE_FLAG,0);
        Pc1SlotProjection p=Pc1SlotProjection.project(caller,o.working.dword(Pc1NativeSlot.Dword.CURRENT_CHECKPOINT),new Pc1SlotProjection.SavedFields(o.working.dword(Pc1NativeSlot.Dword.RAW_CODE_PC),o.working.dword(Pc1NativeSlot.Dword.ARGUMENT0),o.working.dword(Pc1NativeSlot.Dword.ARGUMENT1),o.working.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC)),o.live.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC));
        if(p.refreshCandidate){h.global(Global.CANDIDATE_FLAG,p.candidateFlag);o.candidate.copyFrom(o.working);}
        else if(p.headerOpcode==0x1d){o.live.dword(Pc1NativeSlot.Dword.ARGUMENT0,p.peekOperand0);o.live.dword(Pc1NativeSlot.Dword.ARGUMENT1,p.peekOperand1);}
        // EBX/EDX/EAX survive REP MOVSD. Saved C0/C4 do not: the full copy
        // restores them, replacing the temporary checkpoint-header operands.
        int oldMusic=o.live.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC);o.live.copyFrom(o.working);
        o.live.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC,oldMusic);o.live.dword(Pc1NativeSlot.Dword.RAW_CODE_PC,p.resumeCodePc);o.live.dword(Pc1NativeSlot.Dword.WAIT_KIND,p.wait.nativeValue);
        h.prepareGeometry(Geometry.BODY,0,0,0,0,2);h.prepareGeometry(Geometry.NAME,0,0,0,0,2);
        serviceBranch=o.live.dword(Pc1NativeSlot.Dword.WAIT_KIND)==0x1d;
        if(!serviceBranch){
            h.restoreControls(0);h.bakePending();
            h.copyImage(o.live.dword(Pc1NativeSlot.Dword.BACKGROUND_X),o.live.dword(Pc1NativeSlot.Dword.BACKGROUND_Y),h.global(Global.DRAW_TARGET));
            h.compositeActor(h.global(Global.ACTOR_ORIGIN_X)+o.live.dword(Pc1NativeSlot.Dword.ACTOR1_X),h.global(Global.ACTOR_ORIGIN_Y)+o.live.dword(Pc1NativeSlot.Dword.ACTOR1_Y),h.global(Global.ACTOR1_IMAGE),h.global(Global.ACTOR1_MASK));
            h.overlayActor(h.global(Global.ACTOR_ORIGIN_X)+o.live.dword(Pc1NativeSlot.Dword.ACTOR1_X),h.global(Global.ACTOR_ORIGIN_Y)+o.live.dword(Pc1NativeSlot.Dword.ACTOR1_Y),h.global(Global.ACTOR1_OVERLAY));
            h.compositeActor(h.global(Global.ACTOR_ORIGIN_X)+o.live.dword(Pc1NativeSlot.Dword.ACTOR2_X),h.global(Global.ACTOR_ORIGIN_Y)+o.live.dword(Pc1NativeSlot.Dword.ACTOR2_Y),h.global(Global.ACTOR2_IMAGE),h.global(Global.ACTOR2_MASK));
            h.overlayActor(h.global(Global.ACTOR_ORIGIN_X)+o.live.dword(Pc1NativeSlot.Dword.ACTOR2_X),h.global(Global.ACTOR_ORIGIN_Y)+o.live.dword(Pc1NativeSlot.Dword.ACTOR2_Y),h.global(Global.ACTOR2_OVERLAY));
        }
    }
    private void finish(Owners o,Host h)throws IOException{
        int music=o.working.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC);
        if(music!=-1){h.global(Global.MUSIC_RESTART_LATCH,0);h.playMusic(music,0);h.ensureAudioTimer();}
        int sound=o.working.dword(Pc1NativeSlot.Dword.PACKED_SOUND);if((sound&0x100)!=0)h.playSound(sound&255,1);
        if(!serviceBranch&&o.live.dword(Pc1NativeSlot.Dword.WAIT_KIND)==7){h.redrawText(-1);h.activateText();}
        o.live.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC,o.working.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC));h.ensureMainTimer();
        int kind=o.live.dword(Pc1NativeSlot.Dword.EFFECT_KIND);if(kind!=0)h.startEffect(kind,o.live.dword(Pc1NativeSlot.Dword.EFFECT_STRENGTH));
    }
    private static Pc1LoadFade.Host fadeHost(final Owners o,Host h)throws IOException{
        final Pc1LoadFade.Host io=h.fadeHost();if(io==null)throw new IOException("PC1 slot reconstruction requires the complete native fade platform service");
        return new Pc1LoadFade.Host(){
            public int argument1(){return o.live.dword(Pc1NativeSlot.Dword.ARGUMENT1);}public void argument1(int value){o.live.dword(Pc1NativeSlot.Dword.ARGUMENT1,value);}
            public int speed(){return io.speed();}public boolean skipRequested(){return io.skipRequested();}public void fadeFlag(int value){io.fadeFlag(value);}public void loopFlag(int value){io.loopFlag(value);}public void schedulerDelay(int value){io.schedulerDelay(value);}public int tickCount(){return io.tickCount();}
            public int capture(Pc1LoadFade.Direction direction)throws IOException{return io.capture(direction);}public void draw(int handle,int weight)throws IOException{io.draw(handle,weight);}public void present()throws IOException{io.present();}public int pumpMessages()throws IOException{return io.pumpMessages();}public void release(int handle)throws IOException{io.release(handle);}
        };
    }
    private static void require(Owners owners,Host host)throws IOException{if(owners==null||host==null)throw new IOException("PC1 slot reconstruction requires owners and complete native services");}
    private void snapshotBoundary()throws IOException{if(busy)throw new IOException("PC1 slot reconstruction snapshot during callback");if(phase==Phase.FAILED)throw new IOException("PC1 failed slot reconstruction has no resumable snapshot");}
    public Pc1SlotLoader copy()throws IOException{snapshotBoundary();Pc1SlotLoader result=new Pc1SlotLoader();result.phase=phase;result.serviceBranch=serviceBranch;result.fade=fade.copy();return result;}
    /** Only local continuation state. Store all four frame owners, active
     * code/header owner, graphics/captures, globals, timers and fade epoch in
     * the same enclosing snapshot. Callbacks themselves are atomic boundaries. */
    public void save(DataOutput out)throws IOException{snapshotBoundary();out.writeInt(FORMAT);out.writeByte(VERSION);out.writeByte(phase==Phase.OUT?0:phase==Phase.IN?1:phase==Phase.COMPLETE?2:3);out.writeBoolean(serviceBranch);fade.save(out);}
    public static Pc1SlotLoader load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT||in.readUnsignedByte()!=VERSION)throw new IOException("PC1 slot reconstruction state format");int phase=in.readUnsignedByte(),branch=in.readUnsignedByte();if(phase>3||branch>1)throw new IOException("PC1 slot reconstruction saved phase");
        Pc1SlotLoader result=new Pc1SlotLoader();result.phase=phase==0?Phase.OUT:phase==1?Phase.IN:phase==2?Phase.COMPLETE:Phase.INTERRUPTED;result.serviceBranch=branch==1;result.fade=Pc1LoadFade.load(in);
        boolean valid=phase==0?branch==0&&result.fade.direction()==Pc1LoadFade.Direction.OUT&&(result.fade.active()||result.fade.phase()==Pc1LoadFade.Phase.COMPLETE):phase==1?result.fade.direction()==Pc1LoadFade.Direction.IN&&(result.fade.active()||result.fade.phase()==Pc1LoadFade.Phase.COMPLETE):phase==2?result.fade.direction()==Pc1LoadFade.Direction.IN&&result.fade.phase()==Pc1LoadFade.Phase.COMPLETE:result.fade.phase()==Pc1LoadFade.Phase.INTERRUPTED&&(result.fade.direction()!=Pc1LoadFade.Direction.OUT||branch==0);
        if(!valid)throw new IOException("PC1 slot reconstruction/fade phase mismatch");return result;
    }
}
