package org.circus.core;

import java.io.*;
import java.util.*;

/** Synthetic save-container bounds; no game files, renderer or audio device. */
public final class DesktopSaveEnvelopeAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Work {void run()throws IOException;}
    private static void reject(Work work,String why)throws IOException{boolean failed=false;try{work.run();}catch(IOException expected){failed=true;}check(failed,why);}
    private static DesktopSave fixture(){DesktopSave s=new DesktopSave();s.state=new byte[]{1,2,3};s.caption="checkpoint\u0000漢字\ud83c\udf38";for(int n=0;n<6;n++){s.audio[n]="track"+n;s.position[n]=n*1000;}s.textElapsed=12;return s;}
    public static void main(String[] args)throws Exception{
        DesktopSave source=fixture(),copy=DesktopSave.decode(source.encode());check(Arrays.equals(source.state,copy.state)&&copy.caption.equals(source.caption),"modified UTF and VM state roundtrip");check(Arrays.equals(source.audio,copy.audio)&&Arrays.equals(source.position,copy.position),"six platform cursors roundtrip");
        DesktopSave invalid=fixture();invalid.state=null;reject(invalid::encode,"missing state rejected before writing");invalid=fixture();invalid.preview=new byte[1024*1024+1];reject(invalid::encode,"oversized preview cannot create an unreadable slot");invalid=fixture();invalid.position[3]=-1;reject(invalid::encode,"negative cursor rejected by writer");invalid=fixture();invalid.textElapsed=-1;reject(invalid::encode,"negative text clock rejected by writer");
        invalid=fixture();char[] text=new char[22000];Arrays.fill(text,'漢');invalid.caption=new String(text);reject(invalid::encode,"modified UTF byte count is bounded, not just characters");
        invalid=fixture();invalid.state=new byte[64*1024*1024];reject(invalid::encode,"file overhead is included in the 64MiB limit before allocation");
        System.out.println("DesktopSaveEnvelopeAudit PASS "+checks+"; synthetic bounds and modified UTF");
    }
}
