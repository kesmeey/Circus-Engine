package org.circus.core;

import java.io.*;
import java.util.*;

/** ABB6 UI construction state. All script payloads have already been consumed by MesVm.
 * Native 430066: selector99 switches the construction pointer among 286C/2870/2874;
 * it does not create a button or rewrite the following physical record. */
public final class Dc4UiCommands {
    private static final int MAGIC=0x50485532;
    private final Dc3Ui.Panel[] builders=new Dc3Ui.Panel[3];
    private final int[][] options=new int[3][9];
    private final String[] hover=new String[]{"","",""},click=new String[]{"","",""};
    private int selected;
    private String cursor="";
    private boolean idleRegistered,registeredWindow;
    // Native 412B50 moves the render/input origin of one construction panel.
    // Geometry is translated together here so the common renderer and hit test
    // consume the same position. Native uses twenty frame updates; portable
    // elapsed time uses a 60 Hz clock.
    private final int[] originX=new int[3],originY=new int[3],fromX=new int[3],fromY=new int[3],toX=new int[3],toY=new int[3];
    private final long[] moveMillis=new long[3];
    private final boolean[] moving=new boolean[3];

    public String cursorResource(){return cursor;}
    public int constructionPanel(){return selected;}
    public int option(int panel,int selector){return options[panel][selector];}
    public int originX(int panel){return originX[panel];}
    public int originY(int panel){return originY[panel];}
    public boolean moving(int panel){return moving[panel];}
    /** Native15:255 waits for286C (builder0), regardless of construction target. */
    public long remainingMillis(int panel){return moving[panel]?334-moveMillis[panel]:0;}

    public void elapse(Dc4Host h,long milliseconds){
        if(milliseconds<0)throw new IllegalArgumentException("Negative PH UI elapsed time");
        builders[selected]=h.ui.current;
        for(int p=0;p<3;p++)if(moving[p]){
            moveMillis[p]=Math.min(334,moveMillis[p]+Math.min(334,milliseconds));
            double frames=Math.min(20,moveMillis[p]*60.0/1000.0);
            int x=frames==20?toX[p]:(int)(fromX[p]+(toX[p]-fromX[p])*frames/20.0);
            int y=frames==20?toY[p]:(int)(fromY[p]+(toY[p]-fromY[p])*frames/20.0);
            translate(builders[p],x-originX[p],y-originY[p]);originX[p]=x;originY[p]=y;
            if(frames==20)moving[p]=false;
        }
    }
    private static void translate(Dc3Ui.Panel p,int dx,int dy){
        if(p==null||dx==0&&dy==0)return;
        Set<Dc3Scene.Quad> quads=Collections.newSetFromMap(new IdentityHashMap<>());quads.addAll(p.quads);
        for(Dc3Ui.Button b:p.buttons){b.left+=dx;b.right+=dx;b.top+=dy;b.bottom+=dy;for(Dc3Ui.Sprite s:b.sprites)quads.add(s.quad);}
        for(Dc3Scene.Quad q:quads){q.x+=dx;q.y+=dy;}
        for(Dc3Ui.Text t:p.texts){t.args[0]+=dx;t.args[1]+=dy;}
    }

