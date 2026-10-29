package org.circus.core;

import java.io.IOException;
import java.util.Arrays;

/** Imports only reviewed unlock fields from DC2PC's original system.dat.
 *
 * DC2PC 404C67 writes 0xCF0 bytes from 52B3B0; 41A300 reads the byte
 * banks at +0x70 (100), +0xD4 (97), +0x6B0 (98), +0xC8C (95).
 * This file has no signature or game ID. The caller must obtain it from the
 * selected, independently identified game's AdvData/SaveData directory.
 * It is not a parser for dc2pcNN.dat story saves, or for other PC versions.
 */
public final class PcNativeProgress {
    public static final String PROFILE_ID="dc2pc-5769";
    public static final int FILE_SIZE=3312;
    private static final int GLOBAL_OFFSET=0x70,CG_OFFSET=0xd4,SCENE_OFFSET=0x6b0,FLAG_COUNT=1500;
    // All writes to these flags in the verified MES corpus are assignments of 1.
    // 1..17: route endings/epilogues. MAIN derives 0,18,19,21 from those flags.
    // ED_* sets 28; GEND* sets 29. Do not import 20 (option/alarm transient),
    // 56 (title preference), 57 (temporary), 60..75 (voice mute), or unknowns.
    private static final int[] COMPLETION={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,21,28,29};
    public final String profileId,sourceSummary;
    public final Summary source;
    private final byte[] completion,cg,scenes;

    /** Counts flags, including CG variants and scene records, not gallery tiles or routes. */
    public static final class Summary {
        public final int completion,cg,scenes,total;
        private Summary(int completion,int cg,int scenes){this.completion=completion;this.cg=cg;this.scenes=scenes;total=completion+cg+scenes;}
        @Override public String toString(){return "completion="+completion+", cg="+cg+", scenes="+scenes+", total="+total;}
    }
    private PcNativeProgress(byte[] data){
        profileId=PROFILE_ID;
        completion=new byte[COMPLETION.length];
        for(int n=0;n<COMPLETION.length;n++)completion[n]=data[GLOBAL_OFFSET+COMPLETION[n]];
        cg=Arrays.copyOfRange(data,CG_OFFSET,CG_OFFSET+FLAG_COUNT);
        scenes=Arrays.copyOfRange(data,SCENE_OFFSET,SCENE_OFFSET+FLAG_COUNT);
        source=new Summary(count(completion),count(cg),count(scenes));
        sourceSummary="DC2PC system.dat (3312 bytes; "+source+")";
    }
    public static boolean supports(String profileId){return PROFILE_ID.equals(profileId);}
    public static PcNativeProgress parse(String profileId,byte[] data)throws IOException{
        if(!supports(profileId))throw new IOException("Native system progress import is not supported for "+profileId);
        if(data==null||data.length!=FILE_SIZE)throw new IOException("DC2PC system.dat must contain exactly "+FILE_SIZE+" bytes");
        for(int n:COMPLETION)flag(data[GLOBAL_OFFSET+n],100,n);
        for(int n=0;n<FLAG_COUNT;n++){flag(data[CG_OFFSET+n],97,n);flag(data[SCENE_OFFSET+n],98,n);}
        return new PcNativeProgress(data);
    }
    private static void flag(byte value,int bank,int index)throws IOException{
        if(value!=0&&value!=1)throw new IOException("Invalid DC2PC unlock flag "+bank+":"+index+" = "+(value&255));
    }
    private static int count(byte[] data){int result=0;for(byte value:data)result+=value;return result;}
    private void checkHost(PcHost host)throws IOException{
        if(host==null||!PROFILE_ID.equals(host.resources.profileId))throw new IOException("DC2PC native progress target profile mismatch");
    }
    /** Read-only preview of additions; a locked PC flag never clears a phone unlock. */
    public Summary preview(PcHost host)throws IOException{
        checkHost(host);int c=0,g=0,s=0;
        for(int n=0;n<COMPLETION.length;n++)if(completion[n]==1&&host.read(100,COMPLETION[n])==0)c++;
        for(int n=0;n<FLAG_COUNT;n++){
            if(cg[n]==1&&host.read(97,n)==0)g++;
            if(scenes[n]==1&&host.read(98,n)==0)s++;
        }
        return new Summary(c,g,s);
    }
    /** Monotonic merge. Persistence, backups and source/target identity belong to the caller. */
    public Summary apply(PcHost host)throws IOException{
        Summary changes=preview(host);
        for(int n=0;n<COMPLETION.length;n++)if(completion[n]==1&&host.read(100,COMPLETION[n])==0)host.write(100,COMPLETION[n],1);
        for(int n=0;n<FLAG_COUNT;n++){
            if(cg[n]==1&&host.read(97,n)==0)host.write(97,n,1);
            if(scenes[n]==1&&host.read(98,n)==0)host.write(98,n,1);
        }
        return changes;
    }
}
