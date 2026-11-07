package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent PC1 portable candidate and session persistence. All MES/data
 * are self-authored; no original Windows slot or original process is used. */
public final class Pc1PortableCandidateTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Work {void run()throws Exception;}
    private static void reject(Work work,String detail)throws Exception{boolean failed=false;try{work.run();}catch(IOException e){failed=e.getMessage()!=null&&e.getMessage().contains(detail);}check(failed,"Reject "+detail);}
    private static byte[] script(byte[]...records)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,0x63,0x20});for(byte[] r:records)b.write(r);b.write(new byte[3]);return b.toByteArray();}
    private static byte[] set(int value){return new byte[]{0x58,0,0,0,0,(byte)value,0,0,0};}
    private static byte[] prepare(int flag){return new byte[]{0x1b,(byte)flag,0,0x3b,'l','e','a','f',0};}
    private static final byte[] AUTO={0x15,4,1},SELECTED={0x15,4,0};
    private static final class Fixture {
        final Map<String,byte[]> entries=new HashMap<>();final CircusResources data;
        Fixture(byte[] start,byte[] menu)throws IOException{
            entries.put("AdvData/DAT/NAMES.DAT",new byte[20]);entries.put("AdvData/MES/START.mes",start);entries.put("AdvData/MES/menu.mes",menu);
            GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return entries.keySet();}
                public byte[] read(String path,int limit)throws IOException{byte[] bytes=entries.get(path);if(bytes==null||bytes.length>limit)throw new IOException("Fixture resource");return bytes.clone();}
                public Archive.Source source(String path)throws IOException{final byte[] bytes=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+length);}};}
            });data=new CircusResources(files,Profiles.get("dcpc-2063"));
        }
        Dc2Session session()throws IOException{return new Dc2Session(data,TextCodec.identity(),h->{Pc1TestDefaults.bind(h);PcHost pc=(PcHost)h;pc.nativeFrameCapture=Pc1SystemContextTests::framebuffer;pc.scene.slots[1]="scene-A";pc.scene.publish(pc.ui,0,0);pc.audio[0]="music-A";pc.audioActive[0]=true;});}
    }
    private static PcHost host(Dc2Session s){return(PcHost)s.host;}
    private static void pending(Dc2Session s)throws IOException{check(s.vm.run(100)==MesVm.Status.WAIT_HOST&&s.host.saveRequest>=0,"real persist4 enters a pending platform save");s.requirePc1SaveCandidate();}
    private static byte[] reseal(byte[] source)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(source,0,source.length-66);DataOutputStream out=new DataOutputStream(b);out.writeUTF(Binary.sha256(b.toByteArray()));return b.toByteArray();}
    public static int run()throws Exception{
        checks=0;Fixture f=new Fixture(script(set(31),prepare(1),set(91),AUTO),script());Dc2Session s=f.session();int[] captures={0};s.pc1CandidateCapture=()->{captures[0]++;return new byte[]{(byte)s.vm.variables.read(0,0)};};
        check(s.vm.run(2)==MesVm.Status.RUNNING&&s.vm.offset()==24,"1B consumes its raw companion before the next fetch");
        s.host.scene.slots[1]="scene-B";s.host.audio[0]="music-B";pending(s);
        check(captures[0]==1&&s.vm.variables.read(0,0)==91&&s.pc1CandidateMetadata()[0]==31,"producer callback samples A once before subsequent state B");
        byte[] selected=s.pc1CandidateSnapshot();Dc2Session loaded=f.session();loaded.restore(selected);
        check(loaded.vm.offset()==24&&loaded.vm.variables.read(0,0)==31&&!loaded.inSystem(),"selected leaf resumes after1B, not after the save request or in a menu");
        check(loaded.host.scene.slots[1].equals("scene-A")&&loaded.host.audio[0].equals("music-A")&&loaded.host.saveRequest==-1,"candidate includes full scene/audio and no pending save replay");
        byte[] state=s.snapshot();Dc2Session copy=f.session();copy.restore(state);check(copy.vm.variables.read(0,0)==91&&copy.host.saveRequest==0,"active session state B remains independent of its older A candidate");
        check(Arrays.equals(selected,copy.pc1CandidateSnapshot())&&copy.pc1CandidateMetadata()[0]==31,"candidate leaf and opaque platform metadata survive session save/load");
        check(state.length<2*1024*1024&&selected.length<2*1024*1024,"session has two bounded leaves without a recursive snapshot chain");
        Dc2Session legacy=f.session();legacy.restore(s.vm.snapshot());reject(legacy::requirePc1SaveCandidate,"unavailable");
        byte[] damaged=state.clone();damaged[damaged.length/2]^=1;MesVm oldVm=copy.vm;Dc2Host oldHost=copy.host;reject(()->copy.restore(damaged),"checksum");check(copy.vm==oldVm&&copy.host==oldHost&&copy.vm.variables.read(0,0)==91,"checksum failure never publishes a replacement pair");
        byte[] forged=state.clone();DataInputStream in=new DataInputStream(new ByteArrayInputStream(forged));in.readInt();in.readInt();int active=in.readInt();in.skipBytes(active);in.readBoolean();in.readByte();in.readUTF();in.readUTF();in.readInt();in.readInt();int fingerprintStart=forged.length-in.available()+2;forged[fingerprintStart]=(byte)(forged[fingerprintStart]=='0'?'1':'0');final byte[] invalid=reseal(forged);reject(()->copy.restore(invalid),"provenance");check(copy.vm==oldVm&&copy.host==oldHost,"candidate provenance failure is atomic too");
        copy.vm.variables.write(9,2,6);copy.vm.variables.write(9,20,9);byte[] stamped=copy.pc1CandidateSnapshot(new Pc1Frame.SaveMetadata(2026,11,7,12,34));Dc2Session dated=f.session();dated.restore(stamped);
        check(dated.vm.variables.read(0,0)==31&&dated.host.scene.slots[1].equals("scene-A"),"native metadata preparation cannot replace producer story state");
        check(dated.vm.variables.read(9,2)==6&&dated.vm.variables.read(9,20)==9&&dated.vm.variables.read(9,21)==11&&dated.vm.variables.read(9,22)==7&&dated.vm.variables.read(9,23)==12&&dated.vm.variables.read(9,24)==34&&dated.vm.variables.read(9,25)==26,"native save metadata patches exactly the established7 ordinary bytes");
        check((host(copy).pc1.saves().candidate().ordinaryBytes()[902]&255)==6&&(host(copy).pc1.saves().candidate().ordinaryBytes()[920]&255)==9,"live native candidate metadata changes before platform I/O");
        check(captures[0]==1&&copy.pc1CandidateMetadata()[0]==31,"metadata preparation does not invent another producer or platform sample");
        Fixture current=new Fixture(script(set(47),AUTO),script());Dc2Session cur=current.session();pending(cur);Dc2Session curLoaded=current.session();curLoaded.restore(cur.pc1CandidateSnapshot());
        check(curLoaded.vm.variables.read(0,0)==47&&curLoaded.vm.offset()==18&&curLoaded.host.saveRequest<0&&host(curLoaded).pc1.requestedSave()==null,"flag0 auto captures current continuation and clears pending state only in detached leaf");
        check(cur.host.saveRequest==0&&host(cur).pc1.requestedSave()!=null,"preparing a portable slot does not consume the live request before write success");
        cur.host.completeSave();cur.vm.elapse(0);check(cur.vm.run(1)==MesVm.Status.HALTED,"completed live autosave proceeds once without replaying persist4");
        Fixture menu=new Fixture(script(set(31),prepare(1),set(91),new byte[]{0x26,0,0,0,'m','e','n','u',0}),script(SELECTED));Dc2Session called=menu.session();called.vm.variables.write(9,20,5);called.host.write(99,254,5);pending(called);
        check(called.inSystem()&&called.vm.scriptName().equals("menu")&&host(called).pc1.saves().flag()==1,"raw26 borrows menu without refreshing protected candidate");Dc2Session menuLoaded=menu.session();menuLoaded.restore(called.pc1CandidateSnapshot());
        check(menuLoaded.vm.scriptName().equals("START")&&menuLoaded.vm.variables.read(0,0)==31&&!menuLoaded.inSystem(),"original selected-slot request saves the earlier story candidate, not SAVE VM");
        // A real07 wait gives the external input path its stable caller PC.
        byte[] message={3,0,0,0x4c,35,33,44,44,37,50,0,7,0,0};
        for(int eventKind=0;eventKind<3;eventKind++){
            ByteArrayOutputStream eventBytes=new ByteArrayOutputStream();eventBytes.write(new byte[]{1,0,0,0,14,0,0,0,0x63,0x20});eventBytes.write(set(11));eventBytes.write(message);eventBytes.write(set(42));eventBytes.write(new byte[3]);Fixture event=new Fixture(eventBytes.toByteArray(),script(SELECTED));Dc2Session e=event.session();check(e.vm.run(10)==MesVm.Status.WAIT_HOST&&e.host.presentationWaiting,"event fixture first presents its body");e.vm.signalHost(e.host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);check(e.vm.run(1)==MesVm.Status.WAIT_HOST&&e.host.textWaiting,"event fixture reaches a genuine native message wait: "+e.vm.error());
            e.host.write(99,254,2);e.host.audio[5]="voice-live";e.host.audioActive[5]=true;e.host.audioGeneration[5]=20;final int[] sampledGeneration={-1};e.pc1CandidateCapture=()->{sampledGeneration[0]=e.host.audioGeneration[5];return new byte[]{(byte)eventKindValue(e)};};
            e.openSystem("menu",eventKind);pending(e);check(sampledGeneration[0]==(eventKind==0?20:21),"event capture preserves native voice-stop order kind"+eventKind);Dc2Session er=event.session();er.restore(e.pc1CandidateSnapshot());
            check(!er.inSystem()&&er.vm.scriptName().equals("START")&&er.vm.variables.read(0,0)==11&&er.host.textWaiting,"flag0 event stores caller wait before system entry kind"+eventKind);
        }
        ByteArrayOutputStream heldBytes=new ByteArrayOutputStream();heldBytes.write(new byte[]{1,0,0,0,14,0,0,0,0x63,0x20});heldBytes.write(set(31));heldBytes.write(prepare(1));heldBytes.write(message);heldBytes.write(new byte[3]);Fixture heldFixture=new Fixture(heldBytes.toByteArray(),script(SELECTED));Dc2Session held=heldFixture.session();int[] heldCaptures={0};held.pc1CandidateCapture=()->{heldCaptures[0]++;return new byte[]{31};};
        check(held.vm.run(2)==MesVm.Status.RUNNING,"protected event fixture captures1B before the later message");held.vm.variables.write(0,0,97);held.vm.run(10);held.vm.signalHost(held.host.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);held.vm.run(1);held.host.write(99,254,2);held.openSystem("menu",0);pending(held);
        Dc2Session heldLoaded=heldFixture.session();heldLoaded.restore(held.pc1CandidateSnapshot());check(heldCaptures[0]==1&&heldLoaded.vm.variables.read(0,0)==31&&!heldLoaded.inSystem(),"flag1 native event cannot replace a prepared portable candidate with its later97 state");
        host(held).pc1.saves().scriptLoaded(1);byte[] transferred=held.snapshot();Dc2Session transferredLoaded=heldFixture.session();transferredLoaded.restore(transferred);check(Arrays.equals(held.pc1CandidateSnapshot(),transferredLoaded.pc1CandidateSnapshot()),"normal-loader flag clear preserves independent candidate bytes and portable leaf");
        Fixture failure=new Fixture(script(prepare(1),AUTO),script());Dc2Session fail=failure.session();fail.pc1CandidateCapture=()->{throw new IOException("injected platform capture");};check(fail.vm.run(5)==MesVm.Status.FAULT&&fail.vm.error().contains("injected platform capture"),"platform producer capture failure stops before next fetch");
        byte[] changed=f.entries.get("AdvData/MES/START.mes").clone();changed[11]=32;f.entries.put("AdvData/MES/START.mes",changed);reject(()->copy.restore(state),"Script changed");check(copy.vm==oldVm&&copy.host==oldHost,"source change rejection preserves the published pair");
        return checks;
    }
    private static int eventKindValue(Dc2Session s)throws IOException{return s.vm.variables.read(0,0);}
    public static void main(String[] args)throws Exception{System.out.println("Pc1PortableCandidateTests "+run()+" PASS");}
}
