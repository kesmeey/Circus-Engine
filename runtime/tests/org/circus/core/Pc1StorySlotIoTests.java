package org.circus.core;

import java.io.*;
import java.security.*;
import java.util.*;

/** Entire actual41A970/41AAD0 instruction branches, with bounded private CRT
 *journal hooks, supply the literal native hashes and event order below. */
public final class Pc1StorySlotIoTests {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static byte[] pattern(int seed){byte[] b=new byte[8004];for(int n=0;n<b.length;n++)b[n]=(byte)(n*73+(n>>3)+seed);return b;}
    private static String sha(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static String events(List<Pc1StorySlotIo.Event> events){StringBuilder s=new StringBuilder();for(Pc1StorySlotIo.Event e:events){if(s.length()!=0)s.append(',');s.append(e.kind.name());}return s.toString();}
    // BEGIN ACTUAL MACHINE VECTORS
    private static final String[] VECTORS={
        "missing|read|-1|8004|0|1|0|-1|8378bdbad89354a92532324617a70074237db52dd23c73958bbec9773b8bef28|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ",
        "empty|read|5|0|0|1|0|-1|8378bdbad89354a92532324617a70074237db52dd23c73958bbec9773b8bef28|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE",
        "short17|read|5|17|0|1|0|-1|3f513534572c34aad9a41347d30446747155aeaccf8d2fcfe2a30d1227d97506|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE",
        "read_error|read|5|-1|0|1|0|-1|8378bdbad89354a92532324617a70074237db52dd23c73958bbec9773b8bef28|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE",
        "bad_signature|read|5|8004|0|2|0|-1|b5c575cd5ca9a2b81e6f150b4a96b1b1310055fa097e3d7c21f1c7707a87a3e2|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE",
        "close_failure_admitted|read|5|8004|-1|1|0|0|21c4896938bbc6d858bec738361e66b5ef923806edb36bc6bd23589d2ab048fa|21c4896938bbc6d858bec738361e66b5ef923806edb36bc6bd23589d2ab048fa|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE,COPY_TO_SUSPENDED,RECONSTRUCT",
        "reconstruction_result|read|5|8004|0|1|41|41|21c4896938bbc6d858bec738361e66b5ef923806edb36bc6bd23589d2ab048fa|21c4896938bbc6d858bec738361e66b5ef923806edb36bc6bd23589d2ab048fa|cfb5c3bd0012b9e52afc4388fcb3792831ed528a97f449507a63a128bb369644|CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE,COPY_TO_SUSPENDED,RECONSTRUCT",
        "write_missing|write|-1|8004|0|1|0|-1|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|b62118d502fa045531eae0562349973e3c14c8aa6472d79e87ddc3c1a22ac3a9|PREPARE_CANDIDATE,WRITE_SETTINGS,SET_CANDIDATE_SIGNATURE,OPEN_WRITE",
        "write_full|write|5|8004|0|1|0|0|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|b62118d502fa045531eae0562349973e3c14c8aa6472d79e87ddc3c1a22ac3a9|PREPARE_CANDIDATE,WRITE_SETTINGS,SET_CANDIDATE_SIGNATURE,OPEN_WRITE,WRITE,CLOSE",
        "write_short|write|5|17|0|1|0|0|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|b62118d502fa045531eae0562349973e3c14c8aa6472d79e87ddc3c1a22ac3a9|PREPARE_CANDIDATE,WRITE_SETTINGS,SET_CANDIDATE_SIGNATURE,OPEN_WRITE,WRITE,CLOSE",
        "write_close_failure|write|5|8004|-1|1|0|0|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|b62118d502fa045531eae0562349973e3c14c8aa6472d79e87ddc3c1a22ac3a9|PREPARE_CANDIDATE,WRITE_SETTINGS,SET_CANDIDATE_SIGNATURE,OPEN_WRITE,WRITE,CLOSE",
        "write_error|write|5|-1|0|1|0|0|24d9627b1022fc8aa883f031473c8b5ecec90c62c80b8e7d0abaedd1282a5768|54f4f451b41cdbeafc21f116feba52378dc8f6c67fce492ab84800715ae64402|b62118d502fa045531eae0562349973e3c14c8aa6472d79e87ddc3c1a22ac3a9|PREPARE_CANDIDATE,WRITE_SETTINGS,SET_CANDIDATE_SIGNATURE,OPEN_WRITE,WRITE,CLOSE"
    };
    // END ACTUAL MACHINE VECTORS
    private static int number(String[] row,int at){return Integer.parseInt(row[at]);}
    public static int run()throws Exception{
        checks=0;check(VECTORS.length==12,"All actual reader/writer branch vectors are present");
        for(String vector:VECTORS){
            final String[] f=vector.split("\\|",-1);final boolean reading=f[1].equals("read");
            final Pc1NativeFrame working=Pc1NativeFrame.fromBytes(pattern(17)),suspended=Pc1NativeFrame.fromBytes(pattern(29)),candidate=Pc1NativeFrame.fromBytes(pattern(43));
            final byte[] file=Arrays.copyOf(pattern(61),8012);file[0]=(byte)number(f,5);file[1]=file[2]=file[3]=0;
            final int[] calls=new int[4];
            Pc1NativeSettings.NativeIo io=new Pc1NativeSettings.NativeIo(){
                public int open(String path,int flags,int mode)throws IOException{
                    calls[0]++;check(path.equals("owned/dcpc07.dat"),"Resolved owned path is passed to native adapter");check(flags==(reading?0x8000:0x8301)&&mode==(reading?0:0x180),"Native CRT flags/mode "+f[0]);
                    check(reading?working.dword(Pc1NativeSlot.Dword.SIGNATURE)==0:candidate.dword(Pc1NativeSlot.Dword.SIGNATURE)==1,"Signature store precedes open "+f[0]);return number(f,2);
                }
                public int read(int fd,byte[] destination,int requested){calls[1]++;check(fd==5&&requested==8004&&destination.length==8004,"Reader requests exactly native frame; no trailing data");int count=number(f,3);if(count>0)System.arraycopy(file,0,destination,0,Math.min(count,requested));return count;}
                public int write(int fd,byte[] source,int requested){calls[2]++;check(fd==5&&requested==8004&&Arrays.equals(source,candidate.bytes()),"Writer uses the prepared raw candidate");return number(f,3);}
                public int close(int fd){calls[3]++;check(fd==5,"Acquired descriptor closes");return number(f,4);}
            };
            if(reading){
                Pc1StorySlotIo.ReadResult result=Pc1StorySlotIo.read(io,"owned/dcpc07.dat",working,suspended,()->{check(Arrays.equals(working.bytes(),suspended.bytes()),"Reconstruction receives the fully copied suspended native image");return number(f,6);});
                check(result.nativeResult==number(f,7)&&events(result.events).equals(f[11]),"Reader native result/order "+f[0]);
                check(result.admitted==f[11].contains("RECONSTRUCT"),"Only admitted slots invoke complete reconstruction "+f[0]);
            }else{
                Pc1StorySlotIo.WriteResult result=Pc1StorySlotIo.write(io,"owned/dcpc07.dat",candidate,frame->{check(frame.dword(Pc1NativeSlot.Dword.SIGNATURE)!=1,"Metadata precedes signature change");frame.writeU8(Pc1FrameLayout.ORDINARY_OFFSET+902,91);},()->{check(candidate.dword(Pc1NativeSlot.Dword.SIGNATURE)!=1,"Native settings write precedes candidate signature store");return -1;});
                check(result.nativeResult==number(f,7)&&result.settingsResult==-1&&events(result.events).equals(f[11]),"Writer native result/order "+f[0]);
                check(result.fullCountAndCloseSucceeded==(number(f,2)!=-1&&number(f,3)==8004&&number(f,4)==0),"Observed full-write result is distinct from native return "+f[0]);
            }
            check(sha(working.bytes()).equals(f[8]),"Actual native full working frame hash "+f[0]);
            check(sha(suspended.bytes()).equals(f[9]),"Actual native full suspended frame hash "+f[0]);
            check(sha(candidate.bytes()).equals(f[10]),"Actual native full candidate frame hash "+f[0]);
            check(calls[0]==1&&calls[3]==(number(f,2)==-1?0:1),"Native open/close counts "+f[0]);
        }
        final Pc1NativeFrame failedWork=Pc1NativeFrame.fromBytes(pattern(17)),untouched=Pc1NativeFrame.fromBytes(pattern(29));
        final int[] cleanup={0};
        Pc1NativeSettings.NativeIo broken=new Pc1NativeSettings.NativeIo(){
            public int open(String path,int flags,int mode){return 5;}
            public int read(int fd,byte[] destination,int requested)throws IOException{destination[40]=87;throw new IOException("unexpected adapter read");}
            public int write(int fd,byte[] source,int requested)throws IOException{throw new IOException("unexpected adapter write");}
            public int close(int fd)throws IOException{cleanup[0]++;throw new IOException("unexpected adapter close");}
        };
        try{Pc1StorySlotIo.read(broken,"owned/slot",failedWork,untouched,()->{throw new AssertionError("Unexpected read cannot reach reconstruction");});throw new AssertionError("Missing adapter failure");}
        catch(IOException failure){check(failure.getMessage().equals("unexpected adapter read")&&failure.getSuppressed().length==1,"Unexpected read exception remains distinct from native CRT result and preserves cleanup failure");}
        check(cleanup[0]==1&&failedWork.readU8(40)==87&&Arrays.equals(untouched.bytes(),pattern(29)),"Failed adapter retains observed working writes, closes once and leaves suspended image untouched");
        Pc1NativeFrame failedCandidate=Pc1NativeFrame.fromBytes(pattern(43));
        try{Pc1StorySlotIo.write(broken,"owned/slot",failedCandidate,frame->frame.writeU8(40,93),()->0);throw new AssertionError("Missing adapter failure");}
        catch(IOException failure){check(failure.getMessage().equals("unexpected adapter write")&&failure.getSuppressed().length==1,"Unexpected write failure does not fabricate native success");}
        check(cleanup[0]==2&&failedCandidate.readU8(40)==93&&failedCandidate.dword(Pc1NativeSlot.Dword.SIGNATURE)==1,"Failed adapter preserves actual candidate mutation and cleans acquired descriptor");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS Pc1StorySlotIoTests "+run());}
}
