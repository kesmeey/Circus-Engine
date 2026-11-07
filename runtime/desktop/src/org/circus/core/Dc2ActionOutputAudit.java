package org.circus.core;

import java.io.*;
import java.util.*;

/** Actual desktop background input uses DC2 wait-specific bindings and completion gates. */
public final class Dc2ActionOutputAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void string(ByteArrayOutputStream out,int opcode,String value)throws IOException{out.write(opcode);out.write(value.getBytes(Binary.SJIS));out.write(0);}
    private static DesktopLegacy engine(int wait,int mode,String right,String left)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x33});string(out,0x3b,right);string(out,0x3c,left);out.write(new byte[]{(byte)wait,2,(byte)mode});string(out,0x31,"right");out.write(new byte[]{0,0,0});string(out,0x31,"left");out.write(new byte[]{0,0,0});
        final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/MES/START.mes",out.toByteArray());files.put("AdvData/DAT/NAMES.DAT",new byte[20]);
        GameResources data=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return files.keySet();}
            public byte[] read(String path,int limit)throws IOException{byte[] bytes=files.get(path);if(bytes==null||bytes.length>limit)throw new IOException("Fixture "+path);return bytes.clone();}
            public Archive.Source source(String path){final byte[] bytes=files.get(path);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,bytes.length);return Arrays.copyOfRange(bytes,(int)start,(int)start+length);}};}
        });DesktopLegacy engine=new DesktopLegacy(new CircusResources(data,Profiles.get("dc2-3363")),TextCodec.identity(),true);check(engine.vm().run(100)==MesVm.Status.WAIT_HOST,"raw action setters reach their native platform input wait");return engine;
    }
    public static void main(String[] args)throws Exception {
        DesktopLegacy ui=engine(0x0a,0,"right","left");ui.click(700,500,false);check(ui.vm().status()==MesVm.Status.WAIT_HOST&&ui.host().ui.actions[1].equals("left"),"left background click in native UI wait ignores global action1");ui.click(700,500,true);check(ui.vm().status()==MesVm.Status.RUNNING&&ui.host().ui.actions[0].isEmpty()&&ui.host().ui.actions[1].equals("left"),"right background click in UI wait consumes only global action0");
        DesktopLegacy timed=engine(0x12,1,"","");timed.click(700,500,true);check(timed.vm().status()==MesVm.Status.RUNNING&&!((PcHost)timed.host()).hasTimedInput(),"right click with no action label follows native timed-wait input bit instead of requesting the story menu");
        DesktopLegacy locked=engine(0x12,0,"","");locked.click(700,500,false);locked.click(700,500,true);check(locked.vm().status()==MesVm.Status.WAIT_HOST&&locked.host().remainingMillis()==400,"mode bit0 clear preserves the timer for both background mouse buttons");
        DesktopLegacy absent=engine(0x12,0,"absent","");absent.click(700,500,true);check(absent.vm().status()==MesVm.Status.WAIT_HOST&&absent.host().ui.actions[0].equals("absent"),"missing optional input label is not a VM fault or a consumed action");
        DesktopLegacy delay=engine(0x0a,1,"right","left");delay.click(700,500,true);delay.click(700,500,false);check(delay.vm().status()==MesVm.Status.WAIT_HOST&&delay.host().remainingMillis()==400,"native UI timer mode1 ignores both pointer buttons");
        DesktopLegacy resumed=engine(0x12,0,"right","left");byte[] saved=resumed.snapshot();resumed.restore(saved);resumed.click(700,500,false);check(resumed.vm().status()==MesVm.Status.RUNNING&&resumed.host().ui.actions[1].isEmpty()&&resumed.host().ui.actions[0].equals("right"),"first input immediately after restore resolves the current script and preserves the other binding");
        System.out.println("PASS "+count+" DC2 global action output assertions");
    }
}
