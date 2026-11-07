package org.circus.core;

import java.io.IOException;

/**412260/412320 manipulate the same raw story/dictionary records consumed
 *by40AA50. Neither copies portable regions or applies a ten-entry clamp. */
public final class Pc1TextControls {
    public interface Services {void buttons(int mode)throws IOException;}
    private final Pc1Compositor.State state;
    private final Services services;
    public Pc1TextControls(Pc1Compositor.State state,Services services)throws IOException{if(state==null)throw new IOException("PC1 text-controls canonical state missing");this.state=state;this.services=services;}
    private int g(int at)throws IOException{return state.readInt(at);}
    private void g(int at,int value)throws IOException{state.writeInt(at,value);}
    private void buttons()throws IOException{if(services==null)throw new IOException("PC1 text-controls requires actual40AA50 consumer");services.buttons(1);}
    public void restore(int draw)throws IOException{
        int wait=g(0x9d8a20);if(wait!=7&&wait!=9)return;
        g(Pc1NativeUi.SELECTED_BUTTON,-1);g(Pc1NativeUi.BUTTON_POINTER,Pc1NativeUi.STORY_BUTTONS);
        int initial=g(wait==7?Pc1StoryControls.PREFIX_COUNT:Pc1StoryControls.FULL_COUNT);g(Pc1NativeUi.BUTTON_COUNT,initial);
        if(initial>0){int index=0,offset=0;do{int pointer=g(Pc1NativeUi.BUTTON_POINTER);index++;g(pointer+offset+8,0);offset+=560;}while(index<g(Pc1NativeUi.BUTTON_COUNT));}
        if(draw!=0)buttons();
    }
    public void activate()throws IOException{
        if(g(0x9d8a20)==7&&g(Pc1LayoutMemory.DICT_COUNT)>0){
            int index=0,source=Pc1LayoutMemory.RECORDS;
            do{int destination=g(Pc1NativeUi.BUTTON_POINTER)+g(Pc1NativeUi.BUTTON_COUNT)*560;for(int n=0;n<140;n++)g(destination+n*4,g(source+n*4));g(Pc1NativeUi.BUTTON_COUNT,g(Pc1NativeUi.BUTTON_COUNT)+1);index++;source+=560;}while(index<g(Pc1LayoutMemory.DICT_COUNT));
            buttons();
        }
        g(Pc1LayoutMemory.DICT_COUNT,-1);
    }
}
