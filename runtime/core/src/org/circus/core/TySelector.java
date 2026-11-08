package org.circus.core;

import java.io.*;
import java.util.*;

/** TY service1C12:40AAA0/40AD00/40AFD0/40B360. This owner preserves
 * the two-step preview/confirm input contract and native clipped atlas draws. */
public final class TySelector {
    private static final int FORMAT=0x54595331,MAX_PATCHES=100000;
    public static final String BACK="system/sce_select_back.crx",ATLAS="system/sce_select_chip.crx";
    private static final String[] BACKGROUNDS={"system/sce_select_hyd.crx","system/sce_select_set.crx","system/sce_select_mhr.crx","system/sce_select_myu.crx"};
    private static final int[][] HIT={{552,32,784,144},{552,160,784,272},{552,288,784,400},{552,416,784,528},{610,541,722,573}};
    private static final int[][] RECT={{528,8,800,168},{528,136,800,288},{528,248,800,424},{528,368,800,548},{610,541,722,573}};
    private static final int[][] SELECTED_RECT={{528,8,800,168},{528,120,800,288},{528,248,800,424},{528,368,800,548},{610,541,722,573}};
    private static final int[][] NORMAL={{0,0,272,0,272,0},{0,160,272,160,272,160},{0,312,272,312,272,312},{0,488,272,488,272,488},{0,668,112,668,224,668}};
    private static final int[][] SELECTED={{544,0,816,0,816,0,1092,0,1364,0,1636,0,1908,0},{544,160,816,160,816,160,1092,160,1364,160,1636,160,1908,160},{544,328,816,328,816,328,1092,312,1364,312,1636,312,1908,312},{544,504,816,504,816,504,1092,488,1364,488,1636,488,1908,488},{0,668,112,668,224,668,0,668,0,668,0,668,0,668}};

    /** Draw the full raw resource at drawXY, clipped to the half-open rectangle.
     * Background patches copy BGR; atlas patches key24-bit magenta/use32-bit alpha. */
    public static final class Sprite {
        public final String resource;public final int drawX,drawY,clipLeft,clipTop,clipRight,clipBottom;public final boolean keyed;
        private Sprite(String resource,int x,int y,int left,int top,int right,int bottom,boolean keyed){this.resource=resource;drawX=x;drawY=y;clipLeft=left;clipTop=top;clipRight=right;clipBottom=bottom;this.keyed=keyed;}
    }
    public static final class Surface {
        public final String background;public final List<Sprite> sprites;
        private Surface(String background,List<Sprite> sprites){this.background=background;this.sprites=Collections.unmodifiableList(new ArrayList<Sprite>(sprites));}
    }
    public static final class Frame {
        public final long generation,serial;public final int sourceSerial,weight255;public final Surface from,target;
        private Frame(long generation,long serial,int sourceSerial,Surface from,Surface target,int weight){this.generation=generation;this.serial=serial;this.sourceSerial=sourceSerial;this.from=from;this.target=target;weight255=weight;}
    }
    public boolean active,visible;
    public long generation;
    public int sourceSerial;
    private long serial,elapsed,phaseElapsed;
    private int selected=-1,focus=-1,mouseX,mouseY,phase,returnFrom=-1;
    private int fadeMs,slideMs,result=-1,pendingEvent;
    private float fadeRate;
    private String stableBackground=BACK;
    private Surface current=new Surface(BACK,Collections.<Sprite>emptyList()),from,target;
    private final int[] states={-1,-1,-1,-1,-1};
    private final ArrayDeque<Integer> sounds=new ArrayDeque<Integer>();
    private ArrayList<Sprite> patches=new ArrayList<Sprite>();
    private boolean inputDirty;

