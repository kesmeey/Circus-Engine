package org.circus.core;
import java.io.*;
import java.util.*;

/** Physical MSG parameters never become invented x86 pointer DWORDs. */
public final class Pc1PhysicalMessageTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private interface Work{void run()throws IOException;}
    private static void unknown(Work work)throws IOException{boolean bad=false;try{work.run();}catch(IOException expected){bad=true;}check(bad,"unknown physical or native field must remain unknown");}
    private static byte[] save(Pc1Startup.Message message)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();message.save(new DataOutputStream(out));return out.toByteArray();}
    public static int run()throws Exception{
        checks=0;Pc1Startup.Message message=new Pc1Startup.Message();unknown(message::physical);unknown(()->message.get(8));
        long[] actual={0x123456780L,0x8001,0x1000000123L,0x2000000456L,0xfedcba98L,-500,600};message.physical(actual);check(Arrays.equals(actual,message.physical()),"all modern MSG fields retain their exact physical widths");unknown(()->message.get(0));unknown(()->message.get(8));unknown(()->message.get(12));check(message.get(4)==0x8001&&message.get(16)==0xfedcba98&&message.get(20)==-500,"native scalar payload remains readable without HWND/pointer truncation");
        byte[] before=save(message);List<String> kinds=new ArrayList<>();Pc1Startup.Message restored=new Pc1Startup.Message();restored.load(new DataInputStream(new ByteArrayInputStream(before)),(kind,value)->{kinds.add(kind);return value;});check(kinds.equals(Arrays.asList("message-window","message-wparam","message-lparam"))&&Arrays.equals(before,save(restored)),"snapshot requires explicit physical associations and stores each width once");check(Arrays.equals(actual,restored.physical()),"physical MSG codec restores the exact OS payload");
        Pc1Startup.Message rebound=new Pc1Startup.Message();rebound.load(new DataInputStream(new ByteArrayInputStream(before)),(kind,value)->value+0x100000000L);long[] moved=rebound.physical();check(moved[0]==actual[0]+0x100000000L&&moved[2]==actual[2]+0x100000000L&&moved[3]==actual[3]+0x100000000L&&moved[4]==actual[4],"adoption may rebind actual physical parameters independently of DWORD scalars");
        message.put(8,0x89abcdef);message.put(12,-123);check(message.get(8)==0x89abcdef&&message.get(12)==-123&&message.physical()[2]==0x89abcdefL&&message.physical()[3]==-123,"explicit native DWORD writes retire wide associations and use WPARAM unsigned/LPARAM signed semantics");
        message.physical(actual);message.write(8,new byte[]{7},0,1);unknown(message::physical);check(message.known()[8]==1&&message.known()[9]==0,"partial native overwrite cannot leave a stale wide association");message.physical(actual);message.write(0,new byte[]{9},0,1);unknown(message::window);message.put(0,0xf2345678);check(message.window()==0xf2345678L,"whole observed x86 HWND DWORD explicitly replaces the old physical association");message.window(actual[0]);unknown(()->message.get(0));check(message.physical()[0]==actual[0],"new physical HWND retires obsolete observed32 raw bytes");message.invalidatePhysical();unknown(message::physical);unknown(message::window);unknown(()->message.get(4));message.physical(new long[]{0,0x12,-1,0,0,0,0});check(message.exitCode()==-1&&message.physical()[2]==-1,"typed WM_QUIT exit code preserves negative int without narrowing OS transport");unknown(()->message.get(8));return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" physical MSG ownership checks");}
}
