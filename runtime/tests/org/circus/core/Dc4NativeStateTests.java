package org.circus.core;

import java.io.*;
import java.util.*;

/** Checks skipped recording, delayed playback after restore and native registry ownership. */
public final class Dc4NativeStateTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static Dc4Host host(){Dc4Host h=new Dc4Host(null,TextCodec.identity(),true);h.attach(new Variables(h,Variables.Model.BYTE_WRAP));return h;}
    private static void word(ByteArrayOutputStream b,int n){b.write(n);b.write(n>>>8);}
    private static void command(ByteArrayOutputStream b,int op,int... a){b.write(op);for(int n:a)word(b,n);}
    private static MesScript paletteMacro()throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,(byte)0xb6,(byte)0xab,0});
        command(b,0xb0,0,0,0,0);command(b,0xb0,255,0,0,0);command(b,0x90,5,12,23,34);command(b,0xb0,0,0,0,0);
        b.write(new byte[]{0,0,0});return new MesScript("macro-fixture",b.toByteArray(),Profiles.get("dc4ph-abb6"));
    }
    public static int run()throws Exception{
        checks=0;Dc4Host h=host();Dc4Macros m=new Dc4Macros();MesScript script=paletteMacro();int original=h.ui.palette[5];
        int pc=script.firstOffset;for(int i=0;i<4;i++){MesScript.Instruction in=script.instruction(pc);check(m.intercept(script,in)==in.next,"Native recording consumes fetched record without running handler");pc=in.next;}
        check(h.ui.palette[5]==original&&!m.recording()&&m.recordedCount(0)==1,"Recorded palette operation has no live effect");
        byte[] clean=m.parseBody("A@t2@d1B".getBytes(Binary.SJIS),0);check(new String(clean,Binary.SJIS).equals("AB"),"Macro consumes its explicit delay exactly once");
        m.elapse(h,199);check(h.ui.palette[5]==original&&m.pendingCount()==1,"Macro waits for its own scheduled time");
        ByteArrayOutputStream frozen=new ByteArrayOutputStream();m.save(new DataOutputStream(frozen));Dc4Macros restored=new Dc4Macros();restored.load(new DataInputStream(new ByteArrayInputStream(frozen.toByteArray())));
        restored.elapse(h,1);check(h.ui.palette[5]==0xff0c1722&&restored.pendingCount()==0,"Restored macro executes at remaining one millisecond, using group0 for @d1");
        h.ui.palette[5]=original;restored.elapse(h,500);check(h.ui.palette[5]==original,"Completed macro is not played twice");
        pc=script.firstOffset;for(int i=0;i<4;i++){MesScript.Instruction in=script.instruction(pc);restored.intercept(script,in);pc=in.next;}check(restored.recordedCount(0)==1,"A new native text phase starts recording at slot0 and drops the previous phase bank");
        restored.parseBody(new byte[]{'@','d','1'},0);restored.beforePublish(h);check(h.ui.palette[5]==0xff0c1722,"Second native phase replays from its first slot without retaining a gap from prior recording");
        h.ui.palette[5]=original;restored.parseBody(new byte[]{'@','d','1'},0);restored.beforePublish(h);check(h.ui.palette[5]==original&&restored.recordedCount(0)==0,"A following body without recording cannot replay a previous native phase bank");
        try{restored.parseBody("@d0".getBytes(Binary.SJIS),0);throw new AssertionError("Unreviewed zero trigger accepted");}catch(IOException expected){check(true,"Unreviewed VM recursion trigger is explicit");}

        Dc4Speech speech=new Dc4Speech();speech.command(h,"ph.actor.lip_name",new int[0],"so".getBytes(Binary.SJIS));check(speech.regionCount(0)==0,"Uninitialized AE registers gate47 without fabricated positions");
        speech.command(h,"ph.actor.lip_position",new int[]{0,600,140,255},new byte[0]);speech.command(h,"ph.actor.lip_name",new int[0],"so".getBytes(Binary.SJIS));
        speech.command(h,"ph.actor.lip_position",new int[]{0,601,141,255},new byte[0]);speech.command(h,"ph.actor.lip_name",new int[0],"SO".getBytes(Binary.SJIS));check(speech.regionCount(0)==1&&Arrays.equals(speech.region(0,"so"),new int[]{601,141}),"Native uppercase name updates an existing position");
        frozen.reset();speech.save(new DataOutputStream(frozen));Dc4Speech speechRestored=new Dc4Speech();speechRestored.load(new DataInputStream(new ByteArrayInputStream(frozen.toByteArray())));check(Arrays.equals(speechRestored.region(0,"SO"),new int[]{601,141}),"Face-position table survives snapshot independently of displayed mouth animation");
        speechRestored.command(h,"ph.comment.text",new int[0],"コメント".getBytes(Binary.SJIS));check(speechRestored.commentCount()==0,"Absent COMU implementation does not enqueue text");
        speechRestored.configureComment(1);for(int i=0;i<23;i++)speechRestored.command(h,"ph.comment.text",new int[0],new byte[]{65});check(speechRestored.commentCount()==20,"COMU type1 keeps native queue capacity");
        speechRestored.command(h,"ph.audio.pan",new int[]{7,0,0,2},new byte[0]);
        check(speechRestored.command(h,"ph.audio.spatial_register",new int[]{0},new byte[]{'-'})&&speechRestored.audioPan(5)==700,"Native46 clear does not overwrite independent AF voice-buffer panning");
        try{speechRestored.command(h,"ph.audio.spatial_register",new int[]{255},new byte[]{'-'});throw new AssertionError("Unreviewed actor tracking accepted");}catch(IOException expected){check(true,"Unused actor-following registration remains explicit");}
        h.replaceVoice("VOICE/example.PCM",true,true);speechRestored.command(h,"ph.audio.control",new int[]{0,0,0,11},new byte[0]);
        check(speechRestored.voicePaused()&&h.audio[5].isEmpty()&&!h.audioIsActive(5),"A9 pause retains the voice owner and stops the active portable channel");
        frozen.reset();speechRestored.save(new DataOutputStream(frozen));Dc4Speech paused=new Dc4Speech();paused.load(new DataInputStream(new ByteArrayInputStream(frozen.toByteArray())));paused.command(h,"ph.audio.control",new int[]{0,0,0,10},new byte[0]);
        check(!paused.voicePaused()&&h.audio[5].equals("VOICE/example.PCM")&&h.audioLoop[5]&&h.audioIsActive(5),"Paused resource, loop and active state survive snapshot and resume from the portable bridge");
        h.variable(99,187,41);int seekGeneration=h.musicRoom.seekGeneration;paused.command(h,"ph.audio.control",new int[]{0,0,0,0},new byte[0]);
        check(h.variable(99,187)==42&&h.musicRoom.seek==42&&h.musicRoom.seekGeneration==seekGeneration+1,"Native A9 music seek writes its one-percent increment once");
        h.variable(99,187,99);paused.command(h,"ph.audio.control",new int[]{0,0,0,0},new byte[0]);check(h.variable(99,187)==99&&h.musicRoom.seek==99,"Native A9 special99 seek guard does not increment twice");

        Dc4UiCommands ui=new Dc4UiCommands();ui.command(h,"host.command_with_string",new int[]{0,0,40,40},new byte[]{65});ui.command(h,"ui.navigation",new int[]{99,1},new byte[0]);ui.command(h,"host.command_with_string",new int[]{10,10,50,50},new byte[]{66});ui.command(h,"ui.navigation",new int[]{99,0},new byte[0]);check(h.ui.current.buttons.size()==1&&h.ui.current.buttons.get(0).action.equals("A"),"PH construction target switch preserves each panel");
        ui.command(h,"ui.resource",new int[]{5},"sixth-sheet".getBytes(Binary.SJIS));check(h.scene.slots[42].resource.equals("sixth-sheet"),"Sixth PH UI resource maps to native logical slot42");
        ui.command(h,"ui.action",new int[]{2},new byte[]{65});ui.command(h,"ui.action",new int[]{0},new byte[]{66});check(h.ui.actions[0].equals("B")&&h.ui.actions[2].isEmpty(),"Primary action registration clears its paired common frame action");
        frozen.reset();ui.save(new DataOutputStream(frozen));Dc4UiCommands uiRestored=new Dc4UiCommands();uiRestored.load(new DataInputStream(new ByteArrayInputStream(frozen.toByteArray())));uiRestored.command(h,"ui.navigation",new int[]{99,1},new byte[0]);check(h.ui.current.buttons.size()==1&&h.ui.current.buttons.get(0).action.equals("B"),"Inactive construction panel survives helper snapshot for future commands");
        uiRestored.command(h,"ui.panel",new int[]{0},new byte[0]);uiRestored.command(h,"ui.navigation",new int[]{99,0},new byte[0]);check(h.ui.current.buttons.isEmpty(),"Native panel0 clears inactive construction surfaces as well as the current one");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4NativeStateTests "+run()+" PASS");}
}
