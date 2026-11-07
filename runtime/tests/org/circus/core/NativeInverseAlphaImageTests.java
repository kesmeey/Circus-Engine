package org.circus.core;

import java.io.*;
import java.util.*;

public final class NativeInverseAlphaImageTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String message)throws Exception{boolean bad=false;try{work.run();}catch(IOException failure){bad=String.valueOf(failure.getMessage()).contains(message);}check(bad,"Reject "+message);}
    private static byte[] save(NativeInverseAlphaImage image)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();image.save(new DataOutputStream(b));return b.toByteArray();}
    private static NativeInverseAlphaImage load(byte[] data)throws Exception{return NativeInverseAlphaImage.load(new DataInputStream(new ByteArrayInputStream(data)));}
    public static int run()throws Exception {
        count=0;byte[] abgr={0,1,2,3, 127,4,5,6, (byte)255,7,8,9, 1,10,11,12};NativeInverseAlphaImage image=NativeInverseAlphaImage.fromAbgr32(2,2,8,abgr);
        check(image.inverseAlpha(0,0)==255&&image.rgb(0,0)==0x090807,"Logical top row comes from last storage row");check(image.inverseAlpha(0,1)==0&&image.rgb(0,1)==0x030201,"Bottom row remains first storage row");abgr[0]=99;check(image.inverseAlpha(0,1)==0,"Import is defensive");byte[] exported=image.abgr32();exported[0]=99;check(image.inverseAlpha(0,1)==0,"Export is defensive");NativeInverseAlphaImage copied=image.copy();check(copied!=image&&Arrays.equals(copied.abgr32(),image.abgr32()),"Independent immutable copy");
        byte[] bgra={7,8,9,0, 10,11,12,(byte)254, 1,2,3,(byte)255, 4,5,6,(byte)128};NativeInverseAlphaImage bridge=NativeInverseAlphaImage.fromStraightBgra32(2,2,8,bgra);check(Arrays.equals(bridge.abgr32(),image.abgr32()),"Explicit top-down straightBGRA to native inverseABGR");bgra[0]=0;check(bridge.rgb(0,0)==0x090807,"BGRA bridge owns source");
        byte[] padded={7,8,9,0, 11,12,13,14, 1,2,3,(byte)255, 21,22,23,24};NativeInverseAlphaImage padding=NativeInverseAlphaImage.fromStraightBgra32(1,2,8,padded);byte[] p=padding.abgr32();check(p[4]==21&&p[5]==22&&p[6]==23&&p[7]==24&&p[12]==11,"Explicit known padding is mirrored with row");check(padding.inverseAlpha(0,0)==255&&padding.inverseAlpha(0,1)==0,"Bgra straightalpha is inverted");
        NativeInverseAlphaImage restored=load(save(image));check(restored.width==2&&restored.height==2&&restored.stride==8&&Arrays.equals(restored.abgr32(),image.abgr32()),"NIA1 storage codec");byte[] format=save(image);format[0]=0;reject(()->load(format),"format");byte[] size=save(image);size[19]=0;reject(()->load(size),"storage length");byte[] truncated=Arrays.copyOf(save(image),22);reject(()->load(truncated),"");
        reject(()->NativeInverseAlphaImage.fromAbgr32(0,1,4,new byte[4]),"dimensions/stride");reject(()->NativeInverseAlphaImage.fromAbgr32(2,1,4,new byte[4]),"dimensions/stride");reject(()->NativeInverseAlphaImage.fromAbgr32(1,1,5,new byte[5]),"dimensions/stride");reject(()->NativeInverseAlphaImage.fromAbgr32(1,1,4,new byte[3]),"storage length");reject(()->NativeInverseAlphaImage.fromStraightBgra32(1,1,4,new byte[3]),"BGRA length");reject(()->NativeInverseAlphaImage.fromAbgr32(Integer.MAX_VALUE,1,4,new byte[4]),"dimensions/stride");
        ByteArrayOutputStream rgb=new ByteArrayOutputStream();NativeRgbImage.zeroed(1,1).save(new DataOutputStream(rgb));reject(()->load(rgb.toByteArray()),"format");reject(()->NativeRgbImage.load(new DataInputStream(new ByteArrayInputStream(save(image)))),"format");
        return count;
    }
    public static void main(String[] args)throws Exception {System.out.println("NativeInverseAlphaImageTests assertions: "+run());}
}
