package org.circus.core;
import java.io.*;
import java.util.*;

public final class Pc1ScenarioCatalogTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] bytes(String s){byte[] v=new byte[s.length()/2];for(int n=0;n<v.length;n++)v[n]=(byte)Integer.parseInt(s.substring(n*2,n*2+2),16);return v;}
    private static void string(Pc1CatalogMemory m,int at,byte[] text)throws IOException{for(int n=0;n<text.length;n++)m.write(at+n,text[n]);m.write(at+text.length,0);}
    private static final String[] VECTORS={
        "53544f72792e4d4553|73746f7279|1|0|10|eef578c6918d661c22de789a9f75cf81060ef6dc39093c7ff0b0ba416902875d",
        "5359535c53746f72792e6d6573|73746f7279,7379735c73746f7279|2|0|11|84c40c03d91ed367b7e8416de644215b2eeb46be58b9ad579acc5f04e0d31f3b",
        "53746f72792e6d6573|7379735c73746f7279|1|0|0|641fc66366aa483fb0e0acdad55fbdcdb98e18a040ffc6a8511d814b34d1dda8",
        "6469722e77697468646f745c73746f72792e6d6573|73746f7279,646972|2|0|11|84c40c03d91ed367b7e8416de644215b2eeb46be58b9ad579acc5f04e0d31f3b",
        "2e73746f7279|6f74686572,|2|0|11|84c40c03d91ed367b7e8416de644215b2eeb46be58b9ad579acc5f04e0d31f3b",
        "41007461696c|61|1|0|10|eef578c6918d661c22de789a9f75cf81060ef6dc39093c7ff0b0ba416902875d",
        "c04162|e06162,c06142|2|0|11|84c40c03d91ed367b7e8416de644215b2eeb46be58b9ad579acc5f04e0d31f3b",
        "41||0|0|0|641fc66366aa483fb0e0acdad55fbdcdb98e18a040ffc6a8511d814b34d1dda8",
        "41||-1|0|0|641fc66366aa483fb0e0acdad55fbdcdb98e18a040ffc6a8511d814b34d1dda8",
        "41|61,41|2|0|10|eef578c6918d661c22de789a9f75cf81060ef6dc39093c7ff0b0ba416902875d",
        "6669727374|6669727374,4e455854|2|1|10|adeb919946b0022b0cbb193512cf30928a6aadc92c69469ac5c2332ac3330146",
    };
    public static int run()throws Exception{
        checks=0;check(VECTORS.length==11,"all full41AEA0 programs retained");
        for(String raw:VECTORS){String[] v=raw.split("\\|",-1);byte[] initial=new byte[8004],settings=new byte[1748];for(int n=0;n<initial.length;n++)initial[n]=(byte)(0xa5+n*17);Pc1NativeFrame frame=Pc1NativeFrame.fromBytes(initial);Pc1NativeMemory bus=new Pc1NativeMemory(frame,Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int p){return settings[p]&255;}public void write(int p,int value){settings[p]=(byte)value;}});Pc1CatalogMemory m=new Pc1CatalogMemory(bus);m.dword(Pc1CatalogMemory.SCENARIO_COUNT,Integer.parseInt(v[2]));String[] rows=v[1].isEmpty()?new String[0]:v[1].split(",",-1);for(int n=0;n<rows.length;n++){string(m,0x971778+n*188,bytes(rows[n]));string(m,0x9717f8+n*188,("LABEL"+n).getBytes("US-ASCII"));bus.writeAddress(0x454fac+n,10+n);}if(v[3].equals("1"))for(int n=0;n<60;n++)m.write(0x9717f8+n,'A');
            int result=new Pc1ScenarioCatalog(m).prepare(bytes(v[0]));check(result==Integer.parseInt(v[4]),"native AL result from same scene-seen bytes");check(Binary.sha256(frame.bytes()).equals(v[5]),"whole8004 frame matches actual instructions, including stale label suffix");
        }
        Pc1RenderContextTests.Fixture f=new Pc1RenderContextTests.Fixture(new Pc1RenderContextTests.Font(),null);Pc1CatalogMemory m=f.host.pc1.catalogMemory();m.dword(Pc1CatalogMemory.SCENARIO_COUNT,1);string(m,0x971680,new byte[]{'N'});check(Arrays.equals(Pc1TextExpander.expand("$t-2".getBytes("US-ASCII"),f.host.pc1.nativeCatalogTextContext()),"N-2".getBytes("US-ASCII")),"actual4123A0 signed index borrows known system row and retains signed-digit suffix");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" native scenario label checks");}
}
