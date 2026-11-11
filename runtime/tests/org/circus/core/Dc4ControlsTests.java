package org.circus.core;

import java.io.*;

/** PH4332B0 direct UI preference/replay semantics and legacy protections. */
public final class Dc4ControlsTests {
    private static int n;
    private static void check(boolean ok,String why){n++;if(!ok)throw new AssertionError(why);}
    public static int run()throws Exception{
        n=0;Dc3State ph=new Dc3State("dc4ph-abb6",true);check(ph.read(99,227)==1,"PH native controls initially expanded");
        ph.write(99,227,0);check(ph.read(99,227)==1,"The absent MES227 setter remains read-only");
        ph.togglePhMessageControls();check(ph.read(99,227)==0,"Native input directly selects the collapsed template");
        byte[] settings=ph.settingsSnapshot();ph.togglePhMessageControls();check(ph.read(99,227)==1,"Second native click expands the original controls");ph.restoreSettings(settings);check(ph.read(99,227)==0,"Portable preferences retain the native control-bar selection");
        Dc3State dd=new Dc3State("dc3dd-a5a8",true);int before=dd.read(99,227);try{dd.togglePhMessageControls();throw new AssertionError("PH input changed a legacy state");}catch(IOException expected){check(dd.read(99,227)==before,"PH direct toggle cannot change a legacy profile");}
        Dc3Host h=new Dc3Host(null,TextCodec.identity(),true);h.history.append("owned","owned body","owned/first","owned",1);int generation=h.audioGeneration[5];h.replayVoice("owned/first");
        check(h.audio[5].equals("owned/first")&&!h.audioLoop[5]&&h.audioIsActive(5)&&h.audioGeneration[5]==generation+1,"Replay replaces the voice channel immediately without looping");
        check(h.history.entries.size()==1&&h.history.entries.get(0).voice.equals("owned/first")&&h.history.entries.get(0).body.equals("owned body"),"Replay leaves the current history record unchanged");
        h.replayVoice("");check(!h.audioIsActive(5)&&h.audio[5].isEmpty()&&h.audioGeneration[5]==generation+2,"Replay on an unvoiced line stops the prior channel");
        return n;
    }
}
