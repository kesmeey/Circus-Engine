package org.circus.core;

import java.io.*;
import java.util.*;

/** DCDX actor geometry and wait65 vectors; no original assets. */
public final class DcdxActorTests {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private interface Attempt{void run()throws Exception;}
    private static void reject(Attempt work,String reason)throws Exception{boolean failed=false;try{work.run();}catch(IOException expected){failed=true;}check(failed,reason);}
    private static void rectangle(int[] actor,int width,int height,int alpha,int x,int y,int w,int h,int weight){
        DcdxActorLayout r=DcdxActorLayout.place(actor,width,height,alpha);
        check(r.x==x&&r.y==y&&r.width==w&&r.height==h&&r.weight16==weight,"Native DCDX rectangle/weight "+Arrays.toString(actor));
    }
    private static byte[] save(DcdxActorMotion motion)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();motion.save(new DataOutputStream(out));return out.toByteArray();}
    private static DcdxActorMotion restore(byte[] bytes)throws IOException{DcdxActorMotion m=new DcdxActorMotion();m.load(new DataInputStream(new ByteArrayInputStream(bytes)));return m;}
    private static void geometry(){
        check(DcdxActorLayout.CANVAS_WIDTH==1024&&DcdxActorLayout.CANVAS_HEIGHT==576,"Native viewport independent of actor art");
        rectangle(new int[]{-80,0,100,441,756},882,756,0,-80,0,882,756,16);
        rectangle(new int[]{0,0,100,441,756},1260,1080,7,-185,-317,1260,1080,9);
        rectangle(new int[]{87,5,99,441,322},882,756,16,92,9,873,748,16);
        rectangle(new int[]{-10,20,33,-35,25},100,150,7,-34,37,33,49,16);
        rectangle(new int[]{10,-20,200,35,-25},100,150,7,-25,5,200,300,16);
        rectangle(new int[]{10,-20,0,35,-25},100,150,0,45,-45,0,0,16);
        rectangle(new int[]{10,-20,-1,35,-25},100,150,0,45,-45,-1,-1,16);
        for(int alpha:new int[]{0,1,7,16,17,255}){
            DcdxActorLayout r=DcdxActorLayout.place(new int[]{0,0,100,0,0},882,756,alpha);
            check(r.weight16==(alpha>=1&&alpha<=16?16-alpha:16),"Only native1..16 fade values at100% "+alpha);
            check(DcdxActorLayout.place(new int[]{0,0,75,0,0},882,756,alpha).weight16==16,"Scaled actor ignores fade flag "+alpha);
        }
        for(int[] vector:new int[][]{{8,3,0,4,6},{8,4,0,3,5,7},{5,3,0,2,4},{3,6,0,0,1,1,2,2},{9,1,0}})
            for(int n=2;n<vector.length;n++)check(DcdxActorLayout.stretchSample(vector[0],vector[1],n-2)==vector[n],"Original stretch half-source error sampler "+Arrays.toString(vector)+"/"+(n-2));
    }
    private static void absolute()throws Exception{
        for(int speed=0;speed<4;speed++)for(boolean ctrl:new boolean[]{false,true}){
            DcdxActorMotion m=new DcdxActorMotion();int[][] live=new int[5][3];live[3]=new int[]{-80,100,75};m.start(3,150,-150);
            int distance=ctrl||speed==3?64:speed==2?32:speed==1?16:8;
            check(m.advance(0,speed,ctrl,0,0,0,live)&&live[3][0]==-80+distance&&live[3][1]==100-distance,"Native first poll and independent axes speed "+speed+"/"+ctrl);
            check(!m.advance(9,speed,ctrl,0,0,0,live),"Native10ms interval retains partial phase");
            check(m.advance(1,speed,ctrl,7,2,0,live)&&live[3][0]==-80+2*distance+5,"Absolute ordinary82/83 modifiers");
            int before=live[3][0];m.advance(1000,0,false,0,255,0,live);check(live[3][0]==before+1,"Late timer coalesces once; step has minimum1");
            int n=0;while(m.active&&n++<1000)m.advance(10,3,false,255,0,0,live);
            check(!m.active&&live[3][0]==150&&live[3][1]==-150&&live[3][2]==75,"Absolute target clamp retains zoom");
            check(m.takeClearModifiers()&&!m.takeClearModifiers(),"Absolute completion requests82/83 clear exactly once");
        }
        DcdxActorMotion m=new DcdxActorMotion();int[][] live=new int[5][3];m.start(0,8,0);
        check(m.advance(0,0,false,0,0,2,live)&&m.active&&live[0][0]==8,"Mode2 follows absolute and arrival itself remains waiting");
        check(!m.advance(9,0,false,0,0,2,live)&&m.active,"Arrival still waits for next timer");
        check(!m.advance(1,0,false,0,0,2,live)&&!m.active&&m.takeClearModifiers(),"Next completion poll releases without redraw");
    }
    private static void relativeAndSnapshot()throws Exception{
        int[][] live=new int[5][3];live[0]=new int[]{100,50,125};DcdxActorMotion m=new DcdxActorMotion();m.start(0,5,-3);
        check(m.advance(0,3,true,1,255,1,live)&&live[0][0]==102&&live[0][1]==48,"Relative step uses82+1, ignoring speed/Ctrl/subtract");
        check(m.relativeOriginLatched()&&m.relativeOriginX()==100&&m.relativeOriginY()==50,"Relative target uses latched initial live coordinates");
        m.advance(4,0,false,1,0,1,live);DcdxActorMotion other=restore(save(m));int[][] future=new int[5][3];for(int n=0;n<5;n++)future[n]=live[n].clone();
        check(Arrays.equals(save(m),save(other)),"Relative origin and fractional timer phase snapshot exact");
        for(int ms:new int[]{5,1,10,10,1000}){
            boolean a=m.advance(ms,0,false,1,0,1,live),b=other.advance(ms,0,false,1,0,1,future);
            check(a==b&&Arrays.deepEquals(live,future)&&Arrays.equals(save(m),save(other)),"Snapshot future relative progression "+ms);
        }
        check(!m.active&&live[0][0]==105&&live[0][1]==47&&live[0][2]==125,"Relative completion clamps, keeps zoom");
        check(!m.relativeOriginLatched()&&m.relativeOriginX()==0&&m.relativeOriginY()==0&&!m.takeClearModifiers(),"Relative completion clears origin, preserves82/83");
        DcdxActorMotion immediate=new DcdxActorMotion();immediate.start(1,0,0);check(!immediate.advance(0,0,false,0,0,1,live)&&!immediate.active,"Zero relative offset releases without redraw");
        DcdxActorMotion interrupted=new DcdxActorMotion();interrupted.start(0,50,50);interrupted.advance(0,0,false,0,0,1,live);interrupted.cancel();
        check(!interrupted.active&&interrupted.relativeOriginLatched(),"Abandon wait does not invent writes to native origin globals");
        reject(()->new DcdxActorMotion().start(5,0,0),"Unknown owner rejected");reject(()->new DcdxActorMotion().start(-1,0,0),"Negative owner rejected");
        reject(()->new DcdxActorMotion().start(0,32768,0),"Unsigned-looking coordinate rejected");reject(()->new DcdxActorMotion().start(0,0,-32769),"Target outside native signed word rejected");
        DcdxActorMotion busy=new DcdxActorMotion();busy.start(0,0,0);reject(()->busy.start(1,0,0),"Live wait owner cannot be replaced");
        byte[] bad=save(busy);bad[0]=0;reject(()->restore(bad),"Unversioned motion snapshot rejected");
    }
    public static int run()throws Exception{checks=0;geometry();absolute();relativeAndSnapshot();return checks;}
    public static void main(String[] args)throws Exception{System.out.println("DcdxActorTests: "+run()+" assertions passed");}
}
