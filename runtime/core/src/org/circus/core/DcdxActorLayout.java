package org.circus.core;

/** DCDX7769 draw41C2E0: live XY/zoom and current staged pivot.
 * Raw CRX dimensions are supplied, so a native pre-scaled handle is not scaled twice. */
public final class DcdxActorLayout {
    public static final int CANVAS_WIDTH=1024,CANVAS_HEIGHT=576;
    public final int x,y,width,height,weight16;
    private DcdxActorLayout(int x,int y,int width,int height,int weight16){this.x=x;this.y=y;this.width=width;this.height=height;this.weight16=weight16;}
    public static DcdxActorLayout place(int[] actor,int rawWidth,int rawHeight,int alphaFlag){
        if(actor==null||actor.length<5||rawWidth<=0||rawHeight<=0)throw new IllegalArgumentException("DCDX raw actor geometry");
        int zoom=actor[2],x=actor[0],y=actor[1],width=rawWidth,height=rawHeight;
        if(zoom==100){
            // These thresholds are immediate constants in this DCDX consumer;
            // they are independent of the1024x576 destination viewport.
            if(width>882)x=x-((width*100/882)*actor[3])/100+actor[3];
            if(height>756)y=y-((height*100/756)*actor[4])/100+actor[4];
        }else{
            width=width*zoom/100;height=height*zoom/100;
            x=x-actor[3]*zoom/100+actor[3];y=y-actor[4]*zoom/100+actor[4];
        }
        int weight=zoom==100&&alphaFlag>=1&&alphaFlag<=16?16-alphaFlag:16;
        return new DcdxActorLayout(x,y,width,height,weight);
    }
    /**410BF0's on-the-fly stretch sampler. Offset is measured from the full
     * destination origin, including pixels clipped away by the viewport.
     * The native first sample precedes normalization of its half-source error. */
    public static int stretchSample(int sourceExtent,int destinationExtent,int offset){
        if(sourceExtent<=0||destinationExtent<=0||offset<0||offset>=destinationExtent)throw new IllegalArgumentException("DCDX stretch sample extent");
        return offset==0?0:(int)(((long)sourceExtent/2+(long)offset*sourceExtent)/destinationExtent);
    }
}
