package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/** Actual4015D0+4012E0 goldens: logical rows, all reference forms and64K wrap. */
public final class Pc1CrxLzTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String why)throws Exception{boolean bad=false;try{work.run();}catch(IOException expected){bad=true;}check(bad,why);}
    private static byte[] bytes(String hex){byte[] b=new byte[hex.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);return b;}
    private static void u16(ByteArrayOutputStream out,int value){out.write(value&255);out.write(value>>>8&255);}
    private static ByteArrayOutputStream header(int width,int height,int depth,int compression)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{'C','R','X','G'});u16(out,0);u16(out,0);u16(out,width);u16(out,height);u16(out,compression);u16(out,1);u16(out,depth);u16(out,0);return out;}
    private static byte[] fixture(int width,int height,int depth,int prefix,byte[] reference)throws Exception{
        ByteArrayOutputStream out=header(width,height,depth,1);int tokens=prefix+(reference.length==0?0:1);
        for(int first=0;first<tokens;first+=8){int end=Math.min(first+8,tokens),flags=0;for(int i=first;i<end;i++)if(i<prefix)flags|=1<<(i-first);out.write(flags);for(int i=first;i<end;i++)if(i<prefix)out.write((17+i*29)&255);else out.write(reference);}return out.toByteArray();
    }
    private static byte[] visible(Crx.Image image)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int y=0;y<image.height;y++)out.write(image.pixels,y*image.stride,image.width*(image.bpp/8));return out.toByteArray();}
    private static boolean zeroPadding(Crx.Image image){int line=image.width*(image.bpp/8);for(int y=0;y<image.height;y++)for(int x=line;x<image.stride;x++)if(image.pixels[y*image.stride+x]!=0)return false;return true;}
    private static byte[] predictorFixture(int depth)throws Exception{
        int width=3,height=2,ps=depth==0?3:4,line=width*ps;ByteArrayOutputStream encoded=new ByteArrayOutputStream();try(OutputStream zip=new DeflaterOutputStream(encoded)){for(int y=0;y<height;y++){zip.write(0);for(int x=0;x<line;x++){int current=(17+(y*line+x)*29)&255,prior=x<ps?0:(17+(y*line+x-ps)*29)&255;zip.write((current-prior)&255);}}}ByteArrayOutputStream out=header(width,height,depth,2);out.write(encoded.toByteArray());return out.toByteArray();
    }
    // BEGIN MACHINE VECTORS
    private static final String[] VECTORS={
        "literal_rgb24_padding|3|2|0|18||af19c3b8e9adb507985b917518f486cae5c33196c425aeee54c782eee4369aaf|6bfff82bfe17bc9423028e26c1ba258104b9d8703cf1b3fbd769e13c68bf0eeb|112e4b6885a2bfdcf91633506d8aa7c4e1fe",
        "literal_abgr32|3|2|1|24||466ee84eba72ef2f503feb2efe55bd0748bbcab4141efa3339ad1a70279bee84|bb8e78afef47315c9a497599aba4d2a9fc9c4a9928ef66544999a3f945ba5e7f|2e4b68eea2bfdc7a163350068aa7c492fe1b381e728facaa",
        "short_nonzero_self_overlap|5|1|0|12|a3|bc36b37fbdd77430ad53a46bf6fe7ec17243cb683f613947fba252ce36f6932b|6043d5b150b42979f41341e4cf081c23964e2bbfb5c80640a86f61bcd17ed03d|112e4b6885a2bfdcf9163350163350",
        "short_extended_offset|2|2|1|14|8005|bf74d83efd78b49f1b5a3da4c1f7aaaa444243c31e025dbe861117c03ee4a25f|d4a088bfc1e966fce1ddf534bdc409475bd500462b3e74e2cd49b2838f3f08a8|2e4b68eea2bfdc7a163350068a163392",
        "long_c0|1|5|0|11|c003|568e328cf06dfb1388dd60ab97cf0913caa0a1896017ec060617a9f1a59fa821|936137f0ac98a646c41cee3f92706519f98375aa206684da29f749453316209e|112e4b6885a2bfdcf91633f91633f9",
        "long_c0_high_offset|19|4|1|300|c111|8ddd0726c2b52e2a6c4a1ebc5f5e9c045ee6cdbee1258a680de184665b764845|e5cfbd03daf0af144a86597f586e18e65492311fafccbf0fdadc71d1709a4928|2e4b68eea2bfdc7a163350068aa7c492fe1b381e728facaae60320365a7794c2ceeb084e425f7cdab6d3f0662a4764f29ebbd87e122f4c0a86a3c096fa1734226e8ba8aee2ff1c3a567390c6cae704523e5b78deb2cfec6a264360f69ab7d4820e2b480e829fbc9af61330266a87a4b2defb183e526f8ccac6e300563a5774e2aecbe86e223f5cfa96b3d0860a2744127e9bb89ef20f2c2a6683a0b6daf714424e6b88cec2dffc5a365370e6aac7e4721e3b58fe92afcc8a062340167a97b4a2ee0b282e627f9cbad6f310464a6784d2bedbf85e324f6ceaa6c3e0761a3754028eabc88e021f3c1a7693b0a6ea0724325e7b98bed2ef0c4a466380d6bad7f4622e4b68eea2bfdc7a163350068aa7c492fe1b381e728facaae60320365a7794c2ceeb084e425f7cdab6d3f0663d5a77df",
        "long_7f|3|3|0|19|7f06000700|72fbfc57b92c831cdf95d5a9f4902385e6444ef2eebdd7b9768d337fbef37162|38daae03b2b3e840559efb6377f84abb9860e8cc51d36421a4b13d3fb8f61c07|112e4b6885a2bfdcf91633506d8aa7c4e1fe1b6d8aa7c4e1fe1b6d",
        "low_control|3|3|0|18|050900|21c75b1909749fe64ac55b7eb2f18f4c09ccb58e965ac62811b1ead1e5993d25|55ab91d172e3c9349c78ce13a0162577c56c146a10333a15047cbb141ea6e47e|112e4b6885a2bfdcf91633506d8aa7c4e1fe1633506d8aa7c4e1fe",
        "finish_inside_reference|5|1|0|10|fc03|959b6f1da8342fc03547f00f3e776aead20d1c2a7c77b1b8c65fb6aae65fd950|daf43f14183c195fc7adc85aef150a6c36057a4862e9938f0601497aed39d2b1|112e4b6885a2bfdcf916dcf916dcf9",
        "ring_wrap|3|7283|0|65530|7f0f000800|6508add1cf06ce6770cec8802070c681f6abceaf86d68ffc656cb50bd8a48b91|b83c3f8155ea4f361caca9541f9e67bb71046c26a94feaa9c4e6a9d8b0626f2c|"
    };
    // END MACHINE VECTORS
    public static int run()throws Exception{
        count=0;check(VECTORS.length==10,"All independent LZ machine vectors present");
        for(String value:VECTORS){String[] f=value.split("\\|",-1);int w=Integer.parseInt(f[1]),h=Integer.parseInt(f[2]),depth=Integer.parseInt(f[3]),prefix=Integer.parseInt(f[4]);byte[] raw=fixture(w,h,depth,prefix,bytes(f[5]));check(Binary.sha256(raw).equals(f[6]),"Native reference encoded bytes "+f[0]);Crx.Image strict=Crx.plain(raw),legacy=Crx.plain(raw,true);check(strict.width==w&&strict.height==h&&strict.bpp==(depth==0?24:32),"LZ geometry "+f[0]);check(Binary.sha256(visible(strict)).equals(f[7]),"Native visible pixels "+f[0]);check(Binary.sha256(visible(legacy)).equals(f[7]),"Legacy-tail flag does not change LZ logical rows "+f[0]);check(zeroPadding(strict)&&zeroPadding(legacy),"Decoded alignment is initialized storage, not compressed data "+f[0]);if(!f[8].isEmpty())check(Arrays.equals(visible(strict),bytes(f[8])),"Native exact visible bytes "+f[0]);byte[] truncated=Arrays.copyOf(raw,raw.length-1);reject(()->Crx.plain(truncated),"Truncated LZ input "+f[0]);}
        for(int depth:new int[]{0,1}){Crx.Image lz=Crx.plain(fixture(3,2,depth,depth==0?18:24,new byte[0])),predictor=Crx.plain(predictorFixture(depth),true);check(Arrays.equals(lz.pixels,predictor.pixels),"Compression2 unaffected by LZ row sink depth"+depth);check(lz.stride==12&&lz.pixels.length==24,"24/32 aligned storage stays24 bytes depth"+depth);}
        return count;
    }
    public static void main(String[] args)throws Exception {System.out.println("Pc1CrxLzTests assertions: "+run());}
}
