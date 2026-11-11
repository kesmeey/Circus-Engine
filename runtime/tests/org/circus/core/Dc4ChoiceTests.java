package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Native PH5C owns and decodes raw caption6C, independently of its marker. */
public final class Dc4ChoiceTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void bytes(ByteArrayOutputStream out,int... n){for(int v:n)out.write(v);}
    private static void word(ByteArrayOutputStream out,int n){bytes(out,n,n>>>8);}
    private static void words(ByteArrayOutputStream out,int op,int... n){out.write(op);for(int v:n)word(out,v);}
    private static void string(ByteArrayOutputStream out,int op,String text,boolean encoded){out.write(op);for(byte b:text.getBytes(Binary.SJIS)){int v=b&255;out.write(encoded?((v==32?36:v)-32)&255:v);}out.write(0);}
    private static Dc4Host host()throws IOException{
        byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit){return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int size)throws IOException{Binary.range(at,size,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+size);}};}
        });return new Dc4Host(new CircusResources(files,Profiles.get("dc4ph-abb6")),TextCodec.identity(),true);
    }
    private static MesScript fixture(int marker)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();bytes(out,0,0,0,0,0xb6,0xab,0);
        string(out,0x5f,"",false);string(out,0x10,"not a caption",false);
        string(out,0x5c,"left",false);string(out,marker,"Left Choice",true);
        string(out,0x5c,"right",false);string(out,marker,"夢",true);bytes(out,0x0a,0,0);
        string(out,0x4b,"left",false);words(out,0x82,0,0,1,0);string(out,0x54,"end",false);
        string(out,0x4b,"right",false);words(out,0x82,0,0,2,0);string(out,0x4b,"end",false);bytes(out,0,0,0);
        return new MesScript("choice-fixture",out.toByteArray(),Profiles.get("dc4ph-abb6"));
    }
    public static int run()throws Exception{
        checks=0;MesScript script=fixture(0x6c);Dc4Host h=host();MesVm vm=new MesVm(n->script,h,"choice-fixture");
        check(vm.run(100)==MesVm.Status.WAIT_CHOICE&&h.choices().size()==2,"Actual PH choice marker6C reaches its native selection wait");
        check(h.choices().get(0).caption.equals("Left Choice")&&h.choices().get(1).caption.equals("夢"),"Caption payload always receives byte+32 and dollar-to-space decoding");
        check(Profiles.get("dc4ph-abb6").operation(0x6c).equals("native.no_effect"),"Standalone6C stays a native default rather than a fabricated caption handler");
        byte[] save=vm.snapshot();
        for(int index=0;index<2;index++){Dc4Host copy=host();MesVm selected=new MesVm(n->script,copy,"choice-fixture");selected.restore(save);byte[] label=copy.choices().get(index).label();selected.choose(label,index);check(selected.run(100)==MesVm.Status.HALTED&&selected.variables.read(0,0)==index+1,"Restored PH choice resolves its original label and preserves caption ownership");}
        MesScript arbitrary=fixture(0x30);Dc4Host raw=host();MesVm rawVm=new MesVm(n->arbitrary,raw,"choice-fixture");check(rawVm.run(100)==MesVm.Status.WAIT_CHOICE&&raw.choices().get(1).caption.equals("夢"),"PH handler consumes a raw marker even when standalone framing would differ");
        check(Profiles.get("dc4ph-abb6").ownsStringPayload(0x5f)&&Profiles.get("dc4ph-abb6").ownsStringPayload(0x5c),"Both native5C and5F own following raw strings");
        Profile dd=Profiles.get("dc3dd-a5a8");int choice=-1;for(int op=0;op<256;op++)if(dd.operation(op).equals("choice.item")){choice=op;break;}
        ByteArrayOutputStream other=new ByteArrayOutputStream();bytes(other,0,0,0,0,0xa8,0xa5,0);string(other,choice,"left",false);words(other,dd.wordsStart,1,2,3,4);bytes(other,0,0,0);
        MesScript strict=new MesScript("strict-dd",other.toByteArray(),dd);MesVm strictVm=new MesVm(n->strict,new Dc3Host(null,TextCodec.identity(),true),"strict-dd");check(strictVm.run(100)==MesVm.Status.FAULT&&strictVm.error().contains("Choice caption does not match"),"Other editions retain their reviewed caption-opcode check");
        return checks;
    }
    public static void main(String[] args)throws Exception{
        run();if(args.length>0){int count=0;Profile profile=Profiles.get("dc4ph-abb6");List<Path> files;try(Stream<Path> stream=Files.list(Paths.get(args[0]).resolve("Advdata/MES"))){files=stream.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".mes")).collect(Collectors.toList());}
            for(Path file:files){MesScript script=new MesScript(file.getFileName().toString(),Files.readAllBytes(file),profile);for(MesScript.Record r:script.records())if(r.payloadOwner<0&&r.opcode==0x5c){MesScript.Record payload=script.instruction(r.offset).stringPayload;check(payload!=null&&payload.opcode==0x6c,"Actual native choice payload marker");byte[] caption=payload.bytes.clone();for(int i=0;i<caption.length;i++){int v=(caption[i]&255)+32&255;caption[i]=(byte)(v==36?32:v);}NativeText.plain(caption,TextCodec.identity(),"常坂","一登",false);count++;}}
            check(count==74,"All actual PH choice captions parsed without another text-control rejection");System.out.println("PH actual choice captions "+count);
        }System.out.println("Dc4ChoiceTests "+checks+" PASS");
    }
}
