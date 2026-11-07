package org.circus.core;

import java.io.*;
import java.util.*;

/** Checkpoint projection, not a resource/audio/native-slot bridge. Reference vectors
 * interpret current CHS41A6CA..759 over private arrays; targetSHA7988971f...872057. */
public final class Pc1SlotProjectionTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action action,String message)throws Exception{boolean bad=false;try{action.run();}catch(IOException expected){bad=expected.getMessage().contains(message);}check(bad,"reject "+message);}
    private static void word(ByteArrayOutputStream out,long value){for(int n=0;n<4;n++)out.write((int)(value>>>(n*8)));}
    private static MesScript script(String name,long[] table,byte[] code)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();word(out,table.length);for(long value:table)word(out,value);out.write(code);return new MesScript(name,out.toByteArray(),Profiles.get("dcpc-2063"));}
    private static byte[] padded(int size,int at,int opcode,int a0,int a1){byte[] code=new byte[size];code[0]=0x63;code[1]=0x20;code[at]=(byte)opcode;if(at+1<size)code[at+1]=(byte)a0;if(at+2<size)code[at+2]=(byte)a1;return code;}
    private static final int[][] NATIVE_REFERENCE={
        // header, final nativePC, wait, candidate flag; original savedPC12345678 remains unchanged.
        {0x1b,0x103,-1,34},{0x1d,0x103,0x1d,0},{7,0x103,7,0},{9,0x100,-1,0},{0x4b,0x100,-1,0}
    };
    public static int run()throws Exception{
        count=0;Pc1SlotProjection.SavedFields saved=new Pc1SlotProjection.SavedFields(0x12345678,100,101,7);
        for(int[] reference:NATIVE_REFERENCE){
            MesScript caller=script("current-native-reference",new long[]{0x100},padded(0x108,0x100,reference[0],34,56));
            check(caller.versionOffset==8&&caller.firstOffset==10&&caller.headerMetadata[0]==0,"PC1 has a nativeDWORD header table, version bytes and separately empty metadata");
            caller.headerMetadata[0]=0xeeee;Pc1SlotProjection projection=Pc1SlotProjection.project(caller,0,saved,99);
            check(projection.checkpointCodePc==0x100&&projection.checkpointFilePc==caller.versionOffset+0x100,"table code PC is relative to versionOffset, not firstOffset");
            check(projection.resumeCodePc==reference[1]&&projection.resumeFilePc==caller.versionOffset+reference[1]&&projection.wait.nativeValue==reference[2],"current native instruction fixture resume/wait for header "+reference[0]);
            check(projection.candidateFlag==reference[3]&&projection.refreshCandidate==(reference[0]==0x1b),"native candidate refresh/flag match isolated instruction fixture");
            check(projection.loadedFields==saved&&saved.rawCodePc==0x12345678&&saved.rawCodePc!=projection.resumeCodePc,"candidate retains the original loaded rawPC, even when it is not the reconstructed executionPC");
            check(projection.logicalMusicDuringReconstruction==99&&projection.loadedFields.logicalMusic==7,"current logical BGM is preserved during reconstruction; saved request remains a later owner");
            check(projection.loadedFields.argument0==100&&projection.loadedFields.argument1==101,"working-frame argument values win over the1D header prewrite operands34/56");
            check(projection.script.equals(caller.name)&&projection.scriptSha256.equals(caller.sha256)&&caller.headerMetadata[0]==0xeeee,"projection binds caller identity and does not mutate either table or metadata");
            if(reference[0]==0x1b||reference[0]==0x1d||reference[0]==7)check(projection.peekOperand0==34&&projection.peekOperand1==56,"recognized native header preserves rawu8 operands separately from restored arguments");
            else check(projection.peekOperand0==-1&&projection.peekOperand1==-1,"other signed headers do not invent operands");
        }
        for(int flag:new int[]{0,1,2,255}){
            MesScript caller=script("flag",new long[]{2},new byte[]{0x63,0x20,0x1b,(byte)flag,88,0x3b,'x',0,0,0,0});
            Pc1SlotProjection p=Pc1SlotProjection.project(caller,0,saved,99);check(p.refreshCandidate&&p.candidateFlag==flag,"1B copies the loaded candidate even when flag0 and preserves exact unsignedflag "+flag);check(p.resumeFilePc==caller.firstOffset+3&&p.headerOpcode==0x1b,"1B resumes at its raw owned string marker without consuming the string again here");
        }
        byte[] code={0x63,0x20,0x1d,10,11,7,12,13,9,14,15,0,0,0};MesScript many=script("multiple-checkpoints",new long[]{8,2,5,2},code);
        int[] expectedNative={8,5,8,5},expectedWait={-1,0x1d,7,0x1d};
        for(int i=0;i<4;i++){Pc1SlotProjection p=Pc1SlotProjection.project(many,i,saved,1);check(p.resumeCodePc==expectedNative[i]&&p.wait.nativeValue==expectedWait[i],"native table entries can be repeated or unsorted; index selects rawDWORD, not nearby static record");}
        MesScript payload=script("raw-byte-checkpoint",new long[]{4},new byte[]{0x63,0x20,0x15,4,7,0,0,0});Pc1SlotProjection raw=Pc1SlotProjection.project(payload,0,saved,99);
        check(!payload.boundary(raw.checkpointFilePc)&&raw.headerOpcode==7&&raw.resumeCodePc==7&&raw.wait==Pc1SlotProjection.Wait.MESSAGE,"native checkpoint peek can enter a byte previously parsed as another record's operand");
        MesScript signed=script("signed-peek",new long[]{5},new byte[]{0x63,0x20,0,0,0,(byte)255,0,0});Pc1SlotProjection negative=Pc1SlotProjection.project(signed,0,new Pc1SlotProjection.SavedFields(-1,Integer.MIN_VALUE,Integer.MAX_VALUE,-1),-1);
        check(negative.headerOpcode==-1&&negative.wait==Pc1SlotProjection.Wait.NONE&&negative.resumeCodePc==5,"MOVSX header peek preserves signed native byte semantics");
        check(negative.loadedFields.rawCodePc==-1&&negative.loadedFields.argument0==Integer.MIN_VALUE&&negative.loadedFields.argument1==Integer.MAX_VALUE,"loaded fields preserve raw32-bit values without coercing them into headeru8 operands");
        MesScript stop=script("one-byte-stop",new long[]{5},new byte[]{0x63,0x20,0,0,0,0});check(Pc1SlotProjection.project(stop,0,saved,99).resumeFilePc==stop.length()-1,"an unskipped raw STOP byte can be projected without demanding an unrelated full-record decode");
        MesScript valid=script("bounds",new long[]{2},new byte[]{0x63,0x20,0,0,0});
        rejects(()->Pc1SlotProjection.project(valid,-1,saved,99),"index outside");rejects(()->Pc1SlotProjection.project(valid,1,saved,99),"index outside");rejects(()->Pc1SlotProjection.project(valid,0,null,99),"missing saved");
        // Full41A620 instruction goldens in native-slot-loader-vectors.json
        // independently cover these two real marker-byte peeks.
        for(int offset=0;offset<2;offset++){valid.headerOffsets[0]=offset;Pc1SlotProjection marker=Pc1SlotProjection.project(valid,0,saved,99);check(marker.checkpointCodePc==offset&&marker.resumeCodePc==offset&&marker.wait==Pc1SlotProjection.Wait.NONE&&marker.headerOpcode==(offset==0?0x63:0x20),"native table can select known marker byte "+offset+" without a fabricated firstOffset lower bound");}
        for(long offset:new long[]{-1,5,0xffffffffL,Long.MAX_VALUE}){valid.headerOffsets[0]=offset;rejects(()->Pc1SlotProjection.project(valid,0,saved,99),"outside the owned");}
        for(int opcode:new int[]{0x1b,0x1d,7}){
            MesScript truncated=script("short-header",new long[]{5},padded(7,5,opcode,34,56));rejects(()->Pc1SlotProjection.project(truncated,0,saved,99),"truncated native");
            MesScript noResume=script("no-following-byte",new long[]{5},padded(8,5,opcode,34,56));rejects(()->Pc1SlotProjection.project(noResume,0,saved,99),"resume PC is outside");
        }
        rejects(()->Pc1SlotProjection.project(null,0,saved,99),"requires the dcpc");
        MesScript other=new MesScript("other",new byte[]{0,0,0,0,0x63,0x33,0,0,0},Profiles.get("dc2-3363"));rejects(()->Pc1SlotProjection.project(other,0,saved,99),"requires the dcpc");
        check(Arrays.equals(code,new byte[]{0x63,0x20,0x1d,10,11,7,12,13,9,14,15,0,0,0}),"pure projection never mutates caller input bytes or applies any runtime owner");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" PC1 slot-projection assertions");}
}
