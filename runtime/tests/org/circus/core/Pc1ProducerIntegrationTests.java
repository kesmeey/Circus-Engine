package org.circus.core;

import java.io.*;
import java.util.*;

/** Physical VM fetch + production host + addressed owners. Literal rows come
 * from the separately verified native producer contracts. */
public final class Pc1ProducerIntegrationTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static byte[] words(int op,int...values){byte[] b=new byte[1+values.length*2];b[0]=(byte)op;for(int n=0;n<values.length;n++){b[n*2+1]=(byte)values[n];b[n*2+2]=(byte)(values[n]>>>8);}return b;}
    private static byte[] small(int op,int a,int b){return new byte[]{(byte)op,(byte)a,(byte)b};}
    private static byte[] string(int marker,String text)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(marker);b.write(text.getBytes(Binary.SJIS));b.write(0);return b.toByteArray();}
    private static byte[] script(byte[]...pieces)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,0x63,0x20});for(byte[] p:pieces)b.write(p);b.write(new byte[32]);return b.toByteArray();}
    private static final class Bound {
        final PcHost host;final MesVm vm;
        Bound(byte[] raw)throws IOException{
            GameResources files=new GameResources(new GameResources.Storage(){
                public Set<String> paths(){return Collections.singleton("AdvData/DAT/NAMES.DAT");}
                public byte[] read(String path,int limit)throws IOException{if(!path.equals("AdvData/DAT/NAMES.DAT"))throw new FileNotFoundException(path);return new byte[20];}
                public Archive.Source source(String path)throws IOException{final byte[] bytes=read(path,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return bytes.length;}public byte[] read(long at,int length)throws IOException{Binary.range(at,length,bytes.length);return Arrays.copyOfRange(bytes,(int)at,(int)at+length);}};}
            });
            host=Pc1TestDefaults.bind(new PcHost(new CircusResources(files,Profiles.get("dcpc-2063")),TextCodec.identity(),123));
            vm=new MesVm(name->new MesScript(name,raw,Profiles.get("dcpc-2063")),host,"START");
        }
        void run(int instructions){check(vm.run(instructions)==MesVm.Status.RUNNING,"native producer VM: "+vm.error());check(!host.pc1.dispatching(),"attempt scope closes");}
    }
    private static int[] row(Pc1Compositor.State memory,int address)throws IOException{int[] value=new int[8];for(int n=0;n<8;n++)value[n]=memory.readInt(address+n*4);return value;}
    public static int run()throws Exception{
        count=0;
        Bound draw=new Bound(script(words(0x5d,10,20,0,0),small(0x2a,0,0),words(0x53,100,200,3,4),words(0x90,13,24,70,999),small(0x22,3,0)));
        draw.run(3);Pc1Runtime r=draw.host.pc1;Pc1DrawMemory memory=r.drawMemory();
        check(memory.readInt(0x43d46c)==1,"name-row multiplier has its independently observed PE initialDWORD");
        check(Arrays.equals(row(memory,Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.PENDING_OFFSET),new int[]{110,220,70,1,107,216,10,20}),"53 stores raw type/condition/source offsets after5D and2A");
        check(r.nativeFrame().readI32(Pc1FrameLayout.RAW_CODE_PC)==32&&memory.readInt(Pc1DrawProducer.CONDITION)==0,"53 owns arbitrary extension marker and consumes condition");
        draw.run(1);check(Arrays.equals(row(memory,0x9d23d0),new int[]{110,220,70,1,107,216,10,20})&&memory.readInt(0xbb84d8)==1&&r.nativeFrame().readI32(Pc1FrameLayout.PENDING_COUNT)==0,"22 copies raw pending rows to addressed template");
        byte[] saved=draw.vm.snapshot();Bound drawCopy=new Bound(script(words(0x5d,10,20,0,0),small(0x2a,0,0),words(0x53,100,200,3,4),words(0x90,13,24,70,999),small(0x22,3,0)));drawCopy.vm.restore(saved);
        check(Arrays.equals(saved,drawCopy.vm.snapshot())&&Arrays.equals(row(drawCopy.host.pc1.drawMemory(),0x9d23d0),row(memory,0x9d23d0)),"VM snapshot restores independent raw draw BSS");
        memory.writeInt(0x43d46c,0x89abcdef);byte[] rowsSave=draw.vm.snapshot();drawCopy.vm.restore(rowsSave);check(drawCopy.host.pc1.drawMemory().readInt(0x43d46c)==0x89abcdef&&Arrays.equals(rowsSave,drawCopy.vm.snapshot()),"raw name-row multiplier is not clamped by geometry13 codec");

        Bound alias=new Bound(script(words(0x53,100,200,3,4),words(0x7f,13,24,70,999)));alias.host.pc1.nativeFrame().writeI32(Pc1FrameLayout.PENDING_COUNT,-103);alias.host.pc1.conditionalNext();alias.run(1);
        check(alias.host.pc1.nativeFrame().readI32(Pc1FrameLayout.RAW_CODE_PC)==9&&alias.host.pc1.nativeFrame().readI32(Pc1FrameLayout.PENDING_COUNT)==-102,"negative pending count may alias canonical PC; footer keeps actual handler PC");
        check(alias.host.ui.quads.isEmpty(),"presentation projection cannot overwrite aliased native state");

        byte[] buttons=script(small(0x2a,0,0),words(0x4f,1,2,100,50),string(0xe1,":go"),words(0x50,5,6,7,8),words(0x99,30,40,70,1),words(0x51,9,10,2,9),string(0x80,"A"),small(0x17,1,0));
        Bound ui=new Bound(buttons);ui.run(4);Pc1NativeUi u=ui.host.pc1.nativeUi();int base=Pc1NativeUi.MAIN_BUTTONS;
        check(u.readInt(Pc1NativeUi.BUTTON_COUNT)==1&&u.readInt(base+4)==1&&u.readInt(base+12)==1&&u.readInt(base+24)==50,"4F stores raw rect and literal condition without UI origin translation");
        check(u.readInt(base+0x1c)==1&&u.readInt(base+0x20)==5&&u.readInt(base+0x30)==7&&u.readInt(base+0x38)==30&&u.readInt(base+0x3c)==40,"50 consumes own extension and keeps raw image component");
        check(u.readInt(base+0x44)==9&&u.readInt(base+0x60)==15&&u.readInt(base+0x64)==29&&u.cstring(u.readInt(base+0x68))[0]=='A',"51 stores original source pointer and native14-pitch width");
        check(u.readInt(0xbb83fc)==0,"4F/51 payload tails do not masquerade as native string fetches");
        ui.run(1);check(u.readInt(base)==3&&u.readInt(0x9cb908)==3&&u.readInt(Pc1NativeUi.STORY_BUTTONS)==3&&u.readInt(0xbb8450)==1&&u.readInt(Pc1NativeUi.BUTTON_COUNT)==0,"17 populates native template/story owners and shared prefix count");
        byte[] uiSave=ui.vm.snapshot();Bound uiCopy=new Bound(buttons);uiCopy.vm.restore(uiSave);check(Arrays.equals(uiSave,uiCopy.vm.snapshot())&&uiCopy.host.pc1.nativeUi().readInt(Pc1NativeUi.STORY_BUTTONS)==3,"raw UI BSS survives complete VM round trip");

        byte[] grid=script(words(0x54,1,2,3,4),words(0x80,5,6,7,8),words(0x55,10,20,30,40),words(0x56,77,88,99,100),words(0x91,11,12,13,14),string(0x43,":item"),words(0x57,21,22,23,24),string(0xef,"caption"),words(0x5f,101,102,103,104));
        Bound list=new Bound(grid);list.run(6);Pc1NativeUi gridMemory=list.host.pc1.nativeUi();
        check(gridMemory.readInt(Pc1NativeUi.LIST_COUNT)==1&&gridMemory.readInt(Pc1NativeUi.LIST_VISIBLE)==1&&gridMemory.readInt(0x955408)==10,"54/55/56/43 populate addressed list and visible rectangles");
        check(gridMemory.readInt(Pc1NativeUi.LIST_RECORDS)==77&&gridMemory.readInt(Pc1NativeUi.LIST_RECORDS+4)==13&&gridMemory.readInt(Pc1NativeUi.LIST_RECORDS+16)==11,"56 stores raw extension operands without synthesizing a portable list item");
        check(gridMemory.readInt(Pc1NativeUi.LIST_RECORDS+100)==Pc1NativeMemory.CODE_BASE+48&&Arrays.equals(gridMemory.cstring(Pc1NativeUi.LIST_RECORDS+36),"caption".getBytes(Binary.SJIS)),"43 retains original action pointer and57 expands caption into preceding item");
        check(gridMemory.readInt(0x9553fc)==104&&gridMemory.readInt(0x955550)==101,"5F keeps native bounds write order and locations");

        u.writeInt(0xbb8450,Integer.MIN_VALUE);u.writeInt(0x9b8e1c,Integer.MAX_VALUE);byte[] countSave=ui.vm.snapshot();uiCopy.vm.restore(countSave);
        check(uiCopy.host.pc1.nativeUi().readInt(0xbb8450)==Integer.MIN_VALUE&&uiCopy.host.pc1.nativeUi().readInt(0x9b8e1c)==Integer.MAX_VALUE,"raw story counters are32bit aliases, independent of portable projection size");

        Bound positions=new Bound(script(words(0x60,11,22,33,44)));positions.host.scene.character(0,0,91);positions.run(1);
        check(positions.host.scene.character(0,0)==91&&positions.host.pc1.nativeFrame().readI32(0x114c)==11&&positions.host.pc1.nativeFrame().readI32(0x1158)==44,"60 stages targets without moving current displayed actors");

        Bound rng=new Bound(script(string(0x32,"label"),small(0x4e,0,0)));Pc1DrawMemory rm=rng.host.pc1.drawMemory();
        check(rm.readInt(0x43e100)==123,"PC1 startup uses explicitly injected GetTickCount seed");rm.writeInt(0x43e100,-1);check(rng.host.nativeCrtRandom()==35&&rm.readInt(0x43e100)==2316998,"CRT leaf and addressed43E100 share one32bit state");
        rng.run(1);int pointer=Pc1NativeMemory.CODE_BASE+3;check(rm.readInt(0xbb83fc)==pointer,"string fetch publishes original raw pointer");rng.run(1);check(rm.readInt(0xbb83fc)==pointer,"word fetch preserves raw string pointer");
        byte[] pointerSave=rng.vm.snapshot();Bound pointerCopy=new Bound(script(string(0x32,"label"),small(0x4e,0,0)));pointerCopy.vm.restore(pointerSave);check(pointerCopy.host.pc1.drawMemory().readInt(0xbb83fc)==pointer&&Arrays.equals(pointerSave,pointerCopy.vm.snapshot()),"raw fetch pointer is a separately saved global");
        Pc1PathGlobals roots=rng.host.pc1.pathGlobals();for(int n=0;n<Pc1PathGlobals.BYTES;n++)check(roots.read(Pc1PathGlobals.BASE+n)==0,"PE loader zeroed path global byte"+n);
        for(int n=0;n<Pc1PathGlobals.BYTES;n++)roots.write(Pc1PathGlobals.BASE+n,n*31+17);
        check(rng.host.pc1.nativeMemory().readAddress(Pc1PathGlobals.CONFIGURED_DATA_ROOT)==((260*31+17)&255),"path cells share addressed native memory");
        byte[] rootSave=rng.vm.snapshot();pointerCopy.vm.restore(rootSave);for(int n=0;n<Pc1PathGlobals.BYTES;n++)check(pointerCopy.host.pc1.pathGlobals().read(Pc1PathGlobals.BASE+n)==((n*31+17)&255),"path global stale byte survives VM restore"+n);
        check(Arrays.equals(rootSave,pointerCopy.vm.snapshot()),"path global codec retains all three cells");
        roots.write(Pc1NativePaths.DATA_PREFIX,'X');check(rng.host.pc1.nativeMemory().readAddress(Pc1NativePaths.DATA_PREFIX)=='X',"native literal path strings reside in writablePE.data");
        byte[] literalSave=rng.vm.snapshot();pointerCopy.vm.restore(literalSave);check(pointerCopy.host.pc1.pathGlobals().read(Pc1NativePaths.DATA_PREFIX)=='X'&&Arrays.equals(literalSave,pointerCopy.vm.snapshot()),"modified path literals and padding keep their own saved byte owner");
        rng.host.pc1.nativeMemory().writeAddress(0x9d3af8+99,0xab);rng.host.pc1.nativeMemory().writeAddress(0x9ccaa0+77,0xcd);
        check(rng.host.pc1.textBuffers().unsignedByte(Pc1TextBuffers.LIVE_BODY,99)==0xab&&rng.host.pc1.textBuffers().unsignedByte(Pc1TextBuffers.LIVE_SPEAKER,77)==0xcd,"native text aliases write existing buffers without clearing stale suffix");
        byte[] textSave=rng.vm.snapshot();pointerCopy.vm.restore(textSave);check(pointerCopy.host.pc1.nativeMemory().readAddress(0x9d3af8+99)==0xab&&pointerCopy.host.pc1.nativeMemory().readAddress(0x9ccaa0+77)==0xcd&&Arrays.equals(textSave,pointerCopy.vm.snapshot()),"live text byte bus follows replaced buffer owners after restore");
        int[] literals={Pc1ResourceLiterals.VOICE_ARCHIVE,Pc1ResourceLiterals.PCM_EXTENSION,Pc1ResourceLiterals.VOICE_PREFIX,Pc1ResourceLiterals.WAV_EXTENSION,Pc1ResourceLiterals.MIDI_DIRECTORY,Pc1ResourceLiterals.EMPTY};
        int[] heads={'V','.','V','.','M',0};for(int n=0;n<literals.length;n++){check(rng.host.pc1.nativeMemory().readAddress(literals[n])==heads[n],"verified PE resource literal initial byte"+n);rng.host.pc1.nativeMemory().writeAddress(literals[n],0x80+n);}
        byte[] resourceSave=rng.vm.snapshot();pointerCopy.vm.restore(resourceSave);for(int n=0;n<literals.length;n++)check(pointerCopy.host.pc1.nativeMemory().readAddress(literals[n])==0x80+n,"mutable resource literal survives restore"+n);check(Arrays.equals(resourceSave,pointerCopy.vm.snapshot()),"resource literal owner is included once in runtime state");
        PcHost globals=rng.host;globals.write(99,0,0x89abcdef);check(globals.pc1.drawMemory().readInt(Pc1VmGlobals.RESULT0)==0x89abcdef&&globals.read(99,0)==0x89abcdef,"99:0 setter/getter share raw signedDWORD");
        globals.pc1.drawMemory().writeInt(0x43028c,0x12345678);globals.pc1.drawMemory().writeInt(0x94c61c,0x80000001);check(globals.read(99,3)==0x12345678&&globals.read(99,4)==0x80000001,"99:3/4 read actual graphics globals rather than constant or portable bool");
        globals.write(99,19,73);check(globals.pc1.drawMemory().readInt(Pc1VmGlobals.SET19)==73&&globals.read(99,19)==0,"99:19 is a raw write-only store");
        globals.write(99,243,255);check(globals.read(99,243)==-1&&globals.pc1.drawMemory().readInt(Pc1VmGlobals.SELECTION243)==-1,"99:243 maps255 to native signedminus1");globals.write(99,254,0x12345678);check(globals.pc1.nativeUi().readInt(0x968a00)==0x12345678&&globals.read(99,254)==0x12345678,"99:254 is the existing raw UI scalar");
        check(globals.read(99,247)==1&&globals.read(99,252)==-1&&globals.read(99,253)==-1,"zero-query and negative PE/UI sentinels remain actual native values");
        globals.pc1.drawMemory().writeInt(Pc1VmGlobals.DEVICE247,22);globals.pc1.drawMemory().writeInt(Pc1VmGlobals.VALUE22,0x87654321);check(globals.read(99,247)==0&&globals.read(99,22)==0x87654321,"native pointer-zero predicate and22 DWORD aliases");
        globals.pc1.drawMemory().writeInt(Pc1VmGlobals.RANDOM_RESULT,0x12345678);globals.command("pc.random",new int[]{0},new byte[0]);check(globals.read(99,2)==0&&globals.pc1.drawMemory().readInt(Pc1VmGlobals.RANDOM_RESULT)==0,"random producer writes canonical99:2 result even for zero divisor");
        check(Arrays.equals(globals.pc1.nativeUi().cstring(Pc1ResourceLiterals.GRAPHICS_CATEGORY),new byte[]{'G','R','P'})&&Arrays.equals(globals.pc1.nativeUi().cstring(Pc1ResourceLiterals.GRAPHICS_EXTENSION),new byte[]{'.','c','r','x'}),"graphics path arguments read actual writable PE bytes");
        globals.pc1.nativeMemory().writeAddress(Pc1ResourceLiterals.GRAPHICS_CATEGORY,'X');
        int[] effectNames={Pc1ResourceLiterals.SACRA02,Pc1ResourceLiterals.SACRA01,Pc1ResourceLiterals.YUKI01,Pc1ResourceLiterals.RAINCHIP};
        String[] expectedNames={"system\\SACRA02","system\\SACRA01","system\\YUKI01","system\\RAINCHIP"};
        for(int n=0;n<effectNames.length;n++){check(Arrays.equals(globals.pc1.nativeUi().cstring(effectNames[n]),expectedNames[n].getBytes(Binary.SJIS)),"actual writable PE effect resource name"+n);globals.pc1.nativeMemory().writeAddress(effectNames[n]+15,0xd0+n);}
        byte[] globalsSave=rng.vm.snapshot();pointerCopy.vm.restore(globalsSave);check(pointerCopy.host.read(99,0)==0x89abcdef&&pointerCopy.host.read(99,4)==0x80000001&&pointerCopy.host.read(99,254)==0x12345678&&Arrays.equals(globalsSave,pointerCopy.vm.snapshot()),"all graphics/VM aliases survive one canonical owner restore");
        check(pointerCopy.host.pc1.nativeMemory().readAddress(Pc1ResourceLiterals.GRAPHICS_CATEGORY)=='X',"modified native graphics category roundtrip");
        for(int n=0;n<effectNames.length;n++)check(pointerCopy.host.pc1.nativeMemory().readAddress(effectNames[n]+15)==0xd0+n,"effect literal stale alignment byte roundtrip"+n);
        int[] presentation={0x430284,0xbb8414,0x43d410,0xbb84e4,0x9cca8c,0xbb8548,0xbb8458};
        int[] defaults={40,0,-1,0,0,0,0};
        for(int n=0;n<presentation.length;n++){check(globals.pc1.drawMemory().readInt(presentation[n])==defaults[n],"presentation PE/BSS seed"+n);globals.pc1.drawMemory().writeInt(presentation[n],0x89abcd00+n);}
        int[] effectAddresses={Pc1Effects.PARTICLES,Pc1Effects.PARTICLES+4996,Pc1Effects.RAIN_LEVEL,Pc1Effects.RECTANGLES,Pc1Effects.RECTANGLES+3996,Pc1Effects.RAIN,Pc1Effects.RAIN+1276,Pc1Effects.RECT_BANK,Pc1Effects.TOGGLE,Pc1Effects.SPRITE_HANDLE,Pc1Effects.RAIN_HANDLE};
        for(int n=0;n<effectAddresses.length;n++){check(globals.pc1.effectsMemory().readInt(effectAddresses[n])==(n>=9?-1:0),"effect PE/BSS seed"+n);globals.pc1.drawMemory().writeInt(effectAddresses[n],0x76543200+n);check(globals.pc1.effectsMemory().readInt(effectAddresses[n])==0x76543200+n,"effect view borrows canonical bus"+n);}
        byte[] effectSave=rng.vm.snapshot();pointerCopy.vm.restore(effectSave);
        for(int n=0;n<presentation.length;n++)check(pointerCopy.host.pc1.drawMemory().readInt(presentation[n])==0x89abcd00+n,"presentation rawDWORD roundtrip"+n);
        for(int n=0;n<effectAddresses.length;n++)check(pointerCopy.host.pc1.effectsMemory().readInt(effectAddresses[n])==0x76543200+n,"effect stale record/rectangle/handle roundtrip"+n);
        check(Arrays.equals(effectSave,pointerCopy.vm.snapshot()),"presentation/effects canonical owners serialize exactly once");
        int[] events={0xbb842c,0xbb840c,0xbb8410,0x94c6a4,0x94c670,0x94c698,0x94c6a8,0x94bdb4,0x94c66c,0x94c634,0x94c638,0x94c688,0x94c68c,0x94c694,0x94c678,0x94c67c,0x94c620,0x94c690,0x94c69c,0x94c6a0,0x94c6b0,0x94c6bc};
        for(int n=0;n<events.length;n++){check(globals.pc1.drawMemory().readInt(events[n])==0,"event global PE zero"+n);globals.pc1.drawMemory().writeInt(events[n],0x80000100+n);}
        globals.pc1.drawMemory().writeInt(0x43d414,0x87654321);check(globals.pc1.lastYieldOpcode()==0x87654321,"43D414 and dispatcher last-yield use same full rawDWORD");
        byte[] eventsSave=rng.vm.snapshot();pointerCopy.vm.restore(eventsSave);
        for(int n=0;n<events.length;n++)check(pointerCopy.host.pc1.drawMemory().readInt(events[n])==0x80000100+n,"raw event token/gate roundtrip"+n);
        check(pointerCopy.host.pc1.lastYieldOpcode()==0x87654321&&Arrays.equals(eventsSave,pointerCopy.vm.snapshot()),"raw last-yield alias roundtrip has no portable enum admission");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" production producer integration checks");}
}
