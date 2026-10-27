package org.circus.core;

import java.io.*;
import java.util.*;

/** Frozen inputs needed by renderers; no graphics toolkit belongs in the VM. */
public final class SceneCaptures {
    private SceneCaptures(){}
    public static final class Legacy {
        public final Dc2Scene.Frame frame=new Dc2Scene.Frame();public final int[] palette=new int[64];
        public int particleKind,particleStrength,overlayKind;public long clock;public boolean effects;
        public Legacy(DataInput in)throws IOException{frame.load(in);for(int n=0;n<64;n++)palette[n]=in.readInt();particleKind=in.readInt();particleStrength=in.readInt();overlayKind=in.readInt();clock=in.readLong();effects=in.readBoolean();}
    }
    public static UiCapture legacy(Dc2Scene.Frame frame,Dc2Scene scene,Dc2Ui ui,Variables variables,boolean effects,UiCapture previous)throws IOException {
        boolean depends=false;for(String resource:frame.slots)depends|=resource.equals("@ui-capture");
        // Bindings and sprite animation must not follow later settings/clock changes.
        for(Iterator<Dc2Ui.Quad> it=frame.quads.iterator();it.hasNext();){Dc2Ui.Quad q=it.next();if(q.binding!=0&&variables!=null&&variables.read(q.binding>>8,q.binding&255)==0){it.remove();continue;}q.binding=0;
            if(q.animation!=0&&q.frames>0&&q.period>0)q.sx+=(int)(scene.effectClock/(34L*q.period)%q.frames)*q.step;q.animation=0;
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);frame.save(out);for(int color:ui.palette)out.writeInt(color);
        out.writeInt(scene.particleKind);out.writeInt(scene.particleStrength);out.writeInt(scene.overlayKind);out.writeLong(scene.effectClock);out.writeBoolean(effects);
        return UiCapture.logical(UiCapture.LEGACY,bytes.toByteArray(),depends,previous);
    }
    static void legacySliders(Dc2Scene.Frame frame,Dc2Ui ui,Variables variables)throws IOException {
        if(variables==null)return;
        for(int index=0;index<ui.buttons.size();index++){Dc2Ui.Button button=ui.buttons.get(index);Dc2Ui.Quad cfg=null,stops=null;
            for(Dc2Ui.Quad q:button.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)continue;
            int state=index==ui.pressed?26:index==ui.hovered?25:24;boolean found=false;for(Dc2Ui.Quad q:button.sprites)found|=q.state==state;if(!found)state=24;
            int value=Math.max(0,Math.min(cfg.sx-1,variables.read(cfg.sy,cfg.width))),dx=(cfg.y-cfg.x)*value/Math.max(1,cfg.sx-1),left=cfg.x,right=cfg.x+dx;
            if(stops!=null){int[] ends={cfg.x,stops.x,stops.y,stops.sx,stops.sy};left=ends[Math.min(3,value)];right=ends[Math.min(3,value)+1];}
            for(Dc2Ui.Quad sprite:button.sprites)if(sprite.state==2||sprite.state==12||sprite.state==6||sprite.state==state){Dc2Ui.Quad q=Dc2Ui.copyQuads(Collections.singletonList(sprite)).get(0);
                if(q.state==2||q.state==12){int l=Math.max(q.x,left),r=Math.min(q.x+q.width,right);q.sx+=l-q.x;q.x=l;q.width=Math.max(0,r-l);}
                else if(q.state==6)q.x+=dx;else{q.width/=Math.max(1,cfg.sx);q.sx+=q.width*value;}
                if(q.type==2){q.width=23;q.height=q.flags;}if(q.width>0)frame.quads.add(q);
            }
        }
    }
    public static final class Dd {
        public final Dc3Scene.Frame frame=new Dc3Scene.Frame();public final Dc3Ui.Panel panel=new Dc3Ui.Panel();public final int[] palette=new int[64];
        public Dd(DataInput in)throws IOException{frame.load(in);panel.load(in);for(int n=0;n<64;n++)palette[n]=in.readInt();}
    }
    public static UiCapture dd(Dc3Scene.Frame frame,Dc3Ui.Panel panel,int[] palette,UiCapture previous)throws IOException {
        boolean depends=false;for(Dc3Scene.Layer l:frame.layers)depends|=l.resource.equals("@ui-capture");for(Dc3Scene.Quad q:frame.quads)depends|=q.resource.equals("@ui-capture");for(Dc3Scene.Quad q:panel.quads)depends|=q.resource.equals("@ui-capture");for(Dc3Ui.Button b:panel.buttons)for(Dc3Ui.Sprite s:b.sprites)depends|=s.quad.resource.equals("@ui-capture");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);frame.save(out);panel.save(out);for(int color:palette)out.writeInt(color);
        return UiCapture.logical(UiCapture.DD,bytes.toByteArray(),depends,previous);
    }
    public static Dc3Ui.Panel ddPanel(Dc3Ui ui,Variables variables)throws IOException {
        Dc3Ui.Panel result=new Dc3Ui.Panel();
        if(ui.underlay!=null)append(result,ui.underlay,-1,-1,false,false,variables);
        append(result,ui.current,ui.hovered,ui.pressed,ui.activeConfirmation>=0,ui.confirmChecked,variables);
        return result;
    }
    private static void append(Dc3Ui.Panel result,Dc3Ui.Panel source,int hovered,int pressed,boolean modal,boolean checked,Variables variables)throws IOException {
        if(source.hidden)return;Dc3Ui.Panel p=Dc3Ui.copy(source);result.quads.addAll(p.quads);for(Dc3Ui.Text t:p.texts){t.args[3]=t.args[2];result.texts.add(t);}
        for(int n=0;n<p.buttons.size();n++){Dc3Ui.Button b=p.buttons.get(n);int state=n==pressed?2:n==hovered?1:99;boolean checkbox=modal&&b.action.equals(":c");if(checkbox&&state==2)state=1;
            boolean found=false;Dc3Scene.Quad config=null;for(Dc3Ui.Sprite s:b.sprites){found|=s.state==state;if(s.state==5)config=s.quad;}
            for(Dc3Ui.Sprite s:b.sprites)if(s.state==(found?state:99)||checkbox&&checked&&s.state==2)result.quads.add(s.quad);
            if(config!=null&&variables!=null){int value=variables.read(config.sy,config.width),offset=(config.y-config.x)*Math.max(0,Math.min(config.sx-1,value))/Math.max(1,config.sx-1);
                for(Dc3Ui.Sprite s:b.sprites)if(s.state==2||s.state==6){Dc3Scene.Quad q=s.quad.copy();if(s.state==6)q.x+=offset;else{int left=Math.max(q.x,config.x),right=Math.min(q.x+q.width,config.x+offset);q.sx+=left-q.x;q.x=left;q.width=Math.max(0,right-left);}if(q.width>0)result.quads.add(q);}
            }
        }
    }
}