    public boolean command(Dc4Host h,String op,int[] a,byte[] bytes)throws IOException {
        if(!h.originalSystem)return false;
        builders[selected]=h.ui.current;
        switch(op){
        case "ui.panel":case "ui.template":{
            Dc3Ui.Panel previous=h.ui.current;
            boolean handled=h.baseCommand(op,a,bytes);
            if(op.equals("ui.panel")&&a[0]==0){
                // 41F2B0 clears all three construction surfaces, then the grid.
                Arrays.fill(builders,null);Arrays.fill(hover,"");Arrays.fill(click,"");
                for(int[] flags:options)Arrays.fill(flags,0);
                Arrays.fill(originX,0);Arrays.fill(originY,0);Arrays.fill(moving,false);Arrays.fill(moveMillis,0);
            }else if(previous!=h.ui.current){
                // 413180 ->412740 clears per-builder sound names and options
                // after the old builder has been copied into its template.
                hover[selected]=click[selected]="";Arrays.fill(options[selected],0);
                originX[selected]=originY[selected]=0;moving[selected]=false;moveMillis[selected]=0;
            }
            builders[selected]=h.ui.current;return handled;
        }
        case "ph.ui.move":{
            require(a.length==4,"panel move arguments");
            if(moving[selected])return true; // Native 37C gates overlapping moves.
            fromX[selected]=a[0];fromY[selected]=a[1];toX[selected]=a[2];toY[selected]=a[3];moveMillis[selected]=0;
            translate(h.ui.current,a[0]-originX[selected],a[1]-originY[selected]);originX[selected]=a[0];originY[selected]=a[1];
            moving[selected]=a[0]!=a[2]||a[1]!=a[3];return true;
        }
        case "ph.ui.notice":{
            // Native5B unconditionally builds from pointer28D4 (template21),
            // unlike59's flag-gated template12 at28B0.
            require(h.ui.templates[21]!=null,"notice template21");
            Dc3Ui.Panel original=h.ui.templates[12];
            try{h.ui.templates[12]=h.ui.templates[21];h.ui.confirmation();}
            finally{h.ui.templates[12]=original;}
            h.ui.confirmChecked=false;
            return h.baseCommand("ui.wait",new int[0],new byte[0]);
        }
        case "ui.resource":{
            require(a[0]>=0&&a[0]<6,"UI resource slot");
            String value=TextCodec.cp932(bytes);
            if(!value.equals("-"))h.scene.slots[37+a[0]].resource=value;
            return true;
        }
        case "ui.navigation":
            if(a[0]==99){
                selected=a[1]==1?1:a[1]==2?2:0;
                if(builders[selected]==null)builders[selected]=new Dc3Ui.Panel();
                h.ui.current=builders[selected];
            }else if(a[0]>=0&&a[0]<=8){
                // 0..4/8 are panel switches; 5..7 decorate the last button.
                // The common renderer retains their construction flags. Their
                // pulse/rotation timing remains outside the initial PH scope.
                options[selected][a[0]]=1;
                h.ui.current.flags|=1<<a[0];
            }
            return true;
        case "ui.action":{
            int n=a[0];
            if(n<0||n>16||n>=7&&n<=9)return true; // No native43 case for these selectors.
            h.ui.actions[n]=TextCodec.cp932(bytes);
            if(n<2)h.ui.actions[n+2]="";
            if(n==6)registeredWindow=h.fullscreen;
            if(n==16)idleRegistered=true;
            return true;
        }
        case "host.command_with_string":{
            h.ui.button(a,bytes);
            Dc3Ui.Button b=h.ui.last();
            if(!hover[selected].isEmpty())b.hoverSound=sound(h,hover[selected]);
            if(!click[selected].isEmpty())b.clickSound=sound(h,click[selected]);
            return true;
        }
        case "ph.ui.hover_sound":hover[selected]=TextCodec.cp932(bytes);return true;
        case "ph.ui.click_sound":click[selected]=TextCodec.cp932(bytes);return true;
        case "ph.ui.cursor_resource":cursor=TextCodec.cp932(bytes);if(cursor.equals("-"))cursor="";return true;
        case "ui.button_sprite":{
            require(a.length==8,"button sprite extension");
            int slot=a[6]&63;
            require(slot<43,"button texture slot");
            require(h.ui.last().sprites.size()<1024,"button sprite limit");
            Dc3Scene.Quad q=new Dc3Scene.Quad();
            q.resource=h.scene.pending[slot]!=null?h.scene.pending[slot]:h.scene.slots[slot].resource;
            if(q.resource.isEmpty())q.resource="@slot:"+slot;
            q.x=h.ui.current.x+a[0];q.y=h.ui.current.y+a[1];q.sx=a[2];q.sy=a[3];
            q.width=a[4];q.height=a[5];q.opaque=(a[6]&128)!=0;
            Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.quad=q;s.state=a[7];h.ui.last().sprites.add(s);
            return true;
        }
        default:return false;
        }
    }

    private static int sound(Dc4Host h,String source)throws IOException {
        String name=source.replace('/','\\');
        if(h.resources!=null){List<NamesCatalog.Entry> entries=h.resources.names.table("sound");
            for(int i=0;i<entries.size();i++)if(entries.get(i).name.replace('/','\\').equalsIgnoreCase(name))return i;
        }
        throw new IOException("PH default button sound is outside its NAMES catalog: "+source);
    }
    public void save(DataOutput out)throws IOException {
        out.writeInt(MAGIC);out.writeInt(selected);out.writeUTF(cursor);
        out.writeBoolean(idleRegistered);out.writeBoolean(registeredWindow);
        for(int p=0;p<3;p++){
            out.writeUTF(hover[p]);out.writeUTF(click[p]);for(int v:options[p])out.writeInt(v);
            out.writeBoolean(builders[p]!=null);if(builders[p]!=null)builders[p].save(out);
            out.writeInt(originX[p]);out.writeInt(originY[p]);out.writeInt(fromX[p]);out.writeInt(fromY[p]);out.writeInt(toX[p]);out.writeInt(toY[p]);out.writeLong(moveMillis[p]);out.writeBoolean(moving[p]);
        }
    }
    public void load(DataInput in)throws IOException {
        require(in.readInt()==MAGIC,"UI snapshot version");selected=in.readInt();
        require(selected>=0&&selected<3,"construction panel");cursor=smallString(in);
        idleRegistered=in.readBoolean();registeredWindow=in.readBoolean();
        for(int p=0;p<3;p++){
            hover[p]=smallString(in);click[p]=smallString(in);
            for(int i=0;i<9;i++){options[p][i]=in.readInt();require(options[p][i]>=0&&options[p][i]<=1,"UI option state");}
            builders[p]=null;if(in.readBoolean()){builders[p]=new Dc3Ui.Panel();builders[p].load(in);}
            originX[p]=in.readInt();originY[p]=in.readInt();fromX[p]=in.readInt();fromY[p]=in.readInt();toX[p]=in.readInt();toY[p]=in.readInt();moveMillis[p]=in.readLong();moving[p]=in.readBoolean();
            require(moveMillis[p]>=0&&moveMillis[p]<=334,"UI move clock");
            require(!moving[p]||moveMillis[p]<334&&builders[p]!=null,"UI move state");
        }
    }
    private static String smallString(DataInput in)throws IOException{String s=in.readUTF();require(s.length()<=260,"UI resource name");return s;}
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
