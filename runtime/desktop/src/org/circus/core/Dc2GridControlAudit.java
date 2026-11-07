package org.circus.core;

import java.io.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Exercises render/control projection and pointer activation without original game files. */
public final class Dc2GridControlAudit {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void words(ByteArrayOutputStream out,int opcode,int...args){out.write(opcode);for(int value:args){out.write(value);out.write(value>>>8);}}
    private static CircusResources fixture()throws IOException {
        ByteArrayOutputStream script=new ByteArrayOutputStream();script.write(new byte[]{0,0,0,0,0x63,0x33});
        words(script,84,0,0,0,0);
        words(script,81,0,0,0,0); // Native 54h consumes one complete supplemental record.
        for(int n=0;n<5;n++)words(script,85,10,10+n*50,110,50+n*50);
        script.write(new byte[]{28,6,0,11,0,0,49});script.write("selsave".getBytes(Binary.SJIS));script.write(new byte[]{0,0,0,0});
        final Map<String,byte[]> files=new HashMap<>();files.put("AdvData/MES/START.mes",script.toByteArray());files.put("AdvData/DAT/NAMES.DAT",new byte[20]);
        GameResources resources=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return files.keySet();}
            public byte[] read(String path,int limit)throws IOException{byte[] bytes=files.get(path);if(bytes==null||bytes.length>limit)throw new IOException("Fixture input "+path);return bytes.clone();}
            public Archive.Source source(String path){final byte[] bytes=files.get(path);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,bytes.length);return Arrays.copyOfRange(bytes,(int)start,(int)start+length);}};}
        });
        return new CircusResources(resources,Profiles.get("dc2-3363"));
    }
    public static void main(String[] args)throws Exception {
        DesktopLegacy engine=new DesktopLegacy(fixture(),TextCodec.identity(),true);
        check(engine.vm().run(1000)==MesVm.Status.WAIT_HOST,"fixture reaches its original grid wait");
        Dc2Host host=engine.host();check(host.grid.size()==5&&host.grid.get(2).binding==2&&host.grid.get(2).action.equals("selsave"),"native service configures the model");
        byte[] before=engine.vm().snapshot();
        for(int n=0;n<3;n++){BufferedImage frame=engine.render();check(frame.getWidth()==800&&frame.getHeight()==600,"real renderer produces logical canvas");frame.flush();engine.controls();}
        check(Arrays.equals(before,engine.vm().snapshot()),"rendering and control projection preserve native model and snapshot");
        check(engine.buttons().get(2)!=host.grid.get(2)&&engine.buttons().get(2).binding==2,"input button view is a distinct object with native binding");
        check(host.grid.get(2).action.equals("selsave")&&engine.buttons().get(2).action.equals("@slot:2"),"platform action does not replace native action");
        check(engine.controls().size()==3,"reserved native slots are not actionable platform controls");
        check(host.read(99,246)==0,"DC2 native getter does not expose the internal grid wait as 99:246");
        engine.pointerDown(20,120);engine.pointerUp(20,120);
        check(host.read(99,254)==2&&engine.vm().status()==MesVm.Status.RUNNING,"pointer event activates the native configured slot");
        check(host.grid.get(2).action.equals("selsave"),"pointer conversion retains original script label");
        check(engine.vm().run(100)==MesVm.Status.HALTED,"pointer resolves original selsave label rather than platform @slot action");
        System.out.println("PASS "+checks+" DC2 grid render/control assertions");
    }
}
