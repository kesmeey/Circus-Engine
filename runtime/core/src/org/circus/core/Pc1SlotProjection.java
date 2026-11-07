package org.circus.core;

import java.io.IOException;

/** PC1 CHS 41A6CA..41A759: pure checkpoint projection after a validated slot read.
 * Does not apply a frame, load resources, rebuild text or authorize native-slot I/O. */
public final class Pc1SlotProjection {
    public enum Wait {
        NONE(-1),MESSAGE(0x07),SYSTEM_COMMAND(0x1d);
        public final int nativeValue;
        Wait(int value){nativeValue=value;}
    }
    /** Values owned by the immutable loaded frame. rawCodePc is a native DWORD,
     * not an execution address to use without the checkpoint projection below. */
    public static final class SavedFields {
        public final int rawCodePc,argument0,argument1,logicalMusic;
        public SavedFields(int rawCodePc,int argument0,int argument1,int logicalMusic){this.rawCodePc=rawCodePc;this.argument0=argument0;this.argument1=argument1;this.logicalMusic=logicalMusic;}
    }
    public final String script,scriptSha256;
    public final int checkpointIndex,checkpointCodePc,checkpointFilePc,resumeCodePc,resumeFilePc;
    /** Sign-extended byte, matching MOVSX at41A6F7. */
    public final int headerOpcode,peekOperand0,peekOperand1;
    public final Wait wait;
    /** 1B always replaces the candidate from the unchanged loaded frame, even
     * when candidateFlag==0; all other headers preserve the prior candidate. */
    public final boolean refreshCandidate;
    public final int candidateFlag;
    public final SavedFields loadedFields;
    public final int logicalMusicDuringReconstruction;
    private Pc1SlotProjection(MesScript caller,int index,int code,int file,int resumeCode,int resumeFile,int opcode,int a0,int a1,SavedFields saved,int currentMusic){
        script=caller.name;scriptSha256=caller.sha256;checkpointIndex=index;checkpointCodePc=code;checkpointFilePc=file;resumeCodePc=resumeCode;resumeFilePc=resumeFile;
        headerOpcode=opcode;peekOperand0=a0;peekOperand1=a1;wait=opcode==7?Wait.MESSAGE:opcode==0x1d?Wait.SYSTEM_COMMAND:Wait.NONE;
        refreshCandidate=opcode==0x1b;candidateFlag=refreshCandidate?a0:0;loadedFields=saved;logicalMusicDuringReconstruction=currentMusic;
    }
    /** The caller must already match the loaded candidate's version/name/SHA.
     * PC1's table is headerOffsets, not the zero-filled headerMetadata array.
     * Only owned bytes are accessed; invalid native pointers fail before effects. */
    public static Pc1SlotProjection project(MesScript caller,int checkpointIndex,SavedFields saved,int currentLogicalMusic)throws IOException{
        if(caller==null||!caller.profile.id.equals("dcpc-2063")||caller.profile.headerStride!=4||caller.profile.versionBytes!=2)throw new IOException("PC1 slot projection requires the dcpc-2063 native checkpoint layout");
        if(saved==null)throw failure(caller,checkpointIndex,"missing saved frame fields");
        if(checkpointIndex<0||checkpointIndex>=caller.headerOffsets.length)throw failure(caller,checkpointIndex,"checkpoint index outside native DWORD table");
        long code=caller.headerOffsets[checkpointIndex],file=(long)caller.versionOffset+code;
        // The native DWORD table is not restricted to firstOffset. Header
        // entries0/1 can peek the actual loaded version-marker bytes, just as
        // the dispatcher can fetch them after a raw frame alias changes PC.
        if(code<0||code>Integer.MAX_VALUE||file>Integer.MAX_VALUE||!knownCode(caller,(int)file))throw failure(caller,checkpointIndex,"checkpoint native code PC 0x"+Long.toHexString(code)+" is outside the owned caller bytes");
        int opcode=(byte)caller.rawOpcode((int)file),a0=-1,a1=-1;
        boolean three=opcode==0x1b||opcode==0x1d||opcode==7;long resumeCode=code+(three?3:0),resumeFile=file+(three?3:0);
        if(three){
            if(file+3>caller.length())throw failure(caller,checkpointIndex,"truncated native three-byte checkpoint header at file0x"+Long.toHexString(file));
            a0=caller.rawOpcode((int)file+1);a1=caller.rawOpcode((int)file+2);
        }
        if(resumeCode>Integer.MAX_VALUE||resumeFile>Integer.MAX_VALUE||!knownCode(caller,(int)resumeFile))throw failure(caller,checkpointIndex,"projected native resume PC is outside the owned caller bytes");
        // 1D's pre-copy argument writes are overwritten by the restored frame;
        // saved.argument0/1 survive unchanged. The saved raw PC is never rewritten.
        return new Pc1SlotProjection(caller,checkpointIndex,(int)code,(int)file,(int)resumeCode,(int)resumeFile,opcode,a0,a1,saved,currentLogicalMusic);
    }
    private static boolean knownCode(MesScript caller,int fileOffset){return fileOffset>=caller.versionOffset&&fileOffset<caller.length();}
    private static IOException failure(MesScript caller,int checkpointIndex,String detail){return new IOException("PC1 slot projection 41A6CA..41A759 "+caller.name+" checkpoint="+checkpointIndex+": "+detail);}
}
