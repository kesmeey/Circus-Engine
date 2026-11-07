package org.circus.core;

import java.io.*;
import java.util.*;

/** FL4204C0 exact integer and clock/f32 vectors, independent of Pc2Host's10ms mover. */
public final class FlActorMotionTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static byte[] bytes(FlActorMotion m)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();m.save(new DataOutputStream(b));return b.toByteArray();}
    public static int run()throws Exception {
        checks=0;int[][] live=new int[5][3];FlActorMotion m=new FlActorMotion();m.start(0,17,-17);
        check(m.advance(1,0,false,0,live)&&live[0][0]==8&&live[0][1]==-8&&m.active,"speed0 moves eachaxis8 pernativepoll");
        m.advance(1000,0,false,0,live);check(live[0][0]==16&&live[0][1]==-16&&m.active,"lateevent coalesces to one8-step");
        m.advance(1,0,false,0,live);check(live[0][0]==17&&live[0][1]==-17&&m.active,"arriving update still holds wait65");
        check(!m.advance(1,0,false,0,live)&&!m.active,"nextaligned updater releases without another redraw");
        for(int speed=0;speed<=3;speed++){m.start(1,1000,1000);m.advance(1,speed,false,0,live);check(live[1][0]==(speed==0?8:speed==1?16:speed==2?32:64),"native speed step table "+speed);m=new FlActorMotion();live=new int[5][3];}
        m.start(2,1000,1000);m.advance(1,0,true,0,live);check(live[2][0]==64&&live[2][1]==64,"Ctrl selects64-step without completing wholemotion");
        m=new FlActorMotion();live=new int[5][3];m.start(0,1000,-1000);check(!m.advance(35,0,false,1,live)&&m.clockBase()==0,"timebranch delta<36 performsnodraw");
        check(!m.advance(1,0,false,1,live)&&m.clockBase()==36,"firsteligible tick recordsbase only");
        check(!m.advance(35,0,false,1,live),"relative35ms still gated");
        check(m.advance(1,0,false,1,live)&&live[0][0]==8&&live[0][1]==-8,"36ms nativefloatstep8.228... truncatesaxis result");
        m.advance(36,0,false,1,live);check(live[0][0]==24&&live[0][1]==-24&&m.clockBase()==36,"base is not reset:72ms step16.457... accelerates");
        byte[] saved=bytes(m);FlActorMotion copy=new FlActorMotion();copy.load(new DataInputStream(new ByteArrayInputStream(saved)));int[][] same=new int[5][3];for(int n=0;n<5;n++)same[n]=live[n].clone();
        for(int ms:new int[]{0,1,36,1000,1}){boolean a=m.advance(ms,0,false,1,live),b=copy.advance(ms,0,false,1,same);check(a==b&&Arrays.deepEquals(live,same)&&Arrays.equals(bytes(m),bytes(copy)),"snapshot reproduces x87 residue and clock updater");}
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("FlActorMotionTests "+run()+" PASS");}
}
