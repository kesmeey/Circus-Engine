package org.circus.core;

import java.io.IOException;
import java.util.*;

/** DD ff119f... 430606/417E20/4331BE: template20, caption selector and checkbox19. */
public final class DdModalNotice {
    public static final int TEMPLATE=20,CHECKBOX=19;
    private DdModalNotice(){}
    public static boolean actionable(Dc3Ui.Button button){return !button.action.isEmpty()&&button.action.charAt(0)!='-';}
    /** A null result is native417E20 returning0, including named-buttons-only templates. */
    static Dc3Ui.Panel build(Dc3Ui.Panel source,int caption,int x,int y)throws IOException {
        Dc3Ui.Panel result=Dc3Ui.copy(source);result.quads.clear();result.texts.clear();result.hidden=false;result.flags=0;result.focus=false;result.x=result.y=0;result.nativeModalEntries=true;result.nativeOriginX=x;result.nativeOriginY=y;
        boolean backdrop=false,visible=false;int index=0,selected=caption==-1?CHECKBOX:caption;
        for(Iterator<Dc3Ui.Button> it=result.buttons.iterator();it.hasNext();){
            Dc3Ui.Button button=it.next();if(!button.action.isEmpty()){if(!source.nativeModalEntries)for(Iterator<Dc3Ui.Sprite> sprites=button.sprites.iterator();sprites.hasNext();){Dc3Ui.Sprite sprite=sprites.next();if(sprite.state==5){sprites.remove();continue;}sprite.state=nativeSpriteState(sprite.state);}continue;}
            for(Dc3Ui.Sprite sprite:button.sprites){
                if(sprite.state==99&&!backdrop){result.quads.add(sprite.quad);backdrop=true;visible=true;}
                if(sprite.state==2&&index++==selected){result.quads.add(sprite.quad);visible=true;}
            }
            it.remove();
        }
        if(!visible)return null;
        for(Dc3Ui.Button button:result.buttons){button.left+=x;button.right+=x;button.top+=y;button.bottom+=y;}
        return result;
    }
    /** The sole legacy-source -> native417A10 state conversion boundary. */
    static int nativeSpriteState(int state){return state==6?100:state==8?101:state==10?102:state==11?3:state;}
    static boolean boundFill(Dc3Ui.Button button){for(Dc3Ui.Sprite sprite:button.sprites)if(sprite.state>104)return true;return false;}
    static void update(Dc3Ui.Panel panel,int hovered,int pressed,Variables variables)throws IOException{
        int value=variables==null?0:variables.read(0,0);for(int n=0;n<panel.buttons.size();n++){int state=n==pressed?2:n==hovered?1:99;for(Dc3Ui.Sprite sprite:panel.buttons.get(n).sprites)if(sprite.state==100||sprite.state==101||sprite.state>104)sprite.quad=DdBoundControl.resolveQuad(DdBoundControl.Binding.zero(),sprite,value,state);}
    }
    private static Dc3Scene.Quad translated(Dc3Scene.Quad quad,Dc3Ui.Panel panel){Dc3Scene.Quad result=quad.copy();result.x+=panel.nativeOriginX;result.y+=panel.nativeOriginY;return result;}
    /** Native strict rectangle edges; the shared UI skips empty/'-' action buttons. */
    static int hit(Dc3Ui.Panel panel,int x,int y){
        for(int i=0;i<panel.buttons.size();i++){Dc3Ui.Button b=panel.buttons.get(i);if(actionable(b)&&x>b.left&&x<b.right&&y>b.top&&y<b.bottom)return i;}
        return -1;
    }
    /** 415C60 traverses the button sequence for direction inputs3/5 and4/6. */
    static int focus(Dc3Ui.Panel panel,int current,int direction){
        int count=panel.buttons.size();if(count==0)return -1;
        for(int n=0;n<count;n++){current+=direction;if(current<0)current=count-1;if(current>=count)current=0;if(actionable(panel.buttons.get(current)))return current;}
        return -1;
    }
    /** 415C60 sprite visibility: checkbox state2 comes from93:19, not press state. */
    static void visible(Dc3Ui.Panel panel,int hovered,int pressed,boolean checked,List<Dc3Scene.Quad> out){
        if(panel.hidden)return;for(Dc3Scene.Quad quad:panel.quads)out.add(translated(quad,panel));
        for(int i=0;i<panel.buttons.size();i++){
            Dc3Ui.Button button=panel.buttons.get(i);int state=i==pressed?2:i==hovered?1:99;
            for(Dc3Ui.Sprite sprite:button.sprites){
                boolean show=sprite.state==state;
                if(button.checkbox){if(sprite.state==2)show=checked;if(sprite.state==1)show=state!=99;}
                // Native auxiliary states100/101/102/104 have explicit idle/active gates.
                if(sprite.state>99){show=true;if(sprite.state==100)show=state==99;if(sprite.state==101||sprite.state==102||sprite.state==104)show=state!=99;}
                if(sprite.state==3)show=state==2;
                if(show)out.add(translated(sprite.quad,panel));
            }
        }
    }
}
