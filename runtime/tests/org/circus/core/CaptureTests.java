package org.circus.core;

import java.io.*;
import java.util.*;

final class CaptureTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action,String label)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,label);}
    private static byte[] save(UiCapture capture)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();UiCapture.save(new DataOutputStream(bytes),capture);return bytes.toByteArray();}
    private static UiCapture load(byte[] bytes)throws IOException{return UiCapture.load(new DataInputStream(new ByteArrayInputStream(bytes)));}
    static int run()throws Exception {
        checks=0;byte[] payload={10,20};UiCapture first=UiCapture.logical(UiCapture.LEGACY,payload,false,null);payload[0]=99;check(first.input().readByte()==10,"Capture owns its payload");
        UiCapture second=UiCapture.logical(UiCapture.LEGACY,new byte[]{30},true,first),copy=load(save(second));check(copy.depth==2&&copy.input().readByte()==30&&copy.previous.input().readByte()==10,"Nested capture serializes oldest texture before newest");
        check(UiCapture.logical(UiCapture.DD,new byte[]{40},false,second).depth==1,"Independent capture releases its predecessor");
        reject(()->UiCapture.logical(UiCapture.DD,new byte[]{1},true,null),"Missing texture cannot be silently erased");
        int[] pixels={0xff112233,0x00556677,0x80445566,0xffffffff};UiCapture raster=UiCapture.pixels(2,2,pixels);check(Arrays.equals(load(save(raster)).pixels().argb,pixels),"Raster save preserves alpha and RGB bits exactly");
        reject(()->UiCapture.pixels(0,2,new int[0]),"Zero canvas rejected");reject(()->UiCapture.pixels(2049,1,new int[2049]),"Oversized canvas rejected");
        byte[] packed=save(raster);reject(()->load(Arrays.copyOf(packed,packed.length-1)),"Truncated capture rejected");
        reject(()->load(new byte[]{0,0,1,1}),"Excess capture depth rejected before allocation");
        check(load(save(null))==null,"Empty capture roundtrip");
        Dc3Host h=new Dc3Host(null,TextCodec.identity(),true);h.attach(new Variables(h));h.scene.slots[1].resource="background";h.command("scene.transition",new int[]{9,0},new byte[0]);UiCapture base=h.uiCapture;
        Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource="@ui-capture";q.width=1024;q.height=576;h.scene.quads.add(q);h.scene.slots[1].resource="";h.command("scene.transition",new int[]{9,0},new byte[0]);
        check(h.uiCapture.previous==base&&h.uiCapture.depth==2,"DD opcode 9 retains prior capture without a platform renderer");
        ByteArrayOutputStream state=new ByteArrayOutputStream();h.save(new DataOutputStream(state));Dc3Host restored=new Dc3Host(null,TextCodec.identity(),true);restored.attach(new Variables(restored));restored.load(new DataInputStream(new ByteArrayInputStream(state.toByteArray())));
        SceneCaptures.Dd latest=new SceneCaptures.Dd(restored.uiCapture.input()),prior=new SceneCaptures.Dd(restored.uiCapture.previous.input());check(latest.frame.quads.get(0).resource.equals("@ui-capture")&&prior.frame.layers[1].resource.equals("background"),"Host save retains nested capture resource semantics");
        Dc3Scene.Frame frozen=h.scene.capture();q.x=99;check(frozen.quads.get(0).x==0,"Published DD quads do not alias later scene mutations");
        return checks;
    }
}
