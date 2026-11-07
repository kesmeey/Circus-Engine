package org.circus.core;

import java.io.*;
import java.util.*;

/** Story memory cannot be migrated merely because the old wire representation fits. */
public final class SnapshotContractTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void reject(Work work,String message)throws Exception {
        count++;try{work.run();}catch(IOException expected){if(expected.getMessage().contains(message))return;throw expected;}
        throw new AssertionError("Expected snapshot rejection: "+message);
    }
    private static final class Host implements MesVm.Host {
        public int read(int bank,int index){return 0;}
        public void write(int bank,int index,int value){}
        public boolean command(String semantic,int[] args,byte[] bytes){return false;}
        public boolean beforeTransfer(int mode,String name){return true;}
        public boolean allowNamedJump(){return true;}
        public void save(DataOutput out){}
        public void load(DataInput in){}
    }
    private static MesVm machine(Profile profile)throws IOException {
        ByteArrayOutputStream raw=new ByteArrayOutputStream();raw.write(new byte[4]);
        raw.write(profile.version);if(profile.versionBytes>1)raw.write(profile.version>>>8);if(profile.versionBytes>2)raw.write(0);raw.write(new byte[3]);
        MesScript script=new MesScript("contract",raw.toByteArray(),profile);
        return new MesVm(name->script,new Host(),"contract");
    }
    private static byte[] rewrite(byte[] save,boolean legacy,String replacement)throws IOException {
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(save,0,save.length-66));input.readInt();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);
        output.writeInt(legacy?0x43565331:0x43565332);
        for(int n=0;n<3;n++)output.writeUTF(input.readUTF());String identity=input.readUTF();
        if(!legacy)output.writeUTF(replacement==null?identity:replacement);
        byte[] remaining=new byte[input.available()];input.readFully(remaining);output.write(remaining);output.flush();
        output.writeUTF(Binary.sha256(bytes.toByteArray()));output.flush();return bytes.toByteArray();
    }
    public static int run()throws Exception {
        count=0;
        for(String id:new String[]{"dcpc-2063","dc2pc-5769","dc2-3363","dc2dm-9d72","dc3pp-9872","dc3wy-a09f","dc3dd-a5a8"}){
            Profile profile=Profiles.get(id);MesVm vm=machine(profile);vm.variables.write(0,0,42);byte[] bytes=vm.snapshot();
            check(new DataInputStream(new ByteArrayInputStream(bytes)).readInt()==0x43565332,"New native saves carry CVS2: "+id);
            MesVm restored=machine(profile);restored.restore(bytes);check(Arrays.equals(bytes,restored.snapshot()),"Same execution contract round trips exactly: "+id);
            byte[] legacy=rewrite(bytes,true,null);reject(()->machine(profile).restore(legacy),"Legacy story save");
            String changed=profile.executionIdentity().charAt(0)=='0'?"1":"0";changed+=profile.executionIdentity().substring(1);
            byte[] other=rewrite(bytes,false,changed);reject(()->machine(profile).restore(other),"execution contract differs");
        }
        Profile demo=Profiles.get("circus-demo-v1");byte[] oldDemo=rewrite(machine(demo).snapshot(),true,null);
        machine(demo).restore(oldDemo);check(true,"Known self-authored legacy demo remains readable");
        Profile nativePp=Profiles.get("dc3pp-9872");
        Map<Integer,String> operations=new HashMap<>();
        for(int opcode=0;opcode<128;opcode++)if(!nativePp.operation(opcode).equals("unknown"))operations.put(opcode,nativePp.operation(opcode));
        Profile oldModel=new Profile(nativePp.id,nativePp.version,6,3,nativePp.string8Start,nativePp.stringStart,
            nativePp.encodedStart,nativePp.wordsStart,nativePp.extension,nativePp.label,nativePp.block,nativePp.companion,
            nativePp.signedBlockScan,nativePp.dollarToSpace,nativePp.textVerified,false,Variables.Model.BYTE_WRAP,operations,
            null,nativePp.signedFetchOpcode,nativePp.allowTerminalFragment,nativePp.runtimeExactVersion,
            nativePp.extensionAtFetch,new int[0]);
        MesVm incompatible=machine(oldModel);incompatible.variables.write(0,0,-1);
        check(incompatible.variables.read(0,0)==255,"Old byte-wrap state records a value the native saturated write would not produce");
        byte[] state=incompatible.snapshot();reject(()->machine(nativePp).restore(state),"execution contract differs");
        return count;
    }
}
