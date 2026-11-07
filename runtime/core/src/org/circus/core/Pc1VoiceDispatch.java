package org.circus.core;

import java.io.*;

/**41CD70/41CB10 admission over live raw sources. Playback/load ownership
 *remains in the required Audio tail. No complete name is copied in admission.
 *History's already-copied native stack strings are a separate entry route. */
public final class Pc1VoiceDispatch {
    public static final int SE_ENABLED=0x454d08,VOICE_ENABLED=0x454d10,PCM_READY=0xbc1d10;
    private static final int TABLE=0x43dbe8,MUTE_BASE=Pc1NativeMemory.SETTINGS_BASE+Pc1NativeSettings.GLOBAL_OFFSET;
    private static final int SCAN_LIMIT=1024*1024;
    public interface Services {
        /**Already admitted: no repeated preference/forced/mute/first-byte
         *checks. Retain source.address through old2 stop/release,41BE20 and
         *its final forward strcpy; read it anew at each native consumption. */
        void admittedVoice(int fixedRawAddress,Source source)throws IOException;
        /**42534C exists only when current CRT locale word is nonzero and both
         *compared bytes are non-NUL. The C-locale path does not call this. */
        default int lowerCase42534C(int unsignedByte)throws IOException{throw new IOException("PC1 voice non-C locale requires actual42534C byte converter");}
    }
    public static final class Source implements Pc1Audio.NativeName {
        public final int address;private final Pc1NativeMemory memory;
        private Source(Pc1NativeMemory memory,int address){this.memory=memory;this.address=address;}
        public int address(){return address;}
        public int unsignedByte(int offset)throws IOException{return memory.readAddress(address+offset);}
        public int nativeWord(int offset)throws IOException{return memory.readInt32(address+offset);}
        /**Explicit consumer operation, never called by admission. The caller
         *chooses its actual read point; another call sees subsequent writes. */
        public byte[] copyCString()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<SCAN_LIMIT;n++){int b=unsignedByte(n);if(b==0)return out.toByteArray();out.write(b);}throw new IOException("PC1 live voice source exceeds known scan budget");}
    }
    private final Pc1NativeMemory memory;private final Services services;private int depth;
    public Pc1VoiceDispatch(Pc1NativeMemory memory,Pc1Audio audio,Services services)throws IOException{if(memory==null||audio==null||services==null)throw new IOException("PC1 raw voice requires canonical audio/memory and admitted tail");audio.requireBus(memory);this.memory=memory;this.services=services;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 snapshot during raw voice admission/tail");}
    /**41CD70 is shared by hover+224 and click+228, including raw0. */
    public void uiVoice(int rawPointer)throws IOException{depth++;try{if(word(SE_ENABLED)!=0)voiceEntry(rawPointer);}finally{depth--;}}
    /**41CB10 raw-pointer entry without the additional UI SE preference gate. */
    public void voice(int rawPointer)throws IOException{depth++;try{voiceEntry(rawPointer);}finally{depth--;}}
    private void voiceEntry(int rawPointer)throws IOException{
        if(word(VOICE_ENABLED)==0||word(PCM_READY)==0||rawPointer==0||memory.readAddress(rawPointer)==0)return;
        if(!forced(rawPointer)){int id=character(rawPointer);if(memory.readAddress(MUTE_BASE+(id+60))!=0)return;}
        services.admittedVoice(rawPointer,new Source(memory,rawPointer));
    }
    private int word(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=memory.readAddress(address+n)<<(n*8);return value;}
    private static int lowerC(int value){return value>='A'&&value<='Z'?value+32:value;}
    /**Actual425210 C-locale read/short-circuit order. Locale is sampled once
     *per comparison; non-C converts right before left only when both nonzero. */
    private boolean equal(int left,int right,int count)throws IOException{
        boolean cLocale=word(Pc1CatalogMemory.CRT_LOCALE)==0;
        for(int n=0;n<count;n++){
            int a=memory.readAddress(left+n),b=memory.readAddress(right+n);
            if(a==0||b==0)return a==b;
            if(cLocale){a=lowerC(a);b=lowerC(b);}else{int convertedRight=services.lowerCase42534C(b);a=services.lowerCase42534C(a);b=convertedRight;}
            if(a!=b)return false;
        }return true;
    }
    /**41CAA0 compares each component prefix; SYSTEMX is intentionally forced.
     *An early match need not see the later NUL before the playback tail. */
    private boolean forced(int source)throws IOException{
        int at=source;if(memory.readAddress(at)==0)return false;
        for(int count=0;count<SCAN_LIMIT;count++){
            if(equal(at,0x43de6c,6)||equal(at,0x43de64,5)||equal(at,0x43de5c,4))return true;
            int b=memory.readAddress(at);while(b!=0&&b!='\\'){at++;if(++count>=SCAN_LIMIT)throw new IOException("PC1 forced voice source exceeds known scan budget");b=memory.readAddress(at);}
            if(memory.readAddress(at)=='\\')at++;if(memory.readAddress(at)==0)return false;
        }throw new IOException("PC1 forced voice source exceeds known scan budget");
    }
    /**41CA30 restarts the current table at each backslash, compares3bytes,
     *then reloads the matched row ID AFTER the comparator call. */
    private int character(int source)throws IOException{
        int at=source,b=memory.readAddress(at);
        for(int count=0;count<SCAN_LIMIT;count++,at++){
            if(b==0)return 17;
            if(b=='\\'){
                int row=TABLE;
                for(int rows=0;word(row)!=-1;rows++,row+=12){
                    if(rows>=SCAN_LIMIT)throw new IOException("PC1 live character table exceeds known scan budget");
                    int name=word(row+4);if(equal(at+1,name,3))return word(row);
                }
            }b=memory.readAddress(at+1);
        }throw new IOException("PC1 character source exceeds known scan budget");
    }
}
