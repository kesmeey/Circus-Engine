package org.circus.core;
import java.io.*;
import java.util.*;

/** Actual411480 goldens:8 original instruction programs/144 steps, all bytes. */
public final class Pc1FrameInitTests {
    private static int checks;
    private static final String INITIALIZED="c46064fd2c932209007377e4c9caf1f44ea5bfff3eb738e5e253d6a408cc32e0";
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static int run()throws Exception{
        checks=0;PcHost fixture=Pc1SaveContextTests.fixture(0);PcHost entry=PcHost.pc1BeforeWinMain(fixture.resources,TextCodec.identity());Pc1Runtime runtime=entry.pc1;Pc1NativeMemory bus=runtime.nativeMemory();Pc1NativeFrame live=runtime.nativeFrame(),system=runtime.systemFrame();
        check(Arrays.equals(live.bytes(),new byte[8004])&&Arrays.equals(system.bytes(),new byte[8004]),"explicit process entry has zero-filled live/system BSS before411480");check(entry.nativeCrtState()==1&&runtime.graphicsMemory().readInt(0x43e100)==1,"native process entry reads verified CRT PE seed1 before startup srand");
        Pc1NativeFrame[] frames={live,system,runtime.workingFrame(),runtime.saves().nativeCandidate()};int[] addresses={Pc1FrameLayout.LIVE_BASE,Pc1FrameLayout.SYSTEM_BASE,Pc1FrameLayout.WORKING_BASE,Pc1FrameLayout.CANDIDATE_BASE};
        for(int seed:new int[]{0,1,7,31,127,128,255,0x12345678}){
            byte[][] before=new byte[4][8004];for(int region=0;region<4;region++)for(int n=0;n<8004;n++){before[region][n]=(byte)(seed+n*13+region*71);bus.writeAddress(addresses[region]+n,before[region][n]);}
            runtime.initializeFrame411480();check(Binary.sha256(live.bytes()).equals(INITIALIZED)&&Binary.sha256(system.bytes()).equals(INITIALIZED),"native clear/STOSD/field patch/MOVSD full-frame SHA agrees with original411480");check(Arrays.equals(before[2],frames[2].bytes())&&Arrays.equals(before[3],frames[3].bytes()),"411480 leaves both working and candidate images untouched");check(runtime.nativeFrame()==live&&runtime.systemFrame()==system&&bus.readAddress(Pc1FrameLayout.LIVE_BASE)==1&&bus.readAddress(Pc1FrameLayout.SYSTEM_BASE)==1,"initialization mutates existing native owners and bound views in place");
        }
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" native411480 initialization checks");}
}
