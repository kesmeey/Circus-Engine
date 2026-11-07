package org.circus.core;

import java.io.*;

/** Current native UI producers, writing the addressed raw records directly.
 * Data follows real pointers and live counts; stale component/label bytes are
 * not cleared unless an original producer clears those bytes. */
public final class Pc1UiProducer {
    public interface Expansion {void expand(int rawSourceAddress,int destinationAddress)throws IOException;}
    public interface IntValue {int get()throws IOException;}
    public interface State {
        int readInt(int address)throws IOException;void writeInt(int address,int value)throws IOException;
        int unsignedByte(int address)throws IOException;void writeByte(int address,int value)throws IOException;
        default byte[] cstring(int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;;n++){int b=unsignedByte(address+n);if(b==0)return out.toByteArray();out.write(b);}}
    }
    public interface Source {int unsignedByte(int offset)throws IOException;default int word(int offset)throws IOException{int v=0;for(int n=0;n<4;n++)v|=(unsignedByte(offset+n)&255)<<(n*8);return v;}}
    private final State ui;
    public Pc1UiProducer(final Pc1NativeUi owner)throws IOException{this(owner==null?null:new State(){public int readInt(int at)throws IOException{return owner.readInt(at);}public void writeInt(int at,int value)throws IOException{owner.writeInt(at,value);}public int unsignedByte(int at)throws IOException{return owner.unsignedByte(at);}public void writeByte(int at,int value)throws IOException{owner.writeByte(at,value);}});}
    public Pc1UiProducer(State state)throws IOException{if(state==null)throw new IOException("PC1 raw UI producer owner missing");ui=state;}
    private static final int PC=0x9d8a14,A0=0x9da4c0,A1=0x9da4c4;
    private int g(int at)throws IOException{return ui.readInt(at);}private void g(int at,int value)throws IOException{ui.writeInt(at,value);}
    private Source source(final int address){return new Source(){public int unsignedByte(int n)throws IOException{return ui.unsignedByte(address+n);}public int word(int n)throws IOException{return g(address+n);}};}
    private static Source copied(final byte[] bytes)throws IOException{if(bytes==null)throw new IOException("PC1 copied action missing");return n->{if(n<0||n>bytes.length)throw new IOException("PC1 copied action exceeds known bytes");return n==bytes.length?0:bytes[n]&255;};}
    private static int length(Source source)throws IOException{for(long n=0;n<0xffffffffL;n++)if((source.unsignedByte((int)n)&255)==0)return(int)n;throw new IOException("PC1 C source exhausted native SCAS span");}
    private void copy(Source source,int destination,int bytes)throws IOException{int at=0;for(int n=0;n<(bytes>>>2);n++,at+=4)g(destination+at,source.word(at));for(int n=0;n<(bytes&3);n++,at++)ui.writeByte(destination+at,source.unsignedByte(at));}
    private int count()throws IOException{return ui.readInt(Pc1NativeUi.BUTTON_COUNT);}
    private int pointer()throws IOException{return ui.readInt(Pc1NativeUi.BUTTON_POINTER);}
    private void current(int offset,int value)throws IOException{int index=count(),base=pointer();ui.writeInt(base+index*Pc1NativeUi.BUTTON_BYTES+offset,value);}
    /**40B900 does not impose a100-button bound. Each store reloads live count;
     * admitted aliases may change the remaining writes. Unknown memory fails
     * at that exact access rather than manufacturing another button object. */
    public int rectangle(int left,int top,int right,int bottom,byte[] action)throws IOException{return rectangle(left,top,right,bottom,copied(action));}
    public int rectangle(int left,int top,int right,int bottom,Source action)throws IOException{
        current(0,1);current(8,0);current(4,0);current(12,left);current(16,top);current(20,right);current(24,bottom);current(0x1fc,255);current(0x200,255);current(0x224,0);current(0x228,0);current(0x22c,0);
        for(int n=0;n<12;n++){current(0x1c+n*40,0);current(0x40+n*40,0);}int index=count(),base=pointer(),destination=base+index*560+0x204;copy(action,destination,length(action)+1);int next=count()+1;ui.writeInt(Pc1NativeUi.BUTTON_COUNT,next);return next-1;
    }
    /**40BB40: marking kind2 precedes the full-component early return. */
    public void sprite(int x,int y,int sourceX,int sourceY,int width,int height,int type,int flag)throws IOException{
        int index=count()-1;if(index<0)return;if(flag==5)ui.writeInt(pointer()+index*560,2);int base=pointer(),component=0;while(ui.readInt(base+index*560+0x1c+component*40)!=0){if(++component>=12)return;}int at=index*560+component*40;ui.writeInt(base+at+0x20,x);ui.writeInt(pointer()+at+0x24,y);ui.writeInt(pointer()+at+0x30,sourceX);ui.writeInt(pointer()+at+0x34,sourceY);ui.writeInt(pointer()+at+0x38,width);ui.writeInt(pointer()+at+0x3c,height);ui.writeInt(pointer()+at+0x28,type);ui.writeInt(pointer()+at+0x1c,flag);
    }
    /**40BC20 retains the original source pointer; no eager Unicode text. */
    public void text(int x,int y,int rawSourceAddress,int style,int flag)throws IOException{
        int index=count()-1;if(index<0)return;int base=pointer(),component=0;while(ui.readInt(base+index*560+0x1c+component*40)!=0){if(++component>=12)return;}int at=index*560+component*40;ui.writeInt(base+at+0x20,x);ui.writeInt(pointer()+at+0x24,y);int length=length(source(rawSourceAddress));ui.writeInt(pointer()+at+0x38,((length*7)&0x3fffffff)*2+1);ui.writeInt(pointer()+at+0x3c,29);ui.writeInt(pointer()+at+0x28,style);ui.writeInt(pointer()+at+0x40,rawSourceAddress);ui.writeInt(pointer()+at+0x1c,flag);
    }
    public void sounds(int click,int hover)throws IOException{int index=count()-1;if(index<0)return;int at=index*560;ui.writeInt(pointer()+at+0x1fc,hover);ui.writeInt(pointer()+at+0x200,click);}
    private void copy(int source,int destination,long length)throws IOException{for(long at=0;at<length;at+=4)ui.writeInt(destination+(int)at,ui.readInt(source+(int)at));}
    /**412AF5..412CB2. REP modifies ESI, never global4305F4 mid-copy. */
    public void template17(int selector)throws IOException{
        template17(selector,count());
    }
    /**ECX is the fetched device/count register only for the initial cap/test.*/
    public void template17(int selector,int cachedDevice)throws IOException{
        int cap=selector==1||selector==4?8:selector==2?4:selector==3?10:0;if(selector>=1&&selector<=4){int initial=cachedDevice;if(initial>cap){ui.writeInt(Pc1NativeUi.BUTTON_COUNT,cap);initial=cap;}if((selector==1||selector==4)&&initial>0){int n=0;do{int base=pointer();n++;ui.writeInt(base+(n-1)*560,3);}while(n<count());}if(selector==3)ui.writeInt(pointer(),0);int n=count(),source=pointer();long length=(n*560)&0xffffffffL;int destination=selector==1?0x9cb908:selector==2?0x9b8e20:selector==3?0x9b9f20:0x9ccba8;copy(source,destination,length);if(selector==1){ui.writeInt(0xbb846c,n);copy(source,Pc1NativeUi.STORY_BUTTONS,length);ui.writeInt(0xbb8450,n);}else ui.writeInt(selector==2?0xbb8478:selector==3?0xbb845c:0xbb8468,n);}
        ui.writeInt(Pc1NativeUi.BUTTON_COUNT,0);ui.writeInt(Pc1NativeUi.BUTTON_POINTER,Pc1NativeUi.MAIN_BUTTONS);
    }
    private int word(int codeOffset)throws IOException{int high=ui.unsignedByte(Pc1NativeMemory.CODE_BASE+codeOffset+1),low=ui.unsignedByte(Pc1NativeMemory.CODE_BASE+codeOffset);return(short)(high<<8|low);}
    private static int pc(Pc1NativeFrame frame)throws IOException{return frame.readI32(Pc1FrameLayout.RAW_CODE_PC);}
    private static void pc(Pc1NativeFrame frame,int value)throws IOException{frame.writeI32(Pc1FrameLayout.RAW_CODE_PC,value);}
    /**4F owns arbitrary marker+rawNUL, commits PC before40B900, then maps a
     *nonzero pending condition to1. No label transcoding is applied. */
    public void rectangle4f(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        Pc1Fetch.Frame locals=Pc1Fetch.Frame.unknown();locals.setArgument2(arg2);rectangle4f(frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),arg3,locals);
    }
    /**4F consumes the same caller BUFFER/local A2. PREVIOUS receives an ESP
     *pointer whose numeric bytes remain unknown, never an invented Guest VA.*/
    public void rectangle4f(int cachedA0,int cachedA1,int cachedA3,Pc1Fetch.Frame locals)throws IOException{
        rectangle4f(cachedA0,cachedA1,()->cachedA3,locals);
    }
    public void rectangle4f(int cachedA0,int cachedA1,IntValue cachedA3,Pc1Fetch.Frame locals)throws IOException{
        if(locals==null)throw new IOException("PC1 4F requires caller locals");Source raw=source(Pc1NativeMemory.CODE_BASE+g(PC)+1);int bytes=length(raw)+1;for(int n=0;n<4;n++)locals.unknownByte(Pc1Fetch.Frame.PREVIOUS+n);int at=0;
        for(int n=0;n<(bytes>>>2);n++,at+=4)locals.dword(Pc1Fetch.Frame.BUFFER+at,raw.word(at));for(int n=0;n<(bytes&3);n++,at++)locals.writeByte(Pc1Fetch.Frame.BUFFER+at,raw.unsignedByte(at));
        Source local=new Source(){public int unsignedByte(int offset)throws IOException{return locals.unsignedByte(Pc1Fetch.Frame.BUFFER+offset);}public int word(int offset)throws IOException{return locals.dword(Pc1Fetch.Frame.BUFFER+offset);}};
        g(PC,g(PC)+length(local)+2);int a2=locals.argument2(),a3=cachedA3.get();rectangle(cachedA0,cachedA1,a2,a3,local);if(g(Pc1DrawProducer.CONDITION)!=0){int index=count(),base=pointer();g(base+(index-1)*560+4,1);g(Pc1DrawProducer.CONDITION,0);}g(0xbb8444,0);
    }
    /**50 fetches flag/type/height/width in reverse address order, then calls
     *40BB40 with current C0/C4. Its final two bytes are a real flag operand. */
    public void sprite50(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        sprite50(pc(frame),arg2,arg3);
    }
    /**50 late-loads C4 then C0, unlike51/54/55's cached coordinates.*/
    public void sprite50(int cachedCursor,int cachedA2,int cachedA3)throws IOException{sprite50(cachedCursor,()->cachedA2,()->cachedA3);}
    public void sprite50(int cachedCursor,IntValue cachedA2,IntValue cachedA3)throws IOException{int next=cachedCursor+1;g(PC,next);int flag=word(next+6),type=word(g(PC)+4)|g(0xbb8444),height=word(g(PC)+2),width=word(g(PC)),y=g(A1),x=g(A0),a3=cachedA3.get(),a2=cachedA2.get();sprite(x,y,a2,a3,width,height,type,flag);int finalPc=g(PC)+8;g(0xbb8444,0);g(PC,finalPc);}
    public void text51(Pc1NativeFrame frame,int style,int flag)throws IOException{text51(pc(frame),frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),style,flag);}
    public void text51(int cachedCursor,int cachedA0,int cachedA1,int cachedA2,int cachedA3)throws IOException{text51(cachedCursor,cachedA0,cachedA1,()->cachedA2,()->cachedA3);}
    public void text51(int cachedCursor,int cachedA0,int cachedA1,IntValue cachedA2,IntValue cachedA3)throws IOException{int next=cachedCursor+1,a3=cachedA3.get(),a2=cachedA2.get();g(PC,next);text(cachedA0,cachedA1,Pc1NativeMemory.CODE_BASE+next,a2,a3);int live=g(PC);g(PC,live+length(source(Pc1NativeMemory.CODE_BASE+live))+1);}
    public void opaque19()throws IOException{ui.writeInt(0xbb8444,128);}
    public void grid54(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        grid54(pc(frame),frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),arg2,arg3);
    }
    public void grid54(int cachedCursor,int cachedA0,int cachedA1,int cachedA2,int cachedA3)throws IOException{
        grid54(cachedCursor,cachedA0,cachedA1,()->cachedA2,()->cachedA3);
    }
    public void grid54(int cachedCursor,int cachedA0,int cachedA1,IntValue cachedA2,IntValue cachedA3)throws IOException{
        int first=cachedCursor+1;g(0x9553c8,cachedA0);g(0x9553cc,cachedA1);g(0x9553d0,cachedA2.get());g(0x9553d4,cachedA3.get());g(PC,first);g(0x9553d8,word(first));int value=word(g(PC)+2),before=g(PC);g(0x9553dc,value);value=word(before+4);before=g(PC);g(0x9553e0,value);g(0x9553e4,word(before+6));g(PC,g(PC)+8);
        ui.writeInt(0x4305fc,-1);ui.writeInt(0x430600,-1);ui.writeInt(0x968a00,-1);ui.writeInt(Pc1NativeUi.LIST_VISIBLE,0);ui.writeInt(Pc1NativeUi.LIST_COUNT,0);ui.writeInt(Pc1NativeUi.LIST_OFFSET,0);ui.writeInt(0x9689f8,0);ui.writeInt(0x430604,255);ui.writeInt(0x430608,255);ui.writeInt(0x9689fc,0);for(int n=0;n<220*104;n+=4)ui.writeInt(Pc1NativeUi.LIST_RECORDS+n,0);
    }
    public void gridRect55(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{gridRect55(frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),arg2,arg3);}
    public void gridRect55(int cachedA0,int cachedA1,int cachedA2,int cachedA3)throws IOException{gridRect55(cachedA0,cachedA1,()->cachedA2,()->cachedA3);}
    public void gridRect55(int cachedA0,int cachedA1,IntValue cachedA2,IntValue cachedA3)throws IOException{int index=g(Pc1NativeUi.LIST_VISIBLE),at=index*16;g(0x955408+at,cachedA0);g(0x95540c+at,cachedA1);g(0x955410+at,cachedA2.get());g(0x955414+at,cachedA3.get());g(Pc1NativeUi.LIST_VISIBLE,index+1);}
    public void gridItem56(Pc1NativeFrame frame,int arg2,int arg3)throws IOException{
        gridItem56(frame.readI32(Pc1FrameLayout.ARGUMENT0),arg3,()->arg2);
    }
    public void gridItem56(int cachedA0,int cachedA3,IntValue localA2)throws IOException{gridItem56(cachedA0,()->cachedA3,localA2);}
    public void gridItem56(int cachedA0,IntValue cachedA3,IntValue localA2)throws IOException{int at=g(Pc1NativeUi.LIST_COUNT)*104;g(0x9555b8+at,cachedA0);int second=localA2.get();g(0x9555c0+at,second);g(0x9555c4+at,cachedA3.get());int first=g(PC)+1;g(PC,first);int value=word(first),index=g(Pc1NativeUi.LIST_COUNT),before=g(PC);g(0x9555c8+index*104,value);value=word(before+2);index=g(Pc1NativeUi.LIST_COUNT);g(0x9555cc+index*104,value);at=index*104;value=word(g(PC)+4);g(0x9555bc+at,value);int finalPc=g(PC)+8;ui.writeByte(0x9555dc+at,0);g(PC,finalPc);}
    public void gridCaption57(Pc1NativeFrame frame,int arg2,Expansion expansion)throws IOException{gridCaption57(pc(frame),frame.readI32(Pc1FrameLayout.ARGUMENT0),frame.readI32(Pc1FrameLayout.ARGUMENT1),arg2,expansion);}
    public void gridCaption57(int cachedCursor,int cachedA0,int cachedA1,int cachedA2,Expansion expansion)throws IOException{gridCaption57(cachedCursor,cachedA0,cachedA1,()->cachedA2,expansion);}
    public void gridCaption57(int cachedCursor,int cachedA0,int cachedA1,IntValue cachedA2,Expansion expansion)throws IOException{int index=g(Pc1NativeUi.LIST_COUNT),cursor=cachedCursor+1;g(PC,cursor);if(index>0){int at=index*104;g(0x955568+at,cachedA0);g(0x95556c+at,cachedA1);g(0x955570+at,cachedA2.get());if(expansion==null)throw new IOException("PC1 grid caption requires4123A0 expansion owner");expansion.expand(Pc1NativeMemory.CODE_BASE+cursor,0x955574+at);cursor=g(PC);}g(PC,cursor+length(source(Pc1NativeMemory.CODE_BASE+cursor))+1);}
    /**43 completes the staged list item;56 alone does not increase count. */
    public void sealGrid43(int rawActionPointer)throws IOException{int index=g(Pc1NativeUi.LIST_COUNT);ui.writeInt(0x95561c+index*104,rawActionPointer);ui.writeInt(Pc1NativeUi.LIST_COUNT,index+1);}
    public void sealGrid43()throws IOException{int index=g(Pc1NativeUi.LIST_COUNT),raw=g(0xbb83fc);g(0x95561c+index*104,raw);g(Pc1NativeUi.LIST_COUNT,index+1);}
    public void gridSounds14(int click,int hover)throws IOException{ui.writeInt(0x430608,click);ui.writeInt(0x430604,hover);}
    public void gridCaptionBounds5f(int left,int top,int right,int bottom)throws IOException{gridCaptionBounds5f(left,top,()->right,()->bottom);}
    public void gridCaptionBounds5f(int left,int top,IntValue right,IntValue bottom)throws IOException{ui.writeInt(0x955550,left);ui.writeInt(0x955548,top);ui.writeInt(0x955400,right.get());ui.writeInt(0x9553fc,bottom.get());}
}
