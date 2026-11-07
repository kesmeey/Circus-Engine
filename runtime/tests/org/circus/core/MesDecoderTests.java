package org.circus.core;

import java.io.*;
import java.util.*;

/** Native decoder counterexamples: signed framing, scanner modes, and one extension. */
public final class MesDecoderTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static byte[] words(int opcode,int...args)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(opcode);
        for(int value:args){out.write(value);out.write(value>>8);}return out.toByteArray();
    }
    private static MesScript script(Profile profile,byte[]...records)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[4]);
        out.write(profile.version);if(profile.versionBytes>1)out.write(profile.version>>8);
        if(profile.versionBytes>2)out.write(0);
        for(byte[] record:records)out.write(record);
        return new MesScript("decoder",out.toByteArray(),profile);
    }
    private static final class Host implements MesVm.Host {
        public int read(int bank,int index)throws IOException{throw new IOException("Unexpected special read");}
        public void write(int bank,int index,int value)throws IOException{throw new IOException("Unexpected special write");}
        public boolean command(String semantic,int[] args,byte[] bytes){return semantic.equals("native.no_effect");}
        public boolean allowNamedJump(){return true;}
        public boolean beforeTransfer(int mode,String name){return false;}
        public void save(DataOutput out){}
        public void load(DataInput in){}
    }
    public static int run()throws Exception {
        count=0;
        for(String id:new String[]{"dcpc-2063","dc2pc-5769","dc2dm-9d72","dc2-3363","dc3pp-9872"}){
            Profile profile=Profiles.get(id);
            for(int opcode:new int[]{0x80,0x81,0xff}){
                MesScript data=script(profile,new byte[]{(byte)opcode,0x71,0x7f},new byte[3]);
                MesScript.Record first=data.at(data.firstOffset);
                check(first.end-first.offset==3&&first.args.length==2&&first.args[0]==0x71&&first.args[1]==0x7f,
                      id+" sign-extended fetch keeps unsigned operands and raw opcode");
                MesVm vm=new MesVm(name->data,new Host(),"decoder");
                check(vm.run(10)==MesVm.Status.HALTED&&vm.offset()==first.end,
                      id+" unsigned table default continues to STOP, whose PC does not advance");
            }
        }
        Profile dd=Profiles.get("dc3dd-a5a8");
        MesScript unsigned=script(dd,words(0x80,1,2,3,4),new byte[3]);
        check(unsigned.at(unsigned.firstOffset).end-unsigned.firstOffset==9,"DD MOVZX keeps high-bit opcodes as word records");
        MesScript extended=script(dd,words(0x7c,3,4,-5,0),words(0x71,10,11,12,13),words(0x71,20,21,22,23),new byte[3]);
        MesScript.Instruction fetch=extended.instruction(extended.firstOffset);
        check(Arrays.equals(fetch.args,new int[]{3,4,-5,0,10,11,12,13}),"One extension appends four signed words");
        check(fetch.next==extended.firstOffset+18&&extended.at(fetch.next).opcode==0x71,"A second extension remains the next physical record");
        check(extended.at(extended.firstOffset).args.length==4,"Logical fetch cannot mutate the physical record");
        MesScript afterStop=script(dd,new byte[3],words(0x71,1,2,3,4));
        check(afterStop.instruction(afterStop.firstOffset).args.length==2,"STOP never absorbs a following extension");
        MesScript unknown=script(dd,words(0xff,0,0,0,0),new byte[3]);
        MesVm unknownVm=new MesVm(name->unknown,new Host(),"decoder");
        check(unknownVm.run(10)==MesVm.Status.FAULT,"Unsigned unreviewed opcodes must still fault");
        Profile dm=Profiles.get("dc2dm-9d72");
        MesScript named=script(dm,new byte[]{(byte)0xff,1,2},new byte[]{0x34,'x',0},new byte[3]);
        check(named.requiredLabel(new byte[]{'x'})==named.firstOffset+3,"Native named scan shares physical sizing for signed default records");
        MesScript block=script(dd,words(0x80,0,0,0,0),new byte[]{0x1f,1,42},new byte[3]);
        check(block.scanBlock(block.firstOffset,42)==block.firstOffset+12,"DD block scanner uses its own unsigned opcode rule");
        Profile dc2=Profiles.get("dc2-3363");
        MesScript fragment=script(dc2,new byte[2]);
        MesScript.Record stop=fragment.at(fragment.firstOffset);
        check(stop.terminalFragment&&stop.args.length==1&&stop.end-stop.offset==2,"DC2 short STOP preserves raw size and absent operand");
        MesVm fragmentVm=new MesVm(name->fragment,new Host(),"decoder");
        check(fragmentVm.run(10)==MesVm.Status.HALTED&&fragmentVm.offset()==fragment.firstOffset,"A reachable terminal fragment halts at its original PC");
        MesVm restored=new MesVm(name->fragment,new Host(),"decoder");restored.restore(fragmentVm.snapshot());
        check(restored.status()==MesVm.Status.HALTED&&restored.offset()==fragment.firstOffset,"Terminal PC remains a real boundary after snapshot restore");
        for(int marker:new int[]{0x1a63,0x3163}){
            byte[] data={0,0,0,0,(byte)marker,(byte)(marker>>8),0,0,0};
            MesScript alternative=new MesScript("alternate",data,dc2);
            check(alternative.version==marker,"Structural parsing preserves the actual alternative stamp");
            boolean rejected=false;
            try{new MesVm(name->alternative,new Host(),"alternate");}catch(IOException expected){
                rejected=expected.getMessage().contains("Native loader rejects MES marker");
            }
            check(rejected,"DC2 native loader must reject structurally parseable non-3363 stamps");
        }
        Profile pc2=Profiles.get("dc2pc-5769");
        MesScript noLookahead=script(pc2,new byte[]{(byte)0xbf,1,2},words(0x52,10,11,12,13),new byte[3]);
        MesScript.Instruction oldFetch=noLookahead.instruction(noLookahead.firstOffset);
        check(oldFetch.args.length==2&&oldFetch.next==noLookahead.firstOffset+3,"PC2 negative default must not absorb a following extension (sce_other counterexample)");
        for(String id:new String[]{"dcpc-2063","dc2pc-5769","dc2-3363"}){
            Profile profile=Profiles.get(id);int opcode=id.equals("dc2-3363")?0x52:0x53;
            MesScript handler=script(profile,words(opcode,1,2,3,4),words(0,5,6,7,8),new byte[3]);
            MesScript.Instruction operation=handler.instruction(handler.firstOffset);
            check(Arrays.equals(operation.args,new int[]{1,2,3,4,5,6,7,8}),id+" handler reads raw words without checking payload opcode");
            MesScript.Record payload=handler.at(handler.firstOffset+9);
            check(payload.opcode==0&&payload.payloadOwner==handler.firstOffset&&payload.args.length==4,id+" ignored payload byte is not dispatched as STOP");
            check(operation.next==handler.firstOffset+18&&handler.boundary(operation.next),id+" handler consumes exactly one nine-byte payload");
        }
        Profile pc1=Profiles.get("dcpc-2063");
        MesScript sumTerms=script(pc1,words(pc1.opcode("variable.sum_list"),0,0,0,0),
            words(0x53,0,1,0,0),words(0,0,2,0,0),new byte[]{2,0,0},new byte[3]);
        MesVm summed=new MesVm(name->sumTerms,new Host(),"decoder");
        summed.variables.write(0,1,20);summed.variables.write(0,2,22);
        check(summed.run(20)==MesVm.Status.HALTED&&summed.variables.read(0,0)==42,"Sum rows with quad/STOP first bytes remain data, not handler owners or dispatched commands");
        return count;
    }
}
