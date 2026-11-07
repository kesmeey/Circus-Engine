package org.circus.core;

import java.io.*;
import java.util.*;

/** Current DC2 416499 -> 418890/408160 contracts, with self-authored inputs. */
public final class Dc2MotionTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Fixture {
        final PcHost host;final MesVm vm;final MesScript script;
        Fixture(int x,int y,int mode,int weight)throws IOException {
            final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
                public byte[] read(String path,int limit){return names.clone();}
                public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
            });
            Profile profile=Profiles.get("dc2-3363");host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,profile),TextCodec.identity()));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0,0,0,0,0x63,0x33,0x5e});for(int value:new int[]{x,y,mode,weight}){bytes.write(value);bytes.write(value>>>8);}bytes.write(new byte[]{0,0,0});
            script=new MesScript("native-motion",bytes.toByteArray(),profile);vm=new MesVm(name->script,host,script.name);
            host.write(99,12,2);host.scene.slots[1]="background";host.scene.publish(host.ui,0,0);
        }
        void start(){MesVm.Status status=vm.run(1);check(status==MesVm.Status.RUNNING||status==MesVm.Status.WAIT_HOST,"native 5E decodes and emits its versioned command: "+status+" "+vm.error());}
    }
    private static byte[] journal(Dc2Motion motion)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();motion.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static int scalarBlend(int old,int fresh,int alpha){int result=0xff000000;for(int shift=0;shift<24;shift+=8)result|=((((old>>>shift)&255)*(16-alpha)+((fresh>>>shift)&255)*alpha)/16)<<shift;return result;}
    public static int run()throws Exception {
        count=0;Fixture f=new Fixture(0,-19,0,2);f.start();
        check(f.host.scene.backgroundY()==-8&&f.host.scene.nativeMotion.draws().size()==1,"dispatch immediately enters the wait consumer and moves eight pixels at configured speed two");
        check(f.vm.run(1)==MesVm.Status.WAIT_HOST&&f.vm.offset()==15,"next fetch waits at the next raw PC instead of consuming another instruction");
        f.vm.elapse(9);check(f.host.scene.backgroundY()==-8,"native 10ms period retains its fractional remainder");f.vm.elapse(1);check(f.host.scene.backgroundY()==-16,"one complete period advances one native step");
        f.vm.elapse(10);check(f.host.scene.backgroundY()==-19&&f.host.scene.nativeMotion.settled==0,"negative destination clamps without overshoot, then settling starts on the next frame");
        f.vm.elapse(149);check(f.host.scene.nativeMotion.alpha==2&&f.host.scene.nativeMotion.settled==14&&f.host.waiting(),"feedback weight remains fixed for fourteen complete post-arrival frames");
        f.vm.elapse(1);check(f.host.scene.nativeMotion.alpha==0&&f.host.scene.nativeMotion.settled==15&&f.host.waiting(),"settled+weight greater than sixteen redraws fully but still yields this frame");
        f.vm.elapse(10);check(!f.host.waiting()&&f.vm.run(1)==MesVm.Status.HALTED,"the following boundary releases the coroutine and reaches its native stop");
        check(f.host.scene.nativeMotion.draws().get(17).kind==Dc2Motion.FULL,"last visible draw replaces feedback with the complete composition");
        for(int mode:new int[]{-32768,-1,1,2,3,32767}){
            Fixture immediate=new Fixture(123,-234,mode,32767);immediate.start();
            check(immediate.host.scene.backgroundX()==123&&immediate.host.scene.backgroundY()==-234&&!immediate.host.waiting()&&immediate.host.scene.nativeMotion==null,"every nonzero third operand assigns live offsets immediately and ignores the fourth");
            check(immediate.host.scene.shown.x==0&&immediate.host.scene.shown.y==0,"immediate coordinate assignment does not publish a frame");immediate.vm.elapse(100);check(immediate.host.scene.backgroundY()==-234,"a later clock tick does not undo an immediate assignment");
        }
        int[] speedValues={0,1,2,3,4,-1},steps={2,4,8,16,2,2};
        for(int n=0;n<speedValues.length;n++){Fixture speed=new Fixture(19,-3,0,0);speed.host.write(99,12,speedValues[n]);speed.start();check(speed.host.scene.backgroundX()==steps[n]&&speed.host.scene.backgroundY()==-Math.min(steps[n],3),"native speed table is independent for both signed axes");}
        Fixture skipped=new Fixture(100,0,0,0);skipped.host.write(99,12,0);skipped.host.motionSkipPressed=true;skipped.start();check(skipped.host.scene.backgroundX()==16,"native Ctrl or latch selects sixteen-pixel steps");
        skipped.vm.variables.write(8,18,1);skipped.vm.elapse(10);check(skipped.host.scene.backgroundX()==18,"ordinary byte8:18 equal one suppresses skip on the next frame");
        skipped.host.write(99,12,3);skipped.vm.elapse(10);check(skipped.host.scene.backgroundX()==34,"configured speed three still selects sixteen when the skip gate is disabled");
        check(!skipped.host.signal(skipped.host.waitTicket(),MesVm.HostSignal.ADVANCE)&&skipped.host.waiting(),"a click does not instantly finish the native motion coroutine");
        Fixture large=new Fixture(67,-31,0,3),small=new Fixture(67,-31,0,3);large.start();small.start();large.vm.elapse(87);for(int n=0;n<87;n++)small.vm.elapse(1);
        check(Arrays.equals(journal(large.host.scene.nativeMotion),journal(small.host.scene.nativeMotion)),"large elapse preserves every ordered feedback draw and the same sub-frame remainder");
        byte[] saved=large.vm.snapshot();Fixture restored=new Fixture(67,-31,0,3);restored.vm.restore(saved);
        check(Arrays.equals(journal(large.host.scene.nativeMotion),journal(restored.host.scene.nativeMotion))&&restored.host.waiting(),"CVS2 restores active motion, draw order, counters and native wait state");
        large.vm.elapse(1000);restored.vm.elapse(1000);check(Arrays.equals(journal(large.host.scene.nativeMotion),journal(restored.host.scene.nativeMotion)),"resumed motion reaches exactly the same journal after its final feedback frames");
        for(int alpha=0;alpha<=16;alpha++)for(int value=0;value<256;value++){
            int old=0xff000000|value<<16|(255-value)<<8|(value*73&255),fresh=0xff000000|(255-value)<<16|(value*131&255)<<8|value;
            check(Dc2Motion.blend(old,fresh,alpha)==scalarBlend(old,fresh,alpha),"packed native formula equals independent per-component integer division within the defined sixteen-level blend range");
        }
        int[] canvas=new int[12],image={0xff102030,0xff405060,0xff708090,0xffa0b0c0,0xffd0e0f0,0xff123456,0xff654321,0xffabcdef,0xfffedcba};Arrays.fill(canvas,0xff081018);
        Dc2Motion.blit(canvas,4,3,image,3,3,-1,-1,2);
        check(canvas[0]==scalarBlend(0xff081018,image[4],2)&&canvas[1]==scalarBlend(0xff081018,image[5],2)&&canvas[4]==scalarBlend(0xff081018,image[7],2)&&canvas[5]==scalarBlend(0xff081018,image[8],2),"native negative offsets clip the source and draw toward the signed destination");
        check(canvas[2]==0xff081018&&canvas[3]==0xff081018&&canvas[8]==0xff081018,"feedback preserves prior pixels outside the clipped source rectangle");
        Fixture prepareSource=new Fixture(8,0,0,2);Dc2Scene.Frame source=prepareSource.host.scene.shown;UiCapture initial=SceneCaptures.legacy(source,prepareSource.host.scene,prepareSource.host.ui,prepareSource.vm.variables,false,null);
        Dc2Motion prepare=new Dc2Motion(source,initial,null,8,0,2,true,2,false);
        check(prepare.preparing&&prepare.prepareProgress==4&&prepare.draws().get(0).kind==Dc2Motion.PREPARE&&prepare.draws().get(0).alpha==8,"nonempty native pending batch starts 41C440 at forty milliseconds and its speed-specific feedback weight");
        prepare.elapse(159,2,false);check(prepare.preparing&&prepare.prepareProgress==16&&prepare.x==0,"the fourth preparation frame still owns its final forty-millisecond interval");prepare.elapse(1,2,false);
        check(!prepare.preparing&&prepare.x==8&&prepare.alpha==16&&prepare.draws().size()==6,"preparation ends with a full composition, replaces the requested weight by native sixteen, then enters the first movement tick immediately");
        prepare.elapse(10,2,false);check(prepare.alpha==0&&prepare.active,"prepared motion clears its overwritten weight on the first post-arrival tick");prepare.elapse(10,2,false);check(!prepare.active,"prepared motion releases on the next boundary");
        Fixture unresolved=new Fixture(8,0,0,2);unresolved.host.command("pc.character",new int[0],"actor".getBytes(Binary.SJIS));
        check(unresolved.vm.run(1)==MesVm.Status.FAULT&&unresolved.vm.error().contains("BE8BC8")&&unresolved.host.scene.nativeMotion==null,"unreviewed character batch state cannot silently take the no-preparation path");
        Fixture cleared=new Fixture(8,0,0,2);cleared.host.command("pc.character",new int[0],"actor".getBytes(Binary.SJIS));cleared.host.command("pc.background",new int[0],"background".getBytes(Binary.SJIS));cleared.start();
        check(!cleared.host.scene.nativeMotion.preparing,"native background producer proves the pending composition clear again");
        Fixture persisted=new Fixture(8,0,0,2);persisted.host.command("pc.character",new int[0],"actor".getBytes(Binary.SJIS));byte[] unresolvedSave=persisted.vm.snapshot();Fixture persistedCopy=new Fixture(8,0,0,2);persistedCopy.vm.restore(unresolvedSave);
        check(persistedCopy.vm.run(1)==MesVm.Status.FAULT&&persistedCopy.vm.error().contains("BE8BC8"),"a snapshot preserves unresolved producer state instead of inventing a clear native flag on restore");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 native motion assertions");}
}
