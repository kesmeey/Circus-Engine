package org.circus.core;

import java.io.IOException;

/** Complete current404E70, includingitsactual404EE0(1,1) prefix. All
 *ordinary/candidate bytes remain in the existing four-frame canonical bus. */
public final class Pc1SavePreparation {
    public interface Clock {
        /**ExacteightSYSTEMTIME WORD fields, in nativeorder:year,month,
         *dayOfWeek,day,hour,minute,second,milliseconds. No UTC substitution. */
        int[] localSystemTime()throws IOException;
    }
    private final Pc1NativeMemory bus;private final Clock clock;private int depth;
    public Pc1SavePreparation(Pc1NativeMemory bus,Clock clock)throws IOException{if(bus==null||clock==null)throw new IOException("PC1 save preparation requires canonical frames and native localtime");this.bus=bus;this.clock=clock;}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 save preparation snapshot during native localtime callback");}
    /**404EE0 selector1/mode1 uses STORY month/day, with fixed28-dayFebruary.
     *This is not OSweekday or the complete404EE0 selector dispatcher. */
    private void scenarioWeekday()throws IOException{int sum=bus.readNativeOrdinary(9,1),month=bus.readNativeOrdinary(9,0);int[] days={31,28,31,30,31,30,31,31,30,31,30};for(int n=0;n<days.length;n++)if(month>n+1)sum+=days[n];bus.writeNativeOrdinary(9,2,(sum+1)%7);}
    public void prepare()throws IOException{
        depth++;try{
            scenarioWeekday();int weekday=bus.readAddress(0x9d957a);bus.writeAddress(0x9ced1a,weekday);
            int[] time=clock.localSystemTime();if(time==null||time.length!=8)throw new IOException("PC1 native SYSTEMTIME owner missing its eight WORD fields");
            int day=time[3]&255,metadata=bus.readAddress(0x9d958c),month=time[1]&255;bus.writeAddress(0x9ced2e,day);int year=time[0]&255;bus.writeAddress(0x9ced2c,metadata);int hour=time[4]&255;bus.writeAddress(0x9ced2d,month);int minute=time[5]&255;year+=0x30;bus.writeAddress(0x9ced2f,hour);bus.writeAddress(0x9ced30,minute);bus.writeAddress(0x9ced31,year);
        }finally{depth--;}
    }
}
