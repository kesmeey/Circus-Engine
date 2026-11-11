package org.circus.core;

import java.io.*;
import java.util.*;

/** ABB6 B0 recording / @cmdN and @dN replay. Native recording skips handlers.
 * Recorded group0 lives at41E1C, exactly ten844-byte records after replay
 * origin3CB74; therefore the public text trigger for group0 is @d1. */
public final class Dc4Macros {
    private static final int MAGIC=0x50484d31;
    private static final byte[] EMPTY=new byte[0];
    private static final class Entry {String operation;int[] args;byte[] bytes;}
    private static final class Event {int group;long at;}
    private final Entry[][] groups=new Entry[10][10];
    private final List<Event> events=new ArrayList<>();
    private int selected,cursor;
    private boolean recording,resetAtRecording=true,replaying;
    private long elapsed;

    /** -1 dispatches normally. Any other result is the exact already-fetched next file PC. */
    public int intercept(MesScript script,MesScript.Instruction in)throws IOException {
        if(in.record.opcode==0xb0){
            if(in.args[0]==255){
                require(in.args[1]>=0,"negative recording group");
                int next=Math.min(in.args[1],9);if(next!=selected){selected=next;cursor=0;}
            }else{
                recording=!recording;
                if(recording&&resetAtRecording){for(Entry[] g:groups)Arrays.fill(g,null);selected=cursor=0;resetAtRecording=false;}
            }
            return in.next;
        }
        if(!recording)return -1;
        if(in.record.opcode==0x6a||in.record.opcode==0x6b){selected=cursor=0;return -1;}
        // The native gate precedes owning handlers. Their following strings are
        // separate fetches while recording. No PH corpus macro uses these UI ops;
        // reject this uncommon case instead of swallowing a guessed payload.
        int op=in.record.opcode;
        require(op!=0x72&&op!=0x76&&op!=0x80&&op!=0x87&&op!=0x5c&&op!=0x5f,"UI payload in recorded command");
        require(cursor<10,"recording exceeds ten commands");
        Entry entry=new Entry();entry.operation=script.profile.operation(op);
        entry.args=in.args.clone();entry.bytes=script.nativeText(in.record);
        groups[selected][cursor++]=entry;
        return in.next;
    }

    public byte[] parseBody(byte[] source,int textSpeed)throws IOException {
        if(resetAtRecording){for(Entry[] group:groups)Arrays.fill(group,null);selected=cursor=0;}
        events.clear();elapsed=0;resetAtRecording=true;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int delayStart=-1,delayEnd=-1,explicitDelay=0;
        for(int i=0;i<source.length;){
            if(starts(source,i,"@t")){
                int end=i+2,value=0;while(end<source.length&&digit(source[end])){value=operand(value,source[end++]);}
                delayStart=out.size();out.write(source,i,end-i);delayEnd=out.size();explicitDelay=value;i=end;continue;
            }
            int width=starts(source,i,"@cmd")?4:starts(source,i,"@d")?2:0;
            if(width!=0){
                int end=i+width,value=0;while(end<source.length&&digit(source[end]))value=operand(value,source[end++]);
                require(value>=1&&value<=10,"inline macro group "+value);
                byte[] prefix=out.toByteArray();
                if(delayStart>=0){out.reset();out.write(prefix,0,delayStart);out.write(prefix,delayEnd,prefix.length-delayEnd);prefix=out.toByteArray();}
                require(events.size()<10,"inline macro event count");
                Event event=new Event();event.group=value-1;
                int divisor=textSpeed*2+16;require(divisor>0,"text speed");
                int bytes=new Dc3Performance().parse(prefix,textSpeed).length;
                event.at=explicitDelay!=0?explicitDelay*100L:(long)(((bytes/2*22)/divisor)*16.65999984741211);
                events.add(event);delayStart=delayEnd=-1;explicitDelay=0;i=end;continue;
            }
            // An intervening shared inline event owns the most recent @t.
            if(sharedEvent(source,i)){delayStart=delayEnd=-1;explicitDelay=0;}
            out.write(source[i++]);
        }
        return out.toByteArray();
    }
    private static int operand(int value,byte b)throws IOException{long n=value*10L+b-'0';require(n<=864000,"inline numeric operand");return(int)n;}
    private static boolean digit(byte b){return b>='0'&&b<='9';}
    private static boolean starts(byte[] b,int at,String s){if(at+s.length()>b.length)return false;for(int i=0;i<s.length();i++)if(b[at+i]!=s.charAt(i))return false;return true;}
    private static boolean sharedEvent(byte[] b,int at){return starts(b,at,"@mu")||starts(b,at,"@se")||starts(b,at,"@lse")||starts(b,at,"@hy")||starts(b,at,"@ht")||starts(b,at,"@w")||starts(b,at,"@bt")||starts(b,at,"@by");}

