package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** PH ruby base preservation; the shared DD rejection remains in force. */
public final class Dc4RubyTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] raw(String text){return text.getBytes(Binary.SJIS);}
    private static String parse(Dc4Ruby ruby,String text)throws IOException{return new String(ruby.parse(raw(text)),Binary.SJIS);}
    public static int run()throws Exception{
        checks=0;Dc4Ruby ruby=new Dc4Ruby();
        check(parse(ruby,"普通の文").equals("普通の文")&&ruby.spanCount()==0,"Ordinary PH text remains unchanged");
        byte[] trail={(byte)0x82,(byte)0x81,0x6f,0x78};check(Arrays.equals(ruby.parse(trail),trail),"A CP932 trail byte81 followed by ASCII6F is not mistaken for a ruby marker");
        check(parse(ruby,"　それこそ｛・／夢｝だ。").equals("　それこそ夢だ。"),"Actual prologue ruby preserves its base word");
        check(ruby.spanCount()==1&&ruby.span(0).startByte==10&&ruby.span(0).endByte==12&&Arrays.equals(ruby.span(0).annotationRaw(),raw("・")),"Actual prologue annotation retains native byte position and emphasis");
        check(parse(ruby,"　常坂二乃――俺の義妹はどうやら｛・・／おこ｝のようだ。").equals("　常坂二乃――俺の義妹はどうやらおこのようだ。"),"Actual second-route text also removes control delimiters without deleting body text");
        check(parse(ruby,"｛ゆめ／夢｝").equals("夢")&&Arrays.equals(ruby.span(0).annotationRaw(),raw("ゆめ")),"Reading annotations remain retained separately from the base");
        check(parse(ruby,"｛・・・／おこ｝").equals("おこ")&&Arrays.equals(ruby.span(0).annotationRaw(),raw("・・")),"Native emphasis point count follows base byte width");
        check(parse(ruby,"｛・／ABC｝").equals("ABC")&&ruby.span(0).annotationRaw().length==4,"Odd ASCII byte width rounds native full-width emphasis capacity upward");
        check(parse(ruby,"｛・／夢｝と｛・・／現実｝").equals("夢と現実")&&ruby.spanCount()==2&&ruby.span(1).startByte==4,"Multiple ruby spans retain their positions in the resulting base stream");
        ByteArrayOutputStream out=new ByteArrayOutputStream();ruby.save(new DataOutputStream(out));Dc4Ruby restored=new Dc4Ruby();restored.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));check(restored.spanCount()==2&&Arrays.equals(restored.span(1).annotationRaw(),raw("・・")),"Annotation metadata survives a cold host snapshot");
        for(String invalid:new String[]{"｛・夢｝","｛・／夢","｛・／｛夢｝｝","｛・／夢／文｝"}){try{parse(ruby,invalid);throw new AssertionError("Malformed ruby accepted");}catch(IOException expected){check(true,"Unreviewed malformed ruby remains explicit");}}
        try{NativeText.plain(raw("｛・／夢｝"),TextCodec.identity(),"常坂","一登",false);throw new AssertionError("Shared rejection changed");}catch(NativeText.UnsupportedControl expected){check(true,"Other editions still refuse unimplemented ruby controls");}
        byte[] base=ruby.parse(raw("｛・／@r｝"));try{NativeText.plain(base,TextCodec.identity(),"常坂","一登",false);throw new AssertionError("Ruby bypassed control validation");}catch(NativeText.UnsupportedControl expected){check(true,"Ruby cannot hide another unimplemented text control in its base");}
        return checks;
    }
    public static void main(String[] args)throws Exception{
        run();
        if(args.length>0){
            int files=0,bodies=0,rubyLines=0,rubySpans=0;Profile profile=Profiles.get("dc4ph-abb6");Dc4Macros macros=new Dc4Macros();Dc4Ruby ruby=new Dc4Ruby();TextCodec codec=TextCodec.identity();
            List<Path> scripts;try(Stream<Path> paths=Files.list(Paths.get(args[0]).resolve("Advdata/MES"))){scripts=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mes")).sorted().collect(Collectors.toList());}
            for(Path path:scripts){files++;MesScript script=new MesScript(path.getFileName().toString(),Files.readAllBytes(path),profile);
                for(MesScript.Record r:script.records()){
                    if(r.payloadOwner<0&&r.opcode==0)break;
                    if(r.payloadOwner>=0||r.opcode!=0x6b)continue;
                    try{byte[] body=macros.parseBody(script.nativeText(r),3);body=ruby.parse(body);if(ruby.spanCount()>0){rubyLines++;rubySpans+=ruby.spanCount();}body=new Dc3Performance().parse(body,3);NativeText.plain(body,codec,"常坂","一登",false);bodies++;}
                    catch(IOException problem){throw new IOException(path.getFileName()+"@"+Integer.toHexString(r.offset),problem);}
                }
            }
            check(files==536&&bodies==60494,"All actual PH scripts and body records were checked");check(rubyLines==478&&rubySpans==524,"All emphasis and reading annotation variants pass the actual Java pipeline");
            System.out.println("PH corpus "+files+" scripts, "+bodies+" body records, "+rubyLines+" ruby lines, "+rubySpans+" spans,0 unsupported text controls");
        }
        System.out.println("Dc4RubyTests "+checks+" PASS");
    }
}
