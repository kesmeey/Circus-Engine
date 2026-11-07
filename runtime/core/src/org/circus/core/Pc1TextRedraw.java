package org.circus.core;

import java.io.*;

/** Current418220, including418090/418160 and the real canonical render
 *graph. It does not pre-build a Replay DTO, mutate the VM PC, replace the
 *live body/speaker globals, or activate dictionary controls after return. */
public final class Pc1TextRedraw {
    private static final int LOG=0x9d8a24,COUNT=0x9d8a1c,PARAGRAPH=0x9da4ac;
    private static final String STACK="pc1.418220/";
    private final Pc1Runtime runtime;
    private final Pc1GraphicsPipeline graphics;
    private final Pc1RenderContext render;
    private final Pc1NativeMemory memory;
    public Pc1TextRedraw(Pc1Runtime runtime,Pc1GraphicsPipeline graphics)throws IOException{
        if(runtime==null||graphics==null||graphics.runtime()!=runtime)throw new IOException("PC1 redraw requires the same canonical runtime/graphics graph");
        this.runtime=runtime;this.graphics=graphics;render=graphics.render();memory=runtime.nativeMemory();
    }
    private int b(int address)throws IOException{return memory.readAddress(address);}
    private int g(int address)throws IOException{return b(address)|b(address+1)<<8|b(address+2)<<16|b(address+3)<<24;}
    private void g(int address,int value)throws IOException{for(int n=0;n<4;n++)memory.writeAddress(address+n,value>>>(n*8));}
    private int log(int index)throws IOException{int address=LOG+index*2;return b(address)|b(address+1)<<8;}
    private int checkpoint(int index)throws IOException{return g(Pc1CheckpointMemory.TABLE+(log(index)&0x7fff)*4);}
    private int code(int rawOffset)throws IOException{return b(Pc1CodeBuffer.BASE+rawOffset);}
    private int strlenAddress(int address)throws IOException{for(int n=0;n<1024*1024;n++)if(b(address+n)==0)return n;throw new IOException("PC1 redraw C string exceeds bounded known owner traversal");}
    private static int at(byte[] bytes,int offset)throws IOException{if(offset<0||offset>=bytes.length)throw new IOException("PC1 redraw reads beyond known stack source backing");return bytes[offset]&255;}
    private static int length(byte[] bytes)throws IOException{for(int n=0;n<bytes.length;n++)if(bytes[n]==0)return n;throw new IOException("PC1 redraw stack source lacks known NUL");}
    private Pc1TextLayout.Source source(boolean speaker,String invocation)throws IOException{String owner=speaker?Pc1TextBuffers.REDRAW_SPEAKER:Pc1TextBuffers.REDRAW_BODY;return new Pc1TextLayout.Source(invocation+(speaker?"speaker":"body"),runtime.textBuffers().knownBytes(owner));}
    /**418090 scans raw marker+NUL records, not physical instruction framing.
     *Every record is copied through the1024-byte encoded temporary, even when
     *its marker is neitherK norL. All substitutions are expanded at THIS call. */
    private boolean scan(int rawOffset)throws IOException{
        for(int records=0;records<1024*1024;records++){
            int marker=code(rawOffset),p=rawOffset+1;ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            for(;;){int value=code(p++);if(value==0)break;if(encoded.size()==1023)throw new IOException("PC1 redraw418090 encoded local1024 overflow at rawPC "+rawOffset);encoded.write(value);}
            rawOffset=p;
            if(marker=='K'||marker=='L'){
                byte[] decoded=Pc1TextExpander.decodeEncoded(encoded.toByteArray());boolean speaker=marker=='K';
                byte[] expanded=speaker?Pc1TextExpander.expandSpeaker(decoded,runtime.nativeCatalogTextContext()):Pc1TextExpander.expand(decoded,runtime.nativeCatalogTextContext());
                runtime.textBuffers().writeExpanded(speaker?Pc1TextBuffers.REDRAW_SPEAKER:Pc1TextBuffers.REDRAW_BODY,expanded);
                if(!speaker)return Pc1TextLog.beginsNewLine(expanded);
            }
        }
        throw new IOException("PC1 redraw418090 record traversal exceeds bounded native work");
    }
    private void speaker(Pc1TextLayout layout,int style,String invocation)throws IOException{
        if(g(PARAGRAPH)!=0)return;
        render.restoreName(0,0,800,600,1);int first=g(Pc1LayoutMemory.GLYPH_COUNT);
        Pc1TextLayout.Source src=source(true,invocation);byte[] bytes=src.bytes();if(at(bytes,0)==0)return;
        layout.speaker(src,style,g(0x9da4c4),false);render.drawGlyphJournal(first);
    }
    private void body(Pc1TextLayout layout,int style,String invocation)throws IOException{
        g(Pc1LayoutMemory.BODY_CURSOR,0);Pc1TextLayout.Source src=source(false,invocation);byte[] bytes=src.bytes();layout.bodySource(src);
        if(at(bytes,0)=='@'&&at(bytes,1)=='K'){
            int x=g(Pc1InlineText.X),y=g(Pc1InlineText.Y);render.restoreBody(x,y-2,x+3,y+30,0);render.compositor().fullClip();g(Pc1LayoutMemory.BODY_CURSOR,g(Pc1LayoutMemory.BODY_CURSOR)+2);
        }
        int first=g(Pc1LayoutMemory.GLYPH_COUNT);g(Pc1InlineText.LENGTH,length(bytes));
        do{int cursor=g(Pc1LayoutMemory.BODY_CURSOR);
            if(at(bytes,cursor)=='\n'){g(Pc1InlineText.X,g(Pc1MessageGeometry.BODY_X));g(Pc1InlineText.Y,g(Pc1InlineText.Y)+g(Pc1MessageGeometry.BODY_PITCH));g(Pc1LayoutMemory.BODY_CURSOR,g(Pc1LayoutMemory.BODY_CURSOR)+1);}
            else{Pc1TextLayout.Step step=layout.step(src,cursor,style,true,g(0x9da4c4));g(Pc1LayoutMemory.BODY_CURSOR,step.nextCursor);}
        }while(g(Pc1LayoutMemory.BODY_CURSOR)<g(Pc1InlineText.LENGTH));
        render.drawGlyphJournal(first);
    }
    private void paragraphBody(Pc1TextLayout layout,int rawOffset,int style,String invocation)throws IOException{
        if(scan(rawOffset)&&g(Pc1InlineText.X)!=g(Pc1MessageGeometry.BODY_X)){
            g(Pc1InlineText.X,g(Pc1MessageGeometry.BODY_X));g(Pc1InlineText.FONT,28);g(Pc1InlineText.Y,g(Pc1InlineText.Y)+g(Pc1MessageGeometry.BODY_PITCH));
        }
        body(layout,style,invocation);
    }
    private void position()throws IOException{g(Pc1InlineText.X,g(Pc1MessageGeometry.BODY_X));g(Pc1InlineText.Y,g(Pc1MessageGeometry.BODY_Y));g(Pc1InlineText.FONT,28);render.compositor().fullClip();}
    private void finish(Pc1TextLayout layout,String invocation)throws IOException{
        render.compositor().present();String pending=layout.freezeChecked().pendingOwner();
        if(pending!=null&&pending.startsWith(invocation))throw new IOException("PC1 redraw unfinished dictionary retains an unbound native stack pointer after418220 return");
    }
    /** Complete native entry, fenced across every layout/render/OS callback.
     *Failures retain already committed globals/pixels and never publish a
     *pretend successful DTO. Native callers own any later412320 activation. */
    public void text(int selection)throws IOException{render.nativeCall(()->redraw(selection));}
    private void redraw(int selection)throws IOException{
        int count=g(COUNT);g(Pc1LayoutMemory.GLYPH_COUNT,0);int selected=selection<0?count-1:selection;int selectedStyle,otherStyle,nameStyle;
        if(selected==count-1){
            selectedStyle=g(0x43d444);nameStyle=g(0x43d460);otherStyle=g(0x43d448);int speed=g(0x454cdc),threshold=0;
            if(speed==2)threshold=strlenAddress(0x9d3af8);else if(speed==3)threshold=Integer.divideUnsigned(strlenAddress(0x9d3af8)*2,3);
            g(0x9da4a4,threshold);g(Pc1LayoutMemory.DICT_COUNT,0);
        }else{selectedStyle=g(0x43d44c);nameStyle=g(0x43d464);otherStyle=g(0x43d450);g(Pc1LayoutMemory.DICT_COUNT,-1);}
        if(g(0x454cec)==0){selectedStyle&=0xffbf;otherStyle&=0xffbf;nameStyle&=0xffbf;}
        int firstWord=log(selected);runtime.textBuffers().beginRedraw();String invocation=STACK+runtime.nextTextSourceEpoch()+"/";Pc1TextLayout layout=runtime.nativeTextLayout();
        int raw=g(Pc1CheckpointMemory.TABLE+(firstWord&0x7fff)*4);
        if(code(raw)==4){
            graphics.restoreTextControls(0);render.messageWindow();speaker(layout,g(0x43d460),invocation);
            if(selected==0||g(0xbb8454)==2){finish(layout,invocation);return;}selected--;raw=checkpoint(selected);
        }
        if(g(PARAGRAPH)==0){scan(raw);render.messageWindow();speaker(layout,nameStyle,invocation);position();body(layout,selectedStyle,invocation);}
        else{
            int start=selected;while(start>0&&(log(start)&0x8000)==0)start--;
            render.messageWindow();position();
            for(int n=start;n<selected;n++)paragraphBody(layout,checkpoint(n),otherStyle,invocation);
            if(selection!=-2)paragraphBody(layout,checkpoint(selected),selectedStyle,invocation);
            if(selection>=0){int n=selected+1;while(n<g(COUNT)){
                if((log(n)&0x8000)!=0)break;int next=checkpoint(n);if(code(next)==4)break;
                paragraphBody(layout,next,otherStyle,invocation);n++;
            }}
        }
        finish(layout,invocation);
    }
}
