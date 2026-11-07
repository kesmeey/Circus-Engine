package org.circus.core;

import java.io.*;
import java.util.*;

/** Actual region creation, desktop hover/click conversion and output gain routing. */
public final class Dc2ButtonVoiceOutputAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void string(ByteArrayOutputStream out,int opcode,String value)throws IOException{out.write(opcode);out.write(value.getBytes(Binary.SJIS));out.write(0);}
    private static CircusResources resources()throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,0x63,0x33,0x4e});for(int value:new int[]{10,10,110,60}){out.write(value);out.write(value>>>8);}string(out,0x45,"target");string(out,0x3d,"VOICE\\SYSTEM\\click");string(out,0x3e,"VOICE\\SYSTEM\\hover");out.write(new byte[]{11,0,0});string(out,0x31,"target");out.write(new byte[]{0,0,0});
        final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/MES/START.mes",out.toByteArray());files.put("AdvData/DAT/NAMES.DAT",new byte[20]);
        GameResources data=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return files.keySet();}
            public byte[] read(String path,int limit)throws IOException{byte[] bytes=files.get(path);if(bytes==null||bytes.length>limit)throw new IOException("Fixture "+path);return bytes.clone();}
            public Archive.Source source(String path){final byte[] bytes=files.get(path);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,bytes.length);return Arrays.copyOfRange(bytes,(int)start,(int)start+length);}};}
        });return new CircusResources(data,Profiles.get("dc2-3363"));
    }
    public static void main(String[] args)throws Exception {
        DesktopLegacy engine=new DesktopLegacy(resources(),TextCodec.identity(),true);check(engine.vm().run(100)==MesVm.Status.WAIT_HOST,"actual4E region with following3D/3E reaches its native UI wait");
        Dc2Host host=engine.host();check(host.ui.buttons.size()==1&&host.grid.isEmpty()&&host.ui.buttons.get(0).action.equals("target"),"properties attach to the current native region, with a separate empty thumbnail grid");
        engine.render();engine.hover(20,20);check(engine.audio(5).equals("VOICE\\SYSTEM\\hover")&&engine.audioGeneration(5)==1,"a desktop pointer entering the actual region starts hover voice through channel2's logical voice output");
        engine.hover(30,30);check(engine.audioGeneration(5)==1,"moving inside the same region does not re-submit voice");
        engine.hover(2,2);engine.hover(20,20);check(engine.audioGeneration(5)==2,"leaving and returning causes a second native hover transition");
        float gain=engine.volume(5);check(Math.abs(gain-0.89125094f)<0.000001f,"platform voice gain uses native minus-one-decibel level29");
        host.write(99,5,0);host.write(99,15,0);check(engine.volume(5)==gain,"script99:5 and a later SE gate change do not mute the voice that already started");
        engine.hover(2,2);engine.hover(20,20);check(engine.audioGeneration(5)==2,"SE-disabled native hover rejects the next request without stopping the old channel");
        host.write(99,15,1);byte[] snapshot=engine.snapshot();DesktopLegacy loaded=new DesktopLegacy(resources(),TextCodec.identity(),true);loaded.restore(snapshot);loaded.render();
        check(loaded.host().ui.buttons.get(0).hoverVoice.equals("VOICE\\SYSTEM\\hover")&&loaded.volume(5)==gain,"new platform adapter restores region hover voice and captured playback gain");
        loaded.pointerDown(20,20);loaded.pointerUp(20,20);check(loaded.audio(5).equals("VOICE\\SYSTEM\\click")&&loaded.audioGeneration(5)==3&&loaded.vm().status()==MesVm.Status.RUNNING,"pointer activation submits the click voice, keeps the native target and releases the UI wait");
        check(loaded.vm().run(100)==MesVm.Status.HALTED,"native action resolves its exact script label after click playback");
        System.out.println("PASS "+count+" DC2 region voice output assertions");
    }
}
