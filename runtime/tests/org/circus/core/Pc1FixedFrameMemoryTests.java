package org.circus.core;
import java.io.*;
import java.util.*;

/** Four original MOVSD destinations, not independently synchronized DTOs. */
public final class Pc1FixedFrameMemoryTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] save(PcHost host)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();host.save(new DataOutputStream(out));return out.toByteArray();}
    public static int run()throws Exception{
        checks=0;PcHost host=Pc1SaveContextTests.fixture(0);Pc1Runtime runtime=host.pc1;Pc1NativeMemory bus=runtime.nativeMemory();Pc1SlotLoader.Owners owners=runtime.nativeSlotOwners();
        check(owners.live==runtime.nativeFrame()&&owners.working==runtime.workingFrame()&&owners.suspended==runtime.systemFrame()&&owners.candidate==runtime.saves().nativeCandidate(),"slot reconstruction borrows the four canonical owners without copies");
        int[] addresses={Pc1FrameLayout.WORKING_BASE,Pc1FrameLayout.SYSTEM_BASE};Pc1NativeFrame[] frames={owners.working,owners.suspended};
        for(int n=0;n<2;n++){
            check(Arrays.equals(frames[n].bytes(),new byte[8004]),"fixed frame starts at independently verified PE zero-filled BSS");
            byte[] pattern=new byte[8004];for(int i=0;i<pattern.length;i++){pattern[i]=(byte)(i*13+n*71);bus.writeAddress(addresses[n]+i,pattern[i]);}
            check(Arrays.equals(pattern,frames[n].bytes()),"all native-address writes reach one frame backing");frames[n].writeU8(8003,0xb0+n);check(bus.readAddress(addresses[n]+8003)==0xb0+n,"frame tail writes immediately reach native address reads");
        }
        byte[] before=save(host),working=frames[0].bytes(),system=frames[1].bytes();frames[0].writeU8(0,99);frames[1].writeU8(0,98);host.load(new DataInputStream(new ByteArrayInputStream(before)));
        check(runtime.nativeSlotOwners().working==frames[0]&&runtime.nativeSlotOwners().suspended==frames[1],"Host restore preserves both fixed-buffer owner identities");check(Arrays.equals(working,frames[0].bytes())&&Arrays.equals(system,frames[1].bytes())&&Arrays.equals(before,save(host)),"inactive working and system bytes survive whole Host codec exactly");
        Pc1SystemContext context=Pc1SystemContext.capture(host,host.script.name,9,0,new byte[]{'m'},Pc1SystemContextTests.framebuffer(),0);frames[1].copyFrom(context.nativeFrame());context=context.bindNativeFrame(frames[1]);
        frames[1].dword(Pc1NativeSlot.Dword.ARGUMENT0,0x12345678);check(context.fields().argument0==0x12345678&&context.hasNativeFrame(frames[1]),"legacy context fields are a live projection of the same suspended buffer");
        ByteArrayOutputStream full=new ByteArrayOutputStream(),bound=new ByteArrayOutputStream();context.save(new DataOutputStream(full));context.saveBound(new DataOutputStream(bound));check(full.size()-bound.size()==8008,"bound metadata omits exactly one native frame header and8004-byte payload");
        Pc1SystemContext decoded=Pc1SystemContext.loadBound(new DataInputStream(new ByteArrayInputStream(bound.toByteArray())),frames[1]);frames[1].dword(Pc1NativeSlot.Dword.ARGUMENT0,77);check(decoded.fields().argument0==77&&decoded.hasNativeFrame(frames[1]),"bound decode retains the supplied sole owner");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" fixed-frame ownership checks");}
}
