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
    public static void visit(byte[] candidate,Catalog catalog,Settings settings)throws IOException{
        if(candidate==null||catalog==null||settings==null)throw new IOException("PC1 CG raw owners missing");int length=0;while(length<candidate.length&&candidate[length]!=0&&candidate[length]!='.')length++;
        if(length>=260)throw new IOException("PC1 CG candidate local260 overflow");
        for(int ordinal=0;ordinal<catalog.count();ordinal++){
            if(catalog.localeMode()!=0)throw new IOException("PC1 CG CRT non-C locale case mapping requires42534C owner");
            for(int offset=0;offset<1024*1024;offset++){
                int input=offset<length?candidate[offset]&255:0,name=catalog.nameByte(ordinal,offset)&255;
                if(lower(input)!=lower(name))break;
                if(input==0){settings.writeCgByte(ordinal,1);return;}
                if(offset==1024*1024-1)throw new IOException("PC1 CG catalog string exceeds known host scan bound");
            }
        }
    }
}
