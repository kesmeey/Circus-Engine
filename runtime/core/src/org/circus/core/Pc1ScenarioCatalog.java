package org.circus.core;
import java.io.IOException;

/** Actual41AEA0 side effects over the canonical catalog/frame/settings bus.
 *Lookup and raw label bytes are never replaced by a Unicode display cache. */
public final class Pc1ScenarioCatalog {
    public static final int FRAME_LABEL=0x9da268,SCENE_SEEN=0x454fac;
    private final Pc1CatalogMemory memory;
    public Pc1ScenarioCatalog(Pc1CatalogMemory memory)throws IOException{if(memory==null)throw new IOException("PC1 scenario catalog owner missing");this.memory=memory;}
    /** Caller proves this logical C source, excluding optional final NUL.
     *The first-dot local copy precedes even the frame-label head clear. */
    public int prepare(byte[] source)throws IOException{
        if(source==null)throw new IOException("PC1 scenario source missing");int n=0;while(n<source.length&&source[n]!=0&&source[n]!='.')n++;if(n>=260)throw new IOException("PC1 scenario lookup local260 overflow");
        memory.write(FRAME_LABEL,0);int index=memory.findScenario(source);if(index<0)return 0;
        int at=memory.address(Pc1CatalogMemory.Table.SCENARIO,index,128),length=0;while(memory.read(at+length)!=0){if(++length>=1024*1024)throw new IOException("PC1 scenario label source exceeds known host scan bound");}
        int seen=memory.read(SCENE_SEEN+index),size=length+1,words=size>>>2;
        // REP MOVSD loads a complete DWORD before its stores. A settings
        //callback during the preceding seen read may have changed this source.
        for(int word=0;word<words;word++){int offset=word*4,value=memory.dword(at+offset);memory.dword(FRAME_LABEL+offset,value);}
        for(int offset=words*4;offset<size;offset++)memory.write(FRAME_LABEL+offset,memory.read(at+offset));return seen;
    }
}
