package org.circus.core;
import java.io.*;
import java.util.*;

/** Native9CDD28/BB8418 and legacy candidate projections share one backing. */
public final class Pc1CandidateMemoryTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] save(PcHost h)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();h.save(new DataOutputStream(out));return out.toByteArray();}
    private static int get(Pc1NativeMemory bus,int at)throws IOException{return bus.readAddress(at)|bus.readAddress(at+1)<<8|bus.readAddress(at+2)<<16|bus.readAddress(at+3)<<24;}
    private static void put(Pc1NativeMemory bus,int at,int value)throws IOException{for(int n=0;n<4;n++)bus.writeAddress(at+n,value>>>(n*8));}
    public static int run()throws Exception{
        checks=0;PcHost host=Pc1SaveContextTests.fixture(1);Pc1Runtime runtime=host.pc1;Pc1SaveContext owner=runtime.saves();Pc1NativeMemory bus=runtime.nativeMemory();Pc1NativeFrame candidate=owner.nativeCandidate();
        check(Arrays.equals(candidate.bytes(),new byte[8004])&&bus.readAddress(Pc1SaveContext.CANDIDATE)==0&&bus.readAddress(Pc1SaveContext.CANDIDATE+8003)==0,"whole candidate begins at verified BSS rather than initialized live defaults");
        host.variables.write(0,0,41);owner.prepare(host,6,0x1234);check(owner.candidate().hasNativeFrame(candidate)&&get(bus,Pc1SaveContext.FLAG)==1,"legacy1B projection binds same raw candidate and flag view");
        int argument=Pc1SaveContext.CANDIDATE+Pc1NativeSlot.Dword.ARGUMENT0.offset;put(bus,argument,0x11223344);check(owner.candidate().fields().argument0==0x11223344&&owner.candidate().nativeFrame().dword(Pc1NativeSlot.Dword.ARGUMENT0)==0x11223344,"raw candidate DWORD mutation reaches both projection and exported native bytes");
        bus.writeAddress(Pc1SaveContext.FLAG+3,0x80);check(owner.flag()==0x80000001&&!owner.needsCurrentFrame(1),"BB8418 keeps all32 bits rather than byte-normalizing signed flags");byte[] before=save(host);bus.writeAddress(Pc1SaveContext.CANDIDATE+8003,0x91);put(bus,Pc1SaveContext.FLAG,0);host.load(new DataInputStream(new ByteArrayInputStream(before)));
        check(runtime.saves()==owner&&owner.nativeCandidate()==candidate,"Host restore preserves stable owner objects for existing bus consumers");check(owner.flag()==0x80000001&&owner.candidate().fields().argument0==0x11223344&&Arrays.equals(before,save(host)),"canonical flag/candidate/provenance restore exact wire once");
        byte[] raw=new byte[8004];for(int n=0;n<raw.length;n++)raw[n]=(byte)(n*31+7);for(int word=0;word<2001;word++){int at=word*4,value=0;for(int n=0;n<4;n++)value|=(raw[at+n]&255)<<(n*8);put(bus,Pc1SaveContext.CANDIDATE+at,value);}check(Arrays.equals(raw,candidate.bytes())&&Arrays.equals(raw,owner.candidate().nativeFrame().bytes()),"all2001 native MOVSD stores use the original candidate backing");
        Pc1SaveContext isolated=new Pc1SaveContext();ByteArrayOutputStream stream=new ByteArrayOutputStream();isolated.save(new DataOutputStream(stream));check(stream.size()==4+4+4+8004+1,"empty P1S3 stores raw BSS once even without legacy capture metadata");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" canonical candidate checks");}
}
