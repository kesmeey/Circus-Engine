package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** PC1 CN41A0B0/4079A0/407880 return transfer, using opaqueRGB24 captures.
 * The native temporary handle and surviving front pixels have separate lives.
 * Caller retains this owner after commit until a reviewed full-frame paint. */
public final class Pc1ReturnSurface {
    private static final int FORMAT=0x50315531,ACTIVE=0,COMMITTED=1,INTERRUPTED=2;
    private final UiCapture fromMenu,toCaller;
    private final int width,height;
    private final int[] target;
    private int[] front;
    private int phase,progress,elapsed,frames,ratio;
    private UiCapture image;
    private Pc1ReturnSurface(UiCapture from,UiCapture to)throws IOException{
        UiCapture.Pixels a=opaque(from),b=opaque(to);if(a.width!=b.width||a.height!=b.height)throw new IOException("PC1 return capture dimensions differ");
        fromMenu=from;toCaller=to;width=a.width;height=a.height;front=a.argb;target=b.argb;
    }
    /** The first feedback frame is immediate; its40ms interval starts now. */
    public static Pc1ReturnSurface begin(UiCapture fromMenu,UiCapture toCaller,int speed,boolean ctrl)throws IOException{
        Pc1ReturnSurface result=new Pc1ReturnSurface(fromMenu,toCaller);result.feedback(speed,ctrl);return result;
    }
    private static UiCapture.Pixels opaque(UiCapture value)throws IOException{
        if(value==null||value.kind!=UiCapture.ARGB)throw new IOException("PC1 return surface requires a raster capture");UiCapture.Pixels pixels=value.pixels();for(int pixel:pixels.argb)if((pixel>>>24)!=255)throw new IOException("PC1 return surface requires opaque nativeRGB24 pixels");return pixels;
    }
    private void feedback(int speed,boolean ctrl){
        int increment;if(speed==3||ctrl){increment=8;ratio=10;}else if(speed==2){increment=4;ratio=8;}else if(speed==1){increment=2;ratio=6;}else{increment=1;ratio=3;}
        progress=Math.min(16,progress+increment);frames++;
        for(int i=0;i<front.length;i++)front[i]=0xff000000|Pc1Raster.blendRgb16(front[i],target[i],ratio);
        elapsed=0;image=null;
    }
    /** Consume time only while the native transfer is active. A progress16
     *feedback frame still lasts40ms before407880's exact final copy. The caller
     *owns any leftover time and actual Win32/message-pump event scheduling. */
    public long elapse(long milliseconds,int speed,boolean ctrl){
        long left=Math.max(0,milliseconds),consumed=0;
        while(active()&&left>0){int step=(int)Math.min(left,40-elapsed);elapsed+=step;left-=step;consumed+=step;
            if(elapsed==40){if(progress==16){front=target.clone();phase=COMMITTED;elapsed=0;image=toCaller;}else feedback(speed,ctrl);}}
        return consumed;
    }
    /** Native405710 interruption returns1 before clearing flags or retiring the
     *saved capture. It cannot be interpreted as a successful transfer. */
    public void interrupt(){if(active())phase=INTERRUPTED;}
    public boolean active(){return phase==ACTIVE;}
    public boolean committed(){return phase==COMMITTED;}
    public boolean interrupted(){return phase==INTERRUPTED;}
    public boolean flagsHeld(){return phase!=COMMITTED;}
    public boolean captureRetired(){return committed();}
    public int progress(){return progress;}
    public int phaseElapsed(){return elapsed;}
    public int feedbackFrames(){return frames;}
    public int lastRatio(){return ratio;}
    public UiCapture from(){return fromMenu;}
    public UiCapture target(){return toCaller;}
    /** Defensive real-time raster access; avoids a zlib roundtrip per frame. */
    public UiCapture.Pixels frontPixels(){UiCapture.Pixels result=new UiCapture.Pixels(width,height);System.arraycopy(front,0,result.argb,0,front.length);return result;}
    public UiCapture image()throws IOException{if(image==null)image=UiCapture.pixels(width,height,front);return image;}
    public void save(DataOutput out)throws IOException{
        out.writeInt(FORMAT);for(int n:new int[]{phase,progress,elapsed,frames,ratio})out.writeInt(n);UiCapture.save(out,fromMenu);UiCapture.save(out,toCaller);UiCapture.save(out,image());
    }
    public static Pc1ReturnSurface load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 return surface format");int phase=in.readInt(),progress=in.readInt(),elapsed=in.readInt(),frames=in.readInt(),ratio=in.readInt();
        if(phase<ACTIVE||phase>INTERRUPTED||progress<1||progress>16||elapsed<0||elapsed>=40||frames<1||frames>16||frames>progress||progress>frames*8||(ratio!=3&&ratio!=6&&ratio!=8&&ratio!=10)||phase==COMMITTED&&(progress!=16||elapsed!=0))throw new IOException("PC1 return surface phase");
        UiCapture from=UiCapture.load(in),to=UiCapture.load(in),current=UiCapture.load(in);Pc1ReturnSurface result=new Pc1ReturnSurface(from,to);UiCapture.Pixels pixels=opaque(current);if(pixels.width!=result.width||pixels.height!=result.height)throw new IOException("PC1 return front dimensions differ");if(phase==COMMITTED&&!Arrays.equals(pixels.argb,result.target))throw new IOException("PC1 committed return pixels differ");
        result.phase=phase;result.progress=progress;result.elapsed=elapsed;result.frames=frames;result.ratio=ratio;result.front=pixels.argb;result.image=phase==COMMITTED?to:current;return result;
    }
}
