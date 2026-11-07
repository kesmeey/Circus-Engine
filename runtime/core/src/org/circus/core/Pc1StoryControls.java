package org.circus.core;

import java.io.IOException;

/** Current416B40/416B80 story-button owner switch. This is distinct from
 *40A2A0 clipped repaint and from40AA50's actual render/input consumer. */
public final class Pc1StoryControls {
    public static final int EXTRA_VISIBLE=0xbb8458,PREFIX_COUNT=0xbb8450,FULL_COUNT=0x9b8e1c,STORY=0x9cfc70;
    public interface Services {
        void buttons(int mode)throws IOException; //40AA50,required actual UI/pixels/input
        void restoreBody(int left,int top,int right,int bottom,int mode)throws IOException; //415D50
    }
    private final Pc1Compositor.State state;
    private final Services services;
    public Pc1StoryControls(Pc1Compositor.State state,Services services)throws IOException {if(state==null)throw new IOException("PC1 story-controls canonical raw state missing");this.state=state;this.services=services;}
    private int g(int at)throws IOException{return state.readInt(at);}
    private void g(int at,int value)throws IOException{state.writeInt(at,value);}
    private Services io(int entry)throws IOException{if(services==null)throw new IOException("PC1 story-controls requires native consumer"+Integer.toHexString(entry));return services;}
    /**416B40 clears the flag BEFORE the restore callback; bounds come from
     *the raw third button-template9B9F20+0C,not current pointer or DTO. */
    public void restoreExtra()throws IOException {
        if(g(EXTRA_VISIBLE)==0)return;int bottom=g(0x9b9f38),right=g(0x9b9f34),top=g(0x9b9f30),left=g(0x9b9f2c);g(EXTRA_VISIBLE,0);io(0x415d50).restoreBody(left,top,right,bottom,0);
    }
    /**416B80 keeps signed counts/selection and wrapped historyCount−1.
     *The non7/9 loop rereads raw pointer/count,without an invented18 cap. */
    public void draw()throws IOException {
        int wait=g(0x9d8a20),prefix=g(PREFIX_COUNT);g(Pc1NativeUi.BUTTON_POINTER,STORY);g(Pc1NativeUi.BUTTON_COUNT,prefix);
        if(wait==7){io(0x40aa50).buttons(1);return;}
        if(wait==9){int selection=g(0x43d410);if(selection<0||selection>=g(0x9d8a1c)-1){int full=g(FULL_COUNT);g(EXTRA_VISIBLE,1);g(Pc1NativeUi.BUTTON_COUNT,full);}else if(g(EXTRA_VISIBLE)!=0)restoreExtra();io(0x40aa50).buttons(1);return;}
        if(prefix>0){int index=0,offset=0;do{int pointer=g(Pc1NativeUi.BUTTON_POINTER);index++;g(pointer+offset+8,0);offset+=560;}while(index<g(Pc1NativeUi.BUTTON_COUNT));}
        io(0x40aa50).buttons(0);
    }
}
