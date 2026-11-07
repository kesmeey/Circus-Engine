package org.circus.core;

import java.io.*;
import java.util.Arrays;

/** Native9A077C's mutable100000-byte BSS code owner. A successful411670 read
 *overwrites only its prefix. Bytes after that read remain from the prior load.
 *Identity describes the original loaded file/header, not a hash assertion that
 *its writable code prefix still equals the file after native variable aliases. */
public final class Pc1CodeBuffer {
    public static final int BASE=0x9a077c,CAPACITY=100000;
    private static final int FORMAT=0x50314342,VERSION=1; // P1CB, version1
    private final byte[] memory=new byte[CAPACITY];
    private String name="",sourceSha="",headerSha="";
    private int loadedBytes;
    private long generation,revision;
    private Pc1CodeBuffer(){}
    public static Pc1CodeBuffer zeroBss(){return new Pc1CodeBuffer();}
    public int loadedBytes(){return loadedBytes;}
    public String loadedName(){return name;}
    public String sourceSha256(){return sourceSha;}
    public String headerSha256(){return headerSha;}
    public long generation(){return generation;}
    public long revision(){return revision;}
    public boolean loaded(){return !sourceSha.isEmpty();}
    /** Original-file prefix length admitted as identity metadata. Native
     *_read's actual result remains in Pc1ScriptMemory.CODE_READ_RESULT. */
    private void admitIdentity(MesScript original,int prefixLength,String nextHeader)throws IOException{
        if(generation==Long.MAX_VALUE||revision==Long.MAX_VALUE)throw new IOException("PC1 code owner generation exhausted");
        name=original.name;sourceSha=original.sha256;headerSha=nextHeader;loadedBytes=prefixLength;generation++;revision++;
    }
    /** Preflight all source metadata before touching caller code bytes. */
    public void load(MesScript original)throws IOException{
        if(original==null||!original.profile.id.equals("dcpc-2063")||!original.profile.canExecuteVersion(original.version))throw new IOException("PC1 code load requires a native2063 original source");
        Binary.safePath(original.name);
        byte[] prefix=original.originalCodePrefix(CAPACITY);String nextHeader=original.originalHeaderSha256();
        if(prefix.length<2||prefix[0]!=0x63||prefix[1]!=0x20)throw new IOException("PC1 code loader rejects native marker");
        if(generation==Long.MAX_VALUE||revision==Long.MAX_VALUE)throw new IOException("PC1 code owner generation exhausted");
        System.arraycopy(prefix,0,memory,0,prefix.length);admitIdentity(original,prefix.length,nextHeader);
    }
    /** Outer source admission AFTER411670's ordered reads/close/side effects.
     * This changes identity only: every callback mutation, short-read stale
     *tail and raw checkpoint byte stays in its existing owner. Parsed source
     *admission is optional metadata, never a prerequisite of the raw loader.
     * A native-accepted source that this parser cannot represent must keep
     *using raw execution rather than pretending that a fresh parsed load ran. */
    public void adoptSourceIdentity(MesScript original)throws IOException{
        if(original==null||!original.profile.id.equals("dcpc-2063")||!original.profile.canExecuteVersion(original.version))throw new IOException("PC1 identity admission requires a native2063 original source");
        Binary.safePath(original.name);int prefixLength=Math.min(CAPACITY,original.originalLength()-original.versionOffset);String nextHeader=original.originalHeaderSha256();
        if(prefixLength<2)throw new IOException("PC1 source identity lacks its original marker");admitIdentity(original,prefixLength,nextHeader);
    }
    public void requireSource(MesScript original)throws IOException{
        if(original==null||!loaded()||!name.equals(original.name)||!sourceSha.equals(original.sha256)||!headerSha.equals(original.originalHeaderSha256())||loadedBytes!=Math.min(CAPACITY,original.originalLength()-original.versionOffset))throw new IOException("PC1 code buffer original source/header identity mismatch");
    }
    void requireView(String expectedName,String expectedSha,String expectedHeader,long expectedGeneration)throws IOException{
        if(generation!=expectedGeneration||!name.equals(expectedName)||!sourceSha.equals(expectedSha)||!headerSha.equals(expectedHeader))throw new IOException("PC1 code view belongs to a different loaded source generation");
    }
    public int readU8(int offset)throws IOException{return memory[range(offset,1)]&255;}
    public void writeU8(int offset,int value)throws IOException{range(offset,1);bump();memory[offset]=(byte)value;}
    public byte[] rawSlice(int offset,int length)throws IOException{range(offset,length);return Arrays.copyOfRange(memory,offset,offset+length);}
    public void writeBytes(int offset,byte[] raw)throws IOException{if(raw==null)throw new IOException("PC1 code write requires source bytes");range(offset,raw.length);bump();System.arraycopy(raw,0,memory,offset,raw.length);}
    public byte[] bytes(){return memory.clone();}
    public Pc1CodeBuffer copy(){Pc1CodeBuffer next=zeroBss();next.copyAll(this);return next;}
    public void copyFrom(Pc1CodeBuffer source)throws IOException{if(source==null)throw new IOException("PC1 code copy requires an owner");copyAll(source);}
    private void copyAll(Pc1CodeBuffer source){System.arraycopy(source.memory,0,memory,0,CAPACITY);name=source.name;sourceSha=source.sourceSha;headerSha=source.headerSha;loadedBytes=source.loadedBytes;generation=source.generation;revision=source.revision;}
    private void bump()throws IOException{if(revision==Long.MAX_VALUE)throw new IOException("PC1 code owner revision exhausted");revision++;}
    private static int range(int offset,int length)throws IOException{if(offset<0||length<0||(long)offset+length>CAPACITY)throw new IOException("PC1 code access outside known100000-byte BSS owner: "+offset+"+"+length);return offset;}
    public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(VERSION);out.writeUTF(name);out.writeUTF(sourceSha);out.writeUTF(headerSha);out.writeInt(loadedBytes);out.writeLong(generation);out.writeLong(revision);out.write(memory);}
    public static Pc1CodeBuffer load(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 code buffer state format");Pc1CodeBuffer next=zeroBss();next.name=in.readUTF();next.sourceSha=in.readUTF();next.headerSha=in.readUTF();next.loadedBytes=in.readInt();next.generation=in.readLong();next.revision=in.readLong();
        boolean bound=!next.sourceSha.isEmpty();
        if(next.generation<0||next.revision<next.generation||next.loadedBytes<0||next.loadedBytes>CAPACITY)throw new IOException("PC1 code buffer saved counters");
        if(bound){Binary.safePath(next.name);if(!next.sourceSha.matches("[0-9a-f]{64}")||!next.headerSha.matches("[0-9a-f]{64}")||next.loadedBytes<2||next.generation==0)throw new IOException("PC1 code buffer saved source identity");}
        else if(!next.name.isEmpty()||!next.headerSha.isEmpty()||next.loadedBytes!=0||next.generation!=0)throw new IOException("PC1 unbound code buffer identity");
        in.readFully(next.memory);return next;
    }
}
