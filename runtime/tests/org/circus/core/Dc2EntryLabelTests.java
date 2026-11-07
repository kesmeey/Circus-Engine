package org.circus.core;

import java.io.*;
import java.util.*;

/** Current DC2 37 -> BE8BF8 -> loader4137D0/required lookup413700. */
public final class Dc2EntryLabelTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static byte[] text(int opcode,String value)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(opcode);b.write(value.getBytes(Binary.SJIS));b.write(0);return b.toByteArray();}
    private static byte[] set(int value){return new byte[]{0x58,0,0,0,0,(byte)value,(byte)(value>>>8),0,0};}
    private static MesScript script(String name,byte[]...records)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,0x63,0x33});for(byte[] r:records)b.write(r);b.write(new byte[3]);return new MesScript(name,b.toByteArray(),Profiles.get("dc2-3363"));}
    private static final class Host implements MesVm.Host {
        int transfers,lastMode;String destination="";
        public int read(int bank,int index){return 0;}
        public void write(int bank,int index,int value){}
        public boolean command(String name,int[] args,byte[] bytes){return false;}
        public boolean allowNamedJump(){return true;}
        public boolean beforeTransfer(int mode,String name){transfers++;lastMode=mode;destination=name;return true;}
        public void save(DataOutput out)throws IOException{out.writeInt(transfers);out.writeInt(lastMode);out.writeUTF(destination);}
        public void load(DataInput in)throws IOException{transfers=in.readInt();lastMode=in.readInt();destination=in.readUTF();}
    }
    private static MesVm vm(Map<String,MesScript> scripts,Host host)throws IOException{return new MesVm(name->{MesScript s=scripts.get(name);if(s==null)throw new FileNotFoundException(name);return s;},host,"a");}
    public static int run()throws Exception {
        checks=0;
        for(int opcode:new int[]{0x35,0x36}){
            Map<String,MesScript> scripts=new HashMap<>();
            scripts.put("a",script("a",text(0x37,"entry"),text(opcode,"b")));
            scripts.put("b",script("b",set(11),text(0x31,"entry"),set(42),text(0x35,"c")));
            scripts.put("c",script("c",set(99))); // No entry label: the one-use pending field must have been cleared.
            Host h=new Host();MesVm execution=vm(scripts,h);
            check(execution.run(1)==MesVm.Status.RUNNING&&h.transfers==0,"37 registers raw entry bytes without immediately loading or jumping");
            byte[] pending=execution.snapshot();Host savedHost=new Host();MesVm saved=vm(scripts,savedHost);saved.restore(pending);
            check(Arrays.equals(pending,saved.snapshot()),"CVS2 preserves a registered label before the next transfer");
            check(saved.run(1)==MesVm.Status.RUNNING&&saved.scriptName().equals("b")&&saved.offset()==scripts.get("b").requiredLabel("entry".getBytes(Binary.SJIS)),"loader applies entry to the new script's raw label opcode PC");
            check(savedHost.lastMode==(opcode==0x36?2:1),"36 is fixed loader mode2 while35 normally uses mode1");
            check(saved.variables.read(0,0)==0&&saved.run(2)==MesVm.Status.RUNNING&&saved.variables.read(0,0)==42,"entry skips instructions before the target and resumes on the label itself");
            check(saved.run(20)==MesVm.Status.HALTED&&saved.scriptName().equals("c")&&saved.variables.read(0,0)==99&&savedHost.transfers==2,"successful loader consumes pending entry once, so the following script needs no matching label");
            Host uninterruptedHost=new Host();MesVm uninterrupted=vm(scripts,uninterruptedHost);uninterrupted.run(30);
            check(Arrays.equals(saved.snapshot(),uninterrupted.snapshot()),"restored entry registration reaches exactly the same native VM/host state");
        }
        Map<String,MesScript> scripts=new HashMap<>();scripts.put("b",script("b",set(11),text(0x31,"entry"),set(42)));
        scripts.put("a",script("a",text(0x37,"missing"),text(0x37,"entry"),text(0x35,"b")));
        MesVm replaced=vm(scripts,new Host());check(replaced.run(30)==MesVm.Status.HALTED&&replaced.variables.read(0,0)==42,"a later37 replaces the pending raw label");
        scripts.put("a",script("a",text(0x37,"entry"),text(0x37,""),text(0x35,"b")));
        MesVm cleared=vm(scripts,new Host());check(cleared.run(3)==MesVm.Status.RUNNING&&cleared.scriptName().equals("b")&&cleared.offset()==scripts.get("b").firstOffset,"empty37 clears a prior registration and uses normal script entry");
        check(cleared.run(1)==MesVm.Status.RUNNING&&cleared.variables.read(0,0)==11,"normal entry executes the code that a registered label would skip");
        for(String missing:new String[]{"missing","Entry"}){
            scripts.put("a",script("a",text(0x37,missing),text(0x35,"b")));Host h=new Host();MesVm failure=vm(scripts,h);
            MesVm.Status status=failure.run(20);
            check(status==MesVm.Status.FAULT&&failure.error().contains("Required entry label missing")&&h.transfers==0,"missing/case-different label is an error before host transfer effects: "+status+" / "+failure.error()+" / "+h.transfers);
        }
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 entry-label assertions");}
}
