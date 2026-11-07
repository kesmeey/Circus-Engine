package org.circus.core;
import java.io.IOException;

/**407040 arms timer1 only while its raw shared token is zero. */
public final class Pc1Timer1 {
    public interface Services {int setTimer(int id,int rawInterval)throws IOException;}
    public static final int TOKEN=0x94c670,DELAY=0x430284;
    private final Pc1NativeMemory bus;private final Services io;private int depth;
    public Pc1Timer1(Pc1NativeMemory bus,Services io)throws IOException{if(bus==null||io==null)throw new IOException("PC1 timer1 requires canonical bytes and actual SetTimer leaf");this.bus=bus;this.io=io;}
    public Pc1Timer1(Pc1Runtime runtime,Services io)throws IOException{this(runtime==null?null:runtime.nativeMemory(),io);runtime.bindNativeBoundary(this::requireBoundary);}
    private int d(int at)throws IOException{return bus.readAddress(at)|bus.readAddress(at+1)<<8|bus.readAddress(at+2)<<16|bus.readAddress(at+3)<<24;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 timer1 snapshot during SetTimer callback");}
    public void arm()throws IOException{depth++;try{if(d(TOKEN)!=0)return;int interval=d(DELAY);int result=io.setTimer(1,interval==0?1:interval);for(int n=0;n<4;n++)bus.writeAddress(TOKEN+n,result>>>(8*n));}finally{depth--;}}
}
