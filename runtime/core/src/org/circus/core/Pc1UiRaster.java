package org.circus.core;

import java.io.*;

/** Current PC1 raw-button/list consumers. Pixels, clip, dirty and invalidation
 * belong only to Pc1Compositor. Required scene/GDI/input/audio owners preserve
 * actual call boundaries instead of manufacturing portable UI objects. */
public final class Pc1UiRaster {
    public static final class Point {public final int x,y;public Point(int x,int y){this.x=x;this.y=y;}}
    public interface Services extends Pc1UiProducer.Expansion {
        void restore(int left,int top,int right,int bottom,int mode)throws IOException; //415D50
        Point cursorClient()throws IOException; //GetCursorPos,ScreenToClient
        /**418930 starts font28. Bytes are actual known decoded/expanded backing,
         *including terminator and only genuinely known suffix; no invented0. */
        void glyphs(int x,int y,int newlinePitch,byte[] knownRaw,int style)throws IOException;
        int variable(int bank,int index)throws IOException; //411910
        void sound(int id,int mode)throws IOException; //41C850
        void voice(int rawCStringPointer)throws IOException; //41CD70,including0
        void specialListItem(int visibleIndex,int dataIndex,boolean selected)throws IOException; //404E30
    }
    private final Pc1NativeUi ui;
    private final Pc1Compositor c;
    private final Services services;
    public interface InputServices {
        void writeVariable(int bank,int index,int value)throws IOException; //411B10
        void setCapture()throws IOException;
        void releaseCapture()throws IOException;
        int setTimer(int id,int milliseconds)throws IOException;
        void killTimer(int token)throws IOException;
    }
    private InputServices input;
    public void bindInput(InputServices value)throws IOException{if(value==null||input!=null)throw new IOException("PC1 UI input services need one physical/canonical owner");input=value;}
    private InputServices input(int entry)throws IOException{if(input==null)throw new IOException("PC1 UI input requires native leaf"+Integer.toHexString(entry));return input;}
    public Pc1UiRaster(Pc1NativeUi ui,Pc1Compositor compositor,Services services)throws IOException{if(ui==null||compositor==null)throw new IOException("PC1 UI raster canonical owners missing");this.ui=ui;c=compositor;this.services=services;}
    private Services required(int address)throws IOException{if(services==null)throw new IOException("Unimplemented PC1 UI consumer"+Integer.toHexString(address));return services;}
    private int read(int address)throws IOException{return ui.readInt(address);}
    private int ptr()throws IOException{return read(Pc1NativeUi.BUTTON_POINTER);}
    private int count()throws IOException{return read(Pc1NativeUi.BUTTON_COUNT);}
    private boolean eligible(int button)throws IOException{return read(button+4)==0||read(0x9da4a8)!=0;}
    private void clip(int button)throws IOException{c.setClip(read(button+12),read(button+16),read(button+20),read(button+24));}
    private void restore(int button)throws IOException{required(0x415d50).restore(read(button+12),read(button+16),read(button+20),read(button+24),0);}
    private void restore(int left,int top,int right,int bottom)throws IOException{required(0x415d50).restore(left,top,right,bottom,0);}
    private int palette(int index)throws IOException{int packed=read(Pc1DrawBatch.PALETTE+index*4);return(packed>>>8&255)<<16|(packed&255)<<8|(packed>>>16&255);}
    /**409690 consumes component+20's eight DWORDs, not a button DTO. */
    public void sprite(int component,int mode)throws IOException{
        if((mode&2)!=0)restore(read(component),read(component+4),read(component)+read(component+24),read(component+4)+read(component+28));
        if((mode&1)!=0){c.setClip(read(component),read(component+4),read(component)+read(component+24),read(component+4)+read(component+28));int type=read(component+8),handle=read(Pc1DrawBatch.SLOT_BASE+(type&127)*4);if((type&128)!=0&&read(Pc1DrawBatch.OPACITY_ENABLED)!=0)c.opacity(read(component+16),read(component+20),handle,read(Pc1DrawBatch.OPACITY_LEVEL));else c.transparent(read(component+16),read(component+20),handle);}
    }
    /**409730 expands the original pointer into the shared raw955568 buffer. */
    public void text(int component,int rawSource)throws IOException{
        required(0x4123a0).expand(rawSource,Pc1NativeUi.SHARED_TEXT);int width=ui.cstring(Pc1NativeUi.SHARED_TEXT).length*28;if((read(component+8)&0x8000)!=0)restore(read(component),read(component+4),read(component)+width,read(component+4)+read(component+28));c.setClip(read(component),read(component+4),read(component)+width,read(component+4)+read(component+28));required(0x418930).glyphs(read(component)+1,read(component+4)+1,0,sharedBacking(),read(component+8));
    }
    private byte[] sharedBacking()throws IOException{return ui.bytes(Pc1NativeUi.SHARED_TEXT,Pc1NativeUi.LIST_RECORDS+Pc1NativeUi.LIST_CAPACITY*Pc1NativeUi.LIST_RECORD_BYTES-Pc1NativeUi.SHARED_TEXT);}
    private void caption(int x,int y,int source,int style)throws IOException{byte[] encoded=ui.cstring(source);if(encoded.length>=1024)throw new IOException("PC1 caption local1024 overflow");byte[] known=new byte[encoded.length+1];for(int n=0;n<encoded.length;n++)known[n]=(byte)((encoded[n]&255)+32);required(0x418930).glyphs(x,y,0,known,style);}
    private void component(int component,int mode)throws IOException{int source=read(component+32);if(source==0)sprite(component,mode);else text(component,source);}
    /**409D30 always calls407000, even with no buttons or an offscreen target. */
    public void redrawButtons(int state)throws IOException{
        int p=ptr();
        for(int index=0;index<count();index++){int offset=index*560,button=p+offset;if(!eligible(button))continue;
            if(read(button)==0){if(state==-1)for(int n=0;n<12;n++){int at=p+offset+n*40;if(read(at+28)==255&&read(0x43028c)==0&&read(at+64)!=0){sprite(at+32,3);p=ptr();}}}
            else if(index!=read(Pc1NativeUi.SELECTED_BUTTON)&&read(button+8)!=0||state==-1){clip(button);restore(ptr()+offset);p=ptr();if(read(p+offset)==2){rangeButton(p+offset,0);ui.writeInt(ptr()+offset+8,0);p=ptr();}
                else{for(int n=0;n<12;n++){int at=p+offset+n*40;if(read(at+28)==state+2){sprite(at+32,2);p=ptr();}}for(int n=0;n<12;n++){int at=p+offset+n*40;if(read(at+28)==99){component(at+32,3);p=ptr();}}
                    if(read(p+offset+0x22c)!=0){clip(p+offset);restore(ptr()+offset);p=ptr();caption(read(p+offset+32),read(p+offset+36),read(p+offset+0x22c),read(p+offset+40));p=ptr();}ui.writeInt(p+offset+8,0);p=ptr();}
            }
        }
        for(int index=0;index<count();index++){int offset=index*560,button=p+offset;if(!eligible(button)||read(button)==0)continue;
            if(read(button)==2&&index==read(Pc1NativeUi.SELECTED_BUTTON)){clip(button);restore(ptr()+offset);rangeButton(ptr()+offset,state);ui.writeInt(ptr()+offset+8,state);p=ptr();}
            else if(index==read(Pc1NativeUi.SELECTED_BUTTON)&&read(button+8)!=state){boolean found=false;clip(button);restore(ptr()+offset);p=ptr();for(int n=0;n<12;n++){int at=p+offset+n*40,flag=read(at+28);if(flag==state||flag==state+2){found=true;component(at+32,flag==state+2?3:1);p=ptr();if(read(p+offset+0x22c)!=0){clip(p+offset);restore(ptr()+offset);p=ptr();caption(read(p+offset+n*40+32),read(p+offset+n*40+36),read(p+offset+0x22c),read(p+offset+n*40+40));p=ptr();}}}if(!found&&state==1)c.fillBlend16(read(p+offset+12),read(p+offset+16),read(p+offset+20),read(p+offset+24),palette(0),read(Pc1DrawBatch.OPACITY_LEVEL));ui.writeInt(p+offset+8,state);p=ptr();}
        }
        c.present();
    }
    /**40AA50 draws/presents before querying the current client cursor. */
    public void buttons(int mode)throws IOException{ui.writeInt(Pc1NativeUi.SELECTED_BUTTON,-1);redrawButtons(-1);if(mode!=0){Point point=required(0x40a5b0).cursorClient();if(point==null)throw new IOException("PC1 cursor position is unknown");hoverButtons(point.x,point.y);}}
    public void hoverButtons(int x,int y)throws IOException{
        ui.writeInt(0x9689f0,x);ui.writeInt(0x9689f4,y);for(int index=0;index<count();index++){int button=ptr()+index*560;if(eligible(button)&&read(button)!=0&&read(button+12)<x&&x<read(button+20)&&read(button+16)<y&&y<read(button+24)){int input=read(0x94c680)+1;if(index==read(Pc1NativeUi.SELECTED_BUTTON)&&read(0x4305f8)==input&&read(button)!=2)return;ui.writeInt(0x4305f8,input);ui.writeInt(Pc1NativeUi.SELECTED_BUTTON,index);redrawButtons(input);int sound=read(ptr()+index*560+0x1fc);if(sound!=255)required(0x41c850).sound(sound,0);required(0x41cd70).voice(read(ptr()+index*560+0x224));return;}}
        if(read(Pc1NativeUi.SELECTED_BUTTON)!=-1){ui.writeInt(Pc1NativeUi.SELECTED_BUTTON,-1);redrawButtons(read(0x94c680)+1);}
    }
    private static int divide(int value,int divisor)throws IOException{if(divisor==0||value==Integer.MIN_VALUE&&divisor==-1)throw new IOException("PC1 native UI signed divide fault");return value/divisor;}
    /**4097C0 range draw only. Input/variable writes have a separate owner. */
    public void rangeButton(int button,int state)throws IOException{
        boolean configuration=false,stepped=false;int start=0,end=0,count=0,value=0;int[] positions=new int[10];boolean[] known=new boolean[10];
        for(int n=0;n<12;n++){int component=button+n*40+32,flag=read(component-4);if(flag==5){end=read(component+4);count=read(component+16);start=read(component);value=required(0x411910).variable(read(component+20),read(component+24));configuration=true;}else if(flag==7){for(int at=1;at<=7;at++){int field=at==1?0:at==2?4:at==7?8:at*4+4;positions[at]=read(component+field);known[at]=true;}stepped=true;}}
        if(!configuration)throw new IOException("PC1 range draw reads unknown native stack without flag5 configuration");positions[0]=start;known[0]=true;if(count<0){if(stepped||count< -3)throw new IOException("PC1 range configuration aliases unmodeled native local stack");/*−1..−3 touch only local value/end/count; cached negative EDI prevents continuous range math.*/}else if(count<10){positions[count]=end;known[count]=true;}
        for(int n=0;n<12;n++){int at=button+n*40,flag=read(at+28),component=at+32;if(flag==99){component(component,3);continue;}
            if(stepped){
                if(flag==2||flag==1&&state==1){
                    for(int segment=0;segment<count;segment++){
                        if(segment>=65536)throw new IOException("PC1 stepped range native loop exceeds host replay bound");
                        boolean hit=false;
                        if(flag==1||state==2){int left=position(positions,known,segment),mouse=read(0x9689f0);if(left<=mouse)hit=mouse<position(positions,known,segment+1);}
                        if(hit||flag==2&&segment==value){c.setClip(position(positions,known,segment),read(component+4),position(positions,known,segment+1),read(component+4)+read(component+28));c.transparent(read(component+16),read(component+20),read(Pc1DrawBatch.SLOT_BASE+read(component+8)*4));}
                    }
                }else if(flag==state+2)component(component,3);
            }else if(flag==2||flag==6){
                if(count>1){int offset=divide((end-start)*value,count-1),x=read(component),y=read(component+4),width=read(component+24);if(flag==2){int amount=offset-x+start;if(width<amount)amount=width;c.setClip(x,y,x+amount,y+read(component+28));}else c.setClip(x+offset,y,x+offset+width,y+read(component+28));int type=read(component+8),sourceX=read(component+16)+(flag==6?offset:0),sourceY=read(component+20);if((type&128)!=0&&read(Pc1DrawBatch.OPACITY_ENABLED)!=0)c.opacity(sourceX,sourceY,read(Pc1DrawBatch.SLOT_BASE+(type&127)*4),read(Pc1DrawBatch.OPACITY_LEVEL));else c.transparent(sourceX,sourceY,read(Pc1DrawBatch.SLOT_BASE+type*4));}
            }else if(flag==state+2)component(component,3);
        }
    }
    private static int position(int[] positions,boolean[] known,int index)throws IOException{if(index<0||index>=known.length||!known[index])throw new IOException("PC1 stepped range reads unknown native local position");return positions[index];}
    private int list(int index,int field){return Pc1NativeUi.LIST_RECORDS+index*104+field;}
    private int visible(int index,int field){return 0x955408+index*16+field;}
    private static int measure(byte[] raw){int length=raw.length;return length>=2&&raw[0]=='@'&&raw[1]=='s'?((length-2)+(length*5-10)*2)*2:length*28;}
    public void listItem(int visibleIndex)throws IOException{
        int dataIndex=read(Pc1NativeUi.LIST_OFFSET)+visibleIndex;if(dataIndex>=read(Pc1NativeUi.LIST_COUNT))return;c.intersectClip(read(visible(visibleIndex,0)),read(visible(visibleIndex,4)),read(visible(visibleIndex,8)),read(visible(visibleIndex,12)));
        if(read(0x9689fc)==0){c.copy(read(visible(visibleIndex,0))-read(list(dataIndex,8)),read(visible(visibleIndex,4))-read(list(dataIndex,12)),read(Pc1DrawBatch.SLOT_BASE+read(list(dataIndex,4))*4));int source=list(dataIndex,36);if(ui.unsignedByte(source)!=0&&read(list(dataIndex,28))!=-1){int x=read(list(dataIndex,24));if(x==-1)x=divide(read(visible(visibleIndex,8))-divide(measure(ui.cstring(source)),2)-read(visible(visibleIndex,0)),2);required(0x4123a0).expand(source,Pc1NativeUi.SHARED_TEXT);required(0x418930).glyphs(x+read(visible(visibleIndex,0)),read(list(dataIndex,28))+read(visible(visibleIndex,4)),0,sharedBacking(),read(list(dataIndex,32)));}}
        else required(0x404e30).specialListItem(visibleIndex,dataIndex,read(0x4305fc)==visibleIndex);
        if(read(0x4305fc)==visibleIndex){int source=list(dataIndex,36);if(ui.unsignedByte(source)!=0&&read(list(dataIndex,28))==-1){int x=read(list(dataIndex,24))==-1?divide(read(0x955400)-read(0x955550)-divide(measure(ui.cstring(source)),2),2):0;c.intersectClip(read(0x955550),read(0x955548),read(0x955400),read(0x9553fc));required(0x4123a0).expand(source,Pc1NativeUi.SHARED_TEXT);required(0x418930).glyphs(read(0x955550)+x,read(0x955548),0,sharedBacking(),read(list(dataIndex,32)));}if(read(0x430600)!=-1){c.setClip(read(visible(visibleIndex,0))-40,read(visible(visibleIndex,4)),read(visible(visibleIndex,0))+16,read(visible(visibleIndex,4))+40);c.transparent(read(visible(visibleIndex,0))+read(0x430600)*-56-136,read(visible(visibleIndex,4)),read(0x43d424));}}
    }
    public void scrollbar()throws IOException{
        int position=read(0x968a08),x,y;if(read(Pc1NativeUi.LIST_VISIBLE)<read(Pc1NativeUi.LIST_COUNT)){c.setClip(read(0x9553c8),read(0x9553cc),read(0x9553c8)+24,read(0x9553cc)+24);x=read(0x9553c8);y=read(0x9553cc)-(read(0x9689f8)==1?24:0);}else{c.setClip(read(0x9553c8),read(0x9553cc),read(0x9553c8)+24,read(0x9553cc)+24);x=read(0x9553c8)-48;y=read(0x9553cc);}c.copy(x,y,read(0x43d424));
        int current=read(0x9553cc)+24,previous=read(0x9553cc),budget=0;while(current<read(0x9553d4)-24){if(++budget>65536)throw new IOException("PC1 scrollbar native loop exceeds host replay bound");c.setClip(read(0x9553c8),current,read(0x9553c8)+24,previous+48);int source=previous,state=read(0x9689f8);if(state==3){if(position<=current)source=current;}else if(state!=4||current<=position)source=current;c.copy(read(0x9553c8)-72,source,read(0x43d424));current+=24;previous+=24;}
        if(read(Pc1NativeUi.LIST_VISIBLE)<read(Pc1NativeUi.LIST_COUNT)){c.setClip(read(0x9553c8),read(0x9553d4)-24,read(0x9553c8)+24,read(0x9553d4));x=read(0x9553c8)-24;y=read(0x9553d4)-(read(0x9689f8)==2?48:24);}else{c.setClip(read(0x9553c8),read(0x9553d4)-24,read(0x9553c8)+24,read(0x9553d4));x=read(0x9553c8)-48;y=read(0x9553d4)-24;}c.copy(x,y,read(0x43d424));if(read(Pc1NativeUi.LIST_VISIBLE)<read(Pc1NativeUi.LIST_COUNT)){c.setClip(read(0x9553c8),position,read(0x9553c8)+24,position+24);c.copy(read(0x9553c8)-48,position-(read(0x9689f8)==5?24:0),read(0x43d424));}
    }
    public void redrawGrid(int left,int top,int right,int bottom)throws IOException{c.setClip(left,top,right,bottom);restore(left,top,right,bottom);for(int n=0;n<read(Pc1NativeUi.LIST_VISIBLE);n++){c.setClip(left,top,right,bottom);listItem(n);}c.setClip(left,top,right,bottom);scrollbar();}
    public void grid(int mode)throws IOException{ui.writeInt(0x4305fc,-1);int offset=read(Pc1NativeUi.LIST_OFFSET);if(offset>0&&read(Pc1NativeUi.LIST_COUNT)<read(Pc1NativeUi.LIST_VISIBLE)+offset)ui.writeInt(Pc1NativeUi.LIST_OFFSET,offset-1);if(read(Pc1NativeUi.LIST_VISIBLE)<read(Pc1NativeUi.LIST_COUNT))ui.writeInt(0x968a08,divide((read(0x9553d4)-read(0x9553cc)-72)*read(Pc1NativeUi.LIST_OFFSET),read(Pc1NativeUi.LIST_COUNT)-read(Pc1NativeUi.LIST_VISIBLE))+24+read(0x9553cc));else ui.writeInt(0x968a08,0);redrawGrid(read(0x9553d8),read(0x9553dc),read(0x9553e0),read(0x9553e4));if(mode!=0){Point point=required(0x40b750).cursorClient();if(point==null)throw new IOException("PC1 cursor position is unknown");hoverGrid(point.x,point.y);}}
    public void hoverGrid(int x,int y)throws IOException{
        int selected;if(read(0x9689f8)==5){int candidate=y-read(0x968a04),bound=read(0x9553cc)+24;if(candidate<bound)candidate=bound;else{bound=read(0x9553d4)-48;if(bound<candidate)candidate=bound;}if(candidate==read(0x968a08))return;int range=read(0x9553d4)-read(0x9553cc)-72;ui.writeInt(Pc1NativeUi.LIST_OFFSET,divide(divide(range+(candidate-read(0x9553cc)-24)*(read(Pc1NativeUi.LIST_COUNT)-read(Pc1NativeUi.LIST_VISIBLE))*2,range),2));selected=read(0x4305fc);ui.writeInt(0x968a08,candidate);}
        else{selected=-1;for(int n=0;n<read(Pc1NativeUi.LIST_VISIBLE);n++)if(read(visible(n,0))<=x&&x<read(visible(n,8))&&read(visible(n,4))<=y&&y<read(visible(n,12))){selected=n;int index=read(Pc1NativeUi.LIST_OFFSET)+n;if(n>=0&&index<read(Pc1NativeUi.LIST_COUNT)&&read(list(index,100))==0)selected=-1;break;}if(selected==read(0x4305fc))return;if(selected!=-1&&read(0x430604)!=255)required(0x41c850).sound(read(0x430604),0);}
        ui.writeInt(0x4305fc,selected);redrawGrid(read(0x9553d8),read(0x9553dc),read(0x9553e0),read(0x9553e4));c.present();
    }
    private int buttonResult(int index)throws IOException{int address=ptr()+index*560+0x204;return ui.unsignedByte(address)==';'?(byte)ui.unsignedByte(address+1)+100:index;}
    /**40AAB0 caches count/pointer/readflag for hit-testing; after range/audio
     *callbacks it reloads the current pointer before each later consumer. */
    public int buttonHit(int x,int y)throws IOException{
        int total=count();ui.writeInt(0x9689f0,x);ui.writeInt(0x9689f4,y);if(total<=0)return -1;int pointer=ptr(),cached=read(0x9da4a8);
        for(int n=0;n<total;n++){int button=pointer+n*560;if((read(button+4)==0||cached!=0)&&read(button)!=0&&read(button+12)<x&&x<read(button+20)&&read(button+16)<y&&y<read(button+24)){
            if(read(ptr()+n*560)==2)rangeMouse(ptr()+n*560);int sound=read(ptr()+n*560+0x200);if(sound!=255)required(0x41c850).sound(sound,0);required(0x41cd70).voice(read(ptr()+n*560+0x228));return buttonResult(n);
        }}return -1;
    }
    /**409B70, including its index−1 alias of the native local bank field. */
    public void rangeMouse(int button)throws IOException{
        int start=button,end=button,count=button,index=button,bank=0;boolean configured=false,stepped=false;int[] positions=new int[10];boolean[] known=new boolean[10];
        for(int n=0;n<12;n++){int component=button+n*40+32,flag=read(component-4);if(flag==5){bank=read(component+20);start=read(component);end=read(component+4);count=read(component+16);index=read(component+24);configured=true;}else if(flag==7){for(int k=1;k<=7;k++){int field=k==1?0:k==2?4:k==7?8:k*4+4;positions[k]=read(component+field);known[k]=true;}stepped=true;}}
        if(!configured)throw new IOException("PC1 rangeMouse reads unowned native local bank without flag5");positions[0]=start;known[0]=true;if(count< -1)throw new IOException("PC1 rangeMouse negative count overwrites unmodeled saved registers");if(count==-1)bank=end;else if(count<10){positions[count]=end;known[count]=true;}
        if(!stepped){int range=end-start,value=divide(divide(range,2)+(read(0x9689f0)-start-4)*(count-1),range);if(value<0)value=0;if(count<=value)value=count-1;input(0x411b10).writeVariable(bank,index,value);return;}
        for(int n=0;n<count;n++){if(n>=65536)throw new IOException("PC1 stepped rangeMouse exceeds replay bound");int mouse=read(0x9689f0);if(position(positions,known,n)<=mouse&&mouse<position(positions,known,n+1)){input(0x411b10).writeVariable(bank,index,n);return;}}
    }
    /**409C90 performs TWO live411910 reads. No flag5 retains native argument
     *register defaults; negative local-array aliases are a precise boundary. */
    public int rangeAdjust(int button,int direction)throws IOException{
        int end=button,count=button,bank=button,index=button;for(int n=0;n<12;n++){int p=button+n*40+32;if(read(p-4)==5){end=read(p+4);count=read(p+16);bank=read(p+20);index=read(p+24);}}
        if(count<0)throw new IOException("PC1 rangeAdjust negative local index overwrites saved caller registers");if(count<10){int ignored=end;/*actual unused stackarray[count] write*/}
        int value=required(0x411910).variable(bank,index);if(direction<0){value--;if(value<0)value=0;}else{value++;if(count<=value)value=count-1;}
        if(value==required(0x411910).variable(bank,index))return 0;input(0x411b10).writeVariable(bank,index,value);return 1;
    }
    /**40A6D0: keyboard scanning skips kinds0/3, not the cached-read condition.
     *Unknown directions retain their original candidate just as native. */
    public int keyboardSelect(int direction)throws IOException{
        int selected=read(Pc1NativeUi.SELECTED_BUTTON),candidate=selected;
        if(direction==0){if(selected==-1)return -1;int sound=read(ptr()+selected*560+0x200);if(sound!=255)required(0x41c850).sound(sound,0);required(0x41cd70).voice(read(ptr()+read(Pc1NativeUi.SELECTED_BUTTON)*560+0x228));return buttonResult(read(Pc1NativeUi.SELECTED_BUTTON));}
        if(direction==1){int pointer=ptr(),total=count();candidate=-1;for(int n=selected+1;n<total;n++){int kind=read(pointer+n*560);if(kind!=0&&kind!=3){candidate=n;break;}}if(candidate==-1)for(int n=0;n<=selected;n++){int kind=read(pointer+n*560);if(kind!=0&&kind!=3){candidate=n;break;}}}
        else if(direction==-1){int pointer=ptr();candidate=-1;for(int n=selected-1;n>=0;n--){int kind=read(pointer+n*560);if(kind!=0&&kind!=3){candidate=n;break;}}if(candidate==-1)for(int n=count()-1;n>=selected;n--){int kind=read(pointer+n*560);if(kind!=0&&kind!=3){candidate=n;break;}}}
        else if(direction==-2||direction==2){if(selected!=-1&&read(ptr()+selected*560)==2&&rangeAdjust(ptr()+selected*560,direction)!=0){int sound=read(ptr()+read(Pc1NativeUi.SELECTED_BUTTON)*560+0x200);if(sound!=255)required(0x41c850).sound(sound,0);required(0x41cd70).voice(read(ptr()+read(Pc1NativeUi.SELECTED_BUTTON)*560+0x228));ui.writeInt(Pc1NativeUi.SELECTED_BUTTON,-1);}}
        else candidate=direction;
        if(candidate!=read(Pc1NativeUi.SELECTED_BUTTON)){ui.writeInt(Pc1NativeUi.SELECTED_BUTTON,candidate);if(candidate!=-1){ui.writeInt(0x4305f8,1);redrawButtons(1);int sound=read(ptr()+candidate*560+0x1fc);if(sound!=255)required(0x41c850).sound(sound,0);required(0x41cd70).voice(read(ptr()+candidate*560+0x224));}else redrawButtons(1);}return -1;
    }
    private int scrollPosition(int offset,int top,int visible,int total)throws IOException{return divide((read(0x9553d4)-top-72)*offset,total-visible)+24+top;}
    private void gridArea()throws IOException{redrawGrid(read(0x9553d8),read(0x9553dc),read(0x9553e0),read(0x9553e4));}
    /**40B430. The caller supplies actualmainHWND through InputServices. */
    public void stopScroll()throws IOException{int timer=read(0x94c678);if(timer!=0){input(0x40b430).killTimer(timer);ui.writeInt(0x94c678,0);}}
    /**40B460 PageUp/PageDown moves by ONE entry, retaining native boundary
     *scrollbar repaint even when the offset cannot advance. */
    public int scrollPage(int key)throws IOException{
        if(read(Pc1NativeUi.LIST_VISIBLE)>=read(Pc1NativeUi.LIST_COUNT))return 0;int offset=read(Pc1NativeUi.LIST_OFFSET);boolean moved=false;
        if(key==0x21){if(offset>0){offset--;moved=true;}}else if(key==0x22){if(offset+read(Pc1NativeUi.LIST_VISIBLE)<read(Pc1NativeUi.LIST_COUNT)){offset++;moved=true;}}else return 0;
        if(moved)ui.writeInt(Pc1NativeUi.LIST_OFFSET,offset);ui.writeInt(0x968a08,scrollPosition(offset,read(0x9553cc),read(Pc1NativeUi.LIST_VISIBLE),read(Pc1NativeUi.LIST_COUNT)));if(moved)gridArea();else scrollbar();c.present();return 0;
    }
    /**40B110, including two repaint/present calls after a changed offset. */
    public int scrollInput(int x,int y,int mode)throws IOException{
        if(mode==1&&read(0x9689f8)==5)return 0;int visible=read(Pc1NativeUi.LIST_VISIBLE),total=read(Pc1NativeUi.LIST_COUNT);if(visible>=total)return 0;int top=read(0x9553cc),bottom=read(0x9553d4);
        if(mode!=2&&(x<read(0x9553c8)||x>=read(0x9553c8)+24||y<top||y>=bottom))return 0;
        int offset=read(Pc1NativeUi.LIST_OFFSET),position=divide((bottom-top-72)*offset,total-visible)+24+top,desired=2;boolean changedPath;
        if(mode==2){desired=read(0x9689f8);ui.writeInt(0x9689f8,0);changedPath=true;}
        else{if(y<top+24)desired=1;else if(y<bottom-24){if(y<position)desired=3;else if(y>position+24)desired=4;else{ui.writeInt(0x968a04,y-position);desired=5;input(0x4261d0).setCapture();visible=read(Pc1NativeUi.LIST_VISIBLE);total=read(Pc1NativeUi.LIST_COUNT);top=read(0x9553cc);offset=read(Pc1NativeUi.LIST_OFFSET);}}
            if(mode==1){ui.writeInt(0x968a08,position);ui.writeInt(0x9689f8,0);scrollbar();c.present();changedPath=false;}else changedPath=true;}
        if(changedPath&&read(0x9689f8)!=desired){if(mode!=2&&read(0x430604)!=255){required(0x41c850).sound(read(0x430604),0);visible=read(Pc1NativeUi.LIST_VISIBLE);total=read(Pc1NativeUi.LIST_COUNT);top=read(0x9553cc);offset=read(Pc1NativeUi.LIST_OFFSET);}ui.writeInt(0x9689f8,desired);boolean move=false;
            if(desired==1){if(offset>0){offset--;move=true;}}else if(desired==2){if(offset+visible<total){offset++;move=true;}}else if(desired==3){if(offset>0){offset-=visible;if(offset<0)offset=0;ui.writeInt(Pc1NativeUi.LIST_OFFSET,offset);move=true;}}else if(desired==4){int next=offset+visible;if(next<total){offset=next;ui.writeInt(Pc1NativeUi.LIST_OFFSET,offset);if(offset+visible>=total)offset=total-visible;move=true;}}
            if(move){ui.writeInt(Pc1NativeUi.LIST_OFFSET,offset);ui.writeInt(0x968a08,scrollPosition(offset,top,visible,total));gridArea();c.present();}scrollbar();c.present();}
        int current=read(0x9689f8);if(current<1||current>4)stopScroll();else if(mode==0||mode==2)ui.writeInt(0x94c678,input(0x40b110).setTimer(3,mode==0?500:100));return 0;
    }
    /**40B650 click consumes an active scrollbar with ReleaseCapture; normal
     *selection reads listOffset again after its click-audio callback. */
    public int gridHit(int x,int y)throws IOException{
        if(read(0x9689f8)!=0){ui.writeInt(0x9689f8,0);ui.writeInt(0x968a08,scrollPosition(read(Pc1NativeUi.LIST_OFFSET),read(0x9553cc),read(Pc1NativeUi.LIST_VISIBLE),read(Pc1NativeUi.LIST_COUNT)));scrollbar();c.present();input(0x4261cc).releaseCapture();return -1;}
        int total=read(Pc1NativeUi.LIST_VISIBLE);for(int n=0;n<total;n++)if(read(visible(n,0))<=x&&x<read(visible(n,8))&&read(visible(n,4))<=y&&y<read(visible(n,12))){int at=read(Pc1NativeUi.LIST_OFFSET)+n;if(at>=read(Pc1NativeUi.LIST_COUNT)||read(list(at,100))==0)return -1;if(read(0x430608)!=255)required(0x41c850).sound(read(0x430608),0);at=read(Pc1NativeUi.LIST_OFFSET)+n;ui.writeInt(0x968a00,read(list(at,0)));return at;}return -1;
    }
    /**40B890, independent of a platform timer registration. */
    public void clearAnimation()throws IOException{ui.writeInt(0x430600,-1);}
    /**40B8A0's explicit callback. The caller must supply the real native
     *event; this method does not guess a Java frame/ms scheduling frequency. */
    public void animationTick()throws IOException{int frame=read(0x430600),selected=read(0x4305fc);if(frame==-1||selected==-1)return;int next=frame+1;ui.writeInt(0x430600,next>=8?0:next);int top=read(visible(selected,4)),left=read(visible(selected,0));redrawGrid(left-40,top,left+16,top+40);c.present();}
}
