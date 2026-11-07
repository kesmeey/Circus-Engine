package org.circus.core;

import java.io.*;
import java.util.*;

/** Current PC1 CN byte layout journal. This owns no font, Unicode or GDI work.
 * A failed builder must be discarded; freeze publishes only completed work. */
public final class Pc1TextLayout {
    public static final int GLYPH_CAPACITY=1000,LINK_CAPACITY=10;
    public static final int CN_RUBY_STOP_WORD=0xfda3,CN_RUBY_SPLIT_WORD=0xafa3;
    private static final int FORMAT=0x50315932,ALIAS_FORMAT=0x50315941;
    public static final class Config {
        public final int bodyX,bodyPitch,speakerX,speakerY,speakerColumns,textStyle,rubyStopWord,rubySplitWord;
        public Config(int bodyX,int bodyPitch,int speakerX,int speakerY,int speakerColumns,int textStyle,int rubyStopWord,int rubySplitWord)throws IOException{
            if((rubyStopWord&~65535)!=0||(rubySplitWord&~65535)!=0)throw new IOException("PC1 ruby delimiter word");
            this.bodyX=bodyX;this.bodyPitch=bodyPitch;this.speakerX=speakerX;this.speakerY=speakerY;this.speakerColumns=speakerColumns;this.textStyle=textStyle;this.rubyStopWord=rubyStopWord;this.rubySplitWord=rubySplitWord;
        }
    }
    /** All bytes are known backing memory, including terminators/stale suffix.
     * Reusing ownerId replaces that owner's current bytes, retaining aliases. */
    public static final class Source {
        public final String ownerId;private final byte[] bytes;private final Integer nativeBase;private final int knownLength;
        public Source(String ownerId,byte[] knownBackingBytes)throws IOException{this(ownerId,knownBackingBytes,ownerId!=null&&ownerId.equals(Pc1TextBuffers.LIVE_BODY)?Integer.valueOf(0x9d3af8):ownerId!=null&&ownerId.equals(Pc1TextBuffers.LIVE_SPEAKER)?Integer.valueOf(0x9ccaa0):null);}
        public Source(String ownerId,int nativeBase,byte[] knownBackingBytes)throws IOException{this(ownerId,knownBackingBytes,Integer.valueOf(nativeBase));}
        private Source(String ownerId,byte[] knownBackingBytes,Integer nativeBase)throws IOException{this(ownerId,knownBackingBytes,nativeBase,knownBackingBytes==null?0:knownBackingBytes.length);}
        private Source(String ownerId,byte[] knownBackingBytes,Integer nativeBase,int knownLength)throws IOException{
            if(ownerId==null||ownerId.isEmpty()||knownBackingBytes==null||knownBackingBytes.length>1024||knownLength<0||knownLength>1024)throw new IOException("PC1 layout source owner");
            this.ownerId=ownerId;bytes=knownBackingBytes.clone();this.nativeBase=nativeBase;this.knownLength=knownLength;
        }
        public byte[] bytes(){return bytes.clone();}
        private int at(int p)throws IOException{if(p<0||p>=bytes.length)throw new IOException("PC1 layout reads beyond known source backing");return bytes[p]&255;}
        private int word(int p)throws IOException{return at(p)|(at(p+1)<<8);}
        private int length()throws IOException{for(int i=0;i<bytes.length;i++)if(bytes[i]==0)return i;throw new IOException("PC1 layout source has no terminator");}
    }
    /** Exact eight DWORD glyph record; bytes28..31 contain the raw C string. */
    public static final class Glyph {
        public final int x,y,right,bottom,fontSize,style,decor;private final int raw;
        private Glyph(int x,int y,int right,int bottom,int fontSize,int style,int decor,int raw){this.x=x;this.y=y;this.right=right;this.bottom=bottom;this.fontSize=fontSize;this.style=style;this.decor=decor;this.raw=raw;}
        public byte[] rawCharacter(){return new byte[]{(byte)raw,(byte)(raw>>>8),(byte)(raw>>>16),(byte)(raw>>>24)};}
        public int[] nativeDwords(){return new int[]{x,y,right,bottom,fontSize,style,decor,raw};}
    }
    public static final class Region {
        public final int index,left,top,right,bottom;private final byte[] record,word;
        private Region(int index,byte[] record,byte[] word){this.index=index;this.record=record.clone();this.word=word.clone();left=i32(record,12);top=i32(record,16);right=i32(record,20);bottom=i32(record,24);}
        /** Pointer at22C is represented separately by the frozen alias owner. */
        public byte[] nativeRecord(){return record.clone();}
        public byte[] encodedWord(){return word.clone();}
        public String action(){return ":w";}
        public int[] sprite(int n){if(n<0||n>2)throw new IndexOutOfBoundsException();int[] out=new int[10];for(int i=0;i<10;i++)out[i]=i32(record,28+n*40+i*4);return out;}
    }
    public static final class Patch {
        public final int left,top,right,bottom,selector;
        private Patch(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;selector=0;}
        /** Native418160 redraws this selector0 background strip and publishes
         *it before processing glyphs; this does not request a black fill. */
        public boolean publish(){return true;}
    }
    public static final class Step {public final int nextCursor;private Step(int cursor){nextCursor=cursor;}}
    public static final class Result {
        public final int nextCursor,firstGlyph;public final boolean speakerGeometryRefresh;public final List<Patch> patches;
        private Result(int cursor,int first,boolean geometry,List<Patch> patches){nextCursor=cursor;firstGlyph=first;speakerGeometryRefresh=geometry;this.patches=Collections.unmodifiableList(new ArrayList<>(patches));}
    }
    private static final class Ref {
        final String owner;final int offset;final boolean encoded;final int expectedPointer;
        Ref(String owner,int offset,boolean encoded,int expected){this.owner=owner;this.offset=offset;this.encoded=encoded;expectedPointer=expected;}
    }
    /** Only symbolic stack/source lifetimes are outside addressed bytes.
     * Completed dictionary records use their actual encoded-word pointer. */
    public static final class Aliases {
        private final String bodyOwner;private final LinkedHashMap<String,Source> sources;private final Ref[] refs;
        private Aliases(String body,Map<String,Source> sources,Ref[] refs){bodyOwner=body;this.sources=new LinkedHashMap<>(sources);this.refs=refs.clone();}
        public void save(DataOutput out)throws IOException{save(out,false);}
        private void save(DataOutput out,boolean frozenSources)throws IOException{
            out.writeInt(ALIAS_FORMAT);out.writeBoolean(frozenSources);out.writeUTF(bodyOwner);out.writeInt(sources.size());for(Source source:sources.values()){out.writeUTF(source.ownerId);out.writeBoolean(source.nativeBase!=null);if(source.nativeBase!=null)out.writeInt(source.nativeBase);out.writeInt(source.knownLength);if(source.nativeBase==null||frozenSources){if(source.bytes.length!=source.knownLength)throw new IOException("PC1 cannot freeze a source without its native owner");out.write(source.bytes);}}
            for(Ref ref:refs){out.writeBoolean(ref!=null);if(ref!=null){out.writeBoolean(ref.encoded);out.writeUTF(ref.owner);out.writeInt(ref.offset);out.writeInt(ref.expectedPointer);}}
        }
        public static Aliases load(DataInput in)throws IOException{
            if(in.readInt()!=ALIAS_FORMAT)throw new IOException("PC1 layout aliases format");boolean frozenSources=in.readBoolean();String body=in.readUTF();int count=in.readInt();if(count<0||count>12)throw new IOException("PC1 layout source count");LinkedHashMap<String,Source> sources=new LinkedHashMap<>();
            while(count-->0){String id=in.readUTF();Integer base=in.readBoolean()?Integer.valueOf(in.readInt()):null;int len=in.readInt();if(len<0||len>1024)throw new IOException("PC1 layout source size");byte[] bytes=new byte[base==null||frozenSources?len:0];in.readFully(bytes);if(sources.put(id,new Source(id,bytes,base,len))!=null)throw new IOException("PC1 duplicate layout source");}
            Ref[] refs=new Ref[10];for(int n=0;n<refs.length;n++)if(in.readBoolean()){boolean encoded=in.readBoolean();String owner=in.readUTF();int offset=in.readInt(),expected=in.readInt();if(encoded){if(!owner.isEmpty()||offset<0||offset>=800||expected!=Pc1LayoutMemory.WORDS+offset)throw new IOException("PC1 encoded word alias");}else if(!sources.containsKey(owner)||offset<0||offset>=1024)throw new IOException("PC1 layout source alias");refs[n]=new Ref(owner,offset,encoded,expected);}
            if(!body.isEmpty()&&!sources.containsKey(body))throw new IOException("PC1 body cursor owner");return new Aliases(body,sources,refs);
        }
    }
    /** Immutable DTO/frozen rendering state. Production uses the bound raw
     *owner plus aliases(), never applies this DTO as a second live authority. */
    public static final class Snapshot {
        public final int x,y,fontSize,rubyPhase,rubyCount,baseCount,rubyCursor,rubyOrigin,rubyGap,dictionaryCount,cursor,glyphCount;
        public final String bodyOwner;private final List<Glyph> glyphs;private final byte[] ruby,words,rawImage;private final byte[][] records;private final Aliases aliases;
        private Snapshot(Pc1TextLayout v)throws IOException{
            x=v.get(Pc1InlineText.X);y=v.get(Pc1InlineText.Y);fontSize=v.get(Pc1InlineText.FONT);rubyPhase=v.get(Pc1InlineText.RUBY_PHASE);rubyCount=v.get(Pc1InlineText.RUBY_COUNT);baseCount=v.get(Pc1InlineText.BASE_COUNT);rubyCursor=v.get(Pc1InlineText.RUBY_CURSOR);rubyOrigin=v.get(Pc1InlineText.RUBY_ORIGIN);rubyGap=v.get(Pc1InlineText.RUBY_GAP);dictionaryCount=v.get(Pc1LayoutMemory.DICT_COUNT);cursor=v.get(Pc1LayoutMemory.BODY_CURSOR);glyphCount=v.get(Pc1LayoutMemory.GLYPH_COUNT);bodyOwner=v.bodyOwner;
            List<Glyph> rows=new ArrayList<>();for(int n=0;n<glyphCount;n++){int p=Pc1LayoutMemory.GLYPHS+n*32;rows.add(new Glyph(v.get(p),v.get(p+4),v.get(p+8),v.get(p+12),v.get(p+16),v.get(p+20),v.get(p+24),v.get(p+28)));}glyphs=Collections.unmodifiableList(rows);
            ruby=v.memory.bytes(Pc1LayoutMemory.RUBY,1024);words=v.memory.bytes(Pc1LayoutMemory.WORDS,800);records=new byte[10][];for(int n=0;n<10;n++)records[n]=v.memory.bytes(Pc1LayoutMemory.RECORDS+n*560,560);
            ByteArrayOutputStream out=new ByteArrayOutputStream();v.memory.save(new DataOutputStream(out));rawImage=out.toByteArray();aliases=v.aliases();
        }
        public List<Glyph> glyphs(){return glyphs;}public byte[] rubyBacking(){return ruby.clone();}public byte[] encodedWordsBacking(){return words.clone();}
        /** The real native DWORD pointer remains in byte22C. No zeroing for
         *hashes or substitution by an old source Ref occurs in this method. */
        public byte[] nativeDictionaryRecord(int index){if(index<0||index>=10)throw new IndexOutOfBoundsException();return records[index].clone();}
        public List<Region> regions()throws IOException{return Pc1TextLayout.regions(dictionaryCount,records,words);}
        public Map<String,Source> sources(){return Collections.unmodifiableMap(aliases.sources);}
        private Ref pending(){if(dictionaryCount<0||dictionaryCount>=10)return null;Ref ref=aliases.refs[dictionaryCount];return ref!=null&&!ref.encoded&&i32(records[dictionaryCount],556)==ref.expectedPointer?ref:null;}
        public String pendingOwner(){Ref ref=pending();return ref==null?null:ref.owner;}public int pendingOffset(){Ref ref=pending();return ref==null?-1:ref.offset;}
        public Aliases aliases(){return aliases;}
        public void saveAliases(DataOutput out)throws IOException{aliases.save(out);}
        public void save(DataOutput out)throws IOException{out.writeInt(FORMAT);out.writeInt(rawImage.length);out.write(rawImage);out.write(words);aliases.save(out,true);}
        public static Snapshot load(DataInput in)throws IOException{
            if(in.readInt()!=FORMAT)throw new IOException("PC1 text layout format");int length=in.readInt();if(length<8||length>65536)throw new IOException("PC1 layout raw image length");byte[] raw=new byte[length];in.readFully(raw);DataInputStream imageInput=new DataInputStream(new ByteArrayInputStream(raw));Pc1LayoutMemory.Image image=Pc1LayoutMemory.load(imageInput);if(imageInput.available()!=0)throw new IOException("PC1 layout raw image trailing bytes");
            Pc1LayoutMemory memory=Pc1LayoutMemory.detached(new Config(0,0,0,0,0,0,CN_RUBY_STOP_WORD,CN_RUBY_SPLIT_WORD));memory.restore(image);byte[] words=new byte[800];in.readFully(words);memory.writeBytes(Pc1LayoutMemory.WORDS,words);Aliases aliases=Aliases.load(in);Pc1TextLayout v=new Pc1TextLayout(new Config(0,0,0,0,0,0,CN_RUBY_STOP_WORD,CN_RUBY_SPLIT_WORD),memory,aliases);return v.freezeChecked();
        }
    }
    private Config config;private final Pc1LayoutMemory memory;private String bodyOwner="";private final Ref[] refs=new Ref[10];private final LinkedHashMap<String,Source> sources=new LinkedHashMap<>();
    public Pc1TextLayout(Config config)throws IOException{this(config,Pc1LayoutMemory.detached(config),(Aliases)null);}
    public Pc1TextLayout(Config config,Snapshot saved)throws IOException{
        this(config);if(saved==null)throw new IOException("PC1 text layout snapshot is missing");memory.restore(Pc1LayoutMemory.load(new DataInputStream(new ByteArrayInputStream(saved.rawImage))));memory.writeBytes(Pc1LayoutMemory.WORDS,saved.words);restoreAliases(saved.aliases);
    }
    /** New production builder is a view, not a copy of native layout bytes. */
    public Pc1TextLayout(Config config,Pc1LayoutMemory memory)throws IOException{this(config,memory,(Aliases)null);}
    /** Snapshot restores ONLY pointer-lifetime metadata on an already restored
     *canonical owner; it never overwrites newer Inline/alias raw writes. */
    public Pc1TextLayout(Config config,Pc1LayoutMemory memory,Snapshot saved)throws IOException{this(config,memory,saved==null?null:saved.aliases);}
    public Pc1TextLayout(Config config,Pc1LayoutMemory memory,Aliases aliases)throws IOException{if(memory==null)throw new IOException("PC1 layout raw owner missing");this.memory=memory;configure(config);if(aliases!=null)restoreAliases(aliases);}
    private void restoreAliases(Aliases aliases){bodyOwner=aliases.bodyOwner;sources.putAll(aliases.sources);System.arraycopy(aliases.refs,0,refs,0,10);}
    public Pc1LayoutMemory memory(){return memory;}
    /** Current raw dictionary view for the native active-count owner. Unlike
     *an immutable Snapshot this follows every subsequent record/pointer/word
     *alias, and may read any explicitly known pointed-to native owner. */
    public List<Region> regions(int count)throws IOException{
        List<Region> rows=new ArrayList<>();for(int n=0;n<count;n++){byte[] record=memory.bytes(Pc1LayoutMemory.RECORDS+n*560,560);int pointer=i32(record,556);ByteArrayOutputStream word=new ByteArrayOutputStream();for(int k=0;;k++){if(k>=1024*1024)throw new IOException("PC1 dictionary string exceeds known host allocation bound");int b=memory.read(pointer+k);if(b==0)break;word.write(b);}rows.add(new Region(n,record,word.toByteArray()));}return Collections.unmodifiableList(rows);
    }
    public void configure(Config config)throws IOException{if(config==null)throw new IOException("PC1 layout geometry is missing");this.config=config;memory.configureDetached(config);}
    private int get(int address)throws IOException{return memory.readI32(address);}private void set(int address,int value)throws IOException{memory.writeI32(address,value);}
    public void positionChecked(int x,int y,int fontSize)throws IOException{set(Pc1InlineText.X,x);set(Pc1InlineText.Y,y);set(Pc1InlineText.FONT,fontSize);}
    public void position(int x,int y,int fontSize){try{positionChecked(x,y,fontSize);}catch(IOException e){throw new UncheckedIOException(e);}}
    public void resetRubyPhase(){try{set(Pc1InlineText.RUBY_PHASE,0);}catch(IOException e){throw new UncheckedIOException(e);}}
    public void dictionaryCount(int count)throws IOException{set(Pc1LayoutMemory.DICT_COUNT,count);}
    public void clearGlyphs(){try{set(Pc1LayoutMemory.GLYPH_COUNT,0);}catch(IOException e){throw new UncheckedIOException(e);}}
    public Snapshot freezeChecked()throws IOException{return new Snapshot(this);}public Snapshot freeze(){try{return freezeChecked();}catch(IOException e){throw new UncheckedIOException(e);}}
    public Aliases aliases()throws IOException{
        LinkedHashMap<String,Source> current=new LinkedHashMap<>();for(Source source:sources.values()){byte[] bytes=source.bytes;if(memory.bound()&&source.nativeBase!=null)bytes=memory.bytes(source.nativeBase,source.knownLength);current.put(source.ownerId,new Source(source.ownerId,bytes,source.nativeBase,source.knownLength));}return new Aliases(bodyOwner,current,refs);
    }
    public void update(Source source)throws IOException{
        if(source==null)throw new IOException("PC1 layout source is missing");for(Iterator<String> it=sources.keySet().iterator();it.hasNext();){String id=it.next();boolean keep=id.equals(source.ownerId)||id.equals(bodyOwner);for(Ref ref:refs)if(ref!=null&&!ref.encoded&&ref.owner.equals(id))keep=true;if(!keep)it.remove();}
        if(!sources.containsKey(source.ownerId)&&sources.size()>=12)throw new IOException("PC1 layout source owner capacity");sources.put(source.ownerId,source);
    }
    private int at(Source source,int p)throws IOException{return memory.bound()&&source.nativeBase!=null?memory.read(source.nativeBase+p):source.at(p);}
    private int word(Source source,int p)throws IOException{return at(source,p)|at(source,p+1)<<8;}private int length(Source source)throws IOException{int p=0;try{while(at(source,p)!=0)p++;return p;}catch(IOException missing){throw new IOException("PC1 layout source has no terminator in known source backing",missing);}}
    private static int i32(byte[] b,int p){return(b[p]&255)|(b[p+1]&255)<<8|(b[p+2]&255)<<16|b[p+3]<<24;}
    private static byte[] word(byte[] memory,int start)throws IOException{int end=start;while(end<memory.length&&end>=0&&memory[end]!=0)end++;if(start<0||end==memory.length)throw new IOException("PC1 encoded word lacks known terminator");return Arrays.copyOfRange(memory,start,end);}
    private static List<Region> regions(int count,byte[][] records,byte[] words)throws IOException{
        List<Region> out=new ArrayList<>();for(int i=0;i<count;i++){if(i>=records.length)throw new IOException("PC1 dictionary records reach unknown neighboring backing");int pointer=i32(records[i],556),offset=pointer-Pc1LayoutMemory.WORDS;if(offset<0||offset>=words.length)throw new IOException("PC1 dictionary raw source pointer is not a known encoded word");out.add(new Region(i,records[i],word(words,offset)));}return Collections.unmodifiableList(out);
    }
    private static boolean wide(int first){return first>=0x81&&first<=0xfe;}
    /** Isolated fresh-record value projection; live emit writes bytes below
     *and preserves native raw string padding from the previous record. */
    public static Glyph produce(int x,int y,int first,int second,int style,int size,int decor){int right=x+(wide(first)?size:size/2),bottom=y+size,raw=(first&255)|(wide(first)?(second&255)<<8:0);if((style&0x40)!=0){if((decor&1)!=0)x-=2;if((decor&2)!=0)right+=2;y-=2;bottom+=2;}else if((style&0x80)!=0){right++;bottom++;}return new Glyph(x,y,right,bottom,size,style,decor,raw);}
    /** Actual416F90 write order, including raw[30/31] stale bytes. */
    private void emit(int x,int y,int first,int second,int style,int size,int decor)throws IOException{
        int count=get(Pc1LayoutMemory.GLYPH_COUNT);if(count>=1000)return;int p=Pc1LayoutMemory.GLYPHS+count*32;
        set(p,x);set(p+4,y);set(p+16,size);set(p+20,style);memory.write(p+28,first);set(p+12,y+size);set(p+24,decor);
        set(p+8,x+(wide(first)?size:size/2));if(wide(first)){memory.write(p+29,second);memory.write(p+30,0);}else memory.write(p+29,0);
        if((style&0x40)!=0){if((decor&1)!=0)set(p,get(p)-2);if((decor&2)!=0)set(p+8,get(p+8)+2);set(p+4,get(p+4)-2);int bottom=get(p+12)+2;int next=get(Pc1LayoutMemory.GLYPH_COUNT)+1;set(p+12,bottom);set(Pc1LayoutMemory.GLYPH_COUNT,next);}
        else{if((style&0x80)!=0){set(p+8,get(p+8)+1);set(p+12,get(p+12)+1);}set(Pc1LayoutMemory.GLYPH_COUNT,get(Pc1LayoutMemory.GLYPH_COUNT)+1);}
    }
    private int rubySplit(Source source,int p)throws IOException{
        int ruby=0,base=0,out=Pc1LayoutMemory.RUBY;
        for(;;){int b=at(source,p);if(b==0)break;int w=word(source,p);if(w==memory.readU16(Pc1InlineText.SPLIT_WORD)){p+=2;break;}if(w==memory.readU16(Pc1InlineText.STOP_WORD))break;ruby++;if(wide(b)){memory.write(out++,b);p++;}b=at(source,p);if(b!=0){memory.write(out++,b);p++;}}
        memory.write(out,0);while(at(source,p)!=0&&word(source,p)!=memory.readU16(Pc1InlineText.STOP_WORD)){base++;if(wide(at(source,p)))p++;if(at(source,p)!=0)p++;}
        set(Pc1InlineText.RUBY_COUNT,ruby);set(Pc1InlineText.BASE_COUNT,base);if(ruby!=0&&base!=0){set(Pc1InlineText.RUBY_PHASE,1);set(Pc1InlineText.RUBY_CURSOR,0);return ruby*2+2;}return 0;
    }
    private void begin(Source source,int offset)throws IOException{
        int index=get(Pc1LayoutMemory.DICT_COUNT);if(index==-1||index>=10)return;int p=Pc1LayoutMemory.RECORDS+index*560,x=get(Pc1InlineText.X),y=get(Pc1InlineText.Y);
        set(p,1);set(p+8,0);set(p+4,0);set(p+12,x);set(p+16,y);set(p+20,x);set(p+24,y);set(p+508,255);set(p+512,255);set(p+548,0);set(p+552,0);
        int pointer=get(p+556);if(source.nativeBase!=null&&memory.bound()){pointer=source.nativeBase+offset;set(p+556,pointer);}if(index>=0&&index<refs.length)refs[index]=new Ref(source.ownerId,offset,false,pointer);
        for(int n=0;n<12;n++){set(p+28+n*40,0);set(p+64+n*40,0);}memory.write(p+516,':');memory.write(p+517,'w');memory.write(p+518,0);
    }
    private void end(Source source,int offset,int originalY)throws IOException{
        int index=get(Pc1LayoutMemory.DICT_COUNT);if(index==-1||index>=10)return;int p=Pc1LayoutMemory.RECORDS+index*560,pointer=get(p+556),to=Pc1LayoutMemory.WORDS+index*80;
        Ref ref=index>=0&&index<10?refs[index]:null;boolean matched=ref!=null&&!ref.encoded&&ref.expectedPointer==pointer;Source latest=matched?sources.get(ref.owner):null;boolean symbolic=matched&&(!memory.bound()||latest.nativeBase==null);int from=symbolic?ref.offset:pointer;int limit=symbolic?offset:source.nativeBase==null?0:source.nativeBase+offset;
        if(symbolic&&!ref.owner.equals(source.ownerId)&&source.nativeBase==null)throw new IOException("PC1 dictionary pointer order crosses unknown source owners");
        if(!symbolic&&source.nativeBase==null)throw new IOException("PC1 dictionary pointer order/source lifetime is not mapped");
        while((symbolic?at(latest,from):memory.read(from))!=0&&Integer.compareUnsigned(from,limit)<0){memory.write(to++,((symbolic?at(latest,from):memory.read(from))-32)&255);from++;}memory.write(to,0);
        index=get(Pc1LayoutMemory.DICT_COUNT);p=Pc1LayoutMemory.RECORDS+index*560;int size=get(Pc1InlineText.FONT),x=get(Pc1InlineText.X);set(p+20,x);set(p+24,originalY+size);pointer=Pc1LayoutMemory.WORDS+index*80;set(p+556,pointer);if(index>=0&&index<10)refs[index]=new Ref("",index*80,true,pointer);
        set(p+32,get(p+12));set(p+36,get(p+16));set(p+48,0);set(p+52,0);set(p+56,get(p+12)-x);set(p+60,size);set(p+40,5);set(p+28,99);
        for(int n=0;n<40;n++)memory.write(p+68+n,memory.read(p+28+n));set(p+80,0x49c7);set(p+68,1);
        for(int n=0;n<40;n++)memory.write(p+108+n,memory.read(p+28+n));set(p+120,get(Pc1MessageGeometry.BODY_STYLE));set(p+108,2);set(Pc1LayoutMemory.DICT_COUNT,index+1);
    }
    private static int divide(int a,int b)throws IOException{if(b==0||a==Integer.MIN_VALUE&&b==-1)throw new IOException("PC1 layout native signed division trap");return a/b;}
    public Step step(Source source,int offset,int style,boolean bodyMode,int nativeByteCount)throws IOException{
        update(source);int p=offset;if(at(source,p)=='@'){int code=at(source,p+1);if(code=='s')set(Pc1InlineText.FONT,22);else if(code=='n')set(Pc1InlineText.FONT,28);else if(code=='m')set(Pc1InlineText.FONT,32);else if(code=='l')set(Pc1InlineText.FONT,40);p+=2;}
        int originalY=get(Pc1InlineText.Y);if(bodyMode&&at(source,p)==0x81&&at(source,p+1)=='m'){p+=2;begin(source,p);}int first=at(source,p),second=at(source,p+1),next=p+2;
        if(first==0x81&&second=='o'){
            next=p+2+rubySplit(source,p+2);int gap=get(Pc1InlineText.BASE_COUNT)*28-get(Pc1InlineText.RUBY_COUNT)*12;set(Pc1InlineText.RUBY_ORIGIN,get(Pc1InlineText.X));set(Pc1InlineText.RUBY_GAP,gap);if(gap<0){set(Pc1InlineText.RUBY_GAP,0);set(Pc1InlineText.RUBY_ORIGIN,get(Pc1InlineText.X)-(get(Pc1InlineText.RUBY_COUNT)*12-get(Pc1InlineText.BASE_COUNT)*28)/2);}
        }else{
            if(first==0x81&&second=='p')set(Pc1InlineText.RUBY_PHASE,2);
            else if(get(Pc1InlineText.X)==get(Pc1MessageGeometry.BODY_X)&&first==0x81&&second=='@'&&(at(source,p+2)==0||at(source,p+2)=='\n'))set(Pc1InlineText.X,get(Pc1InlineText.X)+28);
            else{int decor=0;if((style&0x40)!=0){decor=get(Pc1InlineText.X)==get(Pc1MessageGeometry.BODY_X)?1:0;if(nativeByteCount<=p+2||at(source,p+2)=='\n'||at(source,p+2)==0)decor|=2;}emit(get(Pc1InlineText.X),get(Pc1InlineText.Y),first,second,style,get(Pc1InlineText.FONT),decor);set(Pc1InlineText.X,get(Pc1InlineText.X)+get(Pc1InlineText.FONT));}
            if(get(Pc1InlineText.RUBY_PHASE)!=0){
                while(get(Pc1InlineText.RUBY_CURSOR)<get(Pc1InlineText.RUBY_COUNT)*2){int c=get(Pc1InlineText.RUBY_CURSOR),gap=get(Pc1InlineText.RUBY_GAP),rx=get(Pc1InlineText.RUBY_ORIGIN);if(gap>0)rx+=divide((c+1)*gap,get(Pc1InlineText.RUBY_COUNT))/2;rx+=(c*12)/2;if(get(Pc1InlineText.RUBY_PHASE)==1&&get(Pc1InlineText.X)<=rx)break;emit(rx,get(Pc1InlineText.Y)-11,memory.read(Pc1LayoutMemory.RUBY+c),memory.read(Pc1LayoutMemory.RUBY+c+1),style&~0x40,12,0);set(Pc1InlineText.RUBY_CURSOR,get(Pc1InlineText.RUBY_CURSOR)+2);}
                if(get(Pc1InlineText.RUBY_PHASE)==2)set(Pc1InlineText.RUBY_PHASE,0);
            }
        }
        if(bodyMode&&at(source,next)==0x81&&at(source,next+1)=='n'){end(source,next,originalY);next+=2;}
        if(bodyMode){set(Pc1LayoutMemory.BODY_CURSOR,next);bodyOwner=source.ownerId;}return new Step(next);
    }
    public Result body(Source source,int style,int nativeByteCount)throws IOException{
        update(source);bodyOwner=source.ownerId;set(Pc1LayoutMemory.BODY_CURSOR,0);int first=get(Pc1LayoutMemory.GLYPH_COUNT),p=0;List<Patch> patches=new ArrayList<>();if(at(source,0)=='@'&&at(source,1)=='K'){patches.add(new Patch(get(Pc1InlineText.X),get(Pc1InlineText.Y)-2,get(Pc1InlineText.X)+3,get(Pc1InlineText.Y)+30));p=2;set(Pc1LayoutMemory.BODY_CURSOR,2);}int length=length(source);
        do{if(at(source,p)=='\n'){set(Pc1InlineText.X,get(Pc1MessageGeometry.BODY_X));set(Pc1InlineText.Y,get(Pc1InlineText.Y)+get(Pc1MessageGeometry.BODY_PITCH));p++;set(Pc1LayoutMemory.BODY_CURSOR,p);}else p=step(source,p,style,true,nativeByteCount).nextCursor;}while(p<length);return new Result(p,first,false,patches);
    }
    /** Establish source-lifetime metadata for native wrappers which perform
     *their own restore-before-step loop. No native cursor/count is changed. */
    public void bodySource(Source source)throws IOException{update(source);bodyOwner=source.ownerId;}
    public Result speaker(Source source,int style,int nativeByteCount,boolean paragraphMode)throws IOException{
        if(paragraphMode)return new Result(0,get(Pc1LayoutMemory.GLYPH_COUNT),false,Collections.<Patch>emptyList());update(source);int first=get(Pc1LayoutMemory.GLYPH_COUNT),p=0,length=length(source);
        if(length!=0){set(Pc1InlineText.FONT,28);set(Pc1InlineText.X,(get(Pc1MessageGeometry.NAME_HISTORY_AUX)*28-(length*28)/2)/2+get(Pc1MessageGeometry.NAME_X));set(Pc1InlineText.Y,get(Pc1MessageGeometry.NAME_Y));while(at(source,p)!=0){if(at(source,p)=='\n')p++;else p=step(source,p,style,false,nativeByteCount).nextCursor;}}
        return new Result(p,first,true,Collections.<Patch>emptyList());
    }
}
