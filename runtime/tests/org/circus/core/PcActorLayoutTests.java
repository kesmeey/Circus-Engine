package org.circus.core;

/** Values independently calculated from DC2PC 41AA7D..41AB14 integer divisions. */
public final class PcActorLayoutTests {
    private static int checks;
    private static void rectangle(int[] actor,int w,int h,int x,int y,int rw,int rh,String why){
        PcActorLayout r=PcActorLayout.dc2pc(actor,w,h);checks++;
        if(r.x!=x||r.y!=y||r.width!=rw||r.height!=rh)throw new AssertionError(why+": "+r.x+","+r.y+","+r.width+","+r.height);
    }
    public static int run(){
        checks=0;
        rectangle(new int[]{-125,0,100,441,296},1260,1080,-310,-124,1260,1080,"Dacapo Otome back-view anchor");
        rectangle(new int[]{87,0,75,441,322},882,756,198,81,661,567,"Dacapo Sakura at 75 percent");
        rectangle(new int[]{100,0,100,441,322},1260,1080,-85,-135,1260,1080,"Later reverse-angle Sakura back-view");
        rectangle(new int[]{-87,0,75,441,296},882,756,24,74,661,567,"Later reverse-angle Otome");
        rectangle(new int[]{8,-9,100,441,296},882,756,8,-9,882,756,"Ordinary canvas must not move");
        rectangle(new int[]{0,0,100,441,296},891,764,-4,-2,891,764,"Two integer divisions rather than floating point scaling");
        rectangle(new int[]{3,4,100,-441,-296},1260,1080,188,128,1260,1080,"Signed division truncates toward zero");
        rectangle(new int[]{8,9,50,441,296},1260,1080,229,157,630,540,"Explicit zoom does not apply the native-size branch");
        rectangle(new int[]{0,0,100,441,296},1260,756,-185,0,1260,756,"Oversize width and height are independent");
        return checks;
    }
    public static void main(String[] args){System.out.println("PC ACTOR LAYOUT PASS "+run());}
}
