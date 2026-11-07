package org.circus.core;

import java.io.*;
import java.util.*;

/** DC2 2006 map:40A0E0/40A2D0/40B1D0/40B370. Six actors and bank5:10/20/21.
 * Portable draw journal; renderer objects and Windows front-buffer ownership are external. */
public final class Dc2Map {
    private static final int FORMAT=0x44324d31;
    public static final String[] ACTORS={"otome","yume","anzu","nanaka","koko","minatu"};
    private static final int[][] DURATIONS={{500,500,500,500,500,500,500,500,500,200,200,200,500,400,500,500,500,500},{500,500,500,500,500,500,500,500,500,500,500,500,100,200,200,400,700,200,200},{500,500,500,500,500,500,500,500,200,1000,200,500,200,300,1000,200,200,200},{500,500,500,500,500,500,500,500,600,100,200,300,600,200,500,500,500,500},{500,500,500,500,500,500,500,500,500,500,500,500,400,100,100,100,400,700,100,100},{500,500,500,500,500,500,500,500,200,500,200,500,200,500,200,500,100,300,100,100,100,500}};
    private static final int[][] SCHOOL={{71,241,95,265},{203,451,227,475},{288,309,312,333},{409,91,433,115},{0,0,0,0},{543,295,567,319},{618,438,642,462}};
    private static final int[][] ISLAND={{494,398,518,422},{325,380,349,404},{426,331,450,355},{214,331,238,355},{629,192,653,216},{124,239,148,263},{0,0,0,0}};
    private static final int[][] SCHOOL_ACTOR={{55,49,223,201},{137,369,305,521},{222,227,390,379},{343,9,511,161},{0,0,0,0},{477,213,645,365},{552,356,720,508}};
    private static final int[][] ISLAND_ACTOR={{466,322,634,474},{297,304,465,456},{398,255,566,407},{186,255,354,407},{601,116,769,268},{96,163,264,315},{0,0,0,0}};
    // l,t,r,b,original full-image destinationX,destinationY (40A5A0).
    private static final int[][] SCHOOL_HIGHLIGHT={{0,136,248,280,0,24},{0,224,264,488,0,-768},{144,216,352,368,144,-200},{320,0,528,112,320,0},{351,216,503,400,351,-352},{504,208,704,368,504,-48},{576,248,800,488,576,-504}};
    private static final int[][] ISLAND_HIGHLIGHT={{456,368,552,456,456,288},{280,352,408,432,280,96},{384,304,488,392,384,136},{128,288,320,416,128,-48},{592,168,696,248,592,168},{80,216,184,288,80,-248}};
    private static final int[][] SCHOOL_MARKERS={{71,231},{203,451},{288,309},{409,91},{543,295},{618,438}};
    private static final int[][] ISLAND_MARKERS={{124,239},{214,331},{325,380},{426,331},{494,398},{629,192}};
    public boolean active,backdrop,school;public int selected=-1,hovered=-1,confirmHover=-1;public long elapsed;
    private int tick;private final boolean[] visible=new boolean[6];
    private final int[] locations=new int[6],collision=new int[6],frames=new int[6],frameTick=new int[6],specialShift=new int[6];
    public static final class Sprite {
        public final String resource;public final int x,y,sx,sy,width,height;public final boolean opaque;
        Sprite(String r,int x,int y,int sx,int sy,int w,int h,boolean opaque){resource=r;this.x=x;this.y=y;this.sx=sx;this.sy=sy;width=w;height=h;this.opaque=opaque;}
        public String signature(){return resource+":"+x+","+y+","+sx+","+sy+","+width+","+height+","+opaque;}
    }
    public void reset(){active=backdrop=school=false;selected=hovered=confirmHover=-1;elapsed=0;tick=0;Arrays.fill(visible,false);for(int[] a:new int[][]{locations,collision,frames,frameTick,specialShift})Arrays.fill(a,0);}
    public int frame(int actor){return frames[actor];}
    public int location(int actor){return locations[actor];}
    public int collision(int actor){return collision[actor];}
    public int[][] regions(){int[][] copy=new int[7][];int[][] source=school?SCHOOL:ISLAND;for(int n=0;n<7;n++)copy[n]=source[n].clone();return copy;}
    public String background(){return school?"MAP/moveschool_BG00":"MAP/moveisland_BG00";}
    public String chip(){return school?"MAP/moveschool_chip00":"MAP/moveland_chip00";}
    public String highlight(){return school?"MAP/moveschool_chip01":"MAP/moveisland_chip01";}
    public void open(Variables v)throws IOException {
        reset();school=v.read(5,21)==0;Arrays.fill(locations,-1);
        for(int n=0;n<6;n++){visible[n]=v.read(5,n)!=0;locations[n]=v.read(5,10+n);if(visible[n]&&(locations[n]<0||locations[n]>6))throw new IOException("DC2 visible map actor location "+locations[n]);for(int k=0;k<6;k++)if(k!=n&&locations[n]==locations[k])collision[k]=120;specialShift[n]=v.read(5,50+n);}
        active=true;
    }
    /** One native map poll; long gaps advance at most one atlas frame per actor. */
    public void elapse(long milliseconds){if(!active)return;long step=Math.max(0,milliseconds);elapsed=elapsed>Long.MAX_VALUE-step?Long.MAX_VALUE:elapsed+step;tick+=(int)step;for(int n=0;n<6;n++)if(Integer.compareUnsigned(tick-frameTick[n],DURATIONS[n][frames[n]])>0){frames[n]=(frames[n]+1)%DURATIONS[n].length;frameTick[n]=tick;}}
    public void buttons(Dc2Ui ui)throws IOException {
        ui.buttons.clear();ui.waiting=active;ui.hovered=-1;
        if(!active)return;
        if(selected>=0){button(ui,"@dc2map:yes",576,518,672,590);button(ui,"@dc2map:no",692,518,788,590);}
        else{int[][] regions=school?SCHOOL:ISLAND;for(int n=0;n<7;n++){int[] r=regions[n];if(r[2]>r[0])button(ui,"@dc2map:"+n,r[0],r[1],r[2],r[3]);}}
    }
    private static void button(Dc2Ui ui,String action,int l,int t,int r,int b){Dc2Ui.Button button=new Dc2Ui.Button();button.action=action;button.left=l;button.top=t;button.right=r;button.bottom=b;ui.buttons.add(button);}
    public boolean hover(Dc2Ui ui,int index)throws IOException {
        if(index< -1||index>=ui.buttons.size())throw new IOException("DC2 map hover index");int old=selected>=0?confirmHover:hovered;
        if(selected>=0)confirmHover=index;else hovered=index<0?-1:Integer.parseInt(ui.buttons.get(index).action.substring(8));
        ui.hovered=index;int now=selected>=0?confirmHover:hovered;return now>=0&&now!=old;
    }
    public void select(int location)throws IOException {int[][] regions=school?SCHOOL:ISLAND;if(location<0||location>=7||regions[location][2]<=regions[location][0])throw new IOException("DC2 map location is not selectable");selected=hovered=location;confirmHover=-1;}
    public void cancel(){selected=hovered=confirmHover=-1;}
    public void confirm(Variables v)throws IOException {if(selected<0)throw new IOException("DC2 map confirmation without location");v.write(5,20,selected);active=backdrop=false;}
    /** The original MES handles99 by entering its own inline rMenu label. */
    public boolean right(Variables v)throws IOException {if(selected>=0){cancel();return true;}if((tick&0xffffffffL)<500)return false;v.write(5,20,99);active=false;backdrop=true;return true;}
    public List<Sprite> sprites(Variables v)throws IOException {
        List<Sprite> out=new ArrayList<>();if(!active&&!backdrop)return out;
        out.add(new Sprite(background(),0,0,0,0,800,600,true));int location=selected>=0?selected:hovered;
        if(location>=0){int[][] geometry=school?SCHOOL_HIGHLIGHT:ISLAND_HIGHLIGHT;if(location<geometry.length){int[] r=geometry[location];out.add(new Sprite(highlight(),r[0],r[1],r[0]-r[4],r[1]-r[5],r[2]-r[0],r[3]-r[1],true));}}
        if(selected>=0)for(int n=0;n<2;n++)out.add(new Sprite(chip(),n==0?576:692,518,n*96,confirmHover==n?464:393,96,72,false));
        if(location>=0)out.add(new Sprite(chip(),136,524,0,location*56,424,56,false));
        for(int[] p:school?SCHOOL_MARKERS:ISLAND_MARKERS)out.add(new Sprite("MAP/movestop",p[0],p[1],816,0,24,24,false));
        int selectedActor=-1;for(int n=0;n<6;n++)if(locations[n]==location)selectedActor=n;
        for(int n=0;n<6;n++)if(visible[n]){
            int[] r=(school?SCHOOL_ACTOR:ISLAND_ACTOR)[locations[n]];if(r[2]<=r[0])continue;
            if(active)specialShift[n]=v.read(5,50+n);
            int shift=specialShift[n]==1?-120:school?55:10,y=0;if(school&&locations[n]==0){shift-=50;y=100;}
            out.add(new Sprite("MAP/chip_"+ACTORS[n],r[0]+collision[n]+shift,r[1]+y,frames[n]%5*168,frames[n]/5*152+(selectedActor==n?(n==4?762:760):0),r[2]-r[0],r[3]-r[1],false));
        }
        if(selectedActor>=0&&visible[selectedActor])out.add(new Sprite("MAP/movestop",0,440,selectedActor*136,school?0:160,136,160,false));
        return out;
    }
    public void save(DataOutput out)throws IOException {out.writeInt(FORMAT);out.writeBoolean(active);out.writeBoolean(backdrop);out.writeBoolean(school);out.writeInt(selected);out.writeInt(hovered);out.writeInt(confirmHover);out.writeLong(elapsed);out.writeInt(tick);for(boolean b:visible)out.writeBoolean(b);for(int[] a:new int[][]{locations,collision,frames,frameTick,specialShift})for(int n:a)out.writeInt(n);}
    public void load(DataInput in)throws IOException {
        if(in.readInt()!=FORMAT)throw new IOException("DC2 map state version");Dc2Map state=new Dc2Map();state.active=in.readBoolean();state.backdrop=in.readBoolean();state.school=in.readBoolean();state.selected=in.readInt();state.hovered=in.readInt();state.confirmHover=in.readInt();state.elapsed=in.readLong();state.tick=in.readInt();for(int n=0;n<6;n++)state.visible[n]=in.readBoolean();for(int[] a:new int[][]{state.locations,state.collision,state.frames,state.frameTick,state.specialShift})for(int n=0;n<6;n++)a[n]=in.readInt();
        if(state.selected< -1||state.selected>6||state.hovered< -1||state.hovered>6||state.confirmHover< -1||state.confirmHover>1||state.elapsed<0||state.active&&state.backdrop)throw new IOException("Invalid DC2 map selection");
        for(int n=0;n<6;n++)if(state.visible[n]&&(state.locations[n]<0||state.locations[n]>6)||state.frames[n]<0||state.frames[n]>=DURATIONS[n].length||state.collision[n]!=0&&state.collision[n]!=120)throw new IOException("Invalid DC2 map actor");
        active=state.active;backdrop=state.backdrop;school=state.school;selected=state.selected;hovered=state.hovered;confirmHover=state.confirmHover;elapsed=state.elapsed;tick=state.tick;System.arraycopy(state.visible,0,visible,0,6);int[][] target={locations,collision,frames,frameTick,specialShift},source={state.locations,state.collision,state.frames,state.frameTick,state.specialShift};for(int n=0;n<target.length;n++)System.arraycopy(source[n],0,target[n],0,6);
    }
}
