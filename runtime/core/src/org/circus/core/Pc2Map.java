package org.circus.core;
import java.io.*;

/** PC2 native map service: 4089D6 -> 40ECC0, 40F970, 40FB10.
 * These are binary UI constants, not route choices invented by the interpreter. */
public final class Pc2Map {
    public static final int[][] SCHOOL={{71,241,95,265},{203,451,227,475},{288,309,312,333},{409,91,433,115},{0,0,0,0},{543,295,567,319},{618,438,642,462}};
    public static final int[][] ISLAND={{494,398,518,422},{325,380,349,404},{426,331,450,355},{214,331,238,355},{629,192,653,216},{124,239,148,263},{0,0,0,0}};
    public static final int[][] SCHOOL_ACTOR={{55,49,223,201},{137,369,305,521},{222,227,390,379},{343,9,511,161},{0,0,0,0},{477,213,645,365},{552,356,720,508}};
    public static final int[][] ISLAND_ACTOR={{466,322,634,474},{297,304,465,456},{398,255,566,407},{186,255,354,407},{601,116,769,268},{96,163,264,315},{0,0,0,0}};
    public static final String[] ACTORS={"otome","yume","anzu","nanaka","koko","minatu","mayuki","akane","maya","erika"};
    public static final int[][] DURATIONS={{500, 500, 500, 500, 500, 500, 500, 500, 500, 200, 200, 200, 500, 400, 500, 500, 500, 500}, {500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 100, 200, 200, 400, 700, 200, 200}, {500, 500, 500, 500, 500, 500, 500, 500, 200, 1000, 200, 500, 200, 300, 1000, 200, 200, 200}, {500, 500, 500, 500, 500, 500, 500, 500, 600, 100, 200, 300, 600, 200, 500, 500, 500, 500}, {500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 400, 100, 100, 100, 400, 700, 100, 100}, {500, 500, 500, 500, 500, 500, 500, 500, 200, 500, 200, 500, 200, 500, 200, 500, 100, 300, 100, 100, 100, 500}, {500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 200, 200, 700, 400, 700}, {500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 300, 200, 200, 200, 600, 300}, {500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 300, 200, 200, 800}, {500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 600, 200, 400, 300, 100, 300, 700, 600}};
    public boolean active,school;public int selected=-1,hovered=-1;public long elapsed;
    public int[][] regions(){return school?SCHOOL:ISLAND;}
    public String background(){return school?"MAP/moveschool_BG00":"MAP/moveisland_BG00";}
    public String chip(){return school?"MAP/moveschool_chip00":"MAP/moveland_chip00";}
    public void open(Variables v)throws IOException{active=true;school=v.read(5,41)==0;selected=hovered=-1;elapsed=0;}
    public void buttons(Dc2Ui ui)throws IOException{
        ui.buttons.clear();ui.waiting=true;
        if(selected>=0){button(ui,"@map:yes",new int[]{576,518,672,590});button(ui,"@map:no",new int[]{692,518,788,590});}
        else for(int n=0;n<regions().length;n++){int[] r=regions()[n];if(r[2]>r[0])button(ui,"@map:"+n,r);}
    }
    private void button(Dc2Ui ui,String action,int[] r){Dc2Ui.Button b=new Dc2Ui.Button();b.action=action;b.left=r[0];b.top=r[1];b.right=r[2];b.bottom=r[3];ui.buttons.add(b);}
    public void save(DataOutput o)throws IOException{o.writeBoolean(active);o.writeBoolean(school);o.writeInt(selected);o.writeInt(hovered);o.writeLong(elapsed);}
    public void load(DataInput i)throws IOException{active=i.readBoolean();school=i.readBoolean();selected=i.readInt();hovered=i.readInt();elapsed=i.readLong();if(selected< -1||selected>6||hovered< -1||hovered>6||elapsed<0)throw new IOException("Invalid map state");}
}
