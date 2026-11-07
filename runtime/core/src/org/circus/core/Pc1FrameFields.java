package org.circus.core;

import java.io.*;

/** Immutable reviewed dispatch/log fields inside native1F44. Text buffers and
 *43D410 selection are global/separate owners and are deliberately absent. */
public final class Pc1FrameFields {
    private static final int FORMAT=0x50314432,LEGACY_FORMAT=0x50314431; // P1D2 narrow projection
    private final Pc1TextLog textLog;
    public final int cachedScenarioRead,headY,waitKind,argument0,argument1;
    private Pc1FrameFields(Pc1NativeFrame source)throws IOException{
        if(source==null)throw new IOException("PC1 frame fields require canonical frame");
        textLog=Pc1TextLog.backed(source).copy();cachedScenarioRead=source.readI32(Pc1FrameLayout.CACHED_SCENARIO_READ);headY=source.readI32(Pc1FrameLayout.HEAD_Y);waitKind=source.readI32(Pc1FrameLayout.WAIT_KIND);argument0=source.readI32(Pc1FrameLayout.ARGUMENT0);argument1=source.readI32(Pc1FrameLayout.ARGUMENT1);
    }
    private Pc1FrameFields(DataInput in)throws IOException{
        int format=in.readInt();if(format!=FORMAT&&format!=LEGACY_FORMAT)throw new IOException("PC1 frame fields format");textLog=Pc1TextLog.load(in);cachedScenarioRead=in.readInt();headY=in.readInt();waitKind=in.readInt();argument0=in.readInt();argument1=in.readInt();
    }
    public static Pc1FrameFields capture(PcHost host)throws IOException{return capture(runtime(host).nativeFrame());}
    public static Pc1FrameFields capture(Pc1NativeFrame frame)throws IOException{return new Pc1FrameFields(frame);}
    /** Pure subset projection from the native image. Does not apply a host,
     *perform41A620 reconstruction, resolve a caller or replace saved rawPC with
     *checkpoint-derived resumePC. The raw slot retains all original bytes even
     *This narrower projection retains every raw32 checkpoint/count/cache bit,
     *but grants authority only over the supplied1000 reserved log words.
     *In-frame saved arguments survive native1D prewrites and are copied as-is. */
    public static Pc1FrameFields fromNativeSlot(Pc1NativeSlot slot)throws IOException{
        if(slot==null)throw new IOException("PC1 frame fields require a native slot");
        return capture(Pc1NativeFrame.fromSlot(slot));
    }
    public int currentCheckpoint(){return textLog.currentCheckpoint();}
    public Pc1TextLog textLog(){return textLog.copy();}
    public int cachedScenarioRead(){return cachedScenarioRead;}
    public int headY(){return headY;}
    public int waitKind(){return waitKind;}
    public int argument0(){return argument0;}
    public int argument1(){return argument1;}
    /** Replaces only the owned fields, retaining the existing final log object. */
    public void apply(PcHost host)throws IOException{
        apply(runtime(host).nativeFrame());
    }
    /** This is deliberately a subset copy. Even if log count points into
     *ordinary memory, those neighboring bytes are not part of this projection. */
    public void apply(Pc1NativeFrame target)throws IOException{
        if(target==null)throw new IOException("PC1 frame fields require canonical frame");
        Pc1TextLog.backed(target).copyFrom(textLog);target.writeI32(Pc1FrameLayout.CACHED_SCENARIO_READ,cachedScenarioRead);target.writeI32(Pc1FrameLayout.HEAD_Y,headY);target.writeI32(Pc1FrameLayout.WAIT_KIND,waitKind);target.writeI32(Pc1FrameLayout.ARGUMENT0,argument0);target.writeI32(Pc1FrameLayout.ARGUMENT1,argument1);
    }
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);textLog.save(out);out.writeInt(cachedScenarioRead);out.writeInt(headY);out.writeInt(waitKind);out.writeInt(argument0);out.writeInt(argument1);}
    public static Pc1FrameFields load(DataInput in)throws IOException{return new Pc1FrameFields(in);}
    private static Pc1Runtime runtime(PcHost host)throws IOException{if(host==null||!host.resources.profile.id.equals("dcpc-2063")||host.pc1==null)throw new IOException("PC1 frame fields require dcpc-2063");return host.pc1;}
}
