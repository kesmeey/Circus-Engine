package org.circus.core;

import java.io.*;
import java.util.*;

/** PH scene commands from the ABB6 interpreter, with edition-owned pending state. */
public final class Dc4SceneCommands {
    private boolean centerPending,fitPending;
    private boolean retainBackground;
    public boolean cameraLatch;
    private int effectKind,effectMillis;
    private String effectResource="";
    private int portraitVisible=1;
    public int commentType,commentOffset;
    public boolean commentsVisible;
    private int particleX,particleY,particleAngle,particleTarget=-1,particleTicks;
    private final int[] relativeX=new int[5],relativeY=new int[5];
    private final boolean[] relativeBase=new boolean[5];
    private final Dc4Sway sway=new Dc4Sway();
    public long animationRemaining(){return sway.remainingMillis();}
    public void stopCastMotion(Dc4Host h){for(int s=4;s<14;s++)h.scene.stop(s);sway.reset(h);}
    private static void require(boolean ok,String what)throws IOException{if(!ok)throw new IOException("Unimplemented PH scene "+what);}
    private static byte[] raw(String text){return text.getBytes(Binary.SJIS);}
    private static String text(byte[] bytes)throws IOException{return TextCodec.cp932(bytes);}
    private static void slot(int n)throws IOException{require(n>=0&&n<43,"resource slot "+n);}
    private static int actor(int n)throws IOException{require(n>=0&&n<5,"actor "+n);return n+4;}

