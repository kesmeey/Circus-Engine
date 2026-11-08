package org.circus.core;

import java.io.*;
import java.util.*;

/** Immutable MES code, exact byte labels and physical boundaries. No execution. */
public final class MesScript {
    public static final class Record {
        public final int offset, end, opcode;
        public final int[] args;
        public final byte[] bytes;
        public final boolean terminalFragment;
        public final int payloadOwner;
        Record(int off,int end,int op,int[] args,byte[] bytes){
            this(off,end,op,args,bytes,-1);
        }
        Record(int off,int end,int op,int[] args,byte[] bytes,int payloadOwner){
            this.offset=off;this.end=end;this.opcode=op;this.args=args;this.bytes=bytes;
            this.terminalFragment=payloadOwner<0&&op==0&&end-off<3;
            this.payloadOwner=payloadOwner;
        }
    }
    /** One native fetch, including at most one following extension record. */
    public static final class Instruction {
        public final Record record;
        public final Record stringPayload;
        public final int[] args;
        public final int next;
        Instruction(Record record,int[] args,int next,Record stringPayload){this.record=record;this.args=args;this.next=next;this.stringPayload=stringPayload;}
    }
    public final Profile profile;
    public final String name, sha256;
    public final int version,versionOffset,firstOffset;
    public final long[] headerOffsets;
    public final int[] headerMetadata;
    private final byte[] data;
    private final Pc1CodeBuffer codeBuffer;
    private final MesRecordDecoder.Bytes bytes;
    private final long codeGeneration;
    private final String headerSha;
    private boolean staticIndexComplete=true;
    private final Map<Integer,Record> records=new LinkedHashMap<>();
    private final Map<String,Integer> labels=new HashMap<>();
    public MesScript(String name,byte[] input,Profile profile)throws IOException {
        if(input==null)throw new IOException("MES source is missing");
        if(input.length>64*1024*1024)throw new IOException("MES exceeds 64 MiB");
        this.name=name;this.profile=profile;data=input.clone();sha256=Binary.sha256(data);
        codeBuffer=null;codeGeneration=0;
        long count=Binary.u32(data,0);if(count>1000000)throw new IOException("MES header count limit");
        long vo=4+count*profile.headerStride;Binary.range(vo,profile.versionBytes,data.length);versionOffset=(int)vo;
        int ver=profile.versionBytes==1?Binary.u8(data,versionOffset):Binary.u16(data,versionOffset);
        version=ver;
        if(!profile.acceptsVersion(ver))throw new IOException("MES version does not match "+profile.id);
        headerOffsets=new long[(int)count];headerMetadata=new int[(int)count];
        for(int i=0;i<count;i++){headerOffsets[i]=Binary.u32(data,4+4*i);if(profile.headerStride==6)headerMetadata[i]=Binary.u16(data,4+(int)count*4+2*i);}
        firstOffset=versionOffset+profile.versionBytes;
        headerSha=Binary.sha256(Arrays.copyOf(data,versionOffset));bytes=access();
        int indexLimit=profile.id.equals("dcpc-2063")?(int)Math.min((long)data.length,(long)versionOffset+Pc1CodeBuffer.CAPACITY):data.length;
        for(int p=firstOffset;p<indexLimit;){try{
            Record r=MesRecordDecoder.read(data,p,profile);records.put(p,r);if(r.opcode==profile.label)labels.putIfAbsent(Binary.hex(r.bytes),p);p=r.end;
            if(profile.operation(r.opcode).equals("variable.sum_list")){
                // Sum terms are nine raw bytes regardless of their first byte.
                // They must not accidentally become another handler's owner.
                while(p<data.length&&Binary.u8(data,p)!=profile.companion){
                    Record term=MesRecordDecoder.payload(data,p,r.offset);records.put(p,term);p=term.end;
                }
            }else if(profile.ownsExtension(r.opcode)){
                Record payload=MesRecordDecoder.payload(data,p,r.offset);records.put(p,payload);p=payload.end;
            }else if(profile.ownsStringPayload(r.opcode)){
                Record payload=MesRecordDecoder.stringPayload(data,p,r.offset);records.put(p,payload);p=payload.end;
            }
            if(profile.id.equals("dcpc-2063")&&r.opcode==0)break; // 41265F terminates; bytes beyond stop are not instructions.
            // FL422B1A stops fetching at00; its named/block scanners also stop
            // there. Non-truncating translation installs can retain arbitrary
            // old bytes beyond this STOP, including complete old prose. Keep
            // the physical bytes/hash for snapshots, but never index that tail.
            if(profile.id.equals("dc2fl-9c69")&&r.opcode==0&&r.end-r.offset==3){
                for(long entry:headerOffsets)if((long)versionOffset+entry>=p)throw new IOException("Unreviewed FL header entry beyond native STOP");
                if(p<data.length)staticIndexComplete=false;break;
            }
            // Original PC2 and TY SAVE/LOAD contain exactly61 64 after their terminal STOP.
            if((profile.id.equals("dc2pc-5769")||profile.id.equals("dc2ty-5f69"))&&r.opcode==0&&data.length-p==2&&data[p]==0x61&&data[p+1]==0x64)break;
        }catch(IOException incomplete){if(!profile.id.equals("dcpc-2063"))throw incomplete;staticIndexComplete=false;break;}}
        if(records.isEmpty()&&!profile.id.equals("dcpc-2063"))throw new IOException("Empty MES code");
    }
    private MesScript(MesScript source,Pc1CodeBuffer buffer)throws IOException{
        buffer.requireSource(source);name=source.name;sha256=source.sha256;profile=source.profile;version=source.version;versionOffset=source.versionOffset;firstOffset=source.firstOffset;headerOffsets=source.headerOffsets.clone();headerMetadata=source.headerMetadata.clone();data=source.data;headerSha=source.headerSha;codeBuffer=buffer;codeGeneration=buffer.generation();bytes=access();records.putAll(source.records);labels.putAll(source.labels);staticIndexComplete=source.staticIndexComplete;
    }
    /** Same immutable original header/hash, dynamic code bytes from the one
     *live native BSS owner. No decoded-record cache is execution authority. */
    public MesScript nativeView(Pc1CodeBuffer buffer)throws IOException{if(!profile.id.equals("dcpc-2063")||buffer==null)throw new IOException("Native code view requires PC1 code owner");return new MesScript(this,buffer);}
    public boolean nativeCodeView(){return codeBuffer!=null;}
    public boolean staticIndexComplete(){return staticIndexComplete;}
    public int originalLength(){return data.length;}
    public String originalHeaderSha256(){return headerSha;}
    public byte[] originalHeaderBytes(){return Arrays.copyOf(data,versionOffset);}
    public byte[] originalCodePrefix(int limit)throws IOException{if(limit<0)throw new IOException("Negative original-code prefix");return Arrays.copyOfRange(data,versionOffset,(int)Math.min((long)data.length,(long)versionOffset+limit));}
    private void requireCurrentCode()throws IOException{if(codeBuffer!=null)codeBuffer.requireView(name,sha256,headerSha,codeGeneration);}
    private MesRecordDecoder.Bytes access(){return new MesRecordDecoder.Bytes(){
        public int length(){return codeBuffer==null?data.length:versionOffset+Pc1CodeBuffer.CAPACITY;}
        public int u8(int offset)throws IOException{requireCurrentCode();Binary.range(offset,1,length());return codeBuffer==null||offset<versionOffset?Binary.u8(data,offset):codeBuffer.readU8(offset-versionOffset);}
        public byte[] slice(int start,int end)throws IOException{requireCurrentCode();Binary.range(start,(long)end-start,length());if(codeBuffer==null||end<=versionOffset)return Arrays.copyOfRange(data,start,end);if(start>=versionOffset)return codeBuffer.rawSlice(start-versionOffset,end-start);byte[] result=new byte[end-start];System.arraycopy(data,start,result,0,versionOffset-start);System.arraycopy(codeBuffer.rawSlice(0,end-versionOffset),0,result,versionOffset-start,end-versionOffset);return result;}
    };}
    public Instruction instruction(int offset)throws IOException {
        Record record=fetchRecord(offset);int next=record.end;int[] args=record.args;
        // STOP leaves the native PC at STOP; it must not absorb the next record.
        if(profile.ownsExtension(record.opcode)){
            Record extension=wordPayload(next,record.offset);
            args=Arrays.copyOf(args,args.length+4);
            System.arraycopy(extension.args,0,args,record.args.length,4);next=extension.end;
        }else if(profile.extensionAtFetch&&record.opcode!=0&&next<length()&&rawOpcode(next)==profile.extension){
            Record extension=wordPayload(next,record.offset);args=Arrays.copyOf(args,args.length+4);
            System.arraycopy(extension.args,0,args,record.args.length,4);next=extension.end;
        }
        Record stringPayload=null;
        if(profile.ownsStringPayload(record.opcode)){stringPayload=stringPayload(next,record.offset);next=stringPayload.end;}
        return new Instruction(record,args,next,stringPayload);
    }
    public Record at(int offset)throws IOException {Record r=records.get(offset);if(r==null)throw new IOException("PC is not a record boundary: "+offset);return codeBuffer==null?r:fetchRecord(offset);}
    /** Native execution may enter bytes previously owned by another command. */
    public Record fetchRecord(int offset)throws IOException {
        if(!executableOffset(offset)&&!(profile.id.equals("dcpc-2063")&&offset>=versionOffset&&offset<firstOffset))throw new IOException("PC outside MES code: "+offset);
        if(codeBuffer!=null)return MesRecordDecoder.read(bytes,offset,profile);
        Record indexed=records.get(offset);
        return indexed!=null&&indexed.payloadOwner<0?indexed:MesRecordDecoder.read(data,offset,profile);
    }
    public Record wordPayload(int offset,int owner)throws IOException{return MesRecordDecoder.payload(bytes,offset,owner);}
    public Record stringPayload(int offset,int owner)throws IOException{return MesRecordDecoder.stringPayload(bytes,offset,owner);}
    public int rawOpcode(int offset)throws IOException{return bytes.u8(offset);}
    public boolean executableOffset(int offset){return offset>=firstOffset&&offset<length();}
    public boolean boundary(int offset){return records.containsKey(offset);}
    public Collection<Record> records(){return Collections.unmodifiableCollection(records.values());}
    public Integer label(byte[] name){if(codeBuffer!=null)try{return requiredLabel(name);}catch(IOException invalid){throw new IllegalStateException("Native label scanner: "+invalid.getMessage(),invalid);}return name.length==0?null:labels.get(Binary.hex(name));}
    /** Older DM named search sign-extends opcodes and stops at the first zero record. */
    public Integer requiredLabel(byte[] name)throws IOException{
        if(name.length==0)return null;
        for(int pos=firstOffset,steps=0;pos<length()&&++steps<=length();){
            int op=(byte)bytes.u8(pos);if(op==0)return null;
            if(op==profile.label){int end=pos+1;while(end<length()&&bytes.u8(end)!=0)end++;if(end==length())throw new IOException("Unterminated native label");if(Arrays.equals(name,bytes.slice(pos+1,end)))return pos;}
            pos=MesRecordDecoder.end(bytes,pos,profile,true);
        }
        return null;
    }
    public int length(){return bytes.length();}
    public byte[] nativeText(Record r)throws IOException {
        byte[] out=r.bytes.clone();
        if(r.opcode>=profile.encodedStart && r.opcode<profile.wordsStart){
            if(!profile.textVerified)throw new IOException("Text postprocessing unverified for "+profile.id);
            for(int i=0;i<out.length;i++){int v=((out[i]&255)+32)&255;out[i]=(byte)(profile.dollarToSpace&&v==36?32:v);}
        }
        return out;
    }
    public int scanBlock(int start,int id)throws IOException {
        int p=start,steps=0;
        while(p<length() && ++steps<=length()){
            int op=profile.signedBlockScan?(byte)bytes.u8(p):bytes.u8(p);
            if(op==profile.block && p+3<=length() && (byte)bytes.u8(p+2)==id){int target=p+3;if(!executableOffset(target))throw new IOException("Native block scan leaves the script");return target;}
            if(op==0)throw new IOException("Block marker missing before stop");
            p=MesRecordDecoder.end(bytes,p,profile,profile.signedBlockScan);
        }
        throw new IOException("Block scan exceeds script");
    }
}
