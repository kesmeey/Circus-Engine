package org.circus.core;

/** DC2PC 41A950..41AB34: integer actor placement, including native-size close-ups. */
public final class PcActorLayout {
    public final int x,y,width,height;
    private PcActorLayout(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
    public static PcActorLayout dc2pc(int[] actor,int width,int height){
        int zoom=actor[2],x=actor[0],y=actor[1];
        if(zoom==100){
            // The 1260x1080 back-view art is already enlarged. The anchor remains
            // expressed in the ordinary 882x756 canvas, rather than the large bitmap.
            if(width>882)x+=actor[3]-(int)((long)(width*100/882)*actor[3]/100);
            if(height>756)y+=actor[4]-(int)((long)(height*100/756)*actor[4]/100);
        }else{
            x+=actor[3]-(int)((long)actor[3]*zoom/100);
            y+=actor[4]-(int)((long)actor[4]*zoom/100);
            width=(int)((long)width*zoom/100);height=(int)((long)height*zoom/100);
        }
        return new PcActorLayout(x,y,width,height);
    }
}
