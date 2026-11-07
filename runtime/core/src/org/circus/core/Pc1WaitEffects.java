package org.circus.core;

import java.io.IOException;

/** Current CHS4158E0/416620/4167D0/416A30/4047A0. One invocation is one
 *native wait-consumer iteration, not an elapsed-time interpolation. All
 *progress/arguments/coordinates/handles are current canonical native bytes.
 *Required calls preserve their original ordering and side-effect prefixes. */
public final class Pc1WaitEffects {
    public interface Services {
        int createLike(int source)throws IOException;                       //4020B0
        int capture(int rawNameAddress)throws IOException;                  //401EE0
        void freeImage(int handle)throws IOException;                       //401130
        void target(int handle)throws IOException;                          //409480
        void redraw(int mask,int left,int top,int right,int bottom)throws IOException; //41AF50
        void releaseResource(int slot,int mode)throws IOException;          //411080
        void redrawText(int index)throws IOException;                       //418220
        void restoreBody(int left,int top,int right,int bottom,int mode)throws IOException; //415D50
        void rawGlyph28(int x,int y,int rawPointer,int palette)throws IOException; //409140
        void ensureTimer1()throws IOException;                              //407040
        int skip()throws IOException;                                      //4118B0
        void fullClip()throws IOException;                                 //4076B0
        void setClip(int left,int top,int right,int bottom)throws IOException; //407700
        void scale(int handle,int ratio)throws IOException;                 //408880
        void feedback(int x,int y,int handle,int ratio)throws IOException;   //4079A0
        void copy(int x,int y,int handle)throws IOException;                 //407880
        void fillPalette(int left,int top,int right,int bottom,int palette)throws IOException; //408D00
        void present()throws IOException;                                  //407000
        void specialStart()throws IOException;                             //404770
        int specialStep()throws IOException;                               //4051E0
    }
    private static final int A0=0x9da4c0,A1=0x9da4c4,A2=0x9da4c8,PROGRESS=0x9cca8c;
    private static final int CAPTURE=0x9b96e0,BACKGROUND=0x43d418,BUSY=0xbb8434,DELAY=0x430284,SPEED=0x454cf0;
    private final Pc1NativeMemory memory;private final Services io;private int depth;
    public Pc1WaitEffects(Pc1NativeMemory memory,Services services)throws IOException{if(memory==null||services==null)throw new IOException("PC1 wait effects require canonical memory and actual native services");this.memory=memory;io=services;}
    public Pc1WaitEffects(Pc1Runtime runtime,Services services)throws IOException{this(runtime==null?null:runtime.nativeMemory(),services);runtime.bindNativeBoundary(this::requireBoundary);}
    public void requireBoundary()throws IOException{if(depth!=0)throw new IOException("PC1 wait-effect snapshot during native consumer callback");}
    private int d(int address)throws IOException{return memory.readAddress(address)|memory.readAddress(address+1)<<8|memory.readAddress(address+2)<<16|memory.readAddress(address+3)<<24;}
    private void d(int address,int value)throws IOException{for(int n=0;n<4;n++)memory.writeAddress(address+n,value>>>(n*8));}
    private boolean fast()throws IOException{return d(SPEED)==3||io.skip()!=0;}
    private void timer(int delay)throws IOException{d(DELAY,delay);io.ensureTimer1();}
    private int disposeCapture()throws IOException{io.freeImage(d(CAPTURE));d(CAPTURE,-1);d(BUSY,0);return 0;}
    private void incrementFade()throws IOException{int step;if(fast())step=8;else{int speed=d(SPEED);step=speed==2?4:speed==1?2:1;}int value=d(PROGRESS)+step;d(PROGRESS,value);if(value>16)d(PROGRESS,16);}
    public int screen()throws IOException{depth++;try{return screenStep();}finally{depth--;}}
    private int screenStep()throws IOException{
        int kind=d(A0),progress;
        switch(kind){
        case 1:
            progress=d(PROGRESS);if(progress==16)return disposeCapture();
            if(progress==0){d(BUSY,1);int image=io.createLike(0);d(CAPTURE,image);io.target(image);io.redraw(15,0,0,800,600);io.target(0);}
            timer(40);incrementFade();io.fullClip();int ratio=d(PROGRESS),source=d(CAPTURE);io.scale(source,ratio);io.present();return 1;
        case 2:
            progress=d(PROGRESS);if(progress==16)return 0;
            if(progress==0){d(BUSY,1);io.releaseResource(0,0);d(BACKGROUND,io.capture(0x43d9dc));}
            timer(40);incrementFade();io.fullClip();progress=d(PROGRESS);
            if(progress==16)io.fillPalette(0,0,800,600,0);else io.scale(d(BACKGROUND),16-progress);
            io.present();return 1;
        case 3:
            int repeats=d(A1)*2;progress=d(PROGRESS);if(progress>=repeats)return disposeCapture();
            if(progress==0){d(BUSY,1);d(CAPTURE,io.capture(0x43d9d4));}
            timer(40);progress=d(PROGRESS)+1;d(PROGRESS,progress);
            if((progress&1)!=0)io.fillPalette(0,0,800,600,7);else io.copy(0,0,d(CAPTURE));
            io.present();return 1;
        case 4:case 6:
            progress=d(PROGRESS);if(progress==9)return disposeCapture();
            if(progress==0){d(BUSY,1);d(CAPTURE,io.capture(kind==4?0x43d9cc:0x43d9c0));}
            timer(40);boolean fast=fast();progress=d(PROGRESS);
            if(fast&&progress==3){progress=9;d(PROGRESS,9);}else{progress++;d(PROGRESS,progress);if(progress>=9){progress=9;d(PROGRESS,9);}}
            int remainder=progress%3;
            if(remainder==0)io.copy(0,0,d(CAPTURE));
            else if(remainder==1){if(kind==4){io.fillPalette(780,0,800,600,0);io.copy(-20,0,d(CAPTURE));}else{io.fillPalette(0,580,800,600,0);io.copy(0,-20,d(CAPTURE));}}
            else if(remainder==2){if(kind==4){io.fillPalette(0,0,19,600,0);io.copy(20,0,d(CAPTURE));}else{io.fillPalette(0,0,800,19,0);io.copy(0,20,d(CAPTURE));}}
            io.present();return 1;
        default:return 1; //The actual native default, including kind5.
        }
    }
    public int backgroundPan()throws IOException{depth++;try{return backgroundStep();}finally{depth--;}}
    private int backgroundStep()throws IOException{
        if(d(0x9d96bc)==d(A0)&&d(0x9d96c0)==d(A1)){int progress=d(PROGRESS);if(progress==0)return 0;int extra=d(A2)+1;d(A2,extra);if(extra+progress>16)d(PROGRESS,0);}
        timer(10);int step;if(fast())step=16;else{int speed=d(SPEED);step=speed==2?8:speed==1?4:2;}
        moveBackground(0x9d96bc,A0,step);moveBackground(0x9d96c0,A1,step);
        if(d(PROGRESS)==0)io.restoreBody(0,0,800,600,0);else{io.fullClip();int ratio=d(PROGRESS),image=d(BACKGROUND),y=d(0x9d96c0),x=d(0x9d96bc);io.feedback(x,y,image,ratio);}
        io.present();return 1;
    }
    private void moveBackground(int address,int target,int amount)throws IOException{int current=d(address),destination=d(target);if(current<destination){current+=amount;d(address,current);if(current>destination)d(address,destination);}else if(current>destination){current-=amount;d(address,current);if(current<destination)d(address,destination);}}
    public int actorFade()throws IOException{depth++;try{return actorFadeStep();}finally{depth--;}}
    private int actorFadeStep()throws IOException{
        int missing=d(0x9b9be4);d(0xbb8540,0);
        if(missing==-1&&memory.readAddress(0x9d868c)!=0){io.fillPalette(40,40,512,72,0);io.rawGlyph28(40,40,0x9d868c,7);if(d(0x97176c)==-1){appendMissingSuffix();io.fillPalette(40,80,512,112,0);io.rawGlyph28(40,80,0x9d868c,7);}io.present();return 0;}
        int progress=d(A1);if(progress==5)return disposeCapture();
        if(progress==0){int speed=d(SPEED);d(BUSY,1);if(speed==3||io.skip()!=0)d(CAPTURE,-1);else{int image=io.capture(0x43d9e4);d(CAPTURE,image);io.target(image);}
            int bottom=d(0xbb06dc),right=d(0xbb06e0),top=d(0x9dbab4),left=d(0x9dbab0);io.redraw(255,left,top,right,bottom);if(d(0xbb844c)==1)io.redrawText(-1);io.target(0);progress=d(A1);}
        int image=d(CAPTURE);progress++;d(A1,progress);
        if(image==-1){d(A1,5);io.present();d(BUSY,0);return 0;}
        int speed=d(SPEED),ratio;if(speed==2){progress+=2;ratio=12;d(A1,progress);}else if(speed==1){progress++;ratio=10;d(A1,progress);}else ratio=6;
        if(progress>5)d(A1,5);timer(40);
        int bottom=d(0xbb06dc),right=d(0xbb06e0),top=d(0x9dbab4),left=d(0x9dbab0);io.setClip(left,top,right,bottom);
        if(d(A1)==5)io.copy(0,0,d(CAPTURE));else io.feedback(0,0,d(CAPTURE),ratio);io.present();return 1;
    }
    private void appendMissingSuffix()throws IOException{
        int source=0x43d8d8,length=0;while(memory.readAddress(source+length)!=0)length++;length++;
        int destination=0x9d868c;while(memory.readAddress(destination)!=0)destination++;
        int words=length>>>2;for(int n=0;n<words;n++){int value=d(source);d(destination,value);source+=4;destination+=4;}for(int n=0;n<(length&3);n++)memory.writeAddress(destination+n,memory.readAddress(source+n));
    }
    public int actorMove()throws IOException{depth++;try{return actorMoveStep();}finally{depth--;}}
    private int actorMoveStep()throws IOException{
        int x=d(0xbb8400)==0?0x9d96c4:0x9d96cc,y=x+4;
        if(d(x)==d(A0)&&d(y)==d(A1))return 0;
        timer(10);int amount;if(fast())amount=64;else{int speed=d(SPEED);amount=speed==2?32:speed==1?16:8;}
        moveActor(x,A0,amount);moveActor(y,A1,amount);io.redraw(255,0,0,800,600);io.present();return 1;
    }
    private void moveActor(int address,int target,int amount)throws IOException{int current=d(address),destination=d(target);if(current<destination){current+=amount;d(address,current);destination=d(target);if(current>destination)d(address,destination);}else if(current>destination){current-=amount;d(address,current);destination=d(target);if(current<destination)d(address,destination);}}
    public int special()throws IOException{depth++;try{int mode=d(0x94bc24);if(mode==0){io.specialStart();d(DELAY,40);}else if(mode==1&&io.specialStep()!=0){io.present();return 1;}io.present();return 0;}finally{depth--;}}
}
