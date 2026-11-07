package org.circus.core;

import java.io.*;

/** Current CHS412160 and the complete1C case414FDA..415274. Raw ordinary
 * bytes, saved UI/image aliases and coroutine locals are separate owners.
 * Native41A0B0 is the actual Pc1Transition.Frame, never a portable screenshot
 * fade. Script source admission stays in the required loader service. */
public final class Pc1SystemNative {
    public interface Services {
        void resetEffects40F560(int kind,int strength)throws IOException;
        int capture401EE0(int rawLabel)throws IOException;
        void releaseGraphics411080(int slot,int mode)throws IOException;
        void loadScript411670(int rawName,int mode)throws IOException;
        void freeImage401130(int handle)throws IOException;
        void rebuildGraphics41A570(int rawFrame)throws IOException;
        void target409480(int handle)throws IOException;
        void body415D50(int left,int top,int width,int height,int mode)throws IOException;
        void redrawText418220(int selected)throws IOException;
        void activateText412320()throws IOException;
        void sound41C850(int sound,int loop)throws IOException;
    }
    private final Pc1NativeMemory bus;private final Pc1SystemMemory memory;private final Services services;private final Pc1Transition transition;private final Pc1LabelScanner labels;private final Pc1InlineText.Memory inline;
    public Pc1SystemNative(Pc1NativeMemory bus,Pc1SystemMemory memory,Pc1Transition transition,Services services)throws IOException{
        if(bus==null||memory==null||transition==null||services==null)throw new IOException("PC1 native system requires canonical memory/transition and complete services");memory.requireBus(bus);this.bus=bus;this.memory=memory;this.transition=transition;this.services=services;labels=new Pc1LabelScanner(bus);inline=new Pc1InlineText.Memory(){public int read(int at)throws IOException{return bus.readAddress(at);}public void write(int at,int value)throws IOException{bus.writeAddress(at,value);}};
    }
    private int b(int at)throws IOException{return bus.readAddress(at);}
    private int d(int at)throws IOException{return b(at)|b(at+1)<<8|b(at+2)<<16|b(at+3)<<24;}
    private void d(int at,int value)throws IOException{for(int n=0;n<4;n++)bus.writeAddress(at+n,value>>>(n*8));}
    private void copy(int target,int source,int count)throws IOException{for(int n=0;n<count;n++)d(target+n*4,d(source+n*4));}
    private void string(int target,int source)throws IOException{int size=0;for(;size<1024*1024;size++)if(b(source+size)==0)break;if(size==1024*1024)throw new IOException("PC1 system C source exceeds known host bound");int length=size+1,n=0;for(;n+4<=length;n+=4)d(target+n,d(source+n));for(;n<length;n++)bus.writeAddress(target+n,b(source+n));}
    public void requireBoundary()throws IOException{memory.requireBoundary();transition.requireBoundary();}
    public int enter(int rawName,int mode)throws IOException{
        memory.begin();try{
            d(0x9b9f1c,0);Pc1UiPage.capture(inline);copy(Pc1FrameLayout.SYSTEM_BASE,Pc1FrameLayout.LIVE_BASE,0x7d1);
            if(mode==0){services.resetEffects40F560(0,0);d(Pc1SystemMemory.CAPTURE,services.capture401EE0(Pc1SystemMemory.ENTER_LABEL));d(Pc1SystemMemory.SAVED_BACKGROUND,d(0x43d418));d(0x43d418,-1);services.releaseGraphics411080(1,0);services.releaseGraphics411080(2,0);}
            else if(b(0x9d860c)==0){d(Pc1SystemMemory.SAVED_BACKGROUND,d(0x43d418));d(0x43d418,-1);}else d(Pc1SystemMemory.SAVED_BACKGROUND,-1);
            int pc=d(0x9d8a14),list=d(0x9553e8),history=d(0x43d410);d(0x9b9f14,pc);d(0xbb843c,list);int visible=d(0xbb844c);d(Pc1SystemMemory.SAVED_HISTORY,history);d(0x9da4cc,visible);d(0x9d8a20,-1);services.loadScript411670(rawName,0);int wait=d(0x9d8a20);d(0x9d8a14,2);d(0xbb8440,0);return wait==1?-1:0;
        }finally{memory.end();}
    }
    public enum Outcome { ACTIVE,CONTINUE_DISPATCH,INTERRUPTED_DISPATCH }
    public ReturnFrame beginReturn()throws IOException{ReturnFrame frame=new ReturnFrame();memory.begin();frame.inside=true;try{frame.start();return frame;}catch(IOException|RuntimeException error){frame.failed=true;throw error;}finally{frame.inside=false;memory.end();}}
    public final class ReturnFrame {
        private static final int FORMAT=0x50315352,VERSION=1;
        private Outcome outcome=Outcome.ACTIVE;private boolean text,inside,failed;private Pc1Transition.Frame transfer;
        private ReturnFrame(){}
        public Outcome outcome(){return outcome;}
        public boolean active(){return outcome==Outcome.ACTIVE&&!failed;}
        private void start()throws IOException{
            if(d(Pc1SystemMemory.CAPTURE)!=-1){
                int current=d(0x43d418);if(current!=d(Pc1SystemMemory.SAVED_BACKGROUND)){services.freeImage401130(current);d(0x43d418,-1);}
                services.freeImage401130(d(0x9cca90));int mask=d(0x9cca98);d(0x9cca90,-1);services.freeImage401130(mask);int overlay=d(0x9cca94);d(0x9cca98,-1);services.freeImage401130(overlay);
                copy(0x9d6ab4,0x9d91f4,0xfa);d(0x9cca94,-1);copy(Pc1FrameLayout.LIVE_BASE,Pc1FrameLayout.SYSTEM_BASE,0x7d1);
            }else string(0x9d858c,0x9d5e4c);
            services.loadScript411670(0x9d858c,0);int capture=d(Pc1SystemMemory.CAPTURE),background=d(Pc1SystemMemory.SAVED_BACKGROUND);d(0x43d418,background);d(0x9d96e4,0);if(capture!=-1)services.rebuildGraphics41A570(Pc1FrameLayout.LIVE_BASE);
            Pc1UiPage.restore(inline);int history=d(Pc1SystemMemory.SAVED_HISTORY),pc=d(0x9b9f14),visibility=d(0x9da4cc);d(0x43d410,history);int selector=d(0x9b9f1c);d(0x9d8a14,pc);d(0xbb844c,visibility);
            if(selector==1)d(0xbb842c,1);
            else if(selector==4){int found=labels.find(Pc1SystemMemory.NEW_LABEL);if(found!=-1){services.resetEffects40F560(0,0);d(0x9d8a20,-1);d(0x9d8a14,found);d(0x9d8a1c,0);bus.writeAddress(0x9d8a24,0);bus.writeAddress(0x9d8a25,0);d(0x9da4b0,-1);outcome=Outcome.CONTINUE_DISPATCH;return;}}
            int wait=d(0x9d8a20);if(wait==7||wait==9||wait==-1){
                d(0x94fa28,-1);text=wait==7||wait==9;
                if(text){int handle=d(Pc1SystemMemory.CAPTURE);if(handle==-1||d(0x94c6d8+(handle<<7))==0){handle=services.capture401EE0(Pc1SystemMemory.REDRAW_LABEL);d(Pc1SystemMemory.CAPTURE,handle);}services.target409480(handle);services.body415D50(0,0,800,600,0);services.redrawText418220(d(0x43d410));services.target409480(0);}
                transfer=transition.beginFrame(d(Pc1SystemMemory.CAPTURE),0);if(!transfer.active())afterTransfer();
            }else{if(d(Pc1SystemMemory.CAPTURE)!=-1){services.freeImage401130(d(Pc1SystemMemory.CAPTURE));d(Pc1SystemMemory.CAPTURE,-1);}common();}
        }
        private void afterTransfer()throws IOException{if(transfer.result()!=0){outcome=Outcome.INTERRUPTED_DISPATCH;return;}services.freeImage401130(d(Pc1SystemMemory.CAPTURE));if(text)services.activateText412320();d(Pc1SystemMemory.CAPTURE,-1);common();}
        private void common()throws IOException{int sound=d(0x9d8a10),list=d(0xbb843c);d(0xbb8440,list);if((sound&0x100)!=0)services.sound41C850(sound&255,1);int effect=d(0x9da4b4);if(effect!=0)services.resetEffects40F560(effect,d(0x9da4bc));outcome=Outcome.CONTINUE_DISPATCH;}
        /**Exactly one405710 pump via the existing actual native Frame. */
        public Outcome step()throws IOException{if(inside)throw new IOException("PC1 system return same-frame reentry");if(failed)throw new IOException("PC1 system return failed native service");if(outcome!=Outcome.ACTIVE)return outcome;inside=true;memory.begin();try{transfer.step();if(!transfer.active())afterTransfer();return outcome;}catch(IOException|RuntimeException error){failed=true;throw error;}finally{memory.end();inside=false;}}
        public void save(DataOutput out)throws IOException{requireBoundary();if(inside||failed)throw new IOException("PC1 system return not at a resumable native boundary");out.writeInt(FORMAT);out.writeInt(VERSION);out.writeInt(outcome.ordinal());out.writeBoolean(text);out.writeBoolean(transfer!=null);if(transfer!=null)transfer.save(out);}
    }
    public ReturnFrame loadReturn(DataInput in)throws IOException{
        requireBoundary();if(in.readInt()!=ReturnFrame.FORMAT||in.readInt()!=ReturnFrame.VERSION)throw new IOException("PC1 system return frame format");int saved=in.readInt();if(saved<0||saved>=Outcome.values().length)throw new IOException("PC1 system return outcome");ReturnFrame frame=new ReturnFrame();frame.outcome=Outcome.values()[saved];frame.text=in.readBoolean();if(in.readBoolean())frame.transfer=transition.loadFrame(in);if(frame.outcome==Outcome.ACTIVE&&(frame.transfer==null||!frame.transfer.active())||frame.outcome!=Outcome.ACTIVE&&frame.transfer!=null&&(frame.transfer.active()||frame.transfer.result()!=(frame.outcome==Outcome.INTERRUPTED_DISPATCH?1:0))||frame.outcome==Outcome.INTERRUPTED_DISPATCH&&frame.transfer==null)throw new IOException("PC1 system return transfer phase mismatch");return frame;
    }
}
