package org.circus.core;

import java.io.IOException;

/** Raw PC1 fixed-frame copies only. No resource loading, host/VM callbacks,
 *UI tables, external selection, palette, persistence, text replay or audio.
 *Native pointer/PC fields are copied without caller/slot projection or parsing.
 *All8004 bytes, including unnamed fields and stale cells, remain authoritative. */
public final class Pc1FrameCopies {
    private Pc1FrameCopies(){}
    /**412160: after40C1C0 saves its external UI owners, copy live9D8588 into
     *system9D5E48,2001 DWORDs. Call before effects reset and callee entry changes.
     *The consumed returnPC/external9B9F14 remains the surrounding caller's job. */
    public static Pc1NativeFrame captureSystem(Pc1NativeFrame live)throws IOException{require(live,"live");return live.copy();}
    /**1B/native input event: full current image, including the captured ordinary
     *1000 bytes. Caller chooses the exact producer cursor; this method never
     *moves PC past a1B owned string or substitutes a system-return cursor. */
    public static Pc1NativeFrame captureCandidate(Pc1NativeFrame live)throws IOException{require(live,"live");return live.copy();}
    /** Captured-frame1C branch41503B..415065, in its actual native order:
     *1) live ordinary1000 -> suspended ordinary1000 (MUTATES suspended owner);
     *2) full suspended8004 -> live8004. No ordinary setters/shadow/history run.
     *Call only for the native43D42C!=-1 whole-frame branch. Its script-only
     *alternative does not perform these copies and is not modeled here.
     *Resource release precedes this method; caller mode0 reload, pending-count
     *clear, resource/UI rebuild and external cursor writes follow it. */
    public static void restoreSystem(Pc1NativeFrame live,Pc1NativeFrame suspended)throws IOException{
        distinct(live,suspended);byte[] currentOrdinary=live.rawSlice(Pc1FrameLayout.ORDINARY_OFFSET,Pc1FrameLayout.ORDINARY_BYTES);
        suspended.writeBytes(Pc1FrameLayout.ORDINARY_OFFSET,currentOrdinary);live.copyFrom(suspended);
    }
    /** Raw prepared/loaded candidate image copy, WITH its captured ordinary.
     *This is not41A620's complete loader: no checkpoint-derived PC/wait/music
     *post-copy projection, resource reconstruction or timing is performed. */
    public static void restoreCandidate(Pc1NativeFrame live,Pc1NativeFrame candidate)throws IOException{distinct(live,candidate);live.copyFrom(candidate);}
    private static void require(Pc1NativeFrame frame,String role)throws IOException{if(frame==null)throw new IOException("PC1 frame copy requires "+role+" owner");}
    private static void distinct(Pc1NativeFrame live,Pc1NativeFrame source)throws IOException{require(live,"live");require(source,"source");if(live==source)throw new IOException("PC1 native live and saved fixed frames require distinct owners");}
}
