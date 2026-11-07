package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Platform output checked against per-component native integer rules and native slot goldens. */
public final class Dc2ParticleOutputAudit {
    private static int count;
    private static final int OLD=0xff162738,FRESH=0xff718293,ACTOR=0xffac2536,PANEL=0xffb14375;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static int blend(int old,int fresh,int w){int result=0xff000000;for(int shift=0;shift<24;shift+=8)result|=((((old>>>shift)&255)*(16-w)+((fresh>>>shift)&255)*w)/16)<<shift;return result;}
    private static int atlas(int x,int y,int bits){if((x+y)%7==0)return bits==24?0xffff00ff:0;return(bits==32?0x05000000:0xff000000)|((x*7+30)&255)<<16|((y*11+50)&255)<<8|((x*3+y*5+70)&255);}
    private static Crx.Image image(int w,int h,int bits,int color,boolean sheet){int stride=(w*(bits/8)+3)&~3;byte[] raw=new byte[stride*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=sheet?atlas(x,y,bits):color,at=y*stride+x*(bits/8);raw[at]=(byte)c;raw[at+1]=(byte)(c>>>8);raw[at+2]=(byte)(c>>>16);if(bits==32)raw[at+3]=(byte)(c>>>24);}return new Crx.Image(0,0,w,h,bits,raw,null);}
    private static byte[] code()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x33,0x2d});out.write("new".getBytes(Binary.SJIS));out.write(0);out.write(0x4a);for(byte value:"speaker".getBytes(Binary.SJIS))out.write(value-32);out.write(0);out.write(0x4b);for(byte value:"text".getBytes(Binary.SJIS))out.write(value-32);out.write(new byte[]{0,0,0,0});return out.toByteArray();}
    private static CircusResources resources(final int bits)throws IOException{
        final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/DAT/NAMES.DAT",new byte[20]);files.put("AdvData/MES/START.mes",code());
        GameResources data=new GameResources(new GameResources.Storage(){public Set<String> paths(){return files.keySet();}public byte[] read(String path,int limit)throws IOException{byte[] value=files.get(path);if(value==null||value.length>limit)throw new FileNotFoundException(path);return value.clone();}public Archive.Source source(String path){final byte[] value=files.get(path);return new Archive.Source(){public long size(){return value.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,value.length);return Arrays.copyOfRange(value,(int)start,(int)start+length);}};}});
        return new CircusResources(data,Profiles.get("dc2-3363")){@Override public Crx.Image image(String reference)throws IOException{if(reference.equals("old"))return Dc2ParticleOutputAudit.image(800,600,24,OLD,false);if(reference.equals("new"))return Dc2ParticleOutputAudit.image(800,600,24,FRESH,false);if(reference.equals("actor"))return Dc2ParticleOutputAudit.image(200,600,24,ACTOR,false);if(reference.toLowerCase(Locale.ROOT).contains("saku_yuki")||reference.toLowerCase(Locale.ROOT).contains("yuki01"))return Dc2ParticleOutputAudit.image(256,200,bits,0,true);throw new FileNotFoundException(reference);}};
    }
    private static DesktopLegacy engine(int bits)throws Exception{DesktopLegacy e=new DesktopLegacy(resources(bits),TextCodec.identity(),true);e.host().write(99,12,2);e.host().scene.slots[1]="old";e.host().scene.publish(e.host().ui,0,0);e.host().command("pc.effect",new int[]{6,5},new byte[0]);e.vm().elapse(3000);return e;}
    private static int[] pixels(DesktopLegacy e)throws IOException{return e.render().getRGB(0,0,800,600,null,0,800);}
    private static void particles(int[] result,Dc2Particles particles,int layer,int bits){int weight=particles.kind()==3?12:8;for(int n=0;n<50;n++){int[] p=particles.slot(n);if(p[2]==0||p[18]!=layer)continue;for(int y=0;y<24;y++)for(int x=0;x<32;x++){int dx=p[3]+x,dy=p[4]+y;if(dx<0||dx>=800||dy<0||dy>=600)continue;int c=atlas(p[19]*32+x,p[0]*28+y,bits);if(bits==24?(c&0xffffff)==0xff00ff:c>>>24==0)continue;int at=dy*800+dx;result[at]=blend(result[at],c,weight);}}}
    private static int[] flat(int color){int[] result=new int[800*600];Arrays.fill(result,color);return result;}
    public static void main(String[] args)throws Exception{
        for(int bits:new int[]{24,32}){
            DesktopLegacy e=engine(bits);PcHost h=(PcHost)e.host();h.scene.slots[2]="actor";h.scene.character(0,0,200);Dc2Ui.Quad underlay=new Dc2Ui.Quad();underlay.type=1;underlay.x=0;underlay.y=0;underlay.width=800;underlay.height=600;underlay.flags=8;h.ui.quads.add(underlay);h.ui.palette[8]=FRESH;h.scene.publish(h.ui,0,0);
            int[] expected=flat(FRESH);particles(expected,h.scene.nativeParticles,1,bits);for(int y=0;y<600;y++)Arrays.fill(expected,y*800+200,y*800+400,ACTOR);particles(expected,h.scene.nativeParticles,0,bits);
            check(Arrays.equals(expected,pixels(e)),"actual"+bits+"bit renderer orders baked background quads, layer1, actors, layer0 with exact native keyed fixed-weight blend");
            byte[] before=e.vm().snapshot();pixels(e);pixels(e);check(Arrays.equals(before,e.vm().snapshot()),"render repetition cannot consume RNG, move particles or change animation state");
            h.write(99,10,0);check(Arrays.equals(expected,pixels(e)),"native sprite drawing ignores generic99:10");h.write(99,20,0);check(Arrays.equals(expected,pixels(e)),"99:20 disables future work without hiding the existing atlas/particles");
            UiCapture captured=SceneCaptures.legacy(h.scene.shown,h.scene,h.ui,e.vm().variables,true,null);h.uiCapture=e.host().captureRenderer.flatten(captured);UiCapture.Pixels frozen=h.uiCapture.pixels();check(Arrays.equals(expected,frozen.argb),"logical legacy particle capture freezes both layers and resolves to the same actual platform pixels");
        }
        DesktopLegacy e=engine(24);PcHost h=(PcHost)e.host();h.body="old";Dc2Ui.Quad panel=new Dc2Ui.Quad();panel.type=1;panel.x=0;panel.y=500;panel.width=800;panel.height=100;panel.flags=8;h.ui.palette[8]=PANEL;h.ui.quadTemplates.get(1).add(panel);
        int[] old=pixels(e);Dc2Particles frozen=h.scene.nativeParticles.frozenCopy();check(e.vm().run(100)==MesVm.Status.WAIT_HOST&&h.sceneCommandDeferred()&&h.body.isEmpty(),"actual platform raw4B waits on dirty-batch preparation before exposing new UI");
        int[] prepared=flat(FRESH);particles(prepared,frozen,1,24);particles(prepared,frozen,0,24);int[] expected=old.clone();for(int n=0;n<expected.length;n++)expected[n]=blend(expected[n],prepared[n],8);
        check(Arrays.equals(expected,pixels(e)),"first41C440 frame blends the complete old front buffer including its message panel against frozen mask0F composition");
        e.vm().elapse(59);for(int n=0;n<expected.length;n++)expected[n]=blend(expected[n],prepared[n],8);check(Arrays.equals(expected,pixels(e)),"forty millisecond frame ordering retains integer feedback and the exact19ms residual");
        byte[] saved=e.snapshot();DesktopLegacy restored=new DesktopLegacy(resources(24),TextCodec.identity(),true);restored.host().write(99,12,2);restored.restore(saved);check(Arrays.equals(expected,pixels(restored)),"a fresh renderer restores the old UI raster and frozen particle prep from PCH7 without access to the original render cache");
        e.vm().elapse(101);restored.vm().elapse(101);int[] finished=pixels(e),restoredFinished=pixels(restored);int rd=-1;for(int n=0;n<finished.length;n++)if(prepared[n]!=restoredFinished[n]){rd=n;break;}int first=-1;for(int n=0;n<finished.length;n++)if(prepared[n]!=finished[n]){first=n;break;}check(Arrays.equals(prepared,finished)&&Arrays.equals(prepared,restoredFinished)&&h.body.isEmpty(),"native final full composition appears at160ms without prematurely drawing a new message panel: difference="+first+(first<0?"":" expected="+Integer.toHexString(prepared[first])+" actual="+Integer.toHexString(finished[first]))+" body="+h.body+" restoredDifference="+rd+(rd<0?"":" expected="+Integer.toHexString(prepared[rd])+" restored="+Integer.toHexString(restoredFinished[rd]))+" restoredProgress="+restored.host().scene.nativeMotion.prepareProgress+"/"+restored.host().scene.nativeMotion.clock);
        e.vm().run(1);restored.vm().run(1);check(h.body.equals("text")&&e.render().getRGB(10,590)==PANEL&&Arrays.equals(pixels(e),pixels(restored)),"following instruction barrier publishes body/UI once and restored output follows the same path");
        e.vm().signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().run(1);h.command("pc.background",new int[0],"old".getBytes(Binary.SJIS));check(h.frozenOriginalBatch()!=null&&h.frozenOriginalBatch().kind==UiCapture.ARGB,"restore preserves the platform callback for the next real dirty batch and captures its complete rendered front buffer");
        System.out.println("PASS "+count+" DC2 particle output assertions");
    }
}
