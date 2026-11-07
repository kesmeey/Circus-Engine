package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Actual41ABF0 synchronous control order. All file/path operations remain
 *required callbacks. Returned read counts do not select native branches;
 *only bytes actually written to canonical destinations do. */
public final class Pc1CatalogLoader {
    public interface NativeIo {
        /**40E5A0 with addressed writable literals, followed by open8000.
         *Null is native-1. The provider must not guess another root/file. */
        Opened open(int category,int name,int extension,int pathCapacity)throws IOException;
    }
    public interface Opened {
        int descriptor();
        /** requested is the raw32-bit unsigned CRT count argument, including
         *multiplication wrap. Emit exactly the actually written prefix. */
        int read(Destination destination,int requested)throws IOException;
        int close()throws IOException;
    }
    /** An uninitialized native stack DWORD is not a zero-filled BSS field. */
    public static final class LocalDword {
        private final byte[] bytes=new byte[4];private int known;
        private LocalDword(byte[] prefix)throws IOException{if(prefix==null||prefix.length>4)throw new IOException("PC1 NAMES local known prefix");System.arraycopy(prefix,0,bytes,0,prefix.length);known=prefix.length;}
        public static LocalDword unknown()throws IOException{return new LocalDword(new byte[0]);}
        public static LocalDword knownPrefix(byte[] prefix)throws IOException{return new LocalDword(prefix);}
        public byte[] knownBytes(){return Arrays.copyOf(bytes,known);}
        public int value()throws IOException{if(known!=4)throw new IOException("PC1 NAMES first count reads unknown native stack DWORD suffix after short read");int value=0;for(int n=0;n<4;n++)value|=(bytes[n]&255)<<(n*8);return value;}
        private void write(int offset,int value)throws IOException{if(offset<0||offset>=4)throw new IOException("PC1 NAMES first read reaches unknown neighboring stack");bytes[offset]=(byte)value;known=Math.max(known,offset+1);}
    }
    public static final class Destination {
        private final Pc1CatalogMemory memory;private final LocalDword local;private final int address;private final long requested;private int written;
        private Destination(Pc1CatalogMemory memory,LocalDword local,int address,int requested){this.memory=memory;this.local=local;this.address=address;this.requested=requested&0xffffffffL;}
        public boolean local(){return local!=null;}
        /** Not a fabricated stack pointer: only global destinations expose a
         *native address. Callers must distinguish the local count explicitly. */
        public int address()throws IOException{if(local())throw new IOException("PC1 NAMES local count has no claimed native stack address");return address;}
        public int written(){return written;}
        public void write(int offset,int value)throws IOException{if(offset!=written||offset<0||(offset&0xffffffffL)>=requested)throw new IOException("PC1 NAMES IO did not emit an ordered requested prefix");if(local!=null)local.write(offset,value);else memory.write(address+offset,value);written++;}
    }
    private final Pc1CatalogMemory memory;private final NativeIo io;
    public Pc1CatalogLoader(Pc1CatalogMemory memory,NativeIo io)throws IOException{if(memory==null||io==null)throw new IOException("PC1 NAMES loader requires canonical memory/native IO");this.memory=memory;this.io=io;}
    private void read(Opened file,LocalDword local,int address,int count)throws IOException{file.read(new Destination(memory,local,address,count),count);}
    public int load(LocalDword local)throws IOException{
        if(local==null)throw new IOException("PC1 NAMES requires explicit initial stack-count knowledge");memory.begin();
        try{
            Opened file=io.open(Pc1CatalogMemory.CATEGORY,Pc1CatalogMemory.NAME,Pc1CatalogMemory.EXTENSION,260);if(file==null)return -1;
            try{
                read(file,local,0,4);if(local.value()!=6)return -1;
                read(file,null,Pc1CatalogMemory.Table.SYSTEM.address,768);
                read(file,null,Pc1CatalogMemory.MUSIC_COUNT,4);int count=memory.dword(Pc1CatalogMemory.MUSIC_COUNT);if(count>=50)return -1;
                read(file,null,Pc1CatalogMemory.Table.MUSIC.address,count*192);
                read(file,null,Pc1CatalogMemory.SOUND_COUNT,4);count=memory.dword(Pc1CatalogMemory.SOUND_COUNT);if(count>=200)return -1;
                read(file,null,Pc1CatalogMemory.Table.SOUND.address,count*132);
                read(file,null,Pc1CatalogMemory.CG_COUNT,4);count=memory.dword(Pc1CatalogMemory.CG_COUNT);if(count>=512)return -1;
                read(file,null,Pc1CatalogMemory.Table.CG.address,count<<7);
                read(file,null,Pc1CatalogMemory.SCENARIO_COUNT,4);count=memory.dword(Pc1CatalogMemory.SCENARIO_COUNT);if(count>=1024)return -1;
                read(file,null,Pc1CatalogMemory.Table.SCENARIO.address,count*188);return 0;
            }finally{file.close();}
        }finally{memory.end();}
    }
}
