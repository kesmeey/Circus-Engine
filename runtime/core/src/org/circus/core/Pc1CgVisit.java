package org.circus.core;

import java.io.IOException;

/**41ADA0+4252C0's C-locale branch. Catalog/settings are borrowed raw owners;
 *the native function does not save system.dat or normalize a basename/path. */
public final class Pc1CgVisit {
    public static final int COUNT=0xbb84c0,NAMES=0x9bb508,STRIDE=128,CG=0x454dac,CRT_LOCALE=0xbc5e20;
    public interface Catalog {
        int count()throws IOException;
        int nameByte(int ordinal,int byteOffset)throws IOException;
        int localeMode()throws IOException;
    }
    public interface Settings {void writeCgByte(int index,int value)throws IOException;}
    private Pc1CgVisit(){}
    private static int lower(int value){return value>='A'&&value<='Z'?value+32:value;}
    /** Logical array convenience: supplied bytes plus one NUL, no guest VA. */
    public static void visit(final byte[] candidate,Catalog catalog,Settings settings)throws IOException{if(candidate==null)throw new IOException("PC1 CG raw owners missing");visitSource(offset->{if(offset<0||offset>candidate.length)throw new IOException("PC1 CG array suffix unknown");return offset==candidate.length?0:candidate[offset]&255;},catalog,settings);}
    /** Complete41ADA0 local copy prefix. In particular it does not scan the
     *suffix after the first dot, and samples count before writing local NUL. */
    public static void visitSource(Pc1ByteSource candidate,Catalog catalog,Settings settings)throws IOException{
        if(candidate==null||catalog==null||settings==null)throw new IOException("PC1 CG raw owners missing");byte[] local=new byte[260];int length=0,current=candidate.unsignedByte(0)&255;
        while(current!=0&&current!='.'){if(length>=local.length)throw new IOException("PC1 CG candidate local260 overflow");local[length]=(byte)current;current=candidate.unsignedByte(length+1)&255;length++;}
        int count=catalog.count();if(length>=local.length)throw new IOException("PC1 CG candidate local260 overflow");local[length]=0;if(count<=0)return;
        int ordinal=0;for(;;){
            int locale=catalog.localeMode();
            for(int offset=0;;offset++){
                if(offset>=local.length)throw new IOException("PC1 CG comparison reads unknown local260 suffix");int input=local[offset]&255,name=catalog.nameByte(ordinal,offset)&255;
                if(input!=name){if(locale!=0)throw new IOException("PC1 CG CRT non-C locale case mapping requires42534C owner");if(lower(input)!=lower(name))break;}
                if(input==0){settings.writeCgByte(ordinal,1);return;}
            }
            count=catalog.count();ordinal++;if(ordinal>=count)return;
        }
    }
}
