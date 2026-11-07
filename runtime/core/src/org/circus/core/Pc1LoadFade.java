package org.circus.core;

import java.io.*;

/** Current CHS419D30/419E40, not the separate41A0B0 bitmap transition.
 * The host owns native globals, logical tick epoch and captured image resource.
 * This continuation owns only the original function's local phase/tick/handle. */
public final class Pc1LoadFade {
    private static final int FORMAT=0x50314531; // P1E1
    public static final int FRAME_MILLIS=40;
    public enum Direction {
        IN("fadein()"),OUT("fadeout()");
        public final String captureName;
        Direction(String name){captureName=name;}
    }
    public enum Phase { READY,PUMP,COMPLETE,INTERRUPTED,FAILED }
    public interface Host {
        /** Alias of frame9DA4C4, the dispatcher argument1. Never a second counter. */
        int argument1();
        void argument1(int value);
        /**454CF0. Native3 bypasses4118B0; all other values query it, then
         * reread speed. Values other than1/2/3 take the increment1 branch. */
        int speed();
        /**4118B0: Ctrl high bit or rapid latchBB842C, not a generic advance input. */
        boolean skipRequested();
        void fadeFlag(int value); // BB8434
        void loopFlag(int value); // 94C698
        void schedulerDelay(int milliseconds); // 430284
        /** GetTickCount raw DWORD in a host-owned, restorable logical epoch. */
        int tickCount();
        /**401EE0 clone of the current front buffer. The opaque native-style
         * token may be-1; no success test exists in either original function. */
        int capture(Direction direction)throws IOException;
        /**408880 absolute capturedRGB scaling, not alpha-over interpolation.
         * Normally weight0..16; externally changed argument1 has no lower clamp. */
        void draw(int capturedHandle,int weight)throws IOException;
        void present()throws IOException; //407000
        /** One COMPLETE405710(0) drain: PeekMessage/Translate/Dispatch until
         * empty, then return94C688. Callbacks may alter native globals. */
        int pumpMessages()throws IOException;
        void release(int capturedHandle)throws IOException; //401130
    }
    private final Direction direction;
    private Phase phase=Phase.READY;
    private int capturedHandle,frameStartTick;
    private boolean busy;
    private Pc1LoadFade(Direction direction){if(direction==null)throw new IllegalArgumentException("PC1 load fade direction");this.direction=direction;}
    /** Start is atomic to snapshot users. Native IN and OUT write the two
     * flags in opposite orders, then reset the argument alias before capture. */
    public static Pc1LoadFade begin(Direction direction,Host host)throws IOException{
        if(host==null)throw new IllegalArgumentException("PC1 load fade host");Pc1LoadFade result=new Pc1LoadFade(direction);result.busy=true;
        try{
            if(direction==Direction.IN){host.fadeFlag(1);host.loopFlag(1);}else{host.loopFlag(1);host.fadeFlag(1);}
            host.argument1(0);result.capturedHandle=host.capture(direction);
            // The initial signed comparison follows capture, which is allowed
            // to return with a changed global argument value.
            if(host.argument1()>=16)result.finish(host);
            return result;
        }catch(IOException|RuntimeException failure){result.phase=Phase.FAILED;throw failure;}
        finally{result.busy=false;}
    }
    public Direction direction(){return direction;}
    public Phase phase(){return phase;}
    public boolean active(){return phase==Phase.READY||phase==Phase.PUMP;}
    public int capturedHandle(){return capturedHandle;}
    /** INTERRUPTED retains the resource; native early return never releases it. */
    public boolean releaseWasCalled(){return phase==Phase.COMPLETE;}
    public int nativeResult(){if(phase==Phase.COMPLETE)return 0;if(phase==Phase.INTERRUPTED)return 1;throw new IllegalStateException("PC1 load fade has no completed native result");}
    /** Performs at most one405710 call. A READY continuation first submits
     * draw+present, including the final weight, before its first pump. A PUMP
     * continuation pumps before reading time even when the caller is late.
     * No elapsed-time credit is carried into a subsequent frame. */
    public Phase step(Host host)throws IOException{
        if(host==null)throw new IllegalArgumentException("PC1 load fade host");if(busy)throw new IOException("PC1 load fade reentry");if(phase==Phase.FAILED)throw new IOException("PC1 failed load fade cannot resume");if(!active())return phase;
        busy=true;
        try{
            if(phase==Phase.READY){
                frameStartTick=host.tickCount();int initialSpeed=host.speed();host.schedulerDelay(FRAME_MILLIS);
                int increment;
                if(initialSpeed==3||host.skipRequested())increment=8;
                else{int currentSpeed=host.speed();increment=currentSpeed==2?4:currentSpeed==1?2:1;}
                host.argument1(host.argument1()+increment); // int32 ADD wraps as x86.
                if(host.argument1()>16)host.argument1(16); // signed upper clamp only.
                int progress=host.argument1();host.draw(capturedHandle,direction==Direction.IN?progress:16-progress);host.present();phase=Phase.PUMP;
            }
            if(host.pumpMessages()!=0){phase=Phase.INTERRUPTED;return phase;}
            long elapsed=(host.tickCount()-frameStartTick)&0xffffffffL;
            if(elapsed>=FRAME_MILLIS){if(host.argument1()>=16)finish(host);else phase=Phase.READY;}
            return phase;
        }catch(IOException|RuntimeException failure){phase=Phase.FAILED;throw failure;}
        finally{busy=false;}
    }
    private void finish(Host host)throws IOException{
        host.release(capturedHandle);host.loopFlag(0);if(direction==Direction.IN)host.fadeFlag(0);phase=Phase.COMPLETE;
    }
    private void snapshotBoundary()throws IOException{if(busy)throw new IOException("PC1 load fade snapshot during callback");if(phase==Phase.FAILED)throw new IOException("PC1 failed load fade has no resumable snapshot");}
    public Pc1LoadFade copy()throws IOException{snapshotBoundary();Pc1LoadFade copy=new Pc1LoadFade(direction);copy.phase=phase;copy.capturedHandle=capturedHandle;copy.frameStartTick=frameStartTick;return copy;}
    /** P1E1 is14bytes. External globals/capture resource are NOT copied here.
     * Save them in the enclosing load owner, with the same logical tick epoch.
     * Only boundaries after begin/step return are serializable: snapshots
     * from a draw/present/pump callback would duplicate partial side effects. */
    public void save(DataOutput out)throws IOException{snapshotBoundary();out.writeInt(FORMAT);out.writeByte(direction==Direction.IN?0:1);out.writeByte(phase==Phase.READY?1:phase==Phase.PUMP?2:phase==Phase.COMPLETE?3:4);out.writeInt(capturedHandle);out.writeInt(frameStartTick);}
    public static Pc1LoadFade load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 load fade format");int direction=in.readUnsignedByte(),phase=in.readUnsignedByte();if(direction>1||phase<1||phase>4)throw new IOException("PC1 load fade saved phase");
        Pc1LoadFade result=new Pc1LoadFade(direction==0?Direction.IN:Direction.OUT);result.phase=phase==1?Phase.READY:phase==2?Phase.PUMP:phase==3?Phase.COMPLETE:Phase.INTERRUPTED;result.capturedHandle=in.readInt();result.frameStartTick=in.readInt();return result;
    }
}
