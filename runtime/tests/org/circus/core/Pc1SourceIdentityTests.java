package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Identity admission is an outer bookkeeping step, never another411670. */
public final class Pc1SourceIdentityTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] wire(Pc1CodeBuffer owner)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();owner.save(new DataOutputStream(out));return out.toByteArray();}
    public static int run()throws Exception{
        checks=0;Profile profile=Profiles.get("dcpc-2063");MesScript old=new MesScript("OLD",new byte[]{0,0,0,0,0x63,0x20,'A','B','C',0},profile),next=new MesScript("NEXT",new byte[]{1,0,0,0,2,0,0,0,0x63,0x20,0},profile);
        Pc1CodeBuffer buffer=Pc1CodeBuffer.zeroBss();buffer.load(old);MesScript oldView=old.nativeView(buffer);buffer.writeBytes(0,new byte[]{0x63,0x20,'X',0});buffer.writeU8(99,0x91);byte[] actual=buffer.bytes();long generation=buffer.generation(),revision=buffer.revision();buffer.adoptSourceIdentity(next);
        check(Arrays.equals(actual,buffer.bytes())&&buffer.readU8(4)=='C'&&buffer.readU8(99)==0x91,"outer admission preserves complete read/callback/stale code bytes rather than copying NEXT's original prefix");
        check(buffer.loadedName().equals("NEXT")&&buffer.sourceSha256().equals(next.sha256)&&buffer.headerSha256().equals(next.originalHeaderSha256())&&buffer.loadedBytes()==3&&buffer.generation()==generation+1&&buffer.revision()==revision+1,"identity refers to original source prefix while native read result remains separate");
        MesScript view=next.nativeView(buffer);check(view.rawOpcode(next.versionOffset+2)=='X'&&view.rawOpcode(next.versionOffset+99)==0x91,"new source view projects the existing live code owner including stale suffix");
        boolean stale=false;try{oldView.rawOpcode(old.versionOffset+2);}catch(IOException expected){stale=true;}check(stale,"old source generation cannot silently keep executing after explicit new identity admission");
        byte[] saved=wire(buffer);Pc1CodeBuffer restored=Pc1CodeBuffer.load(new DataInputStream(new ByteArrayInputStream(saved)));restored.requireSource(next);check(Arrays.equals(saved,wire(restored))&&next.nativeView(restored).rawOpcode(next.versionOffset+2)=='X',"identity and actual self-modified code survive P1CB serialization without source reload");
        PcHost host=Pc1SaveContextTests.fixture(0);host.pc1.checkpointMemory().applyCountRead(new byte[]{37});host.pc1.checkpointMemory().applyTableRead(new byte[]{11,22});int count=host.pc1.checkpointMemory().count();byte[] table=host.pc1.checkpointMemory().tableBytes(),raw=host.pc1.codeBuffer().bytes();host.pc1.codeBuffer().adoptSourceIdentity(next);check(count==host.pc1.checkpointMemory().count()&&Arrays.equals(table,host.pc1.checkpointMemory().tableBytes())&&Arrays.equals(raw,host.pc1.codeBuffer().bytes()),"metadata admission neither replays parsed checkpoint header nor rewrites actual code");
        byte[] before=wire(buffer);boolean rejected=false;try{buffer.adoptSourceIdentity(null);}catch(IOException expected){rejected=true;}check(rejected&&Arrays.equals(before,wire(buffer)),"unrepresentable source metadata rejects without undoing or mutating native state");return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" source identity admission checks");}
}
