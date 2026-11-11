package org.circus.core;

import java.io.*;
import java.util.*;

/** ABB6 opcode95 mode4: logical PCK33 plays once on native render object66.
 * Native447AC0 installs frame0 immediately;448CE0 advances when the frame
 * counter is strictly greater than the requested threshold, then unloads the
 * last texture. The portable render clock approximates native updates at60Hz. */
public final class Dc4Animation {
    private static final int MAGIC=0x50484131;
    private String pack="";
    private int frames,frame,x,y,threshold,counter,clock;
    private boolean enabled,initialized;
    public boolean active(){return enabled;}
    public int frame(){return frame;}
    public int frameCount(){return frames;}
    public int x(){return x;}
    public int y(){return y;}
    public String archive(){return pack;}
    public String resource(){return enabled?"pck:"+pack+"#"+frame:"";}
    public Set<String> resources(){return enabled?Collections.singleton(resource()):Collections.emptySet();}
    public void reset(){pack="";frames=frame=x=y=threshold=counter=clock=0;enabled=initialized=false;}

    public boolean command(Dc4Host h,String op,int[] a)throws IOException{
        if(!op.equals("ph.scene.motion"))return false;
        require(a.length==4&&a[3]==4,"animation mode outside audited95:4");
        String reference=h.scene.pending[33]!=null?h.scene.pending[33]:h.scene.slots[33].resource;
        // No loaded PCK33 handle means no native95:4 call.
        if(reference==null||reference.isEmpty()||reference.equals("-"))return true;
        require(h.resources!=null,"animation resources");start(h.resources.files,reference,a[0],a[1],a[2]);return true;
    }
    /** Only PCK index and CRX headers are inspected, never all decoded frames. */
    public void start(GameResources files,String reference,int x,int y,int threshold)throws IOException{
        String name=Binary.nativeResourcePath(reference);if(!name.toLowerCase(Locale.ROOT).endsWith(".pck"))name+=".PCK";
        Archive archive=files.archive("AdvData/GRP/"+name);require(archive.kind.equals("PCK"),"animation PCK index");
        // All eleven actual effect packs have the same full-screen CRX shape.
        for(Archive.Entry entry:archive.entries){byte[] h=archive.source(entry).read(0,20);
            require(Binary.u32(h,0)==0x47585243L&&Binary.i16(h,4)==0&&Binary.i16(h,6)==0&&Binary.u16(h,8)==1280&&Binary.u16(h,10)==720,"animation frame geometry");
        }
        start(name,archive.entries.size(),x,y,threshold);
    }
    /** Numeric fixture admission mirrors an already checked PCK index. */
    public void start(String reference,int count,int x,int y,int threshold)throws IOException{
        require(count>=1&&count<=1000000&&threshold>=0&&threshold<=32767,"animation count/timing");
        require(x>=Short.MIN_VALUE&&x<=Short.MAX_VALUE&&y>=Short.MIN_VALUE&&y<=Short.MAX_VALUE,"animation position");
        String name=Binary.nativeResourcePath(reference);require(!name.isEmpty()&&name.length()<=260,"animation archive name");
        pack=name;frames=count;this.x=x;this.y=y;this.threshold=threshold;frame=counter=clock=0;enabled=true;initialized=false;
    }
    private long remainingTicks(){
        if(!enabled)return 0;
        long next=Math.max(1,threshold-counter+2);
        return(initialized?0:1)+next+(frames-frame-1L)*(threshold+1L);
    }
    /** For15:254, which waits until native queued effects have completed. */
    public long remainingMillis(){return enabled?Math.max(1,(remainingTicks()*1000-clock+59)/60):0;}
    public void elapse(long milliseconds){
        if(milliseconds<0)throw new IllegalArgumentException("Negative PH animation elapsed time");
        if(!enabled)return;
        long ticks=(milliseconds/1000)*60+(clock+(milliseconds%1000)*60)/1000;
        clock=(int)((clock+(milliseconds%1000)*60)%1000);
        if(!initialized&&ticks>0){initialized=true;ticks--;}
        if(ticks<=0)return;
        long next=Math.max(1,threshold-counter+2);
        if(ticks<next){counter+=(int)ticks;return;}
        ticks-=next;frame++;counter=1;
        if(frame>=frames){frame=frames;enabled=false;counter=0;return;}
        long advances=ticks/(threshold+1L);
        if(advances>=frames-frame){frame=frames;enabled=false;counter=0;return;}
        frame+=(int)advances;counter+=ticks%(threshold+1L);
    }
    public void save(DataOutput out)throws IOException{
        out.writeInt(MAGIC);out.writeUTF(pack);out.writeInt(frames);out.writeInt(frame);out.writeInt(x);out.writeInt(y);out.writeInt(threshold);out.writeInt(counter);out.writeInt(clock);out.writeBoolean(enabled);out.writeBoolean(initialized);
    }
    public void load(DataInput in)throws IOException{
        require(in.readInt()==MAGIC,"animation snapshot version");String name=in.readUTF();int count=in.readInt(),index=in.readInt(),left=in.readInt(),top=in.readInt(),limit=in.readInt(),elapsed=in.readInt(),remainder=in.readInt();boolean active=in.readBoolean(),begun=in.readBoolean();
        require(name.length()<=260&&count>=0&&count<=1000000&&index>=0&&index<=count&&limit>=0&&limit<=32767&&elapsed>=0&&elapsed<=limit+1&&remainder>=0&&remainder<1000,"animation snapshot state");
        require(left>=Short.MIN_VALUE&&left<=Short.MAX_VALUE&&top>=Short.MIN_VALUE&&top<=Short.MAX_VALUE,"animation snapshot position");
        require(!active||count>0&&index<count&&!name.isEmpty(),"active animation snapshot");
        require(!active||begun||index==0&&elapsed==0,"animation initialization snapshot");
        require(name.isEmpty()||name.equals(Binary.nativeResourcePath(name)),"animation snapshot archive path");
        pack=name;frames=count;frame=index;x=left;y=top;threshold=limit;counter=elapsed;clock=remainder;enabled=active;initialized=begun;
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Invalid PH "+what);}
}
