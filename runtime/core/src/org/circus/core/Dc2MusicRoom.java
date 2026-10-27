package org.circus.core;
import java.io.*;
import java.util.*;

/** Native 413490 / 414630 music room: ten rows and six transport controls. */
public final class Dc2MusicRoom {
    public boolean active,stopped=true;public int selected,top,mode;private int random=0x629d72,shuffleIndex=-1;private final int[] shuffle=new int[73];
    public Dc2NativeData data;
    final List<String> tracks=new ArrayList<>();
    public void prepare(CircusResources resources,TextCodec codec)throws IOException{
        if(data==null)data=new Dc2NativeData(resources,codec);if(tracks.isEmpty())for(int id:Dc2NativeData.MUSIC)tracks.add(resources.named("music",id));
    }
    public void scroll(int amount){top=Math.max(0,Math.min(tracks.size()-10,top+amount));}
    public void select(int value){selected=(value+tracks.size())%tracks.size();if(selected<top)top=selected;if(selected>=top+10)top=selected-9;}
    private int random(int bound){random^=random<<13;random^=random>>>17;random^=random<<5;return (random&0x7fffffff)%bound;}
    public void resetShuffle(){shuffleIndex=-1;}
    public void next(int direction){
        if(mode!=3){select(selected+direction);return;}
        if(shuffleIndex<0){shuffle[0]=selected;for(int n=1,k=0;n<shuffle.length;n++){if(k==selected)k++;shuffle[n]=k++;}for(int n=shuffle.length-1;n>1;n--){int k=1+random(n);int t=shuffle[n];shuffle[n]=shuffle[k];shuffle[k]=t;}shuffleIndex=0;}
        shuffleIndex=(shuffleIndex+direction+shuffle.length)%shuffle.length;select(shuffle[shuffleIndex]);
    }
    public void buttons(Dc2Ui ui){
        ui.buttons.clear();
        button(ui,"@music:prev",66,291,156,341,18,136,10);
        button(ui,"@music:play",168,291,240,341,119,stopped?304:136,10);
        button(ui,"@music:next",250,291,340,341,202,136,10);
        button(ui,"@music:one",100,370,160,413,203,mode==1?400:308,10);
        button(ui,"@music:all",174,370,234,413,275,mode==2?400:308,10);
        button(ui,"@music:random",246,370,306,413,347,mode==3?400:308,10);
        button(ui,"@music:exit",924,496,1008,564,8,362,11);
        for(int n=0;n<10&&n+top<tracks.size();n++){Dc2Ui.Button b=button(ui,"@music:track:"+(top+n),435,78+n*41,944,115+n*41,-1,0,10);b.clickSound=224;}
    }
    static Dc2Ui.Button button(Dc2Ui ui,String action,int l,int t,int r,int b,int sx,int sy,int slot){
        Dc2Ui.Button button=new Dc2Ui.Button();button.action=action;button.left=l;button.top=t;button.right=r;button.bottom=b;
        if(sx>=0){Dc2Ui.Quad q=new Dc2Ui.Quad();q.x=l;q.y=t;q.sx=sx;q.sy=sy;q.width=r-l;q.height=b-t;q.flags=slot;q.state=0;button.sprites.add(q);}
        ui.buttons.add(button);return button;
    }
    public void save(DataOutput o)throws IOException{o.writeBoolean(active);o.writeBoolean(stopped);o.writeInt(selected);o.writeInt(top);o.writeInt(mode);o.writeInt(random);o.writeInt(shuffleIndex);for(int n:shuffle)o.writeInt(n);}
    public void load(DataInput i)throws IOException{active=i.readBoolean();stopped=i.readBoolean();selected=i.readInt();top=i.readInt();mode=i.readInt();random=i.readInt();shuffleIndex=i.readInt();Set<Integer> used=new HashSet<>();for(int n=0;n<shuffle.length;n++){shuffle[n]=i.readInt();if(shuffleIndex>=0&&(shuffle[n]<0||shuffle[n]>=73||!used.add(shuffle[n])))throw new IOException("DM shuffle order");}if(selected<0||selected>=73||top<0||top>63||mode<0||mode>3||shuffleIndex < -1||shuffleIndex>=73)throw new IOException("DM music room state");}
}
