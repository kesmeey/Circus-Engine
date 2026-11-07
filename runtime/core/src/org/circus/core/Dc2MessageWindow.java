package org.circus.core;

import java.io.*;
import java.util.*;

/** Frozen native message-window records, separate from old body glyphs.
 * Preserve ordered /16 blits; a transparent flattened bitmap loses rounding. */
public final class Dc2MessageWindow {
    public boolean effects;
    public int weight;
    public String[] slots=new String[32];
    public int[] palette=new int[64];
    public final List<Dc2Ui.Quad> quads=new ArrayList<>();
    public Dc2MessageWindow(){Arrays.fill(slots,"");Arrays.fill(palette,0xff000000);}
    public static Dc2MessageWindow capture(Dc2Host host)throws IOException{return capture(host,host.messageMode()==0?1:4);}
    public static Dc2MessageWindow capture(Dc2Host host,int selector)throws IOException{
        if(!host.resources.profile.id.equals("dc2-3363"))throw new IOException("Original DC2 message-window owner required");
        if(selector<0||selector>=host.ui.quadTemplates.size())throw new IOException("DC2 message-window selector");
        Dc2MessageWindow result=new Dc2MessageWindow();result.effects=host.read(99,10)!=0;result.weight=host.read(99,11);result.slots=host.scene.slots.clone();result.palette=host.ui.palette.clone();
        List<Dc2Ui.Quad> source=host.ui.quadTemplates.get(selector);
        for(Dc2Ui.Quad q:Dc2Ui.copyQuads(source.subList(0,Math.min(10,source.size())))){
            if(q.type==2||q.nativeCondition!=0&&host.read(99,251)==0||q.binding!=0&&host.variables.read(q.binding>>8,q.binding&255)==0)continue;
            if(q.animation!=0&&q.frames>0&&q.period>0)q.sx+=(int)(host.scene.effectClock/(34L*q.period)%q.frames)*q.step;
            q.binding=q.nativeCondition=q.animation=0;q.nativeTextOffset=-1;result.quads.add(q);
        }
        result.validate();return result;
    }
    private void validate()throws IOException{
        if(weight<0||weight>255||slots==null||slots.length<1||slots.length>64||palette==null||palette.length!=64||quads.size()>10)throw new IOException("Invalid DC2 message-window state");
        for(String slot:slots)if(slot==null)throw new IOException("Missing DC2 window resource");
        for(Dc2Ui.Quad q:quads)if(q.type<0||q.type>1||q.binding!=0||q.nativeCondition!=0||q.nativeTextOffset!=-1||q.type==0&&q.slot()>=slots.length)throw new IOException("Invalid DC2 window draw record");
    }
    public static void save(DataOutput out,Dc2MessageWindow value)throws IOException{
        out.writeBoolean(value!=null);if(value==null)return;value.validate();out.writeInt(0x44325731);out.writeBoolean(value.effects);out.writeInt(value.weight);out.writeInt(value.slots.length);for(String slot:value.slots)out.writeUTF(slot);for(int color:value.palette)out.writeInt(color);Dc2Ui.saveQuads(out,value.quads);
    }
    public static Dc2MessageWindow load(DataInput in)throws IOException{
        if(!in.readBoolean())return null;if(in.readInt()!=0x44325731)throw new IOException("DC2 message-window tag");Dc2MessageWindow value=new Dc2MessageWindow();value.effects=in.readBoolean();value.weight=in.readInt();int count=in.readInt();if(count<1||count>64)throw new IOException("DC2 message-window slots");value.slots=new String[count];for(int n=0;n<count;n++)value.slots[n]=in.readUTF();for(int n=0;n<64;n++)value.palette[n]=in.readInt();value.quads.addAll(Dc2Ui.loadQuads(in,10));value.validate();return value;
    }
}
