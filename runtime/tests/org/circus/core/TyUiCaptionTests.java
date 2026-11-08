package org.circus.core;

import java.io.*;
import java.util.*;

/** Native replay caption encoding does not replace the translated story codec. */
public final class TyUiCaptionTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static PcHost host(String id,TextCodec codec)throws IOException{
        final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/names.dat");}
            public byte[] read(String p,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture bound");return names.clone();}
            public Archive.Source source(String p){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+count);}};}
        });Profile profile=Profiles.get(id);PcHost h=(PcHost)Dc2Host.create(new CircusResources(files,profile),codec);h.attach(new Variables(h,profile.variableModel));int v=profile.version;MesScript script=new MesScript("START",new byte[]{0,0,0,0,(byte)v,(byte)(v>>>8),0,0,0},profile);h.position(script,script.firstOffset);return h;
    }
    private static Dc2Ui.Quad caption(PcHost h,byte[] raw)throws IOException{
        h.list.add(new int[]{0,0,0,0,80,60,10,0},10);h.command("pc.grid.caption",new int[]{88,24,3,9},raw);return h.list.last().sprites.get(1);
    }
    public static int run()throws Exception{
        checks=0;TextCodec gbk=new TextCodec("","","GBK");PcHost ty=host("dc2ty-5f69",gbk);byte[] raw="テスト案内".getBytes(Binary.SJIS);
        check(!gbk.decode(raw).equals("テスト案内"),"Independent GBK counterfactual differs for native CP932 replay caption");
        Dc2Ui.Quad text=caption(ty,raw);check(text.text.equals("テスト案内"),"TY58 native replay caption uses strict CP932");
        check(text.x==88&&text.y==24&&text.width==raw.length*14&&text.height==28,"Native coordinates, raw-byte width and height unchanged");
        check(ty.list.last().sprites.get(1).state==99&&ty.list.last().sprites.get(2).state==1&&ty.list.last().sprites.get(2).text.equals(text.text),"Normal and hover native list fields keep the same Unicode caption");
        String date=PcHost.sceneListDate("hyd_0611_a1_avn");check(date.equals(" 6月11日"),"Replay date generated from native scenario resource remains unchanged");
        ty.familyName="家";ty.givenName="名";check(caption(ty,"$s/$f/$n".getBytes(Binary.SJIS)).text.equals("家/家/名"),"Prior supported player-name substitutions retained");
        String translated="正文测试";ty.command("text.body",new int[0],translated.getBytes("GBK"));check(ty.body.equals(translated),"TY4C translated prose keeps GBK");
        ty.ui.buttons.add(new Dc2Ui.Button());ty.command("ui.button_text",new int[]{0,0,3,99},translated.getBytes("GBK"));check(ty.ui.last().sprites.get(0).text.equals(translated),"TY51 button captions retain selected codec independently of58");
        ty.command("dm.ui.text",new int[]{0,0,120,28},translated.getBytes("GBK"));check(ty.ui.quads.get(ty.ui.quads.size()-1).text.equals(translated),"TY60 free text remains GBK");
        List<Dc2Ui.Button> templates=ty.ui.buttonTemplates.get(3);templates.add(new Dc2Ui.Button());Dc2Ui.Button choice=new Dc2Ui.Button();Dc2Ui.Quad q=new Dc2Ui.Quad();q.type=2;choice.sprites.add(q);templates.add(choice);ty.addChoice(new byte[]{'x'},translated.getBytes("GBK"));check(ty.ui.last().sprites.get(0).text.equals(translated),"TY choice captions retain GBK");
        PcHost pc2=host("dc2pc-5769",gbk);pc2.familyName="家";pc2.givenName="名";check(caption(pc2,(translated+"$s$f$n").getBytes("GBK")).text.equals(translated+"家家名"),"Other profiles retain prior prose codec and substitutions for58");
        PcHost japanese=host("dc2ty-5f69",TextCodec.identity());check(caption(japanese,raw).text.equals("テスト案内"),"Original Japanese TY captions use the same native field rule");
        try{caption(ty,new byte[]{(byte)0x81});throw new AssertionError("Malformed native caption accepted");}catch(IOException expected){check(expected.getMessage().contains("Invalid CP932"),"Malformed CP932 rejected without permissive fallback");}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ty.save(new DataOutputStream(bytes));PcHost restored=host("dc2ty-5f69",gbk);restored.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(restored.list.items.get(0).sprites.get(1).text.equals("テスト案内")&&restored.body.equals(translated),"Snapshot keeps native list text and translated body in their separate fields");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("TyUiCaptionTests "+run()+" PASS");}
}
