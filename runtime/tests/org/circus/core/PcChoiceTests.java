package org.circus.core;

import java.io.*;
import java.util.*;

/** Self-authored choice bytecode checks native state conversion and old phone save migration. */
public final class PcChoiceTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static PcHost host(String profile)throws Exception{
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,20);return new byte[n];}};}
        });
        PcHost h=new PcHost(new CircusResources(files,Profiles.get(profile)),TextCodec.identity());h.attach(new Variables(h,h.second?Variables.Model.PC_WIDE:Variables.Model.PC_BYTE));return h;
    }
    private static void bytes(ByteArrayOutputStream out,int...values){for(int value:values)out.write(value);}
    private static void words(ByteArrayOutputStream out,int op,int...values){out.write(op);for(int value:values)bytes(out,value,value>>8);}
    private static void string(ByteArrayOutputStream out,int op,String value,boolean encoded)throws Exception{out.write(op);for(byte b:value.getBytes(Binary.SJIS))out.write(encoded?((b&255)-32)&255:b&255);out.write(0);}
    private static MesScript script()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();bytes(out,0,0,0,0,0x69,0x57);
        for(int n=0;n<3;n++){
            words(out,0x4f,136,200+56*n,616,240+56*n);string(out,0x3a,n==0?"":":"+n,false);
            if(n>0)for(int state:new int[]{9,1,2}){words(out,0x51,182,148+56*n,135,state);string(out,0x45,"",false);}
        }
        bytes(out,0x17,3,0,8,1,0);string(out,0x44,"left",false);string(out,0x4d,"Left",true);string(out,0x44,"right",false);string(out,0x4d,"Right",true);bytes(out,9,0,0);
        string(out,0x35,"left",false);bytes(out,0,0,0);string(out,0x35,"right",false);bytes(out,0,0,0);
        return new MesScript("choice",out.toByteArray(),Profiles.get("dc2pc-5769"));
    }
    private static List<String> captured(PcHost h,int hovered,int pressed)throws Exception{
        h.ui.hovered=hovered;h.ui.pressed=pressed;h.captureUi();List<String> result=new ArrayList<>();
        for(Dc2Ui.Quad q:new SceneCaptures.Legacy(h.uiCapture.input()).frame.quads)if(q.type==2)result.add(q.text);return result;
    }
    private static void makeOld(List<Dc2Ui.Button> buttons){for(Dc2Ui.Button b:buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2&&q.state==99)q.state=9;}
    public static int run()throws Exception{
        count=0;MesScript code=script();PcHost h=host("dc2pc-5769");MesVm vm=new MesVm(n->code,h,"choice");
        check(h.ui.palette[0]==0xff000000&&h.ui.palette[7]==0xffffffff&&h.ui.palette[9]==0xffccccff,"PC2 native text and shadow palette defaults");
        check(vm.run(100)==MesVm.Status.WAIT_HOST&&h.ui.buttons.size()==2,"PC2 encoded choice captions reach the native menu wait");
        check(h.ui.buttons.get(0).action.equals("left")&&h.ui.buttons.get(1).action.equals("right"),"Choice template cloning retains independently selectable labels");
        for(int n=0;n<2;n++){
            Dc2Ui.Button b=h.ui.buttons.get(n);String caption=n==0?"Left":"Right";
            check(b.sprites.get(0).state==99&&b.sprites.get(1).state==1&&b.sprites.get(2).state==2,"Normal caption is visible without changing hover and press states");
            for(Dc2Ui.Quad q:b.sprites)check(q.text.equals(caption)&&q.flags==135&&q.width==caption.length()*14+1,"Choice substitution preserves native palette and caption extent");
        }
        check(captured(h,-1,-1).equals(Arrays.asList("Left","Right")),"Idle capture contains both captions without a touch or hover");
        check(captured(h,0,-1).equals(Arrays.asList("Left","Left","Right")),"Hover keeps both captions and adds only the selected hover sprite");
        check(captured(h,-1,1).equals(Arrays.asList("Left","Right","Right")),"Press keeps both captions and adds only the selected press sprite");
        h.ui.hovered=h.ui.pressed=-1;byte[] save=vm.snapshot();
        for(int n=0;n<2;n++){
            PcHost copy=host("dc2pc-5769");MesVm selected=new MesVm(s->code,copy,"choice");selected.restore(save);
            check(selected.activateMenu(copy.waitTicket(),n)&&selected.offset()==code.label((n==0?"left":"right").getBytes(Binary.SJIS)),"Visible choice still jumps to its original label");
            check(selected.run(10)==MesVm.Status.HALTED,"Selected choice executes through the synthetic stop");
        }
        makeOld(h.ui.buttons);for(List<Dc2Ui.Button> template:h.ui.buttonTemplates)makeOld(template);Arrays.fill(h.ui.palette,0,13,0xffffffff);h.ui.palette[14]=0xff004da5;byte[] old=vm.snapshot();
        PcHost restored=host("dc2pc-5769");MesVm reloaded=new MesVm(s->code,restored,"choice");reloaded.restore(old);
        check(captured(restored,-1,-1).equals(Arrays.asList("Left","Right")),"Old phone snapshot at a choice regains idle captions immediately");
        check(restored.ui.palette[0]==0xff000000&&restored.ui.palette[9]==0xffccccff&&restored.ui.palette[14]==0xff004da5,"Old all-white default palette migrates without changing original START colors");
        h.ui.palette[0]=0xff123456;byte[] custom=vm.snapshot();PcHost customized=host("dc2pc-5769");new MesVm(s->code,customized,"choice").restore(custom);check(customized.ui.palette[0]==0xff123456&&customized.ui.palette[9]==0xffffffff,"Custom saved colors are preserved instead of guessed as defaults");
        restored.command("pc.choice.begin",new int[]{1,0},new byte[0]);restored.addChoice("left".getBytes(Binary.SJIS),"Next".getBytes(Binary.SJIS));
        check(captured(restored,-1,-1).equals(Collections.singletonList("Next")),"Old saved START templates also work for the next choice");
        PcHost one=host("dcpc-2063");one.ui.button(new int[]{0,0,20,20},new byte[]{'x'});one.command("ui.button_text",new int[]{1,2,135,99},new byte[]{'A'});
        check(one.ui.last().sprites.get(0).state==99,"PC1 already uses normal state99 and remains visible");
        check(one.ui.palette[0]==0xff000000&&one.ui.palette[7]==0xffffffff&&one.ui.palette[9]==0xffccccff,"PC1 independently reviewed native palette matches the common defaults");
        Arrays.fill(one.ui.palette,0,13,0xffffffff);one.ui.palette[13]=0xff246810;ByteArrayOutputStream oldOne=new ByteArrayOutputStream();one.save(new DataOutputStream(oldOne));PcHost restoredOne=host("dcpc-2063");restoredOne.load(new DataInputStream(new ByteArrayInputStream(oldOne.toByteArray())));
        check(restoredOne.ui.palette[0]==0xff000000&&restoredOne.ui.palette[13]==0xff246810&&restoredOne.ui.last().sprites.get(0).state==99,"PC1 old default palette migration keeps its normal caption and script-defined colors");
        h.command("dm.ui.text",new int[]{1,2,23,9},new byte[]{'B'});check(h.ui.quads.get(h.ui.quads.size()-1).height==9,"Static text palette9 is not mistaken for a button state");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC choice assertions");}
}
