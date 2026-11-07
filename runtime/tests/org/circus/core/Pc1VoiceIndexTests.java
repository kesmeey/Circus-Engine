package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Independently interpreted original CRT qsort/lookup vectors, not a Java
 *sort used to bless its own ties or repaired binary-search endpoints. */
public final class Pc1VoiceIndexTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    // Independently interpreted currentCHS420BE5/41BD50/425210 vectors.
    // Full artifact SHA b91ec58d2d230bb461ce7d9c6476e13f0bdd83c1ef9b7ae2843fcae9b30de65a.
    private static final int[][] PORTABLE={
        new int[]{7,1,1,5,6,0,3,2,4},
        new int[]{8,1,1,6,5,0,7,3,2,4},
        new int[]{9,0,0,4,8,3,7,2,6,1,5},
        new int[]{9,1,1,5,0,6,3,2,7,8,4},
        new int[]{16,2,0,8,1,9,2,10,3,11,4,12,5,13,6,14,7,15},
        new int[]{129,1,0,1,65,106,126,5,6,46,85,125,10,11,90,81,121,15,16,80,76,120,20,21,91,105,116,25,26,51,71,115,30,31,95,70,111,35,36,96,66,110,40,41,50,60,86,45,75,101,61,100,56,55,18,38,22,43,53,57,58,42,37,52,62,63,128,3,33,67,68,32,23,28,72,73,27,13,17,77,78,12,8,48,82,83,7,47,2,87,88,108,123,122,92,93,127,118,117,97,98,113,112,102,103,107,104,54,59,49,99,84,39,34,69,74,114,29,24,79,94,119,19,14,44,89,124,9,4,109,64},
        new int[]{129,2,0,128,113,98,83,68,53,38,23,8,121,106,91,76,61,46,31,16,1,114,99,84,69,54,39,24,9,122,107,92,77,62,47,32,17,2,115,100,85,70,55,40,25,10,123,108,93,78,63,48,33,18,3,116,101,86,71,56,41,26,11,124,109,94,79,64,49,34,19,4,117,102,87,72,57,42,27,12,125,110,95,80,65,50,35,20,5,118,103,88,73,58,43,28,13,126,111,96,81,66,51,36,21,6,119,104,89,74,59,44,29,14,127,112,97,82,67,52,37,22,7,120,105,90,75,60,45,30,15},
    };
    private static final String[] LOOKUPS={
        "10:564f4943455c303030302e6f6c64:0:0",
        "10:585c612e776176:0:65536",
        "10:6e6f536c617368:0:0",
        "10:564f4943455c7a7a7a7a:1:0",
        "10:766f6963655c80302e78:1:0",
        "10:585c622e6f6c64:0:66050",
        "10:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:1:0",
        "10:585c:0:0",
        "10:585c612e70636d0069676e6f726564:0:65536",
        "13:564f4943455c303030302e6f6c64:0:0",
        "13:585c612e776176:0:67078",
        "13:6e6f536c617368:0:0",
        "13:564f4943455c7a7a7a7a:1:0",
        "13:766f6963655c80302e78:1:0",
        "13:585c622e6f6c64:0:67335",
        "13:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:1:0",
        "13:585c:0:0",
        "13:585c612e70636d0069676e6f726564:0:67078",
        "15:564f4943455c303030302e6f6c64:0:65536",
        "15:585c612e776176:1:0",
        "15:6e6f536c617368:0:0",
        "15:564f4943455c7a7a7a7a:1:0",
        "15:766f6963655c80302e78:1:0",
        "15:585c622e6f6c64:1:0",
        "15:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:1:0",
        "15:585c:0:0",
        "15:585c612e70636d0069676e6f726564:1:0",
        "16:564f4943455c303030302e6f6c64:0:0",
        "16:585c612e776176:0:66821",
        "16:6e6f536c617368:0:0",
        "16:564f4943455c7a7a7a7a:1:0",
        "16:766f6963655c80302e78:1:0",
        "16:585c622e6f6c64:0:66307",
        "16:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:1:0",
        "16:585c:0:0",
        "16:585c612e70636d0069676e6f726564:0:66821",
        "20:564f4943455c303030302e6f6c64:0:0",
        "20:585c612e776176:0:0",
        "20:6e6f536c617368:0:0",
        "20:564f4943455c7a7a7a7a:0:0",
        "20:766f6963655c80302e78:0:65536",
        "20:585c622e6f6c64:0:0",
        "20:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:0:0",
        "20:585c:0:0",
        "20:585c612e70636d0069676e6f726564:0:0",
        "28:564f4943455c303030302e6f6c64:0:0",
        "28:585c612e776176:0:73503",
        "28:6e6f536c617368:0:0",
        "28:564f4943455c7a7a7a7a:1:0",
        "28:766f6963655c80302e78:1:0",
        "28:585c622e6f6c64:0:98432",
        "28:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:1:0",
        "28:585c:0:0",
        "28:585c612e70636d0069676e6f726564:0:73503",
        "29:564f4943455c303030302e6f6c64:0:0",
        "29:585c612e776176:0:0",
        "29:6e6f536c617368:0:0",
        "29:564f4943455c7a7a7a7a:0:0",
        "29:766f6963655c80302e78:0:65536",
        "29:585c622e6f6c64:0:0",
        "29:564f4943455c666f6c6465722e6578742f6e616d652e6f6c64:0:0",
        "29:585c:0:0",
        "29:585c612e70636d0069676e6f726564:0:0",
    };
    private static byte[] hex(String text){byte[] out=new byte[text.length()/2];for(int n=0;n<out.length;n++)out[n]=(byte)Integer.parseInt(text.substring(n*2,n*2+2),16);return out;}
    /** Self-contained CoreTests entry; expected bytes/order come from actual
     *instruction execution, including every high prefix byte and duplicate tie. */
    public static int run()throws IOException{
        checks=0;Map<Integer,Pc1VoiceIndex> indices=new HashMap<>();int[] ids={10,13,15,16,20,28,29};
        for(int c=0;c<PORTABLE.length;c++){int[] vector=PORTABLE[c];int count=vector[0],pattern=vector[1];byte[] raw=new byte[count*64];
            for(int n=0;n<count;n++){for(int k=0;k<64;k++)raw[n*64+k]=(byte)(k*19+n*23);byte[] name=pattern==0?String.format(Locale.ROOT,"%04d.pcm",(n*7)%count).getBytes(java.nio.charset.StandardCharsets.US_ASCII):pattern==1?new String[]{"A.pcm","a.PCM","B.pcm","b.PCM","c.pcm"}[n%5].getBytes(java.nio.charset.StandardCharsets.US_ASCII):new byte[]{(byte)(0x80+(n*17)%128),(byte)('0'+n%3),'.','p','c','m'};System.arraycopy(name,0,raw,n*64,name.length);raw[n*64+name.length]=0;int offset=0x10000+n*257;for(int k=0;k<4;k++)raw[n*64+60+k]=(byte)(offset>>>(k*8));}
            Pc1VoiceIndex index=Pc1VoiceIndex.fromRawTable(count,raw,0);indices.put(ids[c],index);check(Arrays.equals(index.originalRows(),raw),"Portable full original row preservation");for(int n=0;n<count;n++){int original=vector[n+2];check(index.originalRow(n)==original&&Arrays.equals(index.rowBytes(n),Arrays.copyOfRange(raw,original*64,original*64+64)),"Actual native sort row/tie/unknown tail:"+ids[c]+":"+n);}
        }
        for(String encoded:LOOKUPS){String[] fields=encoded.split(":",-1);boolean fault=fields[2].equals("1"),failed=false;long result=0;try{result=indices.get(Integer.parseInt(fields[0])).lookup(hex(fields[1]),new byte[]{'.','p','c','m'},0);}catch(IOException error){failed=error.getMessage().contains("probes unowned row");if(!failed)throw error;}check(fault?failed:!failed&&result==Long.parseLong(fields[3]),"Native lookup portable counterexample");}
        for(String encoded:LOOKUPS){String[] fields=encoded.split(":",-1);final byte[] source=Arrays.copyOf(hex(fields[1]),hex(fields[1]).length+1),extension={'.','p','c','m',0};Pc1NativePaths.Memory memory=new Pc1NativePaths.Memory(){public int read(int at)throws IOException{if(at>=0x30000000&&at<0x30000000+source.length)return source[at-0x30000000]&255;if(at>=0x43dd94&&at<0x43dd94+extension.length)return extension[at-0x43dd94]&255;throw new IOException("Unknown raw source backing");}public void write(int at,int value)throws IOException{throw new IOException("Raw caller remains read-only");}};boolean fault=fields[2].equals("1"),failed=false;long result=0;try{result=indices.get(Integer.parseInt(fields[0])).lookupRaw(memory,0x30000000,0x43dd94,0);}catch(IOException error){failed=error.getMessage().contains("probes unowned row");if(!failed)throw error;}check(fault?failed:!failed&&result==Long.parseLong(fields[3]),"Actual native vectors also match addressed separator/strlen/word copy path");}
        byte[] expected=Base64.getDecoder().decode("AAAAAAAAAAAAAEAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAA");for(int position=0;position<6;position++)for(int value=0;value<256;value++){byte[] raw={'V','O','I','C','E','\\'};raw[position]=(byte)value;int at=position*256+value;check(Pc1VoiceIndex.prefix(raw,new byte[]{'V','O','I','C','E','\\'},6,0)==((expected[at/8]>>>(at%8)&1)!=0),"Actual CRT prefix all-byte portable vector");}
        return checks;
    }
    public static void main(String[] args)throws Exception{
        if(args.length==0){System.out.println("Pc1VoiceIndexTests portable PASS "+run());return;}
        if(args.length!=2)throw new IOException("Expected original instruction voice/prefix vector files");
        try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[0])))){int programs=in.readInt();for(int n=0;n<programs;n++){int count=in.readInt();byte[] original=new byte[count*64],expected=new byte[count*64];in.readFully(original);in.readFully(expected);Pc1VoiceIndex index=Pc1VoiceIndex.fromRawTable(count,original,0);check(Arrays.equals(original,index.originalRows()),"All original row bytes retained:"+n);check(Arrays.equals(expected,index.sortedRows()),"Full64byte CRT sort incl duplicate/high-byte order:"+n);int queries=in.readInt();for(int k=0;k<queries;k++){byte[] raw=new byte[in.readInt()];in.readFully(raw);boolean fault=in.readBoolean();long value=in.readInt()&0xffffffffL;boolean failed=false;long actual=0;try{actual=index.lookup(raw,new byte[]{'.','p','c','m'},0);}catch(IOException e){failed=e.getMessage().contains("probes unowned row");if(!failed)throw e;}check(fault?failed:!failed&&actual==value,"Exact native lookup upper bound/name/extension:"+n+":"+k);}}check(in.read()==-1,"All native sort/lookup vectors consumed");}
        try(DataInputStream in=new DataInputStream(Files.newInputStream(Paths.get(args[1])))){int n=in.readInt();for(int k=0;k<n;k++){byte[] raw=new byte[6];in.readFully(raw);boolean expected=in.readBoolean();check(Pc1VoiceIndex.prefix(raw,new byte[]{'V','O','I','C','E','\\'},6,0)==expected,"Actual bounded CRT prefix bytes:"+k);}check(in.read()==-1,"Prefix native vectors consumed");}
        boolean failed=false;try{Pc1VoiceIndex.fromRawTable(1,new byte[63],0);}catch(IOException e){failed=e.getMessage().contains("not fully known");}check(failed,"Short heap read never zero-filled");failed=false;try{Pc1VoiceIndex.fromRawTable(0,new byte[0],1);}catch(IOException e){failed=e.getMessage().contains("locale");}check(failed,"Unknown locale branch remains explicit");Pc1VoiceIndex empty=Pc1VoiceIndex.fromRawTable(0,new byte[0],0);failed=false;try{empty.lookup(new byte[]{'A'},new byte[]{'.','p','c','m'},0);}catch(IOException e){failed=e.getMessage().contains("row:0");}check(failed,"Native hi=count on empty non-null allocation probes row0");byte[] name=new byte[82];Arrays.fill(name,(byte)'a');name[0]='\\';failed=false;try{empty.lookup(name,new byte[]{'.','p','c','m'},0);}catch(IOException e){failed=e.getMessage().contains("80byte");}check(failed,"Raw lookup does not grow native stack buffer");
        System.out.println("Pc1VoiceIndexTests PASS "+checks+"; actual CRT sort/lookup/prefix vectors; no original EXE process");
    }
}
