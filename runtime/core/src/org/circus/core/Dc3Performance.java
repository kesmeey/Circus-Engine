package org.circus.core;

import java.io.*;
import java.util.*;

/** Byte-stage DD inline image swaps. The remaining macros still fail in NativeText. */
public final class Dc3Performance {
    public static final class Event {public int source,target,kind,value;public String resource="";public long at;}
    public interface Sink {void accept(Event event);}
    public final List<Event> events=new ArrayList<>();
    public long elapsed;
    public byte[] parse(byte[] input,int textSpeed)throws IOException {
        events.clear();elapsed=0;ByteArrayOutputStream out=new ByteArrayOutputStream();int target=4,explicitDelay=0;
        for(int i=0;i<input.length;){
            if(i+2<input.length&&input[i]=='@'&&input[i+1]=='b'&&input[i+2]>='1'&&input[i+2]<='5'){target=input[i+2]-'1'+4;i+=3;continue;}
            if(i+1<input.length&&input[i]=='@'&&input[i+1]=='t'){
                i+=2;long value=0;while(i<input.length&&input[i]>='0'&&input[i]<='9'){value=value*10+input[i++]-'0';if(value>864000)throw new IOException("Inline delay limit");}explicitDelay=(int)value;continue;
            }
            int length=0,source=0,destination=target,kind=0;
            int value=0;
            if(starts(input,i,"@muf")){kind=5;length=4;}
            else if(starts(input,i,"@mu")){kind=4;length=3;}
            else if(starts(input,i,"@lse")){kind=8;length=4;}
            else if(starts(input,i,"@se")){kind=3;length=3;}
            else if(starts(input,i,"@hy")){kind=6;length=3;}
            else if(starts(input,i,"@ht")){kind=7;length=3;}
            else if(i+3<input.length&&input[i]=='@'&&input[i+1]=='w'&&input[i+2]=='b'&&input[i+3]>='1'&&input[i+3]<='5'){length=4;source=input[i+3]-'1'+19;destination=1;}
            else if(i+2<input.length&&input[i]=='@'&&input[i+1]=='w'&&input[i+2]>='1'&&input[i+2]<='5'){length=3;source=input[i+2]-'1'+19;}
            else if(i+3<input.length&&input[i]=='@'&&input[i+1]=='b'&&(input[i+2]=='t'||input[i+2]=='y')&&input[i+3]>='1'&&input[i+3]<='5'){length=4;destination=input[i+3]-'1'+4;kind=input[i+2]=='t'?1:2;}
            if(length!=0){
                if(kind==3||kind==4||kind==5||kind==8)while(i+length<input.length&&input[i+length]>='0'&&input[i+length]<='9'){value=value*10+input[i+length++]-'0';if(value>86400000)throw new IOException("Inline audio operand limit");}
                if(events.size()>=10)throw new IOException("Native inline event limit");Event e=new Event();e.source=source;e.target=destination;e.kind=kind;
                e.value=value;
                int divisor=textSpeed*2+16;if(divisor<=0)throw new IOException("Invalid native text speed");e.at=explicitDelay!=0?explicitDelay*100L:(long)(((out.size()/2*22)/divisor)*16.65999984741211);
                events.add(e);explicitDelay=0;i+=length;continue;
            }
            out.write(input[i++]);
        }
        return out.toByteArray();
    }
    private static boolean starts(byte[] b,int at,String s){if(at+s.length()>b.length)return false;for(int i=0;i<s.length();i++)if(b[at+i]!=s.charAt(i))return false;return true;}
    public void elapse(long millis,Dc3Scene scene){elapse(millis,scene,null);}
    public void elapse(long millis,Dc3Scene scene,Sink sink){elapsed=Math.min(86400000,elapsed+Math.max(0,millis));for(Iterator<Event> it=events.iterator();it.hasNext();){Event e=it.next();if(e.at<=elapsed){
        if(e.kind==6||e.kind==7){for(int s=0;s<43;s++)scene.shake(s,e.kind==6?0:1,true);}
        else if(e.kind>=3){if(sink==null)throw new IllegalStateException("Audio event needs a host");sink.accept(e);}
        else if(e.kind!=0){if(e.target<8)scene.shake(e.target,e.kind==1?1:0,true);} // native bounds omit the fifth actor
        else {String source=scene.slots[e.source].resource,target=scene.slots[e.target].resource;if(e.target!=1&&!target.contains("_B,")&&!target.contains("_B*,")&&!target.contains("_B@,"))source=source.replace("_B,",",").replace("_B*,",",").replace("_B@,",",");scene.slots[e.target].resource=source;scene.slots[e.source].resource="";scene.revision++;}
        it.remove();}}}
    public void save(DataOutput out)throws IOException{out.writeLong(elapsed);out.writeInt(events.size());for(Event e:events){out.writeInt(e.source);out.writeInt(e.target);out.writeInt(e.kind);out.writeInt(e.value);out.writeUTF(e.resource);out.writeLong(e.at);}}
    public void load(DataInput in)throws IOException{elapsed=in.readLong();int n=in.readInt();if(n<0||n>10||elapsed<0)throw new IOException("Invalid inline event state");events.clear();for(int i=0;i<n;i++){Event e=new Event();e.source=in.readInt();e.target=in.readInt();e.kind=in.readInt();e.value=in.readInt();e.resource=in.readUTF();e.at=in.readLong();if(e.kind<0||e.kind>8||e.kind==0&&(e.source<19||e.source>23)||e.target!=1&&(e.target<4||e.target>8)||e.value<0||e.value>86400000||e.at<0||e.at>86400000)throw new IOException("Invalid inline event");events.add(e);}}
}