    public void start(int fadeUnits,int slideUnits,int multiplier,int sourceSerial)throws IOException{
        if(fadeUnits<0||fadeUnits>255||slideUnits<0||slideUnits>255||multiplier<0||multiplier>255)throw new IOException("TY selector native timing byte range");
        this.sourceSerial=sourceSerial;fadeMs=fadeUnits*multiplier;slideMs=slideUnits*multiplier;fadeRate=255.0f/(float)fadeMs;
        active=visible=true;generation++;serial++;elapsed=phaseElapsed=0;selected=focus=returnFrom=-1;mouseX=mouseY=pendingEvent=0;result=-1;sounds.clear();Arrays.fill(states,-1);
        stableBackground=BACK;current=new Surface(BACK,Collections.<Sprite>emptyList());patches.clear();from=null;target=buildTarget(-1,-1);phase=3;inputDirty=false;
    }
    public void clear(){active=visible=false;result=-1;phase=pendingEvent=0;sounds.clear();from=target=null;patches.clear();current=new Surface(BACK,Collections.<Sprite>emptyList());serial++;}
    public boolean interactive(){return active&&phase==0;}
    public int selected(){return selected;}
    public int focused(){return focus;}
    public long elapsedMillis(){return elapsed;}
    public int fadeMillis(){return fadeMs;}
    public int unusedSlideMillis(){return slideMs;}
    public String currentBackground(){return stableBackground;}
    public int hit(int x,int y){for(int n=4;n>=0;n--){int[] r=HIT[n];if(x>=r[0]&&x<=r[2]&&y>=r[1]&&y<=r[3])return n;}return -1;}
    public List<Dc2Ui.Button> controls(){
        List<Dc2Ui.Button> buttons=new ArrayList<Dc2Ui.Button>();if(!interactive())return buttons;
        for(int n=0;n<5;n++){Dc2Ui.Button b=new Dc2Ui.Button();b.left=HIT[n][0];b.top=HIT[n][1];b.right=HIT[n][2];b.bottom=HIT[n][3];b.binding=n;b.action="ty:"+n;b.hoverSound=b.clickSound=255;buttons.add(b);}return buttons;
    }
    public void pointer(int x,int y){if(!active)return;inputDirty|=mouseX!=x||mouseY!=y||focus!=-1;mouseX=x;mouseY=y;focus=-1;}
    public void click(int x,int y){if(!interactive())return;pointer(x,y);pendingEvent=1;inputDirty=true;}
    public void key(int key){
        if(!interactive())return;
        if(key==39||key==40)focus=(focus+1)%5;
        else if(key==37||key==38)focus=focus<=0?4:focus-1;
        else if(key==1||key==13)pendingEvent=1;
        else if(key==27)pendingEvent=2;
        else return;inputDirty=true;
    }
    public Integer takeResult(){if(result<0)return null;int value=result;result=-1;return value;}
    public Integer takeSound(){return sounds.pollFirst();}
    private boolean hovered(int n){return focus==n||hit(mouseX,mouseY)==n;}
    private int[] rect(int n){return n==selected?SELECTED_RECT[n]:RECT[n];}
    private int[] crop(int n,boolean hover){
        if(n==selected)return new int[]{SELECTED[n][hover?2:0],SELECTED[n][hover?3:1]};
        if(hover||selected<0)return new int[]{NORMAL[n][hover?2:0],NORMAL[n][hover?3:1]};
        int at=(3+selected)*2;return new int[]{SELECTED[n][at],SELECTED[n][at+1]};
    }
    private void atlas(int n,boolean hover,int top,int bottom){int[] r=rect(n),c=crop(n,hover);patches.add(new Sprite(ATLAS,r[0]-c[0],r[1]-c[1],r[0],top,r[2],bottom,true));}
    /**40A510 restores the clicked strip, then vertically overlapping controls. */
    private void restore(int n,boolean normalOnly){
        int[] r=rect(n);patches.add(new Sprite(stableBackground,0,0,r[0],r[1],r[2],r[3],false));
        for(int other=0;other<5;other++)if(other!=n){int[] q=rect(other);if(r[1]<q[1]&&q[1]<r[3])atlas(other,!normalOnly&&hovered(other),q[1],r[3]);else if(r[1]<q[3]&&q[3]<r[3])atlas(other,!normalOnly&&hovered(other),r[1],q[3]);}
    }
    private void normal(int n,boolean force,boolean noRestore){if(!force&&states[n]==0)return;if(!noRestore)restore(n,false);int[] r=rect(n);atlas(n,false,r[1],r[3]);states[n]=0;}
    private void hover(int n,boolean force){if(!force&&(states[n]==1||states[n]==2))return;restore(n,true);int[] r=rect(n);atlas(n,true,r[1],r[3]);if(states[n]==0)sounds.add(224);states[n]=1;}
    private Surface buildTarget(int selection,int last){
        int old=selected;selected=selection;ArrayList<Sprite> saved=patches;patches=new ArrayList<Sprite>();
        for(int n=4;n>=0;n--)if(n!=last)normal(n,true,true);if(last>=0)normal(last,true,true);
        Surface built=new Surface(selection<0?BACK:BACKGROUNDS[selection],patches);patches=saved;selected=old;return built;
    }
    private void publish(){if(patches.size()>MAX_PATCHES)throw new IllegalStateException("TY selector patch history exceeds reviewed snapshot bound");current=new Surface(stableBackground,patches);serial++;}
    private void transition(int selection){
        from=current;returnFrom=selected;selected=selection;focus=-1;phase=selection<0?2:1;phaseElapsed=0;target=buildTarget(selection,selection<0?returnFrom:selection);Arrays.fill(states,0);serial++;
    }
    private void confirm(int n){result=n;active=false;pendingEvent=0;serial++;}
    public boolean advance(long millis){
        if(!active)return false;long delta=Math.max(0,millis);elapsed=(elapsed+delta)&0xffffffffL;
        if(phase!=0){phaseElapsed=(phaseElapsed+delta)&0xffffffffL;if(fadeMs==0||(double)(float)phaseElapsed*(double)fadeRate>=256.0){
                stableBackground=selected<0?BACK:BACKGROUNDS[selected];patches=new ArrayList<Sprite>();Arrays.fill(states,-1);int last=phase==2?returnFrom:selected;
                for(int n=4;n>=0;n--)if(n!=last){normal(n,true,false);states[n]=-1;}if(last>=0){normal(last,true,false);states[last]=-1;}
                publish();from=target=null;phase=0;phaseElapsed=0;inputDirty=true;return true;
            }serial++;return true;}
        if(!inputDirty&&pendingEvent==0)return false;inputDirty=false;int before=patches.size();
        for(int n=4;n>=0;n--)if(!hovered(n)&&n!=selected)normal(n,false,false);
        for(int n=4;n>=0;n--)if(hovered(n)&&n!=selected)hover(n,false);
        if(selected>=0){if(hovered(selected))hover(selected,true);else normal(selected,true,false);}
        if(patches.size()!=before)publish();int event=pendingEvent;pendingEvent=0;
        if(event==2){if(selected<0)confirm(4);else transition(-1);return true;}
        //40B360 visits4..0 and handles another card before the separately
        //redrawn selected card.40AD00 then clears the keyboard focus/event.
        if(event==1){int item=-1;for(int n=4;n>=0;n--)if(n!=selected&&hovered(n)){item=n;break;}if(item<0&&selected>=0&&hovered(selected))item=selected;if(item>=0){if(item==4){sounds.add(223);confirm(4);}else if(item==selected)confirm(item);else{sounds.add(223);transition(item);}return true;}}
        return patches.size()!=before;
    }
    public Frame frame(){
        int weight=255;if(phase!=0){double value=(double)(float)phaseElapsed*(double)fadeRate;weight=fadeMs==0?255:(int)Math.max(0,Math.min(255,value));}
        return new Frame(generation,serial,sourceSerial,phase==0?current:from,phase==0?current:target,weight);
    }
    private static void surface(DataOutput out,Surface value)throws IOException{out.writeBoolean(value!=null);if(value==null)return;out.writeUTF(value.background);out.writeInt(value.sprites.size());for(Sprite s:value.sprites){out.writeUTF(s.resource);out.writeInt(s.drawX);out.writeInt(s.drawY);out.writeInt(s.clipLeft);out.writeInt(s.clipTop);out.writeInt(s.clipRight);out.writeInt(s.clipBottom);out.writeBoolean(s.keyed);}}
    private static Surface surface(DataInput in)throws IOException{if(!in.readBoolean())return null;String bg=in.readUTF();int count=in.readInt();if(count<0||count>MAX_PATCHES)throw new IOException("TY selector patch count");List<Sprite> list=new ArrayList<Sprite>();for(int n=0;n<count;n++){String resource=in.readUTF();int x=in.readInt(),y=in.readInt(),l=in.readInt(),t=in.readInt(),r=in.readInt(),b=in.readInt();boolean key=in.readBoolean();if(l>r||t>b)throw new IOException("TY selector clip");list.add(new Sprite(resource,x,y,l,t,r,b,key));}return new Surface(bg,list);}
    public void save(DataOutput out)throws IOException{
        out.writeInt(FORMAT);out.writeBoolean(active);out.writeBoolean(visible);out.writeLong(generation);out.writeLong(serial);out.writeInt(sourceSerial);out.writeLong(elapsed);out.writeLong(phaseElapsed);out.writeInt(selected);out.writeInt(focus);out.writeInt(mouseX);out.writeInt(mouseY);out.writeInt(phase);out.writeInt(returnFrom);out.writeInt(fadeMs);out.writeInt(slideMs);out.writeFloat(fadeRate);out.writeInt(result);out.writeInt(pendingEvent);out.writeBoolean(inputDirty);out.writeUTF(stableBackground);for(int n:states)out.writeInt(n);out.writeInt(sounds.size());for(int n:sounds)out.writeInt(n);surface(out,current);surface(out,from);surface(out,target);
    }
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("TY selector snapshot version");active=in.readBoolean();visible=in.readBoolean();generation=in.readLong();serial=in.readLong();sourceSerial=in.readInt();elapsed=in.readLong();phaseElapsed=in.readLong();selected=in.readInt();focus=in.readInt();mouseX=in.readInt();mouseY=in.readInt();phase=in.readInt();returnFrom=in.readInt();fadeMs=in.readInt();slideMs=in.readInt();fadeRate=in.readFloat();result=in.readInt();pendingEvent=in.readInt();inputDirty=in.readBoolean();stableBackground=in.readUTF();
        for(int n=0;n<5;n++){states[n]=in.readInt();if(states[n]<-1||states[n]>2)throw new IOException("TY selector control state");}int count=in.readInt();if(count<0||count>1024)throw new IOException("TY selector audio queue");sounds.clear();for(int n=0;n<count;n++){int sound=in.readInt();if(sound!=223&&sound!=224)throw new IOException("TY selector audio");sounds.add(sound);}current=surface(in);from=surface(in);target=surface(in);
        if(current==null||selected< -1||selected>3||focus< -1||focus>4||phase<0||phase>3||returnFrom< -1||returnFrom>3||fadeMs<0||fadeMs>65025||slideMs<0||slideMs>65025||elapsed<0||elapsed>0xffffffffL||phaseElapsed<0||phaseElapsed>0xffffffffL||result< -1||result>4||pendingEvent<0||pendingEvent>2||Float.isNaN(fadeRate)||fadeRate<0||phase!=0&&target==null||active&&!visible)throw new IOException("TY selector state");patches=new ArrayList<Sprite>(current.sprites);
    }
}
