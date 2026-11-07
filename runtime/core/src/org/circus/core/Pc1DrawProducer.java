package org.circus.core;

import java.io.*;

/** Current PC1 native53/5C/22/21/5D/2A/60 physical producers. Canonical frame
 * owns PC/arguments/count/records;State routes external condition/origin and
 * raw template memory. No Dc2Ui records are manufactured as a second owner. */
public final class Pc1DrawProducer {
    private Pc1DrawProducer(){}
    public static final int ORIGIN_X=0xbb8460,ORIGIN_Y=0xbb8464,CONDITION=0xbb8430;
    public static final int SAVED_LIST_OFFSET=0xbb8440,LIST_OFFSET=0x9553e8;
    public static final int TARGET_ACTOR1_X=0x114c,TARGET_ACTOR1_Y=0x1150,TARGET_ACTOR2_X=0x1154,TARGET_ACTOR2_Y=0x1158;
    private static final int[] CAPACITY={0,10,5,20,10,5,20,20};
    private static final int[] TEMPLATE={0,0x9d7d90,0x9d8020,0x9d23d0,0x9d7ed0,0x9d80c0,0x9d2650,0x9d28d0};
    private static final int[] TEMPLATE_COUNT={0,0xbb84c8,0xbb84d0,0xbb84d8,0xbb84cc,0xbb84d4,0xbb84dc,0xbb84e0};
    public interface Code {
        /** Known loaded code bytes, relative to native9A077C. Must reject
         * unowned addresses rather than inventing a zero/stale suffix. */
        int byteAt(int nativeOffset)throws IOException;
    }
    public interface Services {
        /**selector1→415D50(0,0,0,0,2),selector2→416220(...,2).
         * Called after template bytes/count are visible,before pending clear. */
        void geometryMode2(int selector)throws IOException;
        /**Exact4120C0 call;no default scene/UI approximation. */
        void prepareUi(int param1,int param2)throws IOException;
    }
    private static void owners(Pc1NativeFrame f,Pc1Compositor.State s)throws IOException{if(f==null||s==null)throw new IOException("PC1 draw producer requires canonical frame/global owners");}
    private static int readByte(Code code,int offset)throws IOException{if(code==null)throw new IOException("PC1 draw producer has no loaded-code byte owner");int value=code.byteAt(offset);if(value<0||value>255)throw new IOException("PC1 code byte outside unsigned8 range");return value;}
    /**4118D0 reads the high byte first,then low,and returns signed16. */
    private static int word(Code code,int offset)throws IOException{int high=readByte(code,offset+1),low=readByte(code,offset);return(short)(high<<8|low);}
    private static void record(Pc1NativeFrame frame,int field,int value)throws IOException{
        // Native SHL/LEA wrap at32 bits. Negative count may alias earlier or
        // wrapped frame cells;each store reloads the live count independently.
        int address=Pc1FrameLayout.LIVE_BASE+Pc1FrameLayout.PENDING_OFFSET+(frame.readI32(Pc1FrameLayout.PENDING_COUNT)<<5)+field*4;
        long offset=(address&0xffffffffL)-Pc1FrameLayout.LIVE_BASE;if(offset<0||offset+4>Pc1FrameLayout.FRAME_BYTES)throw new IOException("PC1 producer store reaches unowned frame address "+Integer.toHexString(address));frame.writeI32((int)offset,value);
    }
    private static void condition(Pc1NativeFrame frame,Pc1Compositor.State state)throws IOException{int value=state.readInt(CONDITION);record(frame,3,value);if(value!=0)state.writeInt(CONDITION,0);}
    public static void markCondition2a(Pc1Compositor.State state)throws IOException{if(state==null)throw new IOException("PC1 condition owner missing");state.writeInt(CONDITION,1);}
    /**Entry PC points at the skipped companion marker. Full count still
     *advances9 bytes andsets pending/visibility,without reading the8 bytes. */
    public static void quad53(Pc1NativeFrame frame,Pc1Compositor.State state,Code code,int arg2,int arg3)throws IOException{
        owners(frame,state);int x=frame.readI32(Pc1FrameLayout.ARGUMENT0),count=frame.readI32(Pc1FrameLayout.PENDING_COUNT),pc=frame.readI32(Pc1FrameLayout.RAW_CODE_PC)+1;frame.writeI32(Pc1FrameLayout.RAW_CODE_PC,pc);
        if(count<60){
            record(frame,0,state.readInt(ORIGIN_X)+x);record(frame,1,state.readInt(ORIGIN_Y)+frame.readI32(Pc1FrameLayout.ARGUMENT1));
            record(frame,6,word(code,frame.readI32(Pc1FrameLayout.RAW_CODE_PC))-arg2);record(frame,7,word(code,frame.readI32(Pc1FrameLayout.RAW_CODE_PC)+2)-arg3);
            record(frame,4,state.readInt(ORIGIN_X)-arg2+frame.readI32(Pc1FrameLayout.ARGUMENT0));record(frame,5,state.readInt(ORIGIN_Y)-arg3+frame.readI32(Pc1FrameLayout.ARGUMENT1));
            record(frame,2,word(code,frame.readI32(Pc1FrameLayout.RAW_CODE_PC)+4));condition(frame,state);frame.writeI32(Pc1FrameLayout.PENDING_COUNT,frame.readI32(Pc1FrameLayout.PENDING_COUNT)+1);pc=frame.readI32(Pc1FrameLayout.RAW_CODE_PC);
        }
        pc+=8;state.writeInt(Pc1Compositor.COMPOSITION_PENDING,1);frame.writeI32(Pc1FrameLayout.RAW_CODE_PC,pc);state.writeInt(Pc1Compositor.VISIBILITY,0);
    }
    /**5C is not53:it never sets pending/visibility andalways skips the raw
     *marker+NUL payload,even atfull count. Coordinates do notadd UI origin. */
    public static void text5c(Pc1NativeFrame frame,Pc1Compositor.State state,Code code,int newlinePitch,int style)throws IOException{
        owners(frame,state);int x=frame.readI32(Pc1FrameLayout.ARGUMENT0),count=frame.readI32(Pc1FrameLayout.PENDING_COUNT),pc=frame.readI32(Pc1FrameLayout.RAW_CODE_PC);
        if(count<60){record(frame,0,x);record(frame,1,frame.readI32(Pc1FrameLayout.ARGUMENT1));record(frame,6,newlinePitch);record(frame,7,style);record(frame,4,frame.readI32(Pc1FrameLayout.RAW_CODE_PC)+1);record(frame,5,0);record(frame,2,10);condition(frame,state);frame.writeI32(Pc1FrameLayout.PENDING_COUNT,frame.readI32(Pc1FrameLayout.PENDING_COUNT)+1);pc=frame.readI32(Pc1FrameLayout.RAW_CODE_PC);}
        int at=pc+1;while(readByte(code,at)!=0)at++;frame.writeI32(Pc1FrameLayout.RAW_CODE_PC,at+1);
    }
    public static void origin5d(Pc1NativeFrame frame,Pc1Compositor.State state,int mode)throws IOException{
        owners(frame,state);int x=frame.readI32(Pc1FrameLayout.ARGUMENT0),y=frame.readI32(Pc1FrameLayout.ARGUMENT1);if(mode!=0){int oldX=state.readInt(ORIGIN_X),oldY=state.readInt(ORIGIN_Y);x+=oldX;y+=oldY;}state.writeInt(ORIGIN_X,x);state.writeInt(ORIGIN_Y,y);
    }
    /**414A81 records actor targets, without moving the current actors. */
    public static void stageActor60(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        if(frame==null)throw new IOException("PC1 actor producer requires canonical frame");int x=frame.readI32(Pc1FrameLayout.ARGUMENT0),y=frame.readI32(Pc1FrameLayout.ARGUMENT1);frame.writeI32(TARGET_ACTOR1_X,x);frame.writeI32(TARGET_ACTOR1_Y,y);frame.writeI32(TARGET_ACTOR2_X,arg2);frame.writeI32(TARGET_ACTOR2_Y,arg3);
    }
    /**Only414985..4149B8's fixed field copies. The caller owns the real33
     *branch, resource changes, visibility, draws and subsequent transition. */
    public static void applyActorTargets33(Pc1NativeFrame frame)throws IOException{
        if(frame==null)throw new IOException("PC1 actor target copy requires canonical frame");int x2=frame.readI32(TARGET_ACTOR2_X),x1=frame.readI32(TARGET_ACTOR1_X),y1=frame.readI32(TARGET_ACTOR1_Y);frame.writeI32(Pc1FrameLayout.ACTOR2_X,x2);int y2=frame.readI32(TARGET_ACTOR2_Y);frame.writeI32(Pc1FrameLayout.ACTOR1_X,x1);frame.writeI32(Pc1FrameLayout.ACTOR2_Y,y2);frame.writeI32(Pc1FrameLayout.ACTOR1_Y,y1);
    }
    public static void commit21(Pc1NativeFrame frame,Pc1Compositor.State state,Services services)throws IOException{
        owners(frame,state);int arg0=frame.readI32(Pc1FrameLayout.ARGUMENT0),offset=state.readInt(SAVED_LIST_OFFSET);if(offset!=0)state.writeInt(LIST_OFFSET,offset);if(services==null)throw new IOException("Unimplemented PC1 producer4120c0 UI service");services.prepareUi(arg0,1);state.writeInt(Pc1Compositor.COMPOSITION_PENDING,0);
    }
    public static void template22(Pc1NativeFrame frame,Pc1Compositor.State state,Services services)throws IOException{
        owners(frame,state);int selector=frame.readI32(Pc1FrameLayout.ARGUMENT0);
        if(selector==0){state.writeInt(Pc1Compositor.COMPOSITION_PENDING,1);frame.writeI32(Pc1FrameLayout.PENDING_COUNT,0);return;}
        if(selector>=1&&selector<=7){int count=frame.readI32(Pc1FrameLayout.PENDING_COUNT);if(count>CAPACITY[selector]){count=CAPACITY[selector];frame.writeI32(Pc1FrameLayout.PENDING_COUNT,count);}long length=(count<<5)&0xffffffffL;
            if(selector>=3)state.writeInt(TEMPLATE_COUNT[selector],count);
            // REP MOVSD source read then destination write preserves prefix
            // effects if an admitted negative/wrapped count leavesknown memory.
            for(long at=0;at<length;at+=4){long source=(long)Pc1FrameLayout.PENDING_OFFSET+at;if(source+4>Pc1FrameLayout.FRAME_BYTES)throw new IOException("PC1 template copy reads unowned canonical-frame bytes");int value=frame.readI32((int)source);state.writeInt(TEMPLATE[selector]+(int)at,value);}
            if(selector<=2){state.writeInt(TEMPLATE_COUNT[selector],count);if(services==null)throw new IOException("Unimplemented PC1 template mode2 geometry consumer");services.geometryMode2(selector);}
        }
        frame.writeI32(Pc1FrameLayout.PENDING_COUNT,0);
    }
}
