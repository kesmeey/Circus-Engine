package org.circus.core;

import java.io.*;
import java.util.*;

/** Native palette defaults and narrow migration of snapshots from missing-default implementations. */
public final class DmDdPaletteTests {
    private static int count;
    private static void check(boolean condition,String why){count++;if(!condition)throw new AssertionError(why);}
    private static Dc2Host dm()throws Exception{
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){public long size(){return 20;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,20);return new byte[n];}};}
        });
        return new Dc2Host(new CircusResources(files,Profiles.get("dc2dm-9d72")),TextCodec.identity());
    }
    private static Dc2Host roundtrip(Dc2Host from)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();from.save(new DataOutputStream(bytes));Dc2Host to=dm();to.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));return to;
    }
    private static Dc3Ui roundtrip(Dc3Ui from)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();from.save(new DataOutputStream(bytes));Dc3Ui to=new Dc3Ui();to.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));return to;
    }
    public static int run()throws Exception{
        count=0;Dc2Host dm=dm();
        check(dm.ui.palette[0]==0xff000000&&dm.ui.palette[7]==0xffffffff&&dm.ui.palette[11]==0xffff5f82,"DM starts with its own native foreground and shadow colors");
        dm.ui.button(new int[]{10,20,300,60},new byte[]{'x'});
        for(int state:new int[]{99,1,2})dm.command("ui.button_text",new int[]{20,25,135,state},new byte[]{'A'});
        check(dm.ui.last().sprites.get(0).state==99&&dm.ui.last().sprites.get(1).state==1&&dm.ui.last().sprites.get(2).state==2,"DM native normal, hover and press states are preserved");
        Arrays.fill(dm.ui.palette,0,13,0xffffffff);dm.ui.palette[13]=0xffff8686;Dc2Host old=roundtrip(dm);
        check(old.ui.palette[0]==0xff000000&&old.ui.palette[11]==0xffff5f82&&old.ui.palette[13]==0xffff8686,"Old DM missing defaults migrate while original START palette13 survives");
        check(old.ui.last().sprites.get(0).state==99&&old.ui.last().sprites.get(0).flags==135,"Palette migration preserves saved choice state and shadow style");
        dm.ui.palette[0]=0xff123456;Dc2Host custom=roundtrip(dm);
        check(custom.ui.palette[0]==0xff123456&&custom.ui.palette[9]==0xffffffff,"A customized DM palette is never overwritten by legacy-default migration");
        Dc3Ui dd=new Dc3Ui();check(dd.palette[0]==0xff000000&&dd.palette[7]==0xffffffff&&dd.palette[11]==0xffff7f7f,"DD receives its independently verified palette, distinct from DM pink");
        Arrays.fill(dd.palette,0,13,0);dd.palette[13]=0xffff8686;Dc3Ui oldDd=roundtrip(dd);
        check(oldDd.palette[0]==0xff000000&&oldDd.palette[11]==0xffff7f7f&&oldDd.palette[13]==0xffff8686,"Old DD all-zero defaults migrate without replacing script-defined colors");
        dd.palette[0]=0xff334455;Dc3Ui customDd=roundtrip(dd);
        check(customDd.palette[0]==0xff334455&&customDd.palette[9]==0,"A customized DD palette is preserved exactly");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DM/DD palette assertions");}
}
