package org.circus.core;

import java.io.*;
import java.util.*;

/** Native:q always copies live8004 bytes, without the ordinary menu flag gate. */
public final class Pc1QuickSaveTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Work{void run()throws IOException;}
    private static void reject(Work work,String text)throws IOException{boolean failed=false;try{work.run();}catch(IOException expected){failed=expected.getMessage().contains(text);}check(failed,"Reject "+text);}
    private static byte[] script(boolean prepared)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{1,0,0,0,14,0,0,0,0x63,0x20});
        b.write(new byte[]{0x58,0,0,0,0,11,0,0,0});if(prepared)b.write(new byte[]{0x1b,1,0,0x3b,'A',0});
        b.write(new byte[]{3,0,0,0x4c,35,33,44,44,37,50,0,7,0,0});b.write(new byte[]{0x58,0,0,0,0,42,0,0,0});b.write(new byte[3]);return b.toByteArray();
    }
    private static final class Fixture {
        final CircusResources data;
        Fixture(boolean prepared)throws IOException{
            final Map<String,byte[]> entries=new HashMap<>();entries.put("AdvData/DAT/NAMES.DAT",new byte[20]);entries.put("AdvData/MES/START.mes",script(prepared));entries.put("AdvData/MES/menu.mes",new byte[]{0,0,0,0,0x63,0x20,0,0,0});
            GameResources files=new GameResources(new GameResources.Storage(){public Set<String> paths(){return entries.keySet();}public byte[] read(String p,int limit)throws IOException{byte[] bytes=entries.get(p);if(bytes==null||bytes.length>limit)throw new IOException("Fixture read");return bytes.clone();}public Archive.Source source(String p)throws IOException{final byte[] bytes=read(p,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+n);}};}});
            data=new CircusResources(files,Profiles.get("dcpc-2063"));
        }
        Dc2Session session()throws IOException{return new Dc2Session(data,TextCodec.identity(),h->{Pc1TestDefaults.bind(h);PcHost p=(PcHost)h;p.nativeFrameCapture=Pc1SystemContextTests::framebuffer;p.scene.slots[1]="scene-A";p.scene.publish(p.ui,0,0);});}
    }
    private static PcHost host(Dc2Session s){return(PcHost)s.host;}
    private static void wait(Dc2Session s)throws IOException{check(s.vm.run(100)==MesVm.Status.WAIT_HOST&&s.host.presentationWaiting,"fixture presents its original body");s.vm.signalHost(s.host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);check(s.vm.run(1)==MesVm.Status.WAIT_HOST&&s.host.textWaiting&&host(s).pc1.waitKind()==7,"fixture reaches native07 wait");}
    public static int run()throws Exception{
        checks=0;
        for(boolean prepared:new boolean[]{false,true}){
            Fixture f=new Fixture(prepared);Dc2Session s=f.session();final int[] samples={0},voiceSample={-1};s.pc1CandidateCapture=()->{samples[0]++;voiceSample[0]=s.host.audioGeneration[5];return new byte[]{(byte)s.vm.variables.read(0,0)};};wait(s);
            int beforeSamples=samples[0];PcHost h=host(s);s.vm.variables.write(0,0,91);h.scene.slots[1]="scene-B";h.scene.publish(h.ui,0,0);h.audio[0]="music-B";h.audioActive[0]=true;h.audio[5]="voice-live";h.audioActive[5]=true;h.audioGeneration[5]=27;
            long ticket=h.waitTicket();int pc=s.vm.offset(),flag=h.pc1.saves().flag();byte[] live=h.pc1.nativeFrame().bytes();h.pc1.requestQuickSave(pc);
            check(h.saveRequest==1&&h.pc1.requestedSlot()==1&&h.pc1.requestedSave()!=null,"quick input produces a real pending slot1 request");
            check(Arrays.equals(live,h.pc1.saves().nativeCandidate().bytes()),"quick input copies all8004 live bytes without flag filtering");
            check(h.pc1.saves().flag()==flag&&h.waitTicket()==ticket&&h.pc1.waitKind()==7&&h.textWaiting,"copy does not clearBB8418 or change07/ticket");
            check(h.audioActive[5]&&h.audioGeneration[5]==27&&h.audio[5].equals("voice-live")&&voiceSample[0]==27,"native:q does not stop voice before or after its capture");
            check(samples[0]==beforeSamples+1&&s.pc1CandidateMetadata()[0]==91,"q captures current state91 even when1B prepared earlier11");
            s.requirePc1SaveCandidate();byte[] checkpoint=s.pc1CandidateSnapshot();Dc2Session loaded=f.session();loaded.restore(checkpoint);
            check(loaded.vm.offset()==pc&&loaded.vm.variables.read(0,0)==91&&loaded.host.scene.slots[1].equals("scene-B")&&loaded.host.audio[0].equals("music-B"),"slot resumes the quick caller's actualPC/ordinary/scene/audio");
            check(loaded.host.saveRequest==-1&&loaded.host.textWaiting&&host(loaded).pc1.saves().flag()==flag,"load has no pending-save replay and retains story wait/flag");
            Dc2Session active=f.session();active.restore(s.snapshot());check(Arrays.equals(checkpoint,active.pc1CandidateSnapshot()),"F5/session preserves quick leaf and pending selection independently");
            reject(()->h.pc1.requestQuickSave(pc),"stable native story");check(h.saveRequest==1&&Arrays.equals(checkpoint,s.pc1CandidateSnapshot()),"repeated input cannot replace an outstanding write selection");
            h.completeSave();s.vm.elapse(0);check(h.textWaiting&&h.waitTicket()==ticket&&s.vm.status()==MesVm.Status.WAIT_HOST,"successful platform write does not advance dialogue");
            s.vm.variables.write(0,0,72);h.pc1.requestQuickSave(pc);loaded=f.session();loaded.restore(s.pc1CandidateSnapshot());check(loaded.vm.variables.read(0,0)==72,"a later quick input replaces the previous quick leaf");
        }
        Fixture f=new Fixture(false);Dc2Session s=f.session();PcHost h=host(s);reject(()->h.pc1.requestQuickSave(s.vm.offset()),"stable native story");wait(s);
        byte[] before=s.snapshot();reject(()->h.pc1.requestQuickSave(s.vm.offset()+1),"caller PC is stale");check(Arrays.equals(before,s.snapshot()),"stale caller rejection has no native/portable side effect");
        s.openSystem("menu",0);check(s.inSystem(),"fixture enters an actual typed menu");before=s.snapshot();final int systemPc=s.vm.offset();reject(()->host(s).pc1.requestQuickSave(systemPc),"stable native story");check(Arrays.equals(before,s.snapshot()),"quick input cannot capture the active system menu");
        Dc2Session choice=f.session();wait(choice);host(choice).pc1.waitKind(9);choice.host.textWaiting=false;choice.host.ui.waiting=true;int choicePc=choice.vm.offset();host(choice).pc1.requestQuickSave(choicePc);Dc2Session choiceLoaded=f.session();choiceLoaded.restore(choice.pc1CandidateSnapshot());check(host(choiceLoaded).pc1.waitKind()==9&&choiceLoaded.host.ui.waiting,"native09 quick input retains its choice wait");
        Dc2Session failure=f.session();wait(failure);failure.pc1CandidateCapture=()->{throw new IOException("injected metadata failure");};reject(()->host(failure).pc1.requestQuickSave(failure.vm.offset()),"injected metadata failure");check(failure.host.saveRequest<0&&host(failure).pc1.requestedSave()==null,"failed platform capture does not invent a pending file write");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Pc1QuickSaveTests "+run()+" PASS");}
}
