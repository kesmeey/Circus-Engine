package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent display fields: native CP932 menu captions beside GBK story text. */
public final class ScUiCaptionTests {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static CircusResources resources(String id)throws IOException{
        final byte[] names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture exceeds bound");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int size)throws IOException{Binary.range(at,size,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+size);}};}
        });
        return new CircusResources(files,Profiles.get(id));
    }
    private static PcHost host(String id,TextCodec codec)throws IOException{
        CircusResources data=resources(id);PcHost host=(PcHost)Dc2Host.create(data,codec);host.attach(new Variables(host,data.profile.variableModel));
        int version=data.profile.version;MesScript script=new MesScript("START",new byte[]{0,0,0,0,(byte)version,(byte)(version>>>8),0,0,0},data.profile);host.position(script,script.firstOffset);return host;
    }
    private static Dc2Ui.Quad caption(PcHost h,byte[] bytes)throws IOException{
        h.ui.buttons.add(new Dc2Ui.Button());h.command("ui.button_text",new int[]{12,18,3,99},bytes);return h.ui.last().sprites.get(0);
    }
    public static int run()throws Exception{
        checks=0;TextCodec gbk=new TextCodec("","","GBK");PcHost sc=host("dc2sc-3b69",gbk);
        byte[] date="@s03月14日".getBytes(Binary.SJIS);String wrong=gbk.decode(Arrays.copyOfRange(date,2,date.length));
        check(!wrong.equals("03月14日"),"Independent counterfactual GBK date differs even though its bytes are valid GBK");
        Dc2Ui.Quad q=caption(sc,date);
        check(q.text.equals("03月14日"),"SC51 decodes the native CP932 month/day caption instead of the prose encoding");
        check(q.width==date.length*14+1&&q.x==12&&q.y==18,"Caption source-byte width and native coordinates remain unchanged");
        check(sc.codec==gbk,"Native menu caption does not replace the story codec");
        String body="正文测试";sc.command("text.body",new int[0],body.getBytes("GBK"));
        check(sc.body.equals(body),"SC4C body still uses GBK");
        sc.command("dm.ui.text",new int[]{1,2,80,24},body.getBytes("GBK"));
        check(sc.ui.quads.get(sc.ui.quads.size()-1).text.equals(body),"SC60 free text keeps the selected codec; it is not a51 caption");
        List<Dc2Ui.Button> choices=sc.ui.buttonTemplates.get(3);choices.add(new Dc2Ui.Button());Dc2Ui.Button choice=new Dc2Ui.Button();Dc2Ui.Quad text=new Dc2Ui.Quad();text.type=2;choice.sprites.add(text);choices.add(choice);
        sc.addChoice(new byte[]{'x'},body.getBytes("GBK"));
        check(sc.ui.last().sprites.get(0).text.equals(body),"Localized choice captions retain GBK independently of native button text");
        PcHost pc2=host("dc2pc-5769",gbk);
        check(caption(pc2,body.getBytes("GBK")).text.equals(body),"Other profiles retain the original selected-codec button behavior");
        PcHost japanese=host("dc2sc-3b69",TextCodec.identity());
        check(caption(japanese,date).text.equals("03月14日"),"Japanese SC native menu uses the same field rule");
        try{caption(sc,new byte[]{(byte)0x81});throw new AssertionError("Malformed CP932 caption accepted");}catch(IOException expected){check(expected.getMessage().contains("Invalid CP932"),"Malformed native caption fails strictly without a charset fallback");}
        ByteArrayOutputStream state=new ByteArrayOutputStream();sc.save(new DataOutputStream(state));PcHost restored=host("dc2sc-3b69",gbk);restored.load(new DataInputStream(new ByteArrayInputStream(state.toByteArray())));
        check(restored.ui.buttons.get(0).sprites.get(0).text.equals("03月14日")&&restored.body.equals(body),"Snapshot keeps correctly decoded UI and localized story in their own fields");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("ScUiCaptionTests "+run()+" PASS");}
}