    public boolean command(Dc4Host h,String op,int[] a,byte[] bytes)throws IOException{
        switch(op){
        case "ph.scene.resource":{
            int s=a[0];slot(s);if(s==0)return true;
            String name=text(bytes);
            if(name.startsWith("*")){try{name=h.resources.named("images",Integer.parseInt(name.substring(1)));}catch(NumberFormatException invalid){throw new IOException("Invalid PH image catalog number",invalid);}}
            // The shared loader retains native names, '?' variants and the
            // data-owned codecs. PCK33..36 are prepared for the separate95
            // animation consumer, rather than guessed as a loose CRX file.
            if(s>=33&&s<=36){h.scene.pending[s]=name;return true;}
            if(s==1){
                // PH40F40 controls background-object retention, not the
                // dialogue template. Reuse the loader's retention branch only
                // while binding the image, then restore PH's regular message.
                h.scene.novelMode=retainBackground;
                try{return h.baseCommand("image.load",a,raw(name));}
                finally{retainBackground=h.scene.novelMode;h.scene.novelMode=false;}
            }
            return h.baseCommand("image.load",a,raw(name));
        }
        case "ph.scene.stop":h.scene.stop();sway.reset(h);return true;
        case "ph.scene.atmosphere":
            // 425B90(0) stops all five particle planes plus26 fog and27
            // moving fog. 4219A0/425130 are the same3072x600 two-row crop
            // controller represented by the shared clouds presentation owner.
            if(a[0]==0||a[0]==7||a[0]==16)return h.baseCommand("scene.effect",a,bytes);
            if(a[0]==1||a[0]==2||a[0]==3||a[0]==9||a[0]==11||a[0]==13){
                int s=a[0]==2?29:28;
                String resource=a[0]==13?"SYSTEM/EFFYOUSEI":a[0]==2?"SYSTEM/EFFRAIN":null;
                if(resource!=null){h.resources.image(resource);h.scene.pending[s]=resource;}
                h.scene.petals.startPh(a[0],a[1],particleX,particleY,particleAngle,particleTarget,particleTicks);return true;
            }
            throw new IOException("Unimplemented PH atmosphere "+Arrays.toString(a));
        case "ph.scene.atmosphere_config":
            if(a[0]==0){particleX=a[1];particleY=a[2];}else{particleAngle=a[1];particleTarget=a[2];particleTicks=a[3]*12;require(particleTicks>=0,"particle angle duration");}return true;
        case "ph.scene.center":centerPending=true;return true;
        case "ph.scene.fit_background":fitPending=true;return true;
        case "ph.scene.background":{
            String name=text(bytes);
            if(name.equals(":n")||name.equals(":r")){
                // 42B13F/42B15F writes40F40 and5D64. Neither flag selects
                // message templates3/4; PH keeps its regular templates0/1.
                retainBackground=cameraLatch=name.equals(":n");h.scene.novelMode=false;return true;
            }
            if(!name.startsWith(":")){fitPending=false;h.scene.slots[1].centered=false;clearEffect(h);sway.reset(h);}
            return h.baseCommand("scene.background",a,bytes);
        }
        case "ph.scene.character":return h.baseCommand("scene.character",a,bytes);
        case "ph.scene.character_overlay":return h.baseCommand("scene.character_overlay",a,bytes);
        case "ph.scene.overlay":{
            String name=text(bytes);int s=25;
            if(name.equals("--")){require(h.systemScene!=null,"overlay restore without caller");h.scene.slots[s]=h.systemScene.layers[s].copy();return true;}
            if(name.equals("-")){h.scene.pending[s]="-";return true;}
            if(name.equals("-n")){portraitVisible=a[0];if(portraitVisible==0)h.scene.slots[s].alpha=0;return true;}
            if(name.equals("-a")){h.scene.slots[s].alpha=Math.max(0,Math.min(255,a[0]*255));return true;}
            if(name.equals("-m")){h.scene.slots[s].alpha=255;return true;}
            // Native40 binds the resource already prepared by3C25. Ordinary
            // names and '.' do not replace that prepared filename themselves.
            h.scene.revision++;return true;
        }
        case "scene.character_transform":if(a[3]!=255)transform(h,actor((a[3]==0?a[2]:a[3]&15)),a,false);return true;
        case "ph.scene.overlay_transform":transform(h,25,a,true);return true;
        case "ph.scene.character_move":{
            int flags=a[3];
            if(sway.command(h,a))return true;
            boolean relative=(a[2]&0x40)!=0;int index=a[2]&15,s=actor(index);Dc3Scene.Layer l=h.scene.slots[s];
            if(relative&&!relativeBase[index]){relativeX[index]=l.x;relativeY[index]=l.y;relativeBase[index]=true;}
            int x=a[0]+(relative?relativeX[index]:0),y=a[1]+(relative?relativeY[index]:0);
            // Native4472A0 queues6 ticks per unit. The shared scene move
            // owner supplies bounded interpolation; compound movement batches
            // currently retain the last destination rather than every curve.
            h.scene.move(s,x,y,l.alpha,flags==0?1000:Math.max(1,flags)*100L);return true;
        }
        case "ph.scene.image_tone":require(a[0]>=0&&a[0]<=2,"image tint "+a[0]);return h.baseCommand("scene.image_tone",a,bytes);
        case "ph.scene.effect":
            require((a[0]==1||a[0]==2)&&(a[1]==2||a[1]==5),"screen effect "+Arrays.toString(a));
            // 42F31A consumes the EFFECTCHANGESCREEN atlas with a732x720
            // rectangle and12 ticks per unit. The portable presentation uses
            // one finite atlas rectangle, not the native multi-object curve.
            clearEffect(h);effectKind=a[0];effectMillis=a[1]*200;return true;
        case "ph.scene.comments":
            if(a[0]==99){
                require(a[1]>=0&&a[1]<=2,"communication implementation "+a[1]);commentType=a[1];commentOffset=0;commentsVisible=false;h.phSpeech.configureComment(commentType);
                if(commentType==1)h.resources.files.slice("AdvData/GRP/SYSTEM/COMMENTBACK.CRX",0,20);
                if(commentType==2){h.resources.files.slice("AdvData/GRP/BG/BLACK.CRX",0,20);h.resources.files.slice("AdvData/GRP/SYSTEM/COMUCHIP.CRX",0,20);h.resources.files.slice("AdvData/GRP/SYSTEM/COMUSTAMP.CRX",0,20);}
                return true;
            }
            // 430580's guard: inactive implementations ignore ordinary
            // controls, but START itself always executes99,2 initialization.
            if(commentType==0)return true;
            switch(a[0]){
            case -1:h.phSpeech.clearComments();commentOffset=0;commentsVisible=false;return true;
            case 0:case 98:commentsVisible=false;return true;
            case 1:if(commentType==1){h.phSpeech.clearComments();commentOffset=0;}commentsVisible=true;return true;
            case 7:commentsVisible=true;return true;
            case 2:commentOffset=a[1]<0?h.phSpeech.commentCount():Math.max(0,Math.min(h.phSpeech.commentCount(),a[1]));return true;
            case 3:case 4:case 5:case 6:if(commentType==2)return true;throw new IOException("Unimplemented PH comment1 control "+a[0]);
            default:return true; // 4306D7 native switch default.
            }
        case "ph.scene.blend":require(a[0]==0,"blend shader "+a[0]);return true;
        case "ph.scene.gamma":throw new IOException("Unimplemented PH native gamma shader B1");
        case "ph.scene.gamma_to":throw new IOException("Unimplemented PH native gamma animation B2");
        case "ph.scene.motion":throw new IOException("Unimplemented PH native PCK animation95");
        default:return false;
        }
    }
    private void transform(Dc4Host h,int s,int[] a,boolean overlay)throws IOException{
        int flags=a[3];if(flags==255)return;Dc3Scene.Layer l=h.scene.slots[s];
        if(flags==0){if(!overlay)sway.stop(h,s-4);h.scene.stop(s);l.x=a[0];l.y=a[1];if(!overlay)relativeBase[s-4]=false;}
        else if((flags&0x80)!=0){h.scene.stop(s);if(a[1]==0){h.scene.pending[s]="-";if(!overlay)h.scene.pending[s+5]="-";}else h.scene.move(s,l.x,l.y,0,a[1]*200L);}
        else if((flags&0xf0)==0x10){l.scaleX=l.scaleY=a[0];l.pivotX=a[1];l.pivotY=a[2];}
        else if((flags&0xf0)==0x20){require(a[0]>=0&&a[0]<=100,"priority "+a[0]);l.priority=a[0];}
        else if((flags&0xf0)==0x40){
            require(a[0]>=0&&a[0]<=3,"character entrance "+a[0]);int x=l.x,y=l.y;l.alpha=0;
            if(a[0]==0)l.x-=20;if(a[0]==1)l.x+=20;
            // Masked mode3 is a finite alpha approximation of4478E0. It
            // retains the original target/duration and never invents an asset.
            h.scene.move(s,x,y,255,a[0]==3?Math.max(1,a[1])*200L:100);
        }else throw new IOException("Unimplemented PH transform flags "+flags);
        h.scene.revision++;
    }
    public void prepare(Dc4Host h)throws IOException{
        String pending=h.scene.pending[1];
        if(pending!=null&&!pending.isEmpty()&&!pending.equals("-")){
            Dc3Scene.Layer l=h.scene.slots[1];
            if(centerPending){Crx.Image image=h.resources.image(pending);l.x=-(image.width-Dc4Geometry.WIDTH)/2;l.y=-(image.height-Dc4Geometry.HEIGHT)/2;}
            // In PH the wire flag records native mode1/fit. The separate37
            // centering command has already changed the native pixel position.
            l.centered=fitPending;
            h.scene.centerNextBackground=fitPending;
        }
        centerPending=false;fitPending=false;Arrays.fill(relativeBase,false);
        if(effectKind!=0&&effectResource.isEmpty()){
            String name=h.scene.pending[22]!=null?h.scene.pending[22]:h.scene.slots[22].resource;
            require(name!=null&&!name.isEmpty()&&!name.equals("-"),"screen effect without prepared22 atlas");
            effectResource=name;Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource=name;q.x=effectKind==2?280:274;q.y=0;q.sx=q.sy=0;q.width=732;q.height=720;h.scene.quads.add(q);
        }
    }
    private void clearEffect(Dc4Host h){
        if(!effectResource.isEmpty())for(Iterator<Dc3Scene.Quad> it=h.scene.quads.iterator();it.hasNext();){Dc3Scene.Quad q=it.next();if(q.resource.equals(effectResource)&&q.width==732&&q.height==720&&(q.x==274||q.x==280)&&q.y==0)it.remove();}
        effectKind=effectMillis=0;effectResource="";
    }
    public void elapse(Dc4Host h,long millis){sway.elapse(h,millis);if(effectMillis>0){effectMillis=(int)Math.max(0,effectMillis-Math.max(0,millis));if(effectMillis==0)clearEffect(h);}}
    public void reset(Dc4Host h){centerPending=fitPending=false;clearEffect(h);sway.reset(h);Arrays.fill(relativeBase,false);}
    public void save(DataOutput out)throws IOException{out.writeInt(0x50485333);out.writeBoolean(centerPending);out.writeBoolean(fitPending);out.writeBoolean(retainBackground);out.writeBoolean(cameraLatch);out.writeInt(effectKind);out.writeInt(effectMillis);out.writeUTF(effectResource);out.writeInt(portraitVisible);out.writeInt(commentType);out.writeInt(commentOffset);out.writeBoolean(commentsVisible);out.writeInt(particleX);out.writeInt(particleY);out.writeInt(particleAngle);out.writeInt(particleTarget);out.writeInt(particleTicks);for(int i=0;i<5;i++){out.writeBoolean(relativeBase[i]);out.writeInt(relativeX[i]);out.writeInt(relativeY[i]);}sway.save(out);}
    public void load(DataInput in)throws IOException{
        if(in.readInt()!=0x50485333)throw new IOException("PH scene state version");centerPending=in.readBoolean();fitPending=in.readBoolean();retainBackground=in.readBoolean();cameraLatch=in.readBoolean();effectKind=in.readInt();effectMillis=in.readInt();effectResource=in.readUTF();portraitVisible=in.readInt();
        commentType=in.readInt();commentOffset=in.readInt();commentsVisible=in.readBoolean();particleX=in.readInt();particleY=in.readInt();particleAngle=in.readInt();particleTarget=in.readInt();particleTicks=in.readInt();if(particleTicks<0)throw new IOException("PH particle duration");
        if(effectKind<0||effectKind>2||effectMillis<0||effectMillis>1000||portraitVisible<0||portraitVisible>1||commentType<0||commentType>2||commentOffset<0||commentOffset>200)throw new IOException("PH scene state range");
        for(int i=0;i<5;i++){relativeBase[i]=in.readBoolean();relativeX[i]=in.readInt();relativeY[i]=in.readInt();}
        sway.load(in);
    }
}
