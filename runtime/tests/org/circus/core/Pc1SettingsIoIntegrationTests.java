package org.circus.core;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** The native read owns locals until after close; Runtime fences those locals
 *and the legacy importer cannot replace the RAM once native IO is bound. */
public final class Pc1SettingsIoIntegrationTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static int run()throws Exception{
        checks=0;
        PcHost host=Pc1SaveContextTests.fixture(0);
        Pc1Runtime runtime=host.pc1;
        Pc1NativeMemory bus=runtime.nativeMemory();
        byte[] root="owned".getBytes(StandardCharsets.US_ASCII);
        for(int n=0;n<=root.length;n++)bus.writeAddress(Pc1SettingsIo.SAVE_ROOT+n,n==root.length?0:root[n]);
        bus.writeAddress(Pc1SettingsIo.SETTINGS,0xe2);
        final int[] opens={0},closes={0};
        Pc1SettingsIo settings=new Pc1SettingsIo(bus,(path,flags,hasMode,mode)->{
            opens[0]++;
            check(new String(Pc1SettingsIo.cstring(path),StandardCharsets.US_ASCII).equals("owned\\system.dat"),"native path borrows current startup root and PE suffix");
            check(flags==(hasMode?0x8301:0x8000)&&(!hasMode||mode==0x180),"native settings preserves actual open call ABI");
            boolean fenced=false;ByteArrayOutputStream snapshot=new ByteArrayOutputStream();
            try{runtime.save(new DataOutputStream(snapshot));}catch(IOException expected){fenced=expected.getMessage().contains("settings snapshot");}
            check(fenced&&snapshot.size()==0,"whole Runtime save rejects active settings locals before publishing bytes");
            if(hasMode)bus.writeAddress(Pc1SettingsIo.SETTINGS,0x65);
            return new Pc1SettingsIo.Opened(){
                private Pc1SettingsIo.Destination local;
                public int descriptor(){return 7;}
                public int read(Pc1SettingsIo.Destination target,int count)throws IOException{
                    check(count==1748&&bus.readAddress(Pc1SettingsIo.SETTINGS)==0xe2,"read begins with unchanged live settings and exact native destination length");
                    local=target;for(int n=0;n<count;n++)target.write(n,n*37+11);return count;
                }
                public int write(Pc1SettingsIo.Source live,int count)throws IOException{
                    check(count==1748&&live.unsignedByte(0)==0x65,"writer reads same live RAM after open callback mutation");
                    bus.writeAddress(Pc1SettingsIo.SETTINGS+1,0x43);
                    check(live.unsignedByte(1)==0x43,"writer retains a live source during its own consumption");return -1;
                }
                public int close()throws IOException{
                    closes[0]++;if(local!=null){check(bus.readAddress(Pc1SettingsIo.SETTINGS)==0xe2,"close precedes any local-to-global settings publication");local.write(0,0x99);}return -1;
                }
            };
        });
        runtime.bindNativeSettingsIo(settings);
        check(runtime.nativeSettingsIo()==settings&&bus.readAddress(Pc1SettingsIo.SETTINGS)==0xe2,"binding takes authority without replacing native RAM");
        Pc1SettingsIo.Result result=settings.read();
        check(result.nativeResult==0&&result.closeResult==-1&&bus.readAddress(Pc1SettingsIo.SETTINGS)==0x99,"exact read copies locals only after close, retaining its late mutation and ignored close error");
        check(host.nativeSettings().nativeBytes()[0]==(byte)0x99&&host.nativeSettingsReadResult()==null,"legacy accessor observes native RAM without running an importer or fabricating diagnostics");
        result=settings.write();
        check(result.nativeResult==0&&result.writeCount==-1&&opens[0]==2&&closes[0]==2,"native writer returns its proved result despite ordinary write/close failure");
        boolean duplicate=false;try{runtime.bindNativeSettingsIo(settings);}catch(IOException expected){duplicate=true;}
        check(duplicate,"one canonical settings consumer per Runtime");
        settings.requireBoundary();return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" settings IO integration checks");}
}
