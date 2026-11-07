package org.circus.core;

import java.io.IOException;

/** Current401EE0 namedfront and402130 rectcapture, on one live image pool.
 * Bytes/padding/guard lifetime belong to its real allocationleases. */
public final class Pc1Capture {
    private final Pc1NativeMemory bus;private final Pc1Compositor compositor;private int depth;
    public Pc1Capture(Pc1NativeMemory bus,Pc1Compositor compositor)throws IOException{if(bus==null||compositor==null)throw new IOException("PC1 capture requires canonical bus and compositor");this.bus=bus;this.compositor=compositor;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 capture snapshot during native locals/allocation");}
    private int d(int at)throws IOException{int value=0;for(int n=0;n<4;n++)value|=bus.readAddress(at+n)<<(n*8);return value;}
    private void d(int at,int value)throws IOException{for(int n=0;n<4;n++)bus.writeAddress(at+n,value>>>(n*8));}
    private int length(int at)throws IOException{for(int n=0;n<1024*1024;n++)if(bus.readAddress(at+n)==0)return n;throw new IOException("PC1 capture name exceeds known host scan bound");}
    /**401EE0 allocates before reading/copying the label, preserves its stale
     *name suffix, copiesfront rawstride×height andALWAYS resetscurrentdirty. */
    public int frontNamed(int rawLabel)throws IOException{
        depth++;try{
            int format=d(Pc1ImagePool.BASE+20),height=d(Pc1ImagePool.BASE+16),width=d(Pc1ImagePool.BASE+12),handle=compositor.pool().allocate(width,height,format,0);
            if(handle!=-1){int count=length(rawLabel)+1,destination=Pc1ImagePool.BASE+(handle<<7)+24,n=0;for(;n+4<=count;n+=4)d(destination+n,d(rawLabel+n));for(;n<count;n++)bus.writeAddress(destination+n,bus.readAddress(rawLabel+n));
                int stride=d(Pc1ImagePool.BASE+8);Pc1HeapLease.Storage to=compositor.pool().pixelStorage(handle);int currentHeight=d(Pc1ImagePool.BASE+16);long bytes=(stride*currentHeight)&0xffffffffL;Pc1HeapLease.Storage from=compositor.pool().pixelStorage(0);
                for(long at=0;at<bytes;){int offset=(int)at;if(at+4<=bytes){int word=0;for(int k=0;k<4;k++)word|=from.read(offset+k)<<(k*8);for(int k=0;k<4;k++)to.write(offset+k,word>>>(k*8)&255);at+=4;}else{to.write(offset,from.read(offset));at++;}}
            }
            compositor.resetDirty();return handle;
        }finally{depth--;}
    }
    /**402130 allocation precedes scan/copy. Success copies FRONT0 and ends
     *at target0; failure leaves the previous target/clip exactly unchanged. */
    public int rect(int left,int top,int right,int bottom)throws IOException{depth++;try{int handle=compositor.pool().allocate(right-left,bottom-top,0,0);if(handle!=-1){compositor.target(handle);compositor.copy(-left,-top,0);compositor.target(0);}return handle;}finally{depth--;}}
}
