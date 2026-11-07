package org.circus.core;

import java.io.*;

/** Current native UI producers, writing the addressed raw records directly.
 * Data follows real pointers and live counts; stale component/label bytes are
 * not cleared unless an original producer clears those bytes. */
public final class Pc1UiProducer {
    public interface Expansion {void expand(int rawSourceAddress,int destinationAddress)throws IOException;}
    private final Pc1NativeUi ui;
    public Pc1UiProducer(Pc1NativeUi ui)throws IOException{if(ui==null)throw new IOException("PC1 raw UI producer owner missing");this.ui=ui;}
    private int count()throws IOException{return ui.readInt(Pc1NativeUi.BUTTON_COUNT);}
    private int pointer()throws IOException{return ui.readInt(Pc1NativeUi.BUTTON_POINTER);}
    private void current(int offset,int value)throws IOException{ui.writeInt(pointer()+count()*Pc1NativeUi.BUTTON_BYTES+offset,value);}
    /**40B900 does not impose a100-button bound. Each store reloads live count;
     * admitted aliases may change the remaining writes. Unknown memory fails
     * at that exact access rather than manufacturing another button object. */
    public int rectangle(int left,int top,int right,int bottom,byte[] action)throws IOException{
        current(0,1);current(8,0);current(4,0);current(12,left);current(16,top);current(20,right);current(24,bottom);current(0x1fc,255);current(0x200,255);current(0x224,0);current(0x228,0);current(0x22c,0);
        for(int n=0;n<12;n++){current(0x1c+n*40,0);current(0x40+n*40,0);}ui.cstring(pointer()+count()*560+0x204,action);int old=count();ui.writeInt(Pc1NativeUi.BUTTON_COUNT,old+1);return old;
    }
    /**40BB40: marking kind2 precedes the full-component early return. */
    public void sprite(int x,int y,int sourceX,int sourceY,int width,int height,int type,int flag)throws IOException{
        int index=count()-1;if(index<0)return;if(flag==5)ui.writeInt(pointer()+index*560,2);int component=0;while(ui.readInt(pointer()+index*560+0x1c+component*40)!=0){if(++component>=12)return;}int at=index*560+component*40;ui.writeInt(pointer()+at+0x20,x);ui.writeInt(pointer()+at+0x24,y);ui.writeInt(pointer()+at+0x30,sourceX);ui.writeInt(pointer()+at+0x34,sourceY);ui.writeInt(pointer()+at+0x38,width);ui.writeInt(pointer()+at+0x3c,height);ui.writeInt(pointer()+at+0x28,type);ui.writeInt(pointer()+at+0x1c,flag);
    }
    /**40BC20 retains the original source pointer; no eager Unicode text. */
    public void text(int x,int y,int rawSourceAddress,int style,int flag)throws IOException{
        int index=count()-1;if(index<0)return;int component=0;while(ui.readInt(pointer()+index*560+0x1c+component*40)!=0){if(++component>=12)return;}int at=index*560+component*40;ui.writeInt(pointer()+at+0x20,x);ui.writeInt(pointer()+at+0x24,y);int length=ui.cstring(rawSourceAddress).length;ui.writeInt(pointer()+at+0x38,((length*7)&0x3fffffff)*2+1);ui.writeInt(pointer()+at+0x3c,29);ui.writeInt(pointer()+at+0x28,style);ui.writeInt(pointer()+at+0x40,rawSourceAddress);ui.writeInt(pointer()+at+0x1c,flag);
    }
    public void sounds(int click,int hover)throws IOException{int index=count()-1;if(index<0)return;int at=index*560;ui.writeInt(pointer()+at+0x1fc,hover);ui.writeInt(pointer()+at+0x200,click);}
    private void copy(int source,int destination,long length)throws IOException{for(long at=0;at<length;at+=4)ui.writeInt(destination+(int)at,ui.readInt(source+(int)at));}
    /**412AF5..412CB2. REP modifies ESI, never global4305F4 mid-copy. */
    public void template17(int selector)throws IOException{
        int cap=selector==1||selector==4?8:selector==2?4:selector==3?10:0;if(selector>=1&&selector<=4){if(count()>cap)ui.writeInt(Pc1NativeUi.BUTTON_COUNT,cap);if(selector==1||selector==4)for(int n=0;n<count();n++)ui.writeInt(pointer()+n*560,3);if(selector==3)ui.writeInt(pointer(),0);int n=count(),source=pointer();long length=(n*560)&0xffffffffL;int destination=selector==1?0x9cb908:selector==2?0x9b8e20:selector==3?0x9b9f20:0x9ccba8;copy(source,destination,length);if(selector==1){ui.writeInt(0xbb846c,n);copy(source,Pc1NativeUi.STORY_BUTTONS,length);ui.writeInt(0xbb8450,n);}else ui.writeInt(selector==2?0xbb8478:selector==3?0xbb845c:0xbb8468,n);}
        ui.writeInt(Pc1NativeUi.BUTTON_COUNT,0);ui.writeInt(Pc1NativeUi.BUTTON_POINTER,Pc1NativeUi.MAIN_BUTTONS);
    }
    private int word(int codeOffset)throws IOException{int high=ui.unsignedByte(Pc1NativeMemory.CODE_BASE+codeOffset+1),low=ui.unsignedByte(Pc1NativeMemory.CODE_BASE+codeOffset);return(short)(high<<8|low);}
    private static int pc(Pc1NativeFrame frame)throws IOException{return frame.readI32(Pc1FrameLayout.RAW_CODE_PC);}
    private static void pc(Pc1NativeFrame frame,int value)throws IOException{frame.writeI32(Pc1FrameLayout.RAW_CODE_PC,value);}
    /**4F owns arbitrary marker+rawNUL, commits PC before40B900, then maps a
     *nonzero pending condition to1. No label transcoding is applied. */
    public void rectangle4f(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        int x=frame.readI32(Pc1FrameLayout.ARGUMENT0),y=frame.readI32(Pc1FrameLayout.ARGUMENT1),oldPc=pc(frame);byte[] action=ui.cstring(Pc1NativeMemory.CODE_BASE+oldPc+1);if(action.length>=1024)throw new IOException("PC1 button rectangle local1024 overflow");pc(frame,oldPc+action.length+2);rectangle(x,y,arg2,arg3,action);int condition=ui.readInt(Pc1DrawProducer.CONDITION);if(condition!=0){ui.writeInt(pointer()+(count()-1)*560+4,1);ui.writeInt(Pc1DrawProducer.CONDITION,0);}ui.writeInt(0xbb8444,0);
    }
    /**50 fetches flag/type/height/width in reverse address order, then calls
     *40BB40 with current C0/C4. Its final two bytes are a real flag operand. */
    public void sprite50(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        int pointer=pc(frame)+7;pc(frame,pc(frame)+1);int flag=word(pointer),type=word(pc(frame)+4)|ui.readInt(0xbb8444),height=word(pc(frame)+2),width=word(pc(frame));sprite(frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),arg2,arg3,width,height,type,flag);ui.writeInt(0xbb8444,0);pc(frame,pc(frame)+8);
    }
    public void text51(Pc1NativeFrame frame,int style,int flag)throws IOException{int source=Pc1NativeMemory.CODE_BASE+pc(frame)+1;pc(frame,pc(frame)+1);text(frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),source,style,flag);int length=ui.cstring(Pc1NativeMemory.CODE_BASE+pc(frame)).length;pc(frame,pc(frame)+length+1);}
    public void opaque19()throws IOException{ui.writeInt(0xbb8444,128);}
    public void grid54(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        ui.writeInt(0x9553c8,frame.readI32(Pc1FrameLayout.ARGUMENT0));ui.writeInt(0x9553cc,frame.readI32(Pc1FrameLayout.ARGUMENT1));ui.writeInt(0x9553d0,arg2);ui.writeInt(0x9553d4,arg3);int first=pc(frame)+1;pc(frame,first);ui.writeInt(0x9553d8,word(first));ui.writeInt(0x9553dc,word(pc(frame)+2));ui.writeInt(0x9553e0,word(pc(frame)+4));ui.writeInt(0x9553e4,word(pc(frame)+6));pc(frame,pc(frame)+8);
        ui.writeInt(0x4305fc,-1);ui.writeInt(0x430600,-1);ui.writeInt(0x968a00,-1);ui.writeInt(Pc1NativeUi.LIST_VISIBLE,0);ui.writeInt(Pc1NativeUi.LIST_COUNT,0);ui.writeInt(Pc1NativeUi.LIST_OFFSET,0);ui.writeInt(0x9689f8,0);ui.writeInt(0x430604,255);ui.writeInt(0x430608,255);ui.writeInt(0x9689fc,0);for(int n=0;n<220*104;n+=4)ui.writeInt(Pc1NativeUi.LIST_RECORDS+n,0);
    }
    public void gridRect55(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{ui.writeInt(0x955408+ui.readInt(Pc1NativeUi.LIST_VISIBLE)*16,frame.readI32(Pc1FrameLayout.ARGUMENT0));ui.writeInt(0x95540c+ui.readInt(Pc1NativeUi.LIST_VISIBLE)*16,frame.readI32(Pc1FrameLayout.ARGUMENT1));ui.writeInt(0x955410+ui.readInt(Pc1NativeUi.LIST_VISIBLE)*16,arg2);ui.writeInt(0x955414+ui.readInt(Pc1NativeUi.LIST_VISIBLE)*16,arg3);ui.writeInt(Pc1NativeUi.LIST_VISIBLE,ui.readInt(Pc1NativeUi.LIST_VISIBLE)+1);}
    public void gridItem56(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        int index=ui.readInt(Pc1NativeUi.LIST_COUNT),at=index*104;ui.writeInt(0x9555b8+at,frame.readI32(Pc1FrameLayout.ARGUMENT0));ui.writeInt(0x9555c0+at,arg2);ui.writeInt(0x9555c4+at,arg3);int first=pc(frame)+1;pc(frame,first);ui.writeInt(0x9555c8+ui.readInt(Pc1NativeUi.LIST_COUNT)*104,word(first));int oldPc=pc(frame);ui.writeInt(0x9555cc+ui.readInt(Pc1NativeUi.LIST_COUNT)*104,word(oldPc+2));index=ui.readInt(Pc1NativeUi.LIST_COUNT);ui.writeInt(0x9555bc+index*104,word(pc(frame)+4));ui.writeByte(0x9555dc+index*104,0);pc(frame,pc(frame)+8);
    }
    public void gridCaption57(Pc1NativeFrame frame,int arg2,Expansion expansion)throws IOException{int old=pc(frame);pc(frame,old+1);if(ui.readInt(Pc1NativeUi.LIST_COUNT)>0){int at=ui.readInt(Pc1NativeUi.LIST_COUNT)*104;ui.writeInt(0x955568+at,frame.readI32(Pc1FrameLayout.ARGUMENT0));ui.writeInt(0x95556c+at,frame.readI32(Pc1FrameLayout.ARGUMENT1));ui.writeInt(0x955570+at,arg2);if(expansion==null)throw new IOException("PC1 grid caption requires4123A0 expansion owner");expansion.expand(Pc1NativeMemory.CODE_BASE+old+1,0x955574+at);}int length=ui.cstring(Pc1NativeMemory.CODE_BASE+pc(frame)).length;pc(frame,pc(frame)+length+1);}
    /**43 completes the staged list item;56 alone does not increase count. */
    public void sealGrid43(int rawActionPointer)throws IOException{ui.writeInt(0x95561c+ui.readInt(Pc1NativeUi.LIST_COUNT)*104,rawActionPointer);ui.writeInt(Pc1NativeUi.LIST_COUNT,ui.readInt(Pc1NativeUi.LIST_COUNT)+1);}
    public void gridSounds14(int click,int hover)throws IOException{ui.writeInt(0x430604,hover);ui.writeInt(0x430608,click);}
    public void gridCaptionBounds5f(int left,int top,int right,int bottom)throws IOException{ui.writeInt(0x9553fc,bottom);ui.writeInt(0x955400,right);ui.writeInt(0x955548,top);ui.writeInt(0x955550,left);}
}