    public void beforePublish(Dc4Host h)throws IOException{dispatch(h,false);}
    public void elapse(Dc4Host h,long ms)throws IOException{elapsed=Math.min(86400000,elapsed+Math.max(0,Math.min(86400000,ms)));dispatch(h,true);}
    public void finish(Dc4Host h)throws IOException{elapse(h,86400000);}
    private void dispatch(Dc4Host h,boolean publish)throws IOException {
        if(replaying)return;
        for(Iterator<Event> it=events.iterator();it.hasNext();){Event event=it.next();if(event.at>elapsed)continue;it.remove();replaying=true;
            try{
                for(Entry entry:groups[event.group]){
                    if(entry==null)break;
                    String op=entry.operation;
                    require(!op.startsWith("variable.")&&!op.startsWith("script.")&&!op.startsWith("branch.")&&!op.startsWith("jump.")&&!op.equals("stream.stop")&&!op.equals("unknown"),"unreviewed recorded VM operation "+op);
                    if(op.equals("native.no_effect")||op.equals("control.marker"))continue;
                    require(h.command(op,entry.args.clone(),entry.bytes.clone()),"recorded host operation "+op);
                }
                if(publish)h.baseCommand("scene.transition",new int[]{5,0},EMPTY);
            }finally{replaying=false;}
        }
    }
    public boolean recording(){return recording;}
    public boolean replaying(){return replaying;}
    public int recordedCount(int group){int n=0;for(Entry e:groups[group])if(e!=null)n++;return n;}
    public int pendingCount(){return events.size();}
    public void save(DataOutput out)throws IOException {
        require(!replaying,"snapshot during macro callback");out.writeInt(MAGIC);out.writeInt(selected);out.writeInt(cursor);
        out.writeBoolean(recording);out.writeBoolean(resetAtRecording);out.writeLong(elapsed);
        for(Entry[] group:groups)for(Entry e:group){out.writeBoolean(e!=null);if(e!=null){out.writeUTF(e.operation);out.writeInt(e.args.length);for(int a:e.args)out.writeInt(a);out.writeInt(e.bytes.length);out.write(e.bytes);}}
        out.writeInt(events.size());for(Event e:events){out.writeInt(e.group);out.writeLong(e.at);}
    }
    public void load(DataInput in)throws IOException {
        require(in.readInt()==MAGIC,"macro snapshot version");selected=in.readInt();cursor=in.readInt();
        require(selected>=0&&selected<10&&cursor>=0&&cursor<=10,"macro recording cursor");recording=in.readBoolean();resetAtRecording=in.readBoolean();elapsed=in.readLong();require(elapsed>=0&&elapsed<=86400000,"macro clock");
        for(Entry[] group:groups)for(int i=0;i<10;i++){
            group[i]=null;if(in.readBoolean()){Entry e=new Entry();e.operation=in.readUTF();require(e.operation.length()<=128,"macro operation name");int n=in.readInt();require(n>=0&&n<=8,"macro argument count");e.args=new int[n];for(int j=0;j<n;j++)e.args[j]=in.readInt();n=in.readInt();require(n>=0&&n<8192,"macro string length");e.bytes=new byte[n];in.readFully(e.bytes);group[i]=e;}
        }
        events.clear();int n=in.readInt();require(n>=0&&n<=10,"macro event count");for(int i=0;i<n;i++){Event e=new Event();e.group=in.readInt();e.at=in.readLong();require(e.group>=0&&e.group<10&&e.at>=0&&e.at<=86400000,"macro event");events.add(e);}replaying=false;
    }
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Unimplemented PH "+what);}
}
