package org.circus.core;

import java.io.*;
import java.util.*;

/** DCDX service50 mode0, native4079F0/408360/407EE0. The native
 * four-card menu owns its atlas bindings, front ordering and script labels. */
public final class DcdxMenu {
    private static final String[] ACTIONS={"story","ard","3do","end"};
    private static final int[] SOURCE_X={0,400,600,200};
    private static final double RADIANS=0.017453277777777776;
    public static final class Layer {
        public final String resource;
        public final int sx,sy,sw,sh,x,y,dw,dh,alpha;
        /**40EA30 copies BGR;40F0A0 keys magenta BGR and uses source coverage. */
        public final boolean keyed;
        Layer(String resource,int sx,int sy,int width,int height,int x,int y,boolean keyed){this.resource=resource;this.sx=sx;this.sy=sy;sw=dw=width;sh=dh=height;this.x=x;this.y=y;this.keyed=keyed;alpha=255;}
    }
    public boolean active,visible,initialized,presented;
    public int mode,sourceSerial,front,hovered=-1;
    public long generation;
    public String background="",cardAtlas="",yesNoAtlas="",previewAtlas="";
    private final int[] x=new int[4],y=new int[4],order={0,1,2,3};
    private float angle,baseAngle;
    private int rotationIndex,remainingTurns;
    private boolean turning;
    private int clockMs,clockFrame;
    public String action(int item){if(item<0||item>=4)throw new IllegalArgumentException("DCDX menu item");return ACTIONS[item];}
    public int cardX(int item){return x[item];}
    public int cardY(int item){return y[item];}
    public boolean turning(){return turning;}
    public int clockMillis(){return clockMs;}
    public int clockFrame(){return clockFrame;}
    public void syncClock(int ms,int frame)throws IOException{if(ms<0||ms>=1000||frame!=((ms+1)*30-1)/1000)throw new IOException("DCDX menu clock phase");clockMs=ms;clockFrame=frame;}
    public void start(int mode,String background,String cards,String yesNo,String preview,int serial)throws IOException{
        if(mode!=0)throw new IOException("DCDX native voice-selection menu mode not implemented "+mode);
        if(background==null||background.isEmpty()||cards==null||cards.isEmpty()||preview==null||preview.isEmpty())throw new IOException("DCDX menu requires its native background/card/preview atlas bindings");
        boolean clockInitialized=initialized;this.mode=mode;this.background=background;cardAtlas=cards;yesNoAtlas=yesNo;previewAtlas=preview;sourceSerial=serial;
        active=visible=initialized=true;presented=false;front=0;hovered=-1;angle=baseAngle=0;rotationIndex=remainingTurns=0;turning=false;if(!clockInitialized)clockMs=clockFrame=0;for(int i=0;i<4;i++)order[i]=i;coordinates();sort();generation++;
    }
    /**407CD0 resumes the existing menu and clock; it does not construct a new table. */
    public void resume(int serial)throws IOException{if(!initialized||mode!=0)throw new IOException("DCDX menu53 has no initialized mode0 owner");sourceSerial=serial;active=visible=true;hovered=-1;generation++;}
    public void release(){active=false;}
    public void clear(){active=visible=false;}
    private void coordinates(){for(int i=0;i<4;i++){int degrees=(int)(i*90.0+angle);double rad=degrees*RADIANS;x[i]=(int)(Math.sin(rad)*280.0+412.0);y[i]=(int)(Math.cos(rad)*64.0+184.0);}}
    /**Native selection sort compares strictly less, including its tie ordering. */
    private void sort(){for(int i=0;i<4;i++){order[i]=i;}for(int i=0;i<4;i++)for(int j=i;j<4;j++)if(y[order[j]]<y[order[i]]&&i!=j){int n=order[i];order[i]=order[j];order[j]=n;}front=order[3];}
    private boolean due(long millis){long delta=Math.max(0,millis);long elapsed=clockMs+delta%1000;boolean due=delta>=1000||elapsed>=(clockFrame+1)*1000/30;clockMs=(int)(elapsed%1000);if(due)clockFrame=((clockMs+1)*30-1)/1000;return due;}
    /**406C50 consumes all expired30Hz deadlines but calls408360 only once. */
    public boolean advance(long millis){if(!active||!due(millis))return false;sort();coordinates();presented=true;generation++;if(turning){angle+=remainingTurns>0?8.0f:-8.0f;if(remainingTurns>0?angle>=baseAngle+90:angle<=baseAngle-90){int direction=remainingTurns>0?1:-1;remainingTurns-=direction;angle=rotationIndex*90.0f;if(remainingTurns!=0){baseAngle=angle;rotationIndex=(rotationIndex+direction+4)%4;}else turning=false;}}return true;}
    public int hit(int px,int py){int hit=-1;for(int n:order)if(px>=x[n]&&px<=x[n]+200&&py>=y[n]&&py<=y[n]+278)hit=n;return hit;}
    public void hover(int item){if(!active||turning)return;if(item< -1||item>=4)throw new IllegalArgumentException("DCDX hovered item");if(hovered!=item){hovered=item;generation++;}}
    private void turn(int count){if(turning||count==0)return;remainingTurns=count;baseAngle=rotationIndex*90.0f;angle=baseAngle;rotationIndex=(rotationIndex+(count>0?1:-1)+4)%4;turning=true;}
    /**A nonfront click rotates first. Native mode0 right-click is consumed. */
    public String click(int item){if(!active||turning||item<0||item>=4)return null;if(item==front)return action(item);int positive=(front-item+4)%4;turn(positive<=2?positive:positive-4);return null;}
    public String key(int key){if(!active||turning)return null;if(key==13)return action(front);if(key==39||key==38)turn(1);if(key==37||key==40)turn(-1);return null;}
    public List<Dc2Ui.Button> controls(){List<Dc2Ui.Button> result=new ArrayList<Dc2Ui.Button>();if(!active)return result;for(int item:order){Dc2Ui.Button b=new Dc2Ui.Button();b.left=x[item];b.top=y[item];b.right=x[item]+200;b.bottom=y[item]+278;b.binding=item;b.action=action(item);b.hoverSound=201;b.clickSound=200;result.add(b);}return result;}
    public List<Layer> layers(){List<Layer> result=new ArrayList<Layer>();if(!visible)return result;result.add(new Layer(background,0,0,1024,576,0,0,false));if(!presented)return result;for(int item:order){boolean selected=item==front||item==hovered;result.add(new Layer(cardAtlas,SOURCE_X[item],selected?0:278,200,278,x[item],y[item],true));if(item==hovered&&item!=front)result.add(new Layer(cardAtlas,0,556,248,326,x[item]-24,y[item]-20,true));}if(hovered==front)result.add(new Layer(previewAtlas,(front%2)*1024,(front/2)*576,1024,576,0,0,true));return Collections.unmodifiableList(result);}
    /**Portable0D9 fallback: freeze only the visible clipped regions, without an
     *ARGB conversion/cache of the full2048x1152 preview atlas. */
    public UiCapture capture(CircusResources resources)throws IOException{
        int[] front=new int[1024*576];Arrays.fill(front,0xff000000);Map<String,Crx.Image> images=new HashMap<String,Crx.Image>();
        for(Layer layer:layers()){Crx.Image raw=images.get(layer.resource);if(raw==null){raw=resources.image(layer.resource);images.put(layer.resource,raw);}if(raw.bpp!=24&&raw.bpp!=32)throw new IOException("DCDX menu native bitmap format "+raw.bpp);if(!layer.keyed&&raw.bpp!=24)throw new IOException("DCDX40EA30 background requires BGR24");
            int left=Math.max(0,layer.x),top=Math.max(0,layer.y),right=Math.min(1024,layer.x+layer.dw),bottom=Math.min(576,layer.y+layer.dh),ps=raw.bpp/8;
            for(int py=top;py<bottom;py++)for(int px=left;px<right;px++){int sx=layer.sx+px-layer.x,sy=layer.sy+py-layer.y;if(sx<0||sy<0||sx>=raw.width||sy>=raw.height)continue;int at=sy*raw.stride+sx*ps,b=raw.pixels[at]&255,g=raw.pixels[at+1]&255,r=raw.pixels[at+2]&255;if(layer.keyed&&ps==3&&r==255&&g==0&&b==255)continue;int alpha=ps==4?raw.pixels[at+3]&255:255;if(alpha==0)continue;int old=front[py*1024+px];if(alpha!=255){b=(b*alpha+(old&255)*(255-alpha))/255;g=(g*alpha+(old>>>8&255)*(255-alpha))/255;r=(r*alpha+(old>>>16&255)*(255-alpha))/255;}front[py*1024+px]=0xff000000|r<<16|g<<8|b;}
        }return UiCapture.pixels(1024,576,front);
    }
    public void save(DataOutput out)throws IOException{out.writeInt(0x44584d45);out.writeBoolean(active);out.writeBoolean(visible);out.writeBoolean(initialized);out.writeBoolean(presented);out.writeInt(mode);out.writeInt(sourceSerial);out.writeInt(front);out.writeInt(hovered);out.writeLong(generation);out.writeUTF(background);out.writeUTF(cardAtlas);out.writeUTF(yesNoAtlas);out.writeUTF(previewAtlas);out.writeFloat(angle);out.writeFloat(baseAngle);out.writeInt(rotationIndex);out.writeInt(remainingTurns);out.writeBoolean(turning);out.writeInt(clockMs);out.writeInt(clockFrame);for(int n=0;n<4;n++){out.writeInt(x[n]);out.writeInt(y[n]);out.writeInt(order[n]);}}
    public void load(DataInput in)throws IOException{if(in.readInt()!=0x44584d45)throw new IOException("DCDX menu snapshot version");active=in.readBoolean();visible=in.readBoolean();initialized=in.readBoolean();presented=in.readBoolean();mode=in.readInt();sourceSerial=in.readInt();front=in.readInt();hovered=in.readInt();generation=in.readLong();background=in.readUTF();cardAtlas=in.readUTF();yesNoAtlas=in.readUTF();previewAtlas=in.readUTF();angle=in.readFloat();baseAngle=in.readFloat();rotationIndex=in.readInt();remainingTurns=in.readInt();turning=in.readBoolean();clockMs=in.readInt();clockFrame=in.readInt();int seen=0;for(int n=0;n<4;n++){x[n]=in.readInt();y[n]=in.readInt();order[n]=in.readInt();if(order[n]<0||order[n]>=4||(seen&(1<<order[n]))!=0)throw new IOException("DCDX menu sort snapshot");seen|=1<<order[n];}if(mode!=0||front<0||front>=4||hovered< -1||hovered>=4||clockMs<0||clockMs>=1000||clockFrame<0||clockFrame>=30||!Float.isFinite(angle)||!Float.isFinite(baseAngle)||rotationIndex<0||rotationIndex>=4||remainingTurns< -2||remainingTurns>2||turning!=(remainingTurns!=0)||active&&!initialized)throw new IOException("DCDX menu state");}
}
