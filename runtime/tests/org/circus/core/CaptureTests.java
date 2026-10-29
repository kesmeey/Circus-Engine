package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

final class CaptureTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action,String label)throws Exception{boolean failed=false;try{action.run();}catch(IOException expected){failed=true;}check(failed,label);}
    private static byte[] save(UiCapture capture)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();UiCapture.save(new DataOutputStream(bytes),capture);return bytes.toByteArray();}
    private static UiCapture load(byte[] bytes)throws IOException{return UiCapture.load(new DataInputStream(new ByteArrayInputStream(bytes)));}
    /** Pre-buffering wire format, deliberately independent of UiCapture's codec. */
    private static byte[] oldRaster(int width,int height,int[] pixels,int extraByte)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(bytes);header.writeInt(width);header.writeInt(height);
        try(DataOutputStream out=new DataOutputStream(new DeflaterOutputStream(bytes))){for(int pixel:pixels)out.writeInt(pixel);if(extraByte>=0)out.writeByte(extraByte);}
        return bytes.toByteArray();
    }
    private static UiCapture rasterPayload(byte[] payload)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(1);out.writeByte(UiCapture.ARGB);out.writeInt(payload.length);out.write(payload);return load(bytes.toByteArray());
    }
    private static int[] oldPixels(UiCapture capture,int width,int height)throws IOException{
        DataInputStream header=capture.input();if(header.readInt()!=width||header.readInt()!=height)throw new IOException("Legacy raster dimensions changed");int[] pixels=new int[width*height];
        try(DataInputStream in=new DataInputStream(new InflaterInputStream(header))){for(int n=0;n<pixels.length;n++)pixels[n]=in.readInt();if(in.read()!=-1)throw new IOException("Legacy raster has extra pixels");}return pixels;
    }
    static int run()throws Exception {
        checks=0;byte[] payload={10,20};UiCapture first=UiCapture.logical(UiCapture.LEGACY,payload,false,null);payload[0]=99;check(first.input().readByte()==10,"Capture owns its payload");
        UiCapture second=UiCapture.logical(UiCapture.LEGACY,new byte[]{30},true,first),copy=load(save(second));check(copy.depth==2&&copy.input().readByte()==30&&copy.previous.input().readByte()==10,"Nested capture serializes oldest texture before newest");
        check(UiCapture.logical(UiCapture.DD,new byte[]{40},false,second).depth==1,"Independent capture releases its predecessor");
        reject(()->UiCapture.logical(UiCapture.DD,new byte[]{1},true,null),"Missing texture cannot be silently erased");
        int[] pixels={0xff112233,0x00556677,0x80445566,0xffffffff};UiCapture raster=UiCapture.pixels(2,2,pixels);check(Arrays.equals(load(save(raster)).pixels().argb,pixels),"Raster save preserves alpha and RGB bits exactly");
        reject(()->UiCapture.pixels(0,2,new int[0]),"Zero canvas rejected");reject(()->UiCapture.pixels(2049,1,new int[2049]),"Oversized canvas rejected");
        byte[] packed=save(raster);reject(()->load(Arrays.copyOf(packed,packed.length-1)),"Truncated capture rejected");
        // Cross several 32 KiB boundaries, with opaque, transparent and partial-alpha pixels.
        int[] large=new int[193*127];Random random=new Random(0x7ca9d02L);for(int n=0;n<large.length;n++)large[n]=n%7==0?0:n%7==1?-1:random.nextInt();
        UiCapture legacy=rasterPayload(oldRaster(193,127,large,-1));UiCapture.Pixels decoded=legacy.pixels();
        check(decoded.width==193&&decoded.height==127&&Arrays.equals(decoded.argb,large),"Buffered raster reader opens pre-buffering saves across block boundaries");
        UiCapture current=load(save(UiCapture.pixels(193,127,large)));
        check(Arrays.equals(oldPixels(current,193,127),large),"Buffered raster writer remains readable by the legacy decoder");
        check(Arrays.equals(current.pixels().argb,large),"Buffered raster roundtrip preserves every ARGB bit across block boundaries");
        reject(()->rasterPayload(oldRaster(2,2,Arrays.copyOf(pixels,3),-1)).pixels(),"Raster rejects a complete zlib stream containing too few pixels");
        reject(()->rasterPayload(oldRaster(2,2,pixels,123)).pixels(),"Raster rejects an extra decompressed byte");
        reject(()->rasterPayload(oldRaster(2,2,Arrays.copyOf(pixels,5),-1)).pixels(),"Raster rejects an extra decompressed pixel");
        byte[] compressed=oldRaster(193,127,large,-1);
        reject(()->rasterPayload(Arrays.copyOf(compressed,compressed.length-3)).pixels(),"Raster rejects a truncated compressed stream inside a valid capture envelope");
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
