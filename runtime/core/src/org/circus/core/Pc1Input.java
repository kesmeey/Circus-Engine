package org.circus.core;
import java.io.*;

/** Current798897419280/419BD0/419C90 routes over the one canonical bus.
 *Script/context/file/audio/text consumers remain typed required leaves. */
public final class Pc1Input {
    public interface Services {
        void redraw(int mask,int left,int top,int right,int bottom)throws IOException; //41AF50
        void storyControls()throws IOException; //416B80
        void restoreExtraControls()throws IOException; //416B40
        void redrawText(int selectedHistory)throws IOException; //418220
        void dictionaryControls()throws IOException; //412320
        void stopVoice()throws IOException; //41CBE0
        void historyUp()throws IOException; //4189D0
        void historyDown()throws IOException; //418A20
        void historySelect(int row)throws IOException; //418600
        int enterSystem(int rawName,int mode)throws IOException; //412160
        void loadScript(int rawName,int mode)throws IOException; //411670
        void externalScript(int rawName)throws IOException; //4115A0 named-label jump; no file I/O
        void writeSlot(int slot)throws IOException; //41AAD0,metadata/file effects required
        int variable(int bank,int index)throws IOException; //411910
    }
    private static final int WAIT=0x9d8a20,PC=0x9d8a14,C0=0x9da4c0,C4=0x9da4c4,ACTION0=0x9d95dc,ACTION1=0x9d95fc;
    private final Pc1NativeMemory memory;private final Pc1UiRaster ui;private final Pc1Compositor c;private final Services io;private final Pc1LabelScanner labels;private int depth;
    public Pc1Input(Pc1NativeMemory memory,Pc1UiRaster ui,Pc1Compositor compositor,Services services)throws IOException{if(memory==null||ui==null||compositor==null||services==null)throw new IOException("PC1 input requires canonical bus/UI/compositor and all typed native leaves");this.memory=memory;this.ui=ui;c=compositor;io=services;labels=new Pc1LabelScanner(memory);}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 input snapshot during active native callback");}
    private int b(int at)throws IOException{return memory.readAddress(at);}
    private int g(int at)throws IOException{return b(at)|b(at+1)<<8|b(at+2)<<16|b(at+3)<<24;}
    private void g(int at,int v)throws IOException{for(int n=0;n<4;n++)memory.writeAddress(at+n,v>>>(n*8));}
    private int button(int index)throws IOException{return g(Pc1NativeUi.BUTTON_POINTER)+index*560;}
    private void clearAction(int at)throws IOException{for(int n=0;n<8;n++)g(at+n*4,0);}
    private void copyFrame()throws IOException{for(int n=0;n<0x7d1;n++){int value=g(Pc1FrameLayout.LIVE_BASE+n*4);g(Pc1FrameLayout.CANDIDATE_BASE+n*4,value);}}
    private void captureIfNeeded()throws IOException{if(g(0xbb8418)==0)copyFrame();}
    private int enter(int rawName)throws IOException{return io.enterSystem(rawName,0);}
    private void copyString(int destination,int source)throws IOException{int length=0;while(b(source+length)!=0){if(++length>=1024*1024)throw new IOException("PC1 input strlen exceeds known native backing");}length++;int whole=length>>>2;for(int n=0;n<whole;n++){int value=g(source+n*4);g(destination+n*4,value);}for(int n=whole*4;n<length;n++)memory.writeAddress(destination+n,b(source+n));}
    /**4191A0 restores pixels/free/present before restoring pointer/count. */
    public void closeModal()throws IOException{depth++;try{c.copy(g(0x9d8010),g(0x9d8014),g(0x9ccba0));c.freeChecked(g(0x9ccba0));c.present();g(Pc1NativeUi.BUTTON_POINTER,g(0x971460));g(Pc1NativeUi.BUTTON_COUNT,g(0x9d8018));}finally{depth--;}}
    public void hideText()throws IOException{depth++;try{g(0xbb8414,1);g(0xbb844c,0);g(Pc1LayoutMemory.GLYPH_COUNT,0);io.redraw(255,0,0,800,600);c.present();}finally{depth--;}}
    /**419230, including ENTERconsumed after history handling. */
    public int advance()throws IOException{depth++;try{io.stopVoice();if(g(0x43d410)>=0){io.historyDown();return 0;}if(g(0xbb8410)!=0){g(0xbb8410,0);return 0;}if(g(WAIT)==9)return 0;g(WAIT,-1);return 1;}finally{depth--;}}
    private void specialEvent(int x,int y,int key)throws IOException{switch(key){case 1:g(0x968a68,1);break;case 2:case 0x1b:g(0x968a6c,1);break;case 0x21:g(0x968a60,1);break;case 0x22:g(0x968a64,1);break;default:break;}g(0x968a70,x);g(0x968a74,y);}
    private int specialInput(int x,int y,int key)throws IOException{int mode=g(0x94bc24);if(mode==0)specialEvent(x,y,key);else if(mode==1&&(key==1||key==2)&&io.variable(100,59)==1)g(WAIT,-1);return 0;}
    private void specialHover(int x,int y)throws IOException{boolean inside=x>=20&&x<=74&&y>=120&&y<=154;if(inside)g(0x94bc28,1);else if(g(0x94bc28)!=0)g(0x94bc28,0);if(g(0x94bc24)==0)specialEvent(x,y,-1);ui.hoverButtons(x,y);c.present();}
    public void hover(int x,int y)throws IOException{depth++;try{int wait=g(WAIT);if(g(0xbb8470)==1||wait==0x44)ui.hoverButtons(x,y);else if(wait==0x1d)specialHover(x,y);else if(wait==7||wait==9){if(g(0xbb8414)==0)ui.hoverButtons(x,y);}else if(wait==10){int arg=g(C4);if(arg!=1){if(arg>1)g(C0,arg);ui.hoverButtons(x,y);}}else if(wait==11){ui.hoverGrid(x,y);ui.hoverButtons(x,y);}}finally{depth--;}}
    public void leftDown(int x,int y)throws IOException{depth++;try{int wait=g(WAIT);if(wait==7||wait==9){if(g(0xbb8414)==0)ui.redrawButtons(2);}else if(wait==10){int arg=g(C4);if(arg!=1){if(arg>1)g(C0,arg);ui.redrawButtons(2);}}else if(wait==11){ui.scrollInput(x,y,0);ui.redrawButtons(2);}else if(wait==0x1d){/*40DFF0 is a real emptyRET in this image.*/}else if(g(0xbb8470)==1||wait==0x44)hover(x,y);}finally{depth--;}}
    public int timer3(int x,int y,int mode)throws IOException{depth++;try{return ui.scrollInput(x,y,mode);}finally{depth--;}}
    private int finishLabel(int label)throws IOException{g(PC,label);g(WAIT,-1);return 1;}
    private int modal(int x,int y,int key)throws IOException{
        int selected;if(key==1)selected=ui.buttonHit(x,y);else if(key==0x28){ui.keyboardSelect(1);return 0;}else if(key==0x26){ui.keyboardSelect(-1);return 0;}else if(key==13)selected=ui.keyboardSelect(0);else return 0;if(selected<0)return 0;
        int action=button(selected)+0x204;if(b(action)==':'){int second=b(action+1);if(second=='y'){g(0xbb8474,1);selected=labels.find(0x9dbabc);}else if(second=='n'){g(0xbb8474,0);selected=labels.find(0x971440);}}
        if(g(0xbb8470)!=0)g(0xbb8470,2);closeModal();if(g(0xbb8470)!=0||selected==-1)return 0;return finishLabel(selected);
    }
    private int waitAction(int key)throws IOException{
        int label;if(b(ACTION0)!=0&&(key==2||key==0x1b)&&(label=labels.find(ACTION0))!=-1){clearAction(ACTION0);return finishLabel(label);}if(b(ACTION1)!=0&&(key==1||key==13)&&(label=labels.find(ACTION1))!=-1){clearAction(ACTION1);return finishLabel(label);}if((g(C4)&1)!=0&&(key==1||key==2||key==13||key==0x1b)){g(WAIT,-1);return 1;}return 0;
    }
    private int menu(int x,int y,int key)throws IOException{
        int arg=g(C4);if(arg==1)return 0;if(arg>1)g(C0,arg);int label;if(b(ACTION0)!=0&&(key==2||key==0x1b)&&(label=labels.find(ACTION0))!=-1){if(g(WAIT)==11)ui.stopScroll();clearAction(ACTION0);return finishLabel(label);}
        if(g(WAIT)==11&&(key==0x21||key==0x22)){ui.scrollPage(key);return 0;}if(key==0x28){ui.keyboardSelect(1);return 0;}if(key==0x26){ui.keyboardSelect(-1);return 0;}if(key==0x25){ui.keyboardSelect(-2);return 0;}if(key==0x27){ui.keyboardSelect(2);return 0;}
        int selected;if(key==13){selected=ui.keyboardSelect(0);if(selected<0)return 0;}else if(key==1){if(g(WAIT)==11){ui.scrollInput(x,y,1);int row=ui.gridHit(x,y);if(row!=-1){label=labels.find(g(0x95561c+row*104));if(label!=-1){ui.clearAnimation();return finishLabel(label);}}}selected=ui.buttonHit(x,y);}else return 0;
        g(0x43d41c,selected);if(selected>99){if(selected==215||selected==208){captureIfNeeded();if(enter(selected==215?0x971564:0x9715e4)==-1)return 0;g(0x9b9f14,g(0xbb841c));return 1;}if(selected==211){captureIfNeeded();enter(0x9716e4);return 0;}int name=selected==210?0x43da20:selected==216?0x43da18:selected==205?0x43da10:0;if(name!=0){g(WAIT,-1);io.externalScript(name);return 1;}return 0;}
        if(selected==-1)return 0;label=labels.find(button(selected)+0x204);return label==-1?0:finishLabel(label);
    }
    private int storyAction(int selected)throws IOException{
        int action=button(selected)+0x204;if(b(action)==':')switch(b(action+1)){
        case 'u':io.stopVoice();io.historyUp();return 0;
        case 'd':return advance();
        case 'c':hideText();return 0;
        case 'l':io.stopVoice();captureIfNeeded();return enter(0x9715e4)!=-1?1:0;
        case 's':io.stopVoice();captureIfNeeded();return enter(0x971564)!=-1?1:0;
        case 'r':if(g(WAIT)==7){g(0xbb842c,1);return 1;}return 0;
        case 'o':io.stopVoice();captureIfNeeded();enter(0x9716e4);return 0;
        case 'm':io.stopVoice();captureIfNeeded();io.stopVoice();enter(0x971664);return 0;
        case 'q':copyFrame();io.writeSlot(1);return 0;
        case 'w':io.stopVoice();copyString(0xbb84f0,g(button(selected)+0x22c));enter(0x43da08);return 0;
        default:return 0;
        }
        if(selected>0){int label=labels.find(button(selected)+0x204);if(label==-1)return 0;io.restoreExtraControls();g(PC,label);g(0x9d8a1c,g(0x9d8a1c)-1);g(WAIT,-1);return 1;}
        return Integer.MIN_VALUE;
    }
    private int story(int x,int y,int key)throws IOException{
        if(g(0xbb8414)!=0){g(0xbb8414,0);io.redraw(15,0,0,800,600);io.storyControls();io.redrawText(g(0x43d410));io.dictionaryControls();c.present();return 0;}
        if(key==0x21){io.stopVoice();io.historyUp();return 0;}if(key==0x22){io.stopVoice();io.historyDown();return 0;}if(key==0x2d){/*40DFF0 genuineRET*/return 1;}if(key==0x20){hideText();return 0;}
        if(key==0x1b||key==2){if(g(0x43d410)>=0){io.stopVoice();g(0x43d410,-1);io.redrawText(-1);io.dictionaryControls();return 0;}captureIfNeeded();io.stopVoice();return enter(0x971664)!=-1?1:0;}
        if(key==0x28||key==0x26){if(g(WAIT)==9)ui.keyboardSelect(key==0x28?1:-1);return 0;}
        if(key==13){if(g(WAIT)==9){int selected=ui.keyboardSelect(0);if(selected>=0){int result=storyAction(selected);if(result!=Integer.MIN_VALUE)return result;if(g(0xbb840c)!=0){g(0xbb840c,0);return 0;}}}}
        else if(key==1){int selected=ui.buttonHit(x,y);if(selected>=0){int result=storyAction(selected);if(result!=Integer.MIN_VALUE)return result;}if(g(0xbb840c)!=0){g(0xbb840c,0);return 0;}}
        else return 0;
        if(g(0x43d410)>=0){io.historySelect(g(0x43d410));return 0;}return advance();
    }
    public int input(int x,int y,int key)throws IOException{depth++;try{
        if(g(0xbb8470)!=0||g(WAIT)==0x44)return modal(x,y,key);if(g(0xbb842c)!=0&&(key==2||key==0x1b)){g(0xbb842c,0);return 0;}int wait=g(WAIT);if(wait==0x1d)return specialInput(x,y,key);if(wait==7||wait==9)return story(x,y,key);if(wait==10||wait==11)return menu(x,y,key);if(wait==0x12)return waitAction(key);if(wait==1){io.loadScript(0x9714e4,1);g(WAIT,-1);return 1;}return 0;
    }finally{depth--;}}
}
