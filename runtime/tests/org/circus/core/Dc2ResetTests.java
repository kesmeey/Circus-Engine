package org.circus.core;

import java.io.*;
import java.util.*;

/** Native 0F -> 4149D5 clears 0xFA dwords at A0996C, without inspecting either operand. */
public final class Dc2ResetTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Fixture {
        final PcHost host;final MesVm vm;final MesScript script;
        Fixture(int first,int second)throws IOException{
            final byte[] names=new byte[20];GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
                public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
                public Archive.Source source(String path){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}};}
            });
            Profile profile=Profiles.get("dc2-3363");host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,profile),TextCodec.identity()));
            script=new MesScript("native-reset",new byte[]{0,0,0,0,0x63,0x33,15,(byte)first,(byte)second,0,0,0},profile);
            vm=new MesVm(name->script,host,script.name);host.position(script,6);
        }
    }
    private static byte[] hostState(PcHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.save(new DataOutputStream(bytes));return bytes.toByteArray();}
    private static void clear(Fixture f)throws IOException{check(f.vm.run(1)==MesVm.Status.RUNNING&&f.vm.offset()==9,"native 0F completes exactly one record without halting or yielding");}
    public static int run()throws Exception{
        count=0;Fixture full=new Fixture(0,0);
        for(int bank=0;bank<10;bank++)for(int index=0;index<100;index++)full.vm.variables.write(bank,index,1+(bank*100+index)%255);
        full.host.write(100,4,81);full.host.write(97,17,1);full.host.audio[5]="VOICE\\kept";full.host.audioActive[5]=true;full.host.audioGeneration[5]=17;
        full.host.scene.slots[21]="SYSTEM\\kept";Dc2Ui.Button button=new Dc2Ui.Button();button.action="kept-ui-label";button.binding=123;full.host.ui.buttons.add(button);
        byte[] beforeHost=hostState(full.host);clear(full);
        for(int bank=0;bank<10;bank++)for(int index=0;index<100;index++)check(full.vm.variables.read(bank,index)==0,"all ten native ordinary byte banks are cleared");
        check(full.host.read(100,4)==81&&full.host.read(97,17)==1,"native reset leaves persistent globals and CG flags intact");
        check(Arrays.equals(beforeHost,hostState(full.host)),"native reset leaves actual host audio, UI, scene and registers unchanged");
        // Every byte value occurs in each operand position. The original handler reads neither.
        for(int value=0;value<256;value++){
            Fixture f=new Fixture(value,255-value);f.vm.variables.write(0,0,41);f.vm.variables.write(9,99,77);clear(f);
            check(f.vm.variables.read(0,0)==0&&f.vm.variables.read(9,99)==0,"both ignored reset operands admit all byte values");
        }
        Fixture history=new Fixture(255,255);history.vm.variables.write(0,1,11);history.vm.variables.write(3,17,99);history.vm.variables.write(9,99,255);
        history.vm.variables.appendHistory();check(history.vm.variables.historyCount()==3,"native history fixture records three initial changes");
        clear(history);check(history.vm.variables.historyCount()==3,"reset does not clear the existing history log");
        history.vm.variables.appendHistory();check(history.vm.variables.historyCount()==6,"old nonzero shadow survives reset, so append records three zero changes");
        ByteArrayOutputStream journal=new ByteArrayOutputStream();history.vm.variables.save(new DataOutputStream(journal));
        history.vm.variables.replayHistory(3);check(history.vm.variables.read(0,1)==11&&history.vm.variables.read(3,17)==99&&history.vm.variables.read(9,99)==255,"old history prefix still restores pre-reset byte values");
        Variables zeroPrefix=new Variables(history.host,Variables.Model.PC_BYTE);zeroPrefix.load(new DataInputStream(new ByteArrayInputStream(journal.toByteArray())));zeroPrefix.replayHistory(6);
        check(zeroPrefix.read(0,1)==0&&zeroPrefix.read(3,17)==0&&zeroPrefix.read(9,99)==0,"the appended reset deltas replay zeros rather than a cleared history facade");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 native reset assertions");}
}
