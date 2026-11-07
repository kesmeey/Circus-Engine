package org.circus.core;

import java.io.*;
import java.util.*;

/** Whole current4093F0/409140/4170C0 + actual cache/RGB/decoration instruction
 * goldens. Only the mono provider is a synthetic, explicitly fixed leaf. */
public final class Pc1GlyphServiceTests {
    private static int count;
    private static void check(boolean v,String why){count++;if(!v)throw new AssertionError(why);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action,String why)throws Exception{boolean bad=false;try{action.run();}catch(IOException expected){bad=why==null||String.valueOf(expected.getMessage()).contains(why);}check(bad,"Reject "+why);}
    private static final String[] GOLDEN={
        "ascii28|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|14c14c34b8581e51a0f23aa106ff42247f16784464e24bb8b264996a8eedc784|4f0a669d52ca8cb39b731d71efbd74464200e78572f6ddf2c0cfee4c3263f7bd|2|28",
        "gbk_ff12|7687b7bd9784723132e1faf452abb07bfeff0516e1d0753affc1ae6b039edde9|7cd873b44e2bb01b610cd71d874b4f722d63fbd4d6ca98211cbaeef56c3620f6|0a5acbdd2d20f198ce6e04b30307e6f8226e9014062aaaf2e2ec68603dc47930|3|12",
        "gbk_ff28|d61eb5e8db57e4fa6218a41f76adce5ac0dd70b68747e535ef95eab2bb253091|8ce529d9d84a33fa5bcbbd7427ae6a381ee17ff86ca050a6942d35625e264d4c|c0f1f0219eb50c49a910c2f997e45aff12d4a9468a1cefeb571f51949de4b3cc|3|28",
        "odd_size17|360f25812357649ac6f114d654b4a6879eda3243901acf8102070da846451c5a|68b79a33a31ccb8358b567a348b051b8ffe9c0d480af72b88031f8de5b8282d8|f0739b13448e9a4b47555e890ce48f6f5784d14d330f5f36836ccfa3d23e30cf|2|17",
        "negative_size|cfc729ae01ccd416c834b4038d9354e009729c1d9f752ef99badc3c230c6c3d9|6456db9a697810d36c17aa5c4b41db9f8e8221b23c019bf2831622b93998b025|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|2|-1",
        "offscreen|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|6456db9a697810d36c17aa5c4b41db9f8e8221b23c019bf2831622b93998b025|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|2|28",
        "empty|58175f7554288e1a97f0327800d13f37e4263b05f4f55d578c814e8bc4cda9c3|6456db9a697810d36c17aa5c4b41db9f8e8221b23c019bf2831622b93998b025|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|0|0",
        "embedded_nul_cache_collision|601f0384ca8200304da73f731a545528c288d48bfd7308587a3d611c8b93e3e7|0079037a49d81ec64f1b5e72ead6f06c386dce0b75fe7a1a2cc3306c063828a6|a2f0e10a76dc34c51f48a3c48110fa1d2d7bf38cbb8122c3e251554a92e5e6c1|2|12",
        "decor_0|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|4c17a2163a730782a3e4dd0c7810b3a23f67e66e38a18dc0c3b325b8e871c8ca|54fed40be23302ca91353fbcdec64f2833df89d9ba01d210eeccd330600c4a46|2|28",
        "decor_8|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|8ef467ecd7fcb4b2a644ee463476fb889960b7ab13133e2d003b028e083e03da|73879a7d80d6f445bc2bcf4e36695201608a6a01ba6baaf1212f6a58ce3ed667|2|28",
        "decor_31|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|8b866d84366b651dd97ecf9b42c058582aa2312fe7972034b226c4708e26a224|73879a7d80d6f445bc2bcf4e36695201608a6a01ba6baaf1212f6a58ce3ed667|2|28",
        "decor_-1|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|4b4b724c5901d2e8dcfe0c847c754dfad205dd31fe749998a4545bd1613ee0f4|73879a7d80d6f445bc2bcf4e36695201608a6a01ba6baaf1212f6a58ce3ed667|2|28",
        "decor_-2147483648|1a7f37494b39cd7ec203f11c9e45d7a700be8814a955b1a461138fb9ad290dfe|4ed15261277255646256a18b5d55120e878db92db23819a86bbd4fcef29adc36|73879a7d80d6f445bc2bcf4e36695201608a6a01ba6baaf1212f6a58ce3ed667|2|28",
        "solid_decor|c960799e0d8e3b67bee1e7b6a2fe0131c7b372db73628485ed3e9047b4497a8f|ba568f081ffdff091d2d6421c6eda1d24f012cbab86035ef9f182ec2ac42a228|48c0b176d4539281af7e61d8adf10fd23e284fa287451a5fa3873ed1710b3139|1|28",
        "ruby_two_pass|24275c9647d9da9817ea2b112345ac7257e0032309ca0b58491e9edbe5c611f2|ac3526d1b13450102218e3f8fd690b16c87f09bd56a48f54c1d4176123fb11f6|d129363ffaa8a6239eb53963874f2c468d9d5cf1178e21f71ca83dfbf9b34cd9|3|12",
        "raw_cross_record|8c5572aacbdb54deba55920fefb86f93d529ef38d420ccb0f8edc950402d066f|054945576e735bf91c52be42c8a24b023558c664977977824f1d4655f7d7e1c4|7074fae6bddbe35c6cf54b4712b0c982e1e49b18a43bed57c8b02a1f72986943|8|28",
        "negative_size_record|cfc729ae01ccd416c834b4038d9354e009729c1d9f752ef99badc3c230c6c3d9|6456db9a697810d36c17aa5c4b41db9f8e8221b23c019bf2831622b93998b025|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|2|-1",
    };
    private static byte[] hex(String text){byte[] b=new byte[text.length()/2];for(int n=0;n<b.length;n++)b[n]=(byte)Integer.parseInt(text.substring(n*2,n*2+2),16);return b;}
    private static final class Font implements Pc1GlyphCache.Provider {
        int queries,fontSize;
        public Pc1GlyphRaster.Outline outline(byte[] raw,int offset,int size)throws IOException{
            queries++;fontSize=size;if(size<=0||size>40)return new Pc1GlyphRaster.Outline(0,0,0,0,0,0,0,new byte[0]);int w=size*2,stride=(w+31)/32*4;byte[] mono=new byte[stride*w];int character=Pc1GlyphRaster.characterCode(raw,offset);
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){int cell=(character+(y*size+x)*7+size)&15;for(int bit=0;bit<4;bit++)if((cell&(1<<bit))!=0){int px=x*2+(bit&1),py=y*2+(bit>>>1);mono[py*stride+(px>>>3)]|=(byte)(0x80>>>(px&7));}}
            return new Pc1GlyphRaster.Outline(0,w,w,0,0,0,0,mono);
        }
        public byte[] saveFontContext(){return new byte[]{(byte)fontSize};}public void restoreFontContext(byte[] state)throws IOException{if(state.length!=1)throw new IOException("fixture font context");fontSize=state[0];}
    }
    private static final class Sink implements Pc1GlyphService.Sink {
        final NativeRgbImage image;final Pc1Raster.Clip clip;final ByteArrayOutputStream touched=new ByteArrayOutputStream();int paletteQueries;boolean rejectPalette;Pc1GlyphService observed;boolean snapshotRejected,reentryRejected;
        Sink()throws IOException{byte[] bytes=new byte[192*48];for(int n=0;n<bytes.length;n++)bytes[n]=(byte)(n*13+17);image=NativeRgbImage.fromBgr24(64,48,192,bytes);clip=Pc1Raster.Clip.full(image);}
        public Pc1Raster.Clip clip(){return clip;}
        public int rawPalette(int n)throws IOException{paletteQueries++;if(rejectPalette)throw new IOException("fixture unknown palette owner");return((n*19+11)&255)|(((n*31+17)&255)<<8)|(((n*7+3)&255)<<16);}
        void touch(Pc1GlyphRaster.Draw d){if(d.touched)for(int value:new int[]{d.rectangle.left,d.rectangle.top,d.rectangle.right,d.rectangle.bottom})for(int n=0;n<4;n++)touched.write(value>>>(n*8));}
        public Pc1GlyphRaster.Draw draw(int x,int y,int size,byte[] known,int offset,int rgb)throws IOException{
            Pc1GlyphRaster.Draw d=Pc1GlyphRaster.draw(image,clip,x,y,size,known,offset,rgb);touch(d);
            if(observed!=null){try{observed.save(new DataOutputStream(new ByteArrayOutputStream()));}catch(IOException expected){snapshotRejected=true;}try{observed.drawRaw(this,0,0,new byte[]{0},0,0,28);}catch(IOException expected){reentryRejected=true;}}
            return d;
        }
        public void decoration(int l,int t,int r,int b,int rgb,int weight)throws IOException{touch(Pc1GlyphRaster.decoration(image,clip,l,t,r,b,rgb,weight));}
    }
    private static int[] record(int x,int y,int right,int bottom,int size,int style,int decor,int raw){return new int[]{x,y,right,bottom,size,style,decor,raw};}
    private static byte[] records(int[]...values){ByteArrayOutputStream out=new ByteArrayOutputStream();for(int[] v:values)for(int field:v)for(int b=0;b<4;b++)out.write(field>>>(b*8));return out.toByteArray();}
    private static void runCase(Pc1GlyphService service,Sink sink,String name)throws IOException{
        if(name.equals("ascii28"))service.drawRaw(sink,5,10,hex("414200"),0,1,28);
        else if(name.equals("gbk_ff12")||name.equals("gbk_ff28"))service.drawRaw(sink,5,10,hex("8140ff4100"),0,1,name.endsWith("12")?12:28);
        else if(name.equals("odd_size17"))service.drawRaw(sink,5,10,hex("414200"),0,1,17);
        else if(name.equals("negative_size"))service.drawRaw(sink,5,10,hex("414200"),0,1,-1);
        else if(name.equals("offscreen"))service.drawRaw(sink,100,10,hex("414200"),0,1,28);
        else if(name.equals("empty"))service.drawRaw(sink,5,10,new byte[]{0},0,1,12);
        else if(name.equals("embedded_nul_cache_collision"))service.drawRaw(sink,5,10,hex("7d81004100"),0,1,12);
        else if(name.startsWith("decor_")){int shade=Integer.parseInt(name.substring(6));service.drawRecords(sink,records(record(5,10,33,42,28,0xac1,3,65),record(33,10,61,42,28,0xac1,2,66)),0,2,shade);}
        else if(name.equals("solid_decor"))service.drawRecords(sink,records(record(5,10,33,42,28,0x4ac1,3,65)),0,1,31);
        else if(name.equals("ruby_two_pass"))service.drawRecords(sink,records(record(5,5,17,17,12,0x81,0,0x4581),record(5,20,33,48,28,0x81,0,65),record(20,5,32,17,12,0x81,0,0x9c81)),0,3,8);
        else if(name.equals("raw_cross_record"))service.drawRecords(sink,records(record(5,10,33,42,28,1,0,0x44434241),record(0x45464748,0,61,42,28,1,0,90)),0,2,0);
        else if(name.equals("negative_size_record"))service.drawRecords(sink,records(record(5,10,33,42,-1,1,0,0x4241)),0,1,0);
        else throw new IOException("Unknown case "+name);
    }
    public static int run()throws Exception{
        count=0;check(GOLDEN.length==17,"All complete native raw wrappers/two-pass consumers");
        for(String literal:GOLDEN){String[] v=literal.split("\\|");Pc1GlyphCache cache=Pc1GlyphCache.zeroBss();Font font=new Font();Pc1GlyphService service=new Pc1GlyphService(cache,font);Sink sink=new Sink();sink.observed=service;runCase(service,sink,v[0]);check(Binary.sha256(cache.bytes()).equals(v[1]),"whole native cache after consumer "+v[0]);check(Binary.sha256(sink.image.bgr24()).equals(v[2]),"whole native RGB24 buffer after consumer "+v[0]);check(Binary.sha256(sink.touched.toByteArray()).equals(v[3]),"exact407580 call ordering/bounds "+v[0]);check(font.queries==Integer.parseInt(v[4])&&font.fontSize==Integer.parseInt(v[5]),"raw wrapper cache misses/context "+v[0]);if(!v[0].equals("empty"))check(sink.snapshotRejected&&sink.reentryRejected,"no callback snapshot or reentry across submitted pixels "+v[0]);}
        Pc1GlyphCache cache=Pc1GlyphCache.zeroBss();Font font=new Font();Pc1GlyphService service=new Pc1GlyphService(cache,font);Sink off=new Sink();off.rejectPalette=true;service.drawRaw(off,100,100,hex("414200"),0,999,28);check(font.queries==2&&off.paletteQueries==0,"fully clipped strings still populate native glyph cache but never read unused palette");
        Sink normal=new Sink();Pc1GlyphService.Cursor c=service.drawRaw(normal,5,10,hex("8140ff4100"),0,1,17);check(c.x==38&&c.sourceOffset==4,"fixed byte-class advance uses17 and truncated17/2, not GDI cell increment");Pc1GlyphService.Cursor embedded=service.drawRaw(normal,0,0,hex("7d81004100"),0,1,12);check(embedded.sourceOffset==4&&embedded.x==24,"NUL as second DBCS byte is consumed with its lead, not a Unicode/strlen stop");
        Sink unsupported=new Sink();reject(()->service.drawRecords(unsupported,records(record(5,10,33,42,28,1,0,0x44434241)),0,1,0),"unowned neighboring raw source");check(!Arrays.equals(unsupported.image.bgr24(),new Sink().image.bgr24()),"unknown C-string tail faults only after already submitted known-prefix glyphs");reject(()->service.drawRecords(normal,new byte[1],0,1,0),"unowned neighboring backing");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1GlyphServiceTests "+run());}
}
