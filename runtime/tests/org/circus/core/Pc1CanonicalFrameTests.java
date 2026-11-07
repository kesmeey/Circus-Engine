package org.circus.core;

import java.io.*;
import java.util.*;

/** Host/Variables aliases must reach the exact same bytes, including through
 * snapshots and native system/candidate copies. No original process is run. */
public final class Pc1CanonicalFrameTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static PcHost host()throws IOException{
        final Map<String,byte[]> files=new HashMap<>();
        files.put("AdvData/DAT/NAMES.DAT",new byte[20]);
        files.put("AdvData/MES/caller.mes",new byte[]{0,0,0,0,0x63,0x20,0,0,0});
        GameResources data=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return files.keySet();}
            public byte[] read(String path,int limit)throws IOException{byte[] b=files.get(path);if(b==null)throw new FileNotFoundException(path);if(b.length>limit)throw new IOException("Limit");return b.clone();}
            public Archive.Source source(String path)throws IOException{final byte[] b=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,b.length);return Arrays.copyOfRange(b,(int)p,(int)p+n);}};}
        });
        PcHost h=Pc1TestDefaults.bind(new PcHost(new CircusResources(data,Profiles.get("dcpc-2063")),TextCodec.identity()));
        h.attach(new Variables(h,Variables.Model.PC_BYTE));h.script=h.resources.scripts().script("caller");return h;
    }
    private static void alias(PcHost host,int offset,int value)throws IOException{
        for(int n=0;n<4;n++)host.variables.write(90,offset-Pc1FrameLayout.BANK90_OFFSET+n,value>>>(8*n));
    }
    public static int run()throws Exception{
        count=0;PcHost h=host();Pc1NativeFrame frame=h.pc1.nativeFrame();
        alias(h,Pc1FrameLayout.PARAGRAPH_MODE,0x12345678);check(h.messageMode()==0x12345678,"Paragraph reads all four live bank90 bytes");
        h.messageMode(1);check(frame.readI32(Pc1FrameLayout.PARAGRAPH_MODE)==1&&h.read(90,1604)==1,"Host paragraph write is the bank90 owner");
        h.scene.backgroundX(-17);h.scene.backgroundY(29);h.scene.character(0,0,83);h.scene.character(1,1,-31);
        check(frame.dword(Pc1NativeSlot.Dword.BACKGROUND_X)==-17&&frame.dword(Pc1NativeSlot.Dword.BACKGROUND_Y)==29&&frame.dword(Pc1NativeSlot.Dword.ACTOR1_X)==83&&frame.dword(Pc1NativeSlot.Dword.ACTOR2_Y)==-31,"Scene producers write canonical background and actor coordinate DWORDs");
        alias(h,Pc1FrameLayout.BACKGROUND_X,0x12345678);alias(h,Pc1FrameLayout.ACTOR1_Y,0x89abcdef);
        check(h.scene.backgroundX()==0x12345678&&h.scene.character(0,1)==0x89abcdef,"Coordinate consumers immediately observe signed bank90 aliases");
        check(h.scene.shown.x==0&&h.scene.shown.actors[0][1]==0,"A live coordinate write does not fabricate a repaint of already displayed state");
        h.scene.publish(h.ui,0,0);check(h.scene.shown.x==0x12345678&&h.scene.shown.actors[0][1]==0x89abcdef,"Explicit portable publication reads the bound live coordinate view");
        alias(h,Pc1FrameLayout.LOGICAL_MUSIC,0x89abcdef);check(h.logicalMusic()==0x89abcdef,"Music request follows signed bank90 aliases");
        h.logicalMusic(37);check(frame.dword(Pc1NativeSlot.Dword.LOGICAL_MUSIC)==37,"Music setter shares native DWORD");
        alias(h,Pc1FrameLayout.PACKED_SOUND,0xdead0123);check(h.pc1.packedSoundRequest()==0xdead0123,"Packed SE preserves high bits outside normal producer range");
        alias(h,Pc1FrameLayout.CACHED_SCENARIO_READ,0x80000100);check(h.pc1.cachedScenarioRead()==0x80000100&&h.read(99,251)==0x80000100,"Cached scenario read is a full DWORD throughout the Host");
        h.variables.write(0,0,300);check(frame.readOrdinary(0,0)==255,"Ordinary Variables writes reach frame with native saturation");
        frame.writeOrdinary(0,1,17);check(h.variables.read(0,1)==17,"Direct native ordinary producer is immediately visible to VM variables");
        h.variables.write(0,-1,511);check(frame.rawLogWord(999)==0xff00,"Signed ordinary index aliases the preceding log word with saturation");
        frame.writeI32(Pc1FrameLayout.LOG_COUNT,1000);h.pc1.textLog().onCheckpoint03(0x1234);
        check(h.variables.read(0,0)==0x34&&h.variables.read(0,1)==0x12,"Native log overflow writes the same ordinary bytes used by VM");
        h.variables.appendHistory();h.variables.write(0,0,7);h.variables.replayHistory(h.variables.historyCount());
        check(h.variables.read(0,0)==0x34&&frame.readOrdinary(0,1)==0x12,"History replay writes back through the native ordinary owner");
        frame.writeU8(0x1099,0xa7);h.write(90,1023,0xc1);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();h.save(new DataOutputStream(bytes));
        PcHost loaded=host();loaded.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(Arrays.equals(frame.bytes(),loaded.pc1.nativeFrame().bytes()),"Host snapshot preserves every native frame byte, including unknown cells");
        check(loaded.variables.read(0,0)==0x34&&loaded.messageMode()==1&&loaded.logicalMusic()==37&&loaded.pc1.packedSoundRequest()==0xdead0123,"Restored consumers bind to restored raw owner without a scalar mirror");
        check(loaded.scene.backgroundX()==0x12345678&&loaded.scene.character(0,1)==0x89abcdef,"Scene codec placeholders cannot overwrite canonical coordinate bytes during host restore");
        loaded.variables.write(0,0,63);ByteArrayOutputStream variables=new ByteArrayOutputStream();h.variables.save(new DataOutputStream(variables));
        loaded.variables.load(new DataInputStream(new ByteArrayInputStream(variables.toByteArray())));
        check(loaded.variables.read(0,0)==63,"Variables codec owns history only; it cannot overwrite external canonical ordinary memory");
        Pc1SystemContext suspended=Pc1SystemContext.capture(h,"caller",6,0,"menu".getBytes(Binary.SJIS),Pc1SystemContextTests.framebuffer(),h.pc1.packedSoundRequest());
        h.pc1.nativeFrame().writeU8(0x1099,11);h.write(90,1023,22);h.variables.write(0,0,91);h.messageMode(0);h.logicalMusic(-1);
        suspended.applyReturn(h);
        check(frame.readU8(0x1099)==0xa7&&h.read(90,1023)==0xc1,"System return restores unknown fields and bank90 beyond the former100-byte reservation");
        check(h.variables.read(0,0)==91&&suspended.nativeFrame().readOrdinary(0,0)==91,"Return patches current ordinary into the actual suspended owner before full copy");
        check(h.messageMode()==1&&h.logicalMusic()==37&&h.pc1.packedSoundRequest()==0xdead0123,"System return restores canonical scalar consumers including raw32 SE");
        h.script=new MesScript("caller",new byte[]{0,0,0,0,0x63,0x20,0x1b,1,0,0x3b,'x',0,0,0,0},Profiles.get("dcpc-2063"));
        Pc1Frame candidate=Pc1Frame.capturePrepared(h,6,h.pc1.packedSoundRequest());
        frame.writeU8(0x1099,0);h.write(90,1023,0);h.variables.write(0,0,7);h.messageMode(0);candidate.apply(h);
        check(frame.readU8(0x1099)==0xa7&&h.read(90,1023)==0xc1&&h.variables.read(0,0)==91&&h.messageMode()==1,"Candidate apply restores all known bytes and its captured ordinary rather than a field subset");
        Pc1Frame dated=candidate.withNativeSaveMetadata(25,26,new Pc1Frame.SaveMetadata(2026,11,4,13,45));
        byte[] original=candidate.nativeFrame().bytes(),patched=dated.nativeFrame().bytes();
        for(int n=0;n<original.length;n++)if(n!=Pc1FrameLayout.ORDINARY_OFFSET+902&&(n<Pc1FrameLayout.ORDINARY_OFFSET+920||n>Pc1FrameLayout.ORDINARY_OFFSET+925))check(original[n]==patched[n],"Save metadata preserves raw frame byte "+n);
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1CanonicalFrameTests "+run());}
}
