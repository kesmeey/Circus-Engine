package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Current CN private native instruction goldens, before Unicode/glyph layout. */
public final class Pc1TextExpanderTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String text)throws Exception{boolean failed=false;try{work.run();}catch(IOException e){failed=String.valueOf(e.getMessage()).contains(text);}check(failed,"Reject "+text);}
    private static byte[] ascii(String text){return text.getBytes(StandardCharsets.US_ASCII);}
    private static byte[] hex(String text){byte[] out=new byte[text.length()/2];for(int n=0;n<out.length;n++)out[n]=(byte)Integer.parseInt(text.substring(n*2,n*2+2),16);return out;}
    private static byte[] encode(byte[] decoded){byte[] out=decoded.clone();for(int i=0;i<out.length;i++)out[i]=(byte)((out[i]&255)-32);return out;}
    private static final class Fixture implements Pc1TextExpander.Context {
        byte[] given=ascii("N$n"),family=ascii("F$0,1");
        final byte[][] texts={ascii("T0"),new byte[0],ascii("Text2")},music={ascii("M0"),ascii("Music1")};
        final List<String> reads=new ArrayList<>();boolean failRead;
        public byte[] givenName(){return given;}
        public byte[] familyName(){return family;}
        public int textCount(){return texts.length;}
        public byte[] textLabel(int index){return texts[index];}
        public int musicCount(){return music.length;}
        public byte[] musicLabel(int index){return music[index];}
        public int read(int bank,int index)throws IOException{if(failRead)throw new IOException("Fixture getter owner");reads.add(bank+","+index);return bank==1&&index==0?Integer.MIN_VALUE:bank==2&&index==0?Integer.MAX_VALUE:bank*1000+index;}
    }
    // The171 successful vectors interpret current4123A0 plus420AD0/420B4F.
    // Getter and observed %d formatting are explicit fixture hooks; no OS runs.
    private static final String[][] NATIVE={
        {"","",""},
        {"706c61696e","706c61696e",""},
        {"8140c9fa","8140c9fa",""},
        {"246e2f2466","4e246e2f4624302c31",""},
        {"247430","5430",""},
        {"247431","",""},
        {"247432","5465787432",""},
        {"24743321","21",""},
        {"2474","5430",""},
        {"246d30","4d30",""},
        {"246d31","4d7573696331",""},
        {"246d3221","21",""},
        {"246d","4d30",""},
        {"24742b315a","2b315a",""},
        {"24742b32","54657874322b32",""},
        {"24742d3058","54302d3058",""},
        {"2474203258","5465787432203258",""},
        {"24302c31","31","0,1"},
        {"24332c313521","3330313521","3,15"},
        {"24302c2d3221","2d322d3221","0,-2"},
        {"24302c203721","37203721","0,7"},
        {"24302c2b3721","372b3721","0,7"},
        {"2431327841","41",""},
        {"2478","",""},
        {"61242462","6162",""},
        {"245430","30",""},
        {"6c696e650a6e657874","6c696e650a6e657874",""},
        {"24743432393439363732393621","543021",""},
        {"24343239343936373239362c3121","3121","0,1"},
        {"24312c34323934393637323936","2d32313437343833363438","1,0"},
        {"24322c30","32313437343833363437","2,0"},
        {"247430c921","543021",""},
        {"24302c31c921","3121","0,1"},
        {"24302c318140","318140","0,1"},
        {"24302c31c940","3140","0,1"},
        {"24302c31fcfec75a","315a","0,1"},
        {"24c92c32","32","0,2"},
        {"248158","58",""},
        {"6162630069676e6f726564","616263",""},
        {"24302c","30","0,0"},
        {"24302c2d","302d","0,0"},
        {"24302c2b","302b","0,0"},
        {"24302c09395a","3909395a","0,9"},
        {"24302c318021","318021","0,1"},
        {"24302c318121","318121","0,1"},
        {"24302c318221","318221","0,1"},
        {"24302c318321","318321","0,1"},
        {"24302c318421","318421","0,1"},
        {"24302c318521","318521","0,1"},
        {"24302c318621","318621","0,1"},
        {"24302c318721","3121","0,1"},
        {"24302c318821","318821","0,1"},
        {"24302c318921","318921","0,1"},
        {"24302c318a21","318a21","0,1"},
        {"24302c318b21","318b21","0,1"},
        {"24302c318c21","318c21","0,1"},
        {"24302c318d21","318d21","0,1"},
        {"24302c318e21","318e21","0,1"},
        {"24302c318f21","318f21","0,1"},
        {"24302c319021","319021","0,1"},
        {"24302c319121","319121","0,1"},
        {"24302c319221","319221","0,1"},
        {"24302c319321","319321","0,1"},
        {"24302c319421","319421","0,1"},
        {"24302c319521","319521","0,1"},
        {"24302c319621","319621","0,1"},
        {"24302c319721","319721","0,1"},
        {"24302c319821","319821","0,1"},
        {"24302c319921","319921","0,1"},
        {"24302c319a21","319a21","0,1"},
        {"24302c319b21","319b21","0,1"},
        {"24302c319c21","319c21","0,1"},
        {"24302c319d21","319d21","0,1"},
        {"24302c319e21","319e21","0,1"},
        {"24302c319f21","319f21","0,1"},
        {"24302c31a021","31a021","0,1"},
        {"24302c31a121","31a121","0,1"},
        {"24302c31a221","31a221","0,1"},
        {"24302c31a321","31a321","0,1"},
        {"24302c31a421","31a421","0,1"},
        {"24302c31a521","31a521","0,1"},
        {"24302c31a621","31a621","0,1"},
        {"24302c31a721","31a721","0,1"},
        {"24302c31a821","31a821","0,1"},
        {"24302c31a921","31a921","0,1"},
        {"24302c31aa21","31aa21","0,1"},
        {"24302c31ab21","31ab21","0,1"},
        {"24302c31ac21","31ac21","0,1"},
        {"24302c31ad21","31ad21","0,1"},
        {"24302c31ae21","31ae21","0,1"},
        {"24302c31af21","3121","0,1"},
        {"24302c31b021","31b021","0,1"},
        {"24302c31b121","31b121","0,1"},
        {"24302c31b221","31b221","0,1"},
        {"24302c31b321","31b321","0,1"},
        {"24302c31b421","31b421","0,1"},
        {"24302c31b521","31b521","0,1"},
        {"24302c31b621","31b621","0,1"},
        {"24302c31b721","31b721","0,1"},
        {"24302c31b821","31b821","0,1"},
        {"24302c31b921","31b921","0,1"},
        {"24302c31ba21","31ba21","0,1"},
        {"24302c31bb21","31bb21","0,1"},
        {"24302c31bc21","31bc21","0,1"},
        {"24302c31bd21","31bd21","0,1"},
        {"24302c31be21","31be21","0,1"},
        {"24302c31bf21","31bf21","0,1"},
        {"24302c31c021","31c021","0,1"},
        {"24302c31c121","31c121","0,1"},
        {"24302c31c221","31c221","0,1"},
        {"24302c31c321","31c321","0,1"},
        {"24302c31c421","31c421","0,1"},
        {"24302c31c521","31c521","0,1"},
        {"24302c31c621","31c621","0,1"},
        {"24302c31c721","3121","0,1"},
        {"24302c31c821","31c821","0,1"},
        {"24302c31c921","3121","0,1"},
        {"24302c31ca21","3121","0,1"},
        {"24302c31cb21","31cb21","0,1"},
        {"24302c31cc21","3121","0,1"},
        {"24302c31cd21","31cd21","0,1"},
        {"24302c31ce21","31ce21","0,1"},
        {"24302c31cf21","31cf21","0,1"},
        {"24302c31d021","31d021","0,1"},
        {"24302c31d121","31d121","0,1"},
        {"24302c31d221","31d221","0,1"},
        {"24302c31d321","31d321","0,1"},
        {"24302c31d421","31d421","0,1"},
        {"24302c31d521","31d521","0,1"},
        {"24302c31d621","31d621","0,1"},
        {"24302c31d721","31d721","0,1"},
        {"24302c31d821","31d821","0,1"},
        {"24302c31d921","31d921","0,1"},
        {"24302c31da21","31da21","0,1"},
        {"24302c31db21","31db21","0,1"},
        {"24302c31dc21","31dc21","0,1"},
        {"24302c31dd21","31dd21","0,1"},
        {"24302c31de21","31de21","0,1"},
        {"24302c31df21","31df21","0,1"},
        {"24302c31e021","31e021","0,1"},
        {"24302c31e121","31e121","0,1"},
        {"24302c31e221","3121","0,1"},
        {"24302c31e321","31e321","0,1"},
        {"24302c31e421","3121","0,1"},
        {"24302c31e521","31e521","0,1"},
        {"24302c31e621","31e621","0,1"},
        {"24302c31e721","31e721","0,1"},
        {"24302c31e821","31e821","0,1"},
        {"24302c31e921","31e921","0,1"},
        {"24302c31ea21","31ea21","0,1"},
        {"24302c31eb21","31eb21","0,1"},
        {"24302c31ec21","31ec21","0,1"},
        {"24302c31ed21","31ed21","0,1"},
        {"24302c31ee21","31ee21","0,1"},
        {"24302c31ef21","31ef21","0,1"},
        {"24302c31f021","31f021","0,1"},
        {"24302c31f121","31f121","0,1"},
        {"24302c31f221","31f221","0,1"},
        {"24302c31f321","31f321","0,1"},
        {"24302c31f421","31f421","0,1"},
        {"24302c31f521","31f521","0,1"},
        {"24302c31f621","31f621","0,1"},
        {"24302c31f721","31f721","0,1"},
        {"24302c31f821","31f821","0,1"},
        {"24302c31f921","31f921","0,1"},
        {"24302c31fa21","31fa21","0,1"},
        {"24302c31fb21","31fb21","0,1"},
        {"24302c31fc21","3121","0,1"},
        {"24302c31fd21","31fd21","0,1"},
        {"24302c31fe21","3121","0,1"},
        {"24302c31ff21","31ff21","0,1"}
    };
    public static int run()throws Exception{
        count=0;
        for(String[] vector:NATIVE){Fixture context=new Fixture();byte[] source=hex(vector[0]),before=source.clone(),out=Pc1TextExpander.expand(source,context);check(Arrays.equals(out,hex(vector[1])),"Native vector "+vector[0]);check(String.join(";",context.reads).equals(vector[2]),"Native getter arguments "+vector[0]);check(Arrays.equals(source,before),"Source remains raw "+vector[0]);byte[] nativeBuffer=Pc1TextExpander.nativeBuffer(out);check(nativeBuffer.length==out.length+2&&nativeBuffer[out.length]==0&&nativeBuffer[out.length+1]==0,"Native double NUL "+vector[0]);}
        for(int n=1;n<256;n++){byte[] value=Pc1TextExpander.decodeEncoded(new byte[]{(byte)n});int decoded=(n+32)&255;check(decoded==0?value.length==0:value.length==1&&(value[0]&255)==decoded,"Byte+20 wrap "+n);}
        check(Pc1TextExpander.decodeEncoded(hex("040000ff")).length==1,"Original NUL ends418090 copy");
        check(Arrays.equals(Pc1TextExpander.decodeEncoded(hex("0404")),ascii("$$")),"Dollar remains byte24");
        check(Arrays.equals(Pc1TextExpander.decodeEncoded(hex("41e042")),hex("61")),"Decoded NUL truncates before later converted bytes");
        Fixture live=new Fixture();check(Arrays.equals(Pc1TextExpander.expand(Pc1TextExpander.decodeEncoded(encode(ascii("$n"))),live),ascii("N$n")),"Decode then live name without recursive expansion");
        live.given=ascii("New");check(Arrays.equals(Pc1TextExpander.expand(ascii("$n"),live),ascii("New")),"Fresh redraw reads live name");
        live.texts[0]=ascii("$m1");check(Arrays.equals(Pc1TextExpander.expand(ascii("$t0"),live),ascii("$m1")),"Catalog bytes do not recurse");
        live.family=hex("41ff0042");check(Arrays.equals(Pc1TextExpander.expand(ascii("$f"),live),hex("41ff")),"Substitution is a raw C string");
        live.failRead=true;reject(()->Pc1TextExpander.expand(ascii("$0,1"),live),"getter owner");
        reject(()->Pc1TextExpander.expand(ascii("$"),new Fixture()),"source terminator");
        reject(()->Pc1TextExpander.expand(ascii("$12"),new Fixture()),"source terminator");
        reject(()->Pc1TextExpander.expand(ascii("$t-1"),new Fixture()),"negative text catalog");
        reject(()->Pc1TextExpander.expand(ascii("$m2147483648"),new Fixture()),"negative music catalog");
        byte[] maximum=new byte[1022];Arrays.fill(maximum,(byte)'A');check(Pc1TextExpander.expand(maximum,new Fixture()).length==1022,"Body has room for both NULs");
        byte[] tooLong=Arrays.copyOf(maximum,1023);tooLong[1022]='A';reject(()->Pc1TextExpander.expand(tooLong,new Fixture()),"destination");
        byte[] speaker=new byte[254];Arrays.fill(speaker,(byte)'S');check(Pc1TextExpander.expandSpeaker(speaker,new Fixture()).length==254,"Speaker256 allocation includes double NUL");
        reject(()->Pc1TextExpander.expandSpeaker(Arrays.copyOf(maximum,255),new Fixture()),"destination");
        reject(()->Pc1TextExpander.expand(ascii("A"),new Fixture(),2),"destination");
        check(Pc1TextExpander.expand(new byte[0],new Fixture(),2).length==0,"Empty double terminator fits2");
        reject(()->Pc1TextExpander.expand(new byte[0],new Fixture(),1),"double terminator");
        byte[] scratch=new byte[1024];Arrays.fill(scratch,(byte)1);reject(()->Pc1TextExpander.decodeEncoded(scratch),"418090 scratch");
        check(Pc1TextExpander.decodeEncoded(Arrays.copyOf(scratch,1023)).length==1023,"418090 original scratch last valid C string");
        byte[] drawMaximum=new byte[2046];Arrays.fill(drawMaximum,(byte)0x21);check(Pc1TextExpander.expandDraw(drawMaximum,new Fixture()).length==2046,"411D30 uses its own2048-byte destination including two NULs");
        byte[] drawCopyMaximum=Arrays.copyOf(drawMaximum,2047);drawCopyMaximum[2046]=0x21;reject(()->Pc1TextExpander.expandDraw(drawCopyMaximum,new Fixture()),"destination");
        drawCopyMaximum[1]=(byte)0xe0;check(Arrays.equals(Pc1TextExpander.expandDraw(drawCopyMaximum,new Fixture()),ascii("A")),"2047-byte encoded copy fits411D30 and a converted NUL truncates expansion");
        byte[] drawTooLong=new byte[2048];Arrays.fill(drawTooLong,(byte)0x21);reject(()->Pc1TextExpander.expandDraw(drawTooLong,new Fixture()),"411D30 scratch");
        check(Arrays.equals(Pc1TextExpander.expandDraw(encode(ascii("$n/$0,1")),new Fixture()),ascii("N$n/1")),"411D30 adds20 before current name/variable expansion without recursive substitution");
        reject(()->Pc1TextExpander.nativeBuffer(hex("410042")),"contains NUL");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PC1 native byte expander assertions: "+run());}
}
