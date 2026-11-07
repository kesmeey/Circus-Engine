package org.circus.core;

import java.io.IOException;

/** Addressed search/replacement shared by the verified512/256 native caches.
 *Character decoding, cache bytes, font state and projection remain external
 *owners. No copied entry masks repair a spill into a neighboring owner. */
public final class NativeGlyphCache {
    public interface Memory {int readByte(int address)throws IOException;void writeByte(int address,int value)throws IOException;}
    public interface Projection {void project(int destination,int requestedSize)throws IOException;}
    public static final class Layout {
        public final int variable,fixed,variableCursor,fixedCursor;
        public Layout(int variable,int fixed,int variableCursor,int fixedCursor){this.variable=variable;this.fixed=fixed;this.variableCursor=variableCursor;this.fixedCursor=fixedCursor;}
    }
    public static final class Lookup {
        public final int address;
        public final boolean hit;
        private Lookup(int address,boolean hit){this.address=address;this.hit=hit;}
    }
    private final Memory memory;
    private final Layout layout;
    private boolean busy;
    public NativeGlyphCache(Memory memory,Layout layout)throws IOException{if(memory==null||layout==null)throw new IOException("Native glyph cache memory/layout missing");this.memory=memory;this.layout=layout;}
    private int g(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=memory.readByte(address+n)<<(n*8);return value;}
    private void g(int address,int value)throws IOException{for(int n=0;n<4;n++)memory.readByte(address+n);for(int n=0;n<4;n++)memory.writeByte(address+n,value>>>(n*8));}
    public boolean busy(){return busy;}
    public Lookup lookup(int key,int requestedSize,Projection projection)throws IOException{
        if(busy)throw new IOException("Native glyph cache reentry");busy=true;
        try{
            boolean fixed=requestedSize==28;int count=fixed?256:512,stride=fixed?788:1608,base=fixed?layout.fixed:layout.variable,index=0;
            for(;index<count;index++){int at=base+index*stride,stored=g(at);if(stored==key&&(fixed||g(at+4)==requestedSize))return new Lookup(at+(fixed?4:8),true);if(stored==0)break;}
            if(index==count){int cursor=fixed?layout.fixedCursor:layout.variableCursor;index=g(cursor);int next=index+1;g(cursor,next>=count?0:next);}
            int address=base+index*stride,destination=address+(fixed?4:8);
            if(projection==null)throw new IOException("Native glyph cache actual projection/provider missing");projection.project(destination,requestedSize);g(address,key);if(!fixed)g(address+4,requestedSize);return new Lookup(destination,false);
        }finally{busy=false;}
    }
}
