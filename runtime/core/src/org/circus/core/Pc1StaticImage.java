package org.circus.core;

import java.io.*;
import java.security.*;
import java.util.*;

/** Preferred-address PC1 section bytes left between canonical fixed owners.
 * This is not a loaded Windows process: import/CRT mutation ranges are unknown
 * unless a real owner has already claimed them. No stack or heap is seeded. */
public final class Pc1StaticImage {
    private static final int FORMAT=0x50315349,VERSION=1,PAGE=4096;
    public static final String TARGET_SHA256="7988971ff38f6b0fb2f21827b1ba38c3c7fe5cc824420b179ea9d9a838872057";
    public static final String PHASE="PC1_PREFERRED_SECTIONS_CRT_LOADER_EXCLUDED_V1";
    private static final class Section {
        final long base,length;final int rawOffset,rawLength;final boolean writable;final String name;
        Section(long base,long length,int rawOffset,int rawLength,boolean writable,String name){this.base=base;this.length=length;this.rawOffset=rawOffset;this.rawLength=rawLength;this.writable=writable;this.name=name;}
        boolean contains(long address){return base<=address&&address<base+length;}
    }
    public static final class Exclusion {
        public final int address;public final int length;public final String reason;
        public Exclusion(int address,int length,String reason)throws IOException{if(length<=0||(address&0xffffffffL)+length>0x100000000L||reason==null||reason.length()==0||reason.length()>1024)throw new IOException("PC1 static exclusion range/authority");this.address=address;this.length=length;this.reason=reason;}
        long base(){return address&0xffffffffL;}
        boolean contains(long value){return base()<=value&&value<base()+length;}
    }
    /** Private immutable source clone. Shared RO/BSS seed does not contain
     *module state and cannot be mutated by the caller's original byte array. */
    public static final class Seed {
        private final byte[] file;private final List<Section> sections;private final List<Exclusion> exclusions;private final String sha,identity;private final long base,end;
        private Seed(byte[] input,String expected,String authority,boolean nativeTarget)throws IOException{
            if(input==null||expected==null||authority==null||authority.length()==0)throw new IOException("PC1 immutable PE source and identity required");file=input.clone();sha=hash(file);if(!sha.equals(expected))throw new IOException("PC1 static PE source SHA256 mismatch");identity=(nativeTarget?"PC1_CHS:":"FIXTURE:")+authority+":"+sha;
            if(file.length<0x40||u16(file,0)!=0x5a4d)throw new IOException("PC1 static source is not DOS/PE32");long nt=u32(file,0x3c);if(nt>Integer.MAX_VALUE)throw new IOException("PC1 static PE header offset");int h=(int)nt;if(u32(file,h)!=0x4550||u16(file,h+4)!=0x14c)throw new IOException("PC1 static source requires x86 PE32");int count=u16(file,h+6),optional=u16(file,h+20),o=h+24;if(count<1||count>96||optional<224||u16(file,o)!=0x10b)throw new IOException("PC1 static PE32 section/optional header");range(file,o,optional);base=u32(file,o+28);long image=u32(file,o+56);end=base+image;if(base==0||image<1||image>Integer.MAX_VALUE||end>0x100000000L)throw new IOException("PC1 static preferred image range");if(nativeTarget&&(base!=0x400000||image!=0x7dd000))throw new IOException("PC1 static CHS preferred image identity");int directories=(int)Math.min(u32(file,o+92),16);exclusions=new ArrayList<>();
            for(int index:new int[]{5,9,10,11,13})if(index<directories&&(u32(file,o+96+index*8)!=0||u32(file,o+100+index*8)!=0))throw new IOException("PC1 static unsupported relocation/TLS/load-config/bound/delay mutation directory "+index);
            if(directories>12){long rva=u32(file,o+96+12*8),length=u32(file,o+100+12*8);if((rva==0)!=(length==0)||length>Integer.MAX_VALUE||rva+length>image)throw new IOException("PC1 static IAT directory range");if(length!=0)exclusions.add(new Exclusion((int)(base+rva),(int)length,"Windows loader IAT: hint RVAs are not resolved function pointers"));}
            sections=new ArrayList<>();int table=o+optional;range(file,table,count*40);for(int n=0;n<count;n++){int at=table+n*40;long virtual=u32(file,at+8),rva=u32(file,at+12),raw=u32(file,at+16),offset=u32(file,at+20),flags=u32(file,at+36),length=Math.max(virtual,raw);String name=name(file,at);if(length==0)continue;if(rva==0||length>Integer.MAX_VALUE||rva+length>image||raw>Integer.MAX_VALUE||offset>Integer.MAX_VALUE)throw new IOException("PC1 static section range");range(file,(int)offset,(int)raw);if((flags&0x40000000L)==0)continue;Section section=new Section(base+rva,length,(int)offset,(int)raw,(flags&0x80000000L)!=0,name);for(Section prior:sections)if(overlap(section.base,section.length,prior.base,prior.length))throw new IOException("PC1 static overlapping readable sections");sections.add(section);}
            sections.sort(Comparator.comparingLong(value->value.base));if(sections.isEmpty())throw new IOException("PC1 static PE has no known readable section");
            if(nativeTarget){exclusions.add(new Exclusion(0x43e100,0x4409b0-0x43e100,"CRT initialized-data/locale/runtime mutations; canonical RNG may claim its own bytes"));exclusions.add(new Exclusion(0xbc5da8,0xbc5f58-0xbc5da8,"CRT process/locking globals: entry420DB3 and library writers"));exclusions.add(new Exclusion(0xbdafc4,0xbdb33c-0xbdafc4,"CRT descriptor/process globals: entry/library writers"));}
        }
        public static Seed readPreferredPc1Chs(byte[] originalPe)throws IOException{return new Seed(originalPe,TARGET_SHA256,"read-only original CHS preferred sections",true);}
        /** Package-private, explicitly synthetic PE for portable unit tests. */
        static Seed fixture(byte[] file,String sha,String authority)throws IOException{return new Seed(file,sha,authority,false);}
        public String targetSha256(){return sha;}
        public String identity(){return identity;}
        public int imageBase(){return(int)base;}
        public long imageEnd(){return end;}
        private Section section(long address){for(Section s:sections)if(s.contains(address))return s;return null;}
        private int read(long address)throws IOException{Section section=section(address);if(section==null)throw new IOException("PC1 static address is not in a proved readable section");long offset=address-section.base;return offset<section.rawLength?file[section.rawOffset+(int)offset]&255:0;}
    }
    private static final class Span {final long base,end;final Section section;Span(long base,long end,Section section){this.base=base;this.end=end;this.section=section;}}
    private static final class Page {final byte[] values=new byte[PAGE];final BitSet changed=new BitSet(PAGE);Page copy(){Page result=new Page();System.arraycopy(values,0,result.values,0,PAGE);result.changed.or(changed);return result;}}
    private final Seed seed;private final List<Exclusion> exclusions;private final TreeMap<Long,Span> holes=new TreeMap<>();private final TreeMap<Long,Page> pages=new TreeMap<>();
    private Pc1NativeMemory bound;private String layoutFingerprint;
    public Pc1StaticImage(Seed seed)throws IOException{this(seed,Collections.emptyList());}
    public Pc1StaticImage(Seed seed,List<Exclusion> additional)throws IOException{if(seed==null||additional==null)throw new IOException("PC1 static immutable seed/exclusion contract required");this.seed=seed;exclusions=new ArrayList<>(seed.exclusions);for(Exclusion e:additional){if(e==null)throw new IOException("PC1 static null exclusion");exclusions.add(e);}exclusions.sort(Comparator.comparingLong(Exclusion::base));}
    public Seed seed(){return seed;}
    public String phase(){return PHASE;}
    public boolean finalized(){return bound!=null;}
    public String layoutFingerprint()throws IOException{ready();return layoutFingerprint;}
    public int changedPageCount(){return pages.size();}
    public int changedByteCount(){int result=0;for(Page page:pages.values())result+=page.changed.cardinality();return result;}
    private static boolean overlap(long a,long alen,long b,long blen){return a<b+blen&&b<a+alen;}
    private static String hash(byte[] raw)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");StringBuilder text=new StringBuilder();for(byte value:digest.digest(raw))text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}catch(NoSuchAlgorithmException impossible){throw new IOException("SHA256 unavailable",impossible);}}
    private static void range(byte[] raw,int offset,int size)throws IOException{if(offset<0||size<0||(long)offset+size>raw.length)throw new IOException("PC1 static truncated PE source");}
    private static int u16(byte[] raw,int at)throws IOException{range(raw,at,2);return(raw[at]&255)|(raw[at+1]&255)<<8;}
    private static long u32(byte[] raw,int at)throws IOException{range(raw,at,4);return((long)u16(raw,at)|((long)u16(raw,at+2)<<16))&0xffffffffL;}
    private static String name(byte[] raw,int at)throws IOException{range(raw,at,8);StringBuilder name=new StringBuilder();for(int n=0;n<8&&raw[at+n]!=0;n++)name.append((char)(raw[at+n]&255));return name.toString();}
    private String fingerprint(List<Pc1NativeMemory.FixedRegion> fixed)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeUTF(seed.identity);out.writeUTF(PHASE);out.writeInt(exclusions.size());for(Exclusion exclusion:exclusions){out.writeInt(exclusion.address);out.writeInt(exclusion.length);out.writeUTF(exclusion.reason);}out.writeInt(fixed.size());for(Pc1NativeMemory.FixedRegion region:fixed){out.writeInt(region.address);out.writeInt(region.length);}return hash(bytes.toByteArray());}
    void attach(Pc1NativeMemory memory,List<Pc1NativeMemory.FixedRegion> fixed)throws IOException{
        if(bound!=null){if(bound==memory)return;throw new IOException("PC1 static image already belongs to another native bus");}if(memory==null||fixed==null)throw new IOException("PC1 static final fixed-owner layout required");TreeMap<Long,Span> staged=new TreeMap<>();
        for(Section section:seed.sections){List<long[]> blocked=new ArrayList<>();for(Exclusion exclusion:exclusions)if(overlap(section.base,section.length,exclusion.base(),exclusion.length))blocked.add(new long[]{Math.max(section.base,exclusion.base()),Math.min(section.base+section.length,exclusion.base()+exclusion.length)});for(Pc1NativeMemory.FixedRegion region:fixed){long start=region.address&0xffffffffL;if(overlap(section.base,section.length,start,region.length))blocked.add(new long[]{Math.max(section.base,start),Math.min(section.base+section.length,start+region.length)});}blocked.sort(Comparator.comparingLong(value->value[0]));long current=section.base;for(long[] block:blocked){if(block[0]>current)staged.put(current,new Span(current,block[0],section));current=Math.max(current,block[1]);}if(current<section.base+section.length)staged.put(current,new Span(current,section.base+section.length,section));}
        String signature=fingerprint(fixed);holes.clear();holes.putAll(staged);layoutFingerprint=signature;bound=memory;
    }
    List<Pc1NativeMemory.FixedRegion> sectionRanges(){List<Pc1NativeMemory.FixedRegion> result=new ArrayList<>();for(Section section:seed.sections)result.add(new Pc1NativeMemory.FixedRegion((int)section.base,(int)section.length));return result;}
    List<Pc1NativeMemory.FixedRegion> ownedRanges(){List<Pc1NativeMemory.FixedRegion> result=new ArrayList<>();for(Span hole:holes.values())result.add(new Pc1NativeMemory.FixedRegion((int)hole.base,(int)(hole.end-hole.base)));return result;}
    private void ready()throws IOException{if(bound==null)throw new IOException("PC1 static image must be finalized after all fixed owners");}
    boolean inSection(int address){return seed.section(address&0xffffffffL)!=null;}
    private Span hole(long at){Map.Entry<Long,Span> previous=holes.floorEntry(at);return previous!=null&&at<previous.getValue().end?previous.getValue():null;}
    public boolean owns(int address){return bound!=null&&hole(address&0xffffffffL)!=null;}
    private IOException unknown(long at){for(Exclusion exclusion:exclusions)if(exclusion.contains(at))return new IOException("PC1 static byte excluded: "+exclusion.reason+" at0x"+Long.toHexString(at));return new IOException("PC1 static byte belongs to another owner or unknown section at0x"+Long.toHexString(at));}
    public int read(int address)throws IOException{ready();long at=address&0xffffffffL;if(hole(at)==null)throw unknown(at);long base=at&~(PAGE-1L);Page page=pages.get(base);int offset=(int)(at-base);return page!=null&&page.changed.get(offset)?page.values[offset]&255:seed.read(at);}
    public void write(int address,int value)throws IOException{ready();long at=address&0xffffffffL;Span range=hole(at);if(range==null)throw unknown(at);if(!range.section.writable)throw new IOException("PC1 static readonly section "+range.section.name+" at0x"+Long.toHexString(at));long base=at&~(PAGE-1L);int offset=(int)(at-base),next=value&255;Page page=pages.get(base);if(next==seed.read(at)){if(page!=null){page.changed.clear(offset);page.values[offset]=0;if(page.changed.isEmpty())pages.remove(base);}return;}if(page==null){page=new Page();pages.put(base,page);}page.values[offset]=(byte)next;page.changed.set(offset);}
    public void save(DataOutput out)throws IOException{ready();out.writeInt(FORMAT);out.writeInt(VERSION);out.writeUTF(seed.identity);out.writeUTF(PHASE);out.writeUTF(layoutFingerprint);out.writeInt(pages.size());for(Map.Entry<Long,Page> entry:pages.entrySet()){out.writeInt((int)(long)entry.getKey());Page page=entry.getValue();out.writeInt(page.changed.cardinality());for(int at=page.changed.nextSetBit(0);at>=0;at=page.changed.nextSetBit(at+1)){out.writeShort(at);out.writeByte(page.values[at]);}}}
    public static final class Image {private final String seed,phase,fingerprint;private final TreeMap<Long,Page> pages;private Image(String seed,String phase,String fingerprint,TreeMap<Long,Page> pages){this.seed=seed;this.phase=phase;this.fingerprint=fingerprint;this.pages=pages;}public int changedPageCount(){return pages.size();}}
    public static Image loadImage(DataInput in,Seed seed)throws IOException{if(seed==null)throw new IOException("PC1 static restore requires original immutable PE seed");if(in.readInt()!=FORMAT||in.readInt()!=VERSION)throw new IOException("PC1 static image codec format");String identity=in.readUTF(),phase=in.readUTF(),fingerprint=in.readUTF();if(!seed.identity.equals(identity)||!PHASE.equals(phase)||!fingerprint.matches("[0-9a-f]{64}"))throw new IOException("PC1 static seed/phase identity mismatch");int count=in.readInt();if(count<0||count>(seed.end-seed.base+PAGE-1)/PAGE)throw new IOException("PC1 static sparse page count");TreeMap<Long,Page> pages=new TreeMap<>();long previous=-1;for(int n=0;n<count;n++){long base=in.readInt()&0xffffffffL;int size=in.readInt();if((base&(PAGE-1))!=0||base<=previous||base<seed.base||base>=seed.end||size<1||size>PAGE)throw new IOException("PC1 static sparse page descriptor");previous=base;Page page=new Page();int last=-1;for(int b=0;b<size;b++){int at=in.readUnsignedShort(),value=in.readUnsignedByte();long address=base+at;Section section=seed.section(address);if(at>=PAGE||at<=last||section==null||!section.writable||value==seed.read(address))throw new IOException("PC1 static sparse byte descriptor");last=at;page.values[at]=(byte)value;page.changed.set(at);}pages.put(base,page);}return new Image(identity,phase,fingerprint,pages);}
    public void restore(Image image)throws IOException{ready();if(image==null||!seed.identity.equals(image.seed)||!PHASE.equals(image.phase)||!layoutFingerprint.equals(image.fingerprint))throw new IOException("PC1 static restore fixed-layout/exclusion fingerprint mismatch");TreeMap<Long,Page> staged=new TreeMap<>();for(Map.Entry<Long,Page> entry:image.pages.entrySet()){Page page=entry.getValue();for(int at=page.changed.nextSetBit(0);at>=0;at=page.changed.nextSetBit(at+1)){long address=entry.getKey()+at;Span span=hole(address);if(span==null||!span.section.writable)throw new IOException("PC1 static restore cannot overwrite borrowed/readonly/excluded owner");}staged.put(entry.getKey(),page.copy());}pages.clear();pages.putAll(staged);}
}
