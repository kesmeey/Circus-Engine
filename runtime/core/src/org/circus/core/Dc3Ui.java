package org.circus.core;

import java.io.*;
import java.util.*;

/** Original MES UI objects. No game labels, routes or screen layouts live here. */
public final class Dc3Ui {
    // Native DD GRB palette at 4AA108; START customizes entries after these defaults.
    private static final int[] DEFAULT_PALETTE={0xff000000,0xff0000ff,0xffff0000,0xffff00ff,0xff00ff00,0xff00ffff,0xffffff00,0xffffffff,0xff854e28,0xffccccff,0xff774400,0xffff7f7f,0xffcccccc};
    private void defaultPalette(boolean restoring){
        if(restoring){for(int n=0;n<DEFAULT_PALETTE.length;n++)if(palette[n]!=0)return;}
        System.arraycopy(DEFAULT_PALETTE,0,palette,0,DEFAULT_PALETTE.length);
    }
    public static final class Sprite {
        public Dc3Scene.Quad quad=new Dc3Scene.Quad();public int state;
        void save(DataOutput o)throws IOException{quad.save(o);o.writeInt(state);}
        void load(DataInput i)throws IOException{quad.load(i);state=i.readInt();}
    }
    public static final class Button {
        public int left,top,right,bottom,hoverSound=255,clickSound=255;public boolean checkbox;
        public String action="";private byte[] actionBytes=new byte[0];public final List<Sprite> sprites=new ArrayList<>();
        public byte[] actionBytes(){return actionBytes.clone();}
        public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
        void save(DataOutput o)throws IOException{o.writeInt(left);o.writeInt(top);o.writeInt(right);o.writeInt(bottom);o.writeInt(hoverSound);o.writeInt(clickSound);o.writeInt(actionBytes.length);o.write(actionBytes);o.writeInt(sprites.size());for(Sprite s:sprites)s.save(o);}
        void load(DataInput i)throws IOException{left=i.readInt();top=i.readInt();right=i.readInt();bottom=i.readInt();hoverSound=i.readInt();clickSound=i.readInt();actionBytes=new byte[count(i,255)];i.readFully(actionBytes);action=TextCodec.cp932(actionBytes);int n=count(i,1024);for(int j=0;j<n;j++){Sprite s=new Sprite();s.load(i);sprites.add(s);}}
    }
    public static final class Text {
        public int[] args;public String text;public boolean button;
        void save(DataOutput o)throws IOException{for(int n:args)o.writeInt(n);o.writeUTF(text);o.writeBoolean(button);}
        void load(DataInput i)throws IOException{args=new int[4];for(int n=0;n<4;n++)args[n]=i.readInt();text=i.readUTF();button=i.readBoolean();}
    }
    public static final class Panel {
        public int x,y,flags;public boolean focus,hidden;
        /** DD source panels retain legacy record markers; only417E20 output uses native sprite states. */
        public boolean nativeModalEntries;public int nativeOriginX,nativeOriginY;
        public final List<Button> buttons=new ArrayList<>();public final List<Dc3Scene.Quad> quads=new ArrayList<>();public final List<Text> texts=new ArrayList<>();
        public int[] textStyle={0,0,0,0};
        void save(DataOutput o)throws IOException{o.writeInt(x);o.writeInt(y);o.writeInt(flags);o.writeBoolean(focus);o.writeBoolean(hidden);for(int a:textStyle)o.writeInt(a);o.writeInt(buttons.size());for(Button b:buttons)b.save(o);o.writeInt(quads.size());for(Dc3Scene.Quad q:quads)q.save(o);o.writeInt(texts.size());for(Text t:texts)t.save(o);}
        void load(DataInput i)throws IOException{x=i.readInt();y=i.readInt();flags=i.readInt();focus=i.readBoolean();hidden=i.readBoolean();for(int n=0;n<4;n++)textStyle[n]=i.readInt();int n=count(i,1024);for(int j=0;j<n;j++){Button b=new Button();b.load(i);buttons.add(b);}n=count(i,1024);for(int j=0;j<n;j++){Dc3Scene.Quad q=new Dc3Scene.Quad();q.load(i);quads.add(q);}n=count(i,1024);for(int j=0;j<n;j++){Text t=new Text();t.load(i);texts.add(t);}}
    }
    public Panel current=new Panel();public final Panel[] templates=new Panel[22];
    public final List<Button> grid=new ArrayList<>();public final int[] gridBounds=new int[8];public boolean gridWaiting,saveGrid;public int gridHoverSound=255,gridClickSound=255;
    public Panel underlay;public int confirmX,confirmY,confirmKind,activeConfirmation=-1;public boolean confirmChecked;
    private boolean modalNotice,pendingCheckbox,buttonOpen,exactCheckboxFlags,framePanelVisible;
    private Panel retainedNotice;private boolean retainedChecked;private int retainedHovered=-1,retainedPressed=-1;
    private Variables variables;private String boundFailure;
    public void attach(Variables variables){this.variables=variables;}
    public boolean noticeActive(){return modalNotice;}
    public Panel noticeOverlay(){return retainedNotice;}
    public boolean framePanelVisible(){return framePanelVisible;}
    public void exactCheckboxFlags(boolean exact){exactCheckboxFlags=exact;}
    public boolean exactCheckboxFlags(){return exactCheckboxFlags;}
    public final int[] palette=new int[64];public final String[] actions=new String[17];
    public final List<String> voiceNames=new ArrayList<>();
    public boolean waiting;public String version="";public int hovered=-1,pressed=-1,pointerX,pointerY,messageX,messageY,inputMode;
    public Dc3Ui(){Arrays.fill(actions,"");defaultPalette(false);}
    public void clear(){boundFailure=null;grid.clear();gridWaiting=false;current=new Panel();underlay=null;retainedNotice=null;activeConfirmation=-1;modalNotice=pendingCheckbox=buttonOpen=framePanelVisible=false;waiting=false;hovered=pressed=-1;Arrays.fill(actions,"");}
    public Button last()throws IOException{if(current.buttons.isEmpty())throw new IOException("UI property without a button");return current.buttons.get(current.buttons.size()-1);}
    public void button(int[] a,String action)throws IOException{button(a,action.getBytes(Binary.SJIS));}
    public void button(int[] a,byte[] action)throws IOException{if(current.buttons.size()>=1024||action.length>255)throw new IOException("UI button limit");Button b=new Button();b.left=a[0];b.top=a[1];b.right=a[2];b.bottom=a[3];b.actionBytes=action.clone();b.action=TextCodec.cp932(action);b.checkbox=pendingCheckbox;pendingCheckbox=false;buttonOpen=true;current.buttons.add(b);}
    public void buttonCheckbox(){if(!buttonOpen)pendingCheckbox=true;else current.buttons.get(current.buttons.size()-1).checkbox=true;}
    public void finalizeButtons(){buttonOpen=false;pendingCheckbox=false;}
    /** Native 432948 / 43298C selector tables, not a per-game layout table. */
    public void store(int selector,boolean alternate)throws IOException{
        int[] mapping=alternate?new int[]{-1,0,1,2,3,4,5,6,-1,-1,-1,-1,-1,17,18,7,8}:new int[]{-1,9,12,11,-1,-1,10,13,-1,-1,-1,-1,-1,17,18,19,20,21};
        if(alternate&&(selector==254||selector==255)){current.hidden=selector==254;return;}
        if(selector<0||selector>=mapping.length||mapping[selector]<0)return;
        finalizeButtons();templates[mapping[selector]]=current;current=new Panel();hovered=pressed=-1;
    }
    public int hit(float x,float y){if(!waiting||current.hidden)return -1;for(int n=current.buttons.size()-1;n>=0;n--)if(!current.buttons.get(n).action.isEmpty()&&current.buttons.get(n).contains(x,y))return n;return -1;}
    public void checkBounds()throws IOException{if(boundFailure!=null)throw new IOException(boundFailure);}
    public void nativeFailure(IOException failure){boundFailure=failure.getMessage();}
    /** Input/frame update owns persistent source x/crop; draw/capture only resolve translation/visibility. */
    public void updateBoundSprites(){try{if(current.nativeModalEntries&&waiting)DdModalNotice.update(current,hovered,pressed,variables);}catch(IOException failure){boundFailure=failure.getMessage();}}
    public List<Dc3Scene.Quad> visible(){List<Dc3Scene.Quad> out=visibleUnderlay();out.addAll(visibleCurrent());out.addAll(visibleNoticeOverlay());return out;}
    public List<Dc3Scene.Quad> visibleUnderlay(){List<Dc3Scene.Quad> out=new ArrayList<>();if((waiting||framePanelVisible)&&underlay!=null)visible(underlay,-1,-1,false,false,out);return out;}
    public List<Dc3Scene.Quad> visibleCurrent(){List<Dc3Scene.Quad> out=new ArrayList<>();if(!waiting&&!framePanelVisible)return out;if(modalNotice||exactCheckboxFlags&&activeConfirmation>=0)DdModalNotice.visible(current,hovered,pressed,confirmChecked,out);else visible(current,hovered,pressed,activeConfirmation>=0,confirmChecked,out);return out;}
    public List<Dc3Scene.Quad> visibleNoticeOverlay(){List<Dc3Scene.Quad> out=new ArrayList<>();if(retainedNotice!=null)DdModalNotice.visible(retainedNotice,retainedHovered,retainedPressed,retainedChecked,out);return out;}
    public void checkboxChanged(int index,int value){if(activeConfirmation==index)confirmChecked=value!=0;}
    private void visible(Panel panel,int hovered,int pressed,boolean modal,boolean checked,List<Dc3Scene.Quad> out){if(panel.hidden)return;out.addAll(panel.quads);for(int n=0;n<panel.buttons.size();n++){Button b=panel.buttons.get(n);int state=n==pressed?2:n==hovered?1:99;boolean checkbox=modal&&(exactCheckboxFlags?b.checkbox:b.action.equals(":c"));if(checkbox&&state==2)state=1;boolean found=false;for(Sprite s:b.sprites)if(s.state==state)found=true;for(Sprite s:b.sprites)if(s.state==(found?state:99)||checkbox&&checked&&s.state==2)out.add(s.quad);}}
    public Set<String> resources(){Set<String> out=new HashSet<>();if(waiting)for(Panel p:new Panel[]{underlay,current})if(p!=null&&!p.hidden){for(Dc3Scene.Quad q:p.quads)out.add(q.resource);for(Button b:p.buttons)for(Sprite s:b.sprites)if(s.state!=5)out.add(s.quad.resource);}return out;}
    static Panel copy(Panel panel)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();panel.save(new DataOutputStream(bytes));Panel result=new Panel();result.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));result.nativeModalEntries=panel.nativeModalEntries;result.nativeOriginX=panel.nativeOriginX;result.nativeOriginY=panel.nativeOriginY;for(int i=0;i<panel.buttons.size();i++)result.buttons.get(i).checkbox=panel.buttons.get(i).checkbox;return result;}
    /** Native 43059A / 417E20: template 12, first backdrop and indexed caption from anonymous button. */
    public void confirmation()throws IOException{
        if(underlay!=null||templates[12]==null)throw new IOException("Confirmation template unavailable");
        if(exactCheckboxFlags){Panel built=DdModalNotice.build(templates[12],confirmKind,confirmX,confirmY);if(built==null)return;underlay=current;current=built;activeConfirmation=confirmKind;modalNotice=false;confirmX=confirmY=0;hovered=pressed=-1;waiting=true;return;}
        Panel p=copy(templates[12]);boolean backdrop=false;int caption=0;
        for(Iterator<Button> it=p.buttons.iterator();it.hasNext();){Button b=it.next();if(!b.action.isEmpty())continue;
            for(Sprite s:b.sprites){if(s.state==99&&!backdrop){p.quads.add(s.quad);backdrop=true;}if(s.state==2&&caption++==confirmKind)p.quads.add(s.quad);}it.remove();}
        if(!backdrop||confirmKind<0||confirmKind>=caption)throw new IOException("Confirmation caption unavailable: "+confirmKind);
        for(Dc3Scene.Quad q:p.quads){q.x+=confirmX;q.y+=confirmY;}
        for(Button b:p.buttons){b.left+=confirmX;b.right+=confirmX;b.top+=confirmY;b.bottom+=confirmY;for(Sprite s:b.sprites){s.quad.x+=confirmX;s.quad.y+=confirmY;}}
        underlay=current;current=p;activeConfirmation=confirmKind;modalNotice=false;confirmX=confirmY=0;hovered=pressed=-1;waiting=true;
    }
    public boolean modalNotice()throws IOException{
        if(underlay!=null)throw new IOException("Nested DD modal notice");
        if(templates[DdModalNotice.TEMPLATE]==null)return false;
        Panel panel=DdModalNotice.build(templates[DdModalNotice.TEMPLATE],confirmKind,confirmX,confirmY);if(panel==null)return false;
        retainedNotice=null;underlay=current;current=panel;activeConfirmation=DdModalNotice.CHECKBOX;modalNotice=true;confirmX=confirmY=0;pressed=-1;
        hovered=panel.buttons.isEmpty()?-1:0;waiting=true;return true;
    }
    public int noticeHit(int x,int y){return modalNotice?DdModalNotice.hit(current,x,y):-1;}
    public int noticeFocus(int direction){if(!modalNotice)return -1;hovered=DdModalNotice.focus(current,hovered,direction);pressed=-1;return hovered;}
    /** 432C60 common-tail jumps clear wait61C but do not clear popup34C0. */
    public void noticeFrameJump(){if(!modalNotice)return;retainedNotice=current;retainedChecked=confirmChecked;retainedHovered=hovered;retainedPressed=pressed;current=underlay;underlay=null;activeConfirmation=-1;modalNotice=false;framePanelVisible=true;waiting=false;hovered=pressed=-1;}
    public void retainCheckboxVisual(boolean checked){if(retainedNotice!=null)retainedChecked=checked;}
    public void closeConfirmation(){if(modalNotice)framePanelVisible=true;if(underlay!=null){current=underlay;underlay=null;activeConfirmation=-1;}modalNotice=false;hovered=pressed=-1;}
    /** DD host41 appendix: Panel/capture wire remains unchanged for other profiles. */
    public void saveDdModal(DataOutput out)throws IOException{
        checkBounds();
        out.writeInt(0x44444d32);out.writeInt(modalNotice?86:0);out.writeBoolean(pendingCheckbox);out.writeBoolean(buttonOpen);out.writeBoolean(framePanelVisible);
        out.writeBoolean(retainedNotice!=null);if(retainedNotice!=null){retainedNotice.save(out);out.writeBoolean(retainedChecked);out.writeInt(retainedHovered);out.writeInt(retainedPressed);}
        saveCheckboxes(out,current);saveCheckboxes(out,underlay);for(Panel panel:templates)saveCheckboxes(out,panel);saveCheckboxes(out,retainedNotice);
    }
    private static void saveCheckboxes(DataOutput out,Panel panel)throws IOException{out.writeInt(panel==null?-1:panel.buttons.size());if(panel!=null){out.writeBoolean(panel.nativeModalEntries);out.writeInt(panel.nativeOriginX);out.writeInt(panel.nativeOriginY);for(Button b:panel.buttons)out.writeBoolean(b.checkbox);}}
    public void loadDdModal(DataInput in)throws IOException{
        if(in.readInt()!=0x44444d32)throw new IOException("DD modal appendix version");int kind=in.readInt();boolean pending=in.readBoolean(),open=in.readBoolean(),visible=in.readBoolean();
        if(kind!=0&&kind!=86||kind==86&&(!waiting||underlay==null||activeConfirmation!=19))throw new IOException("DD modal appendix state");
        retainedNotice=null;if(in.readBoolean()){retainedNotice=new Panel();retainedNotice.load(in);retainedChecked=in.readBoolean();retainedHovered=in.readInt();retainedPressed=in.readInt();if(kind==86||retainedHovered< -1||retainedHovered>=retainedNotice.buttons.size()||retainedPressed< -1||retainedPressed>=retainedNotice.buttons.size())throw new IOException("DD retained modal focus");}
        if(open&&(underlay==null?current:underlay).buttons.isEmpty()||open&&pending)throw new IOException("DD checkbox construction state");
        List<boolean[]> flags=new ArrayList<>();Panel[] panels=new Panel[25];panels[0]=current;panels[1]=underlay;System.arraycopy(templates,0,panels,2,22);panels[24]=retainedNotice;
        for(Panel panel:panels){int n=in.readInt();if(n!=(panel==null?-1:panel.buttons.size()))throw new IOException("DD modal button flags");if(panel!=null){panel.nativeModalEntries=in.readBoolean();panel.nativeOriginX=in.readInt();panel.nativeOriginY=in.readInt();if(panel.nativeOriginX<Short.MIN_VALUE||panel.nativeOriginX>Short.MAX_VALUE||panel.nativeOriginY<Short.MIN_VALUE||panel.nativeOriginY>Short.MAX_VALUE)throw new IOException("DD modal native origin");}boolean[] values=new boolean[Math.max(0,n)];for(int i=0;i<values.length;i++)values[i]=in.readBoolean();flags.add(values);}
        for(int n=0;n<panels.length;n++)if(panels[n]!=null)for(int i=0;i<panels[n].buttons.size();i++)panels[n].buttons.get(i).checkbox=flags.get(n)[i];
        if(kind==86&&!current.nativeModalEntries||retainedNotice!=null&&!retainedNotice.nativeModalEntries)throw new IOException("DD modal sprite ownership");
        modalNotice=kind==86;pendingCheckbox=pending;buttonOpen=open;framePanelVisible=visible;exactCheckboxFlags=true;
    }
    public void save(DataOutput o)throws IOException{o.writeBoolean(gridWaiting);o.writeBoolean(saveGrid);o.writeInt(gridHoverSound);o.writeInt(gridClickSound);for(int v:gridBounds)o.writeInt(v);o.writeInt(grid.size());for(Button b:grid)b.save(o);o.writeBoolean(waiting);o.writeUTF(version);o.writeInt(hovered);o.writeInt(pressed);o.writeInt(pointerX);o.writeInt(pointerY);o.writeInt(messageX);o.writeInt(messageY);o.writeInt(inputMode);o.writeInt(confirmX);o.writeInt(confirmY);o.writeInt(confirmKind);o.writeInt(activeConfirmation);o.writeBoolean(confirmChecked);o.writeBoolean(underlay!=null);if(underlay!=null)underlay.save(o);current.save(o);for(Panel p:templates){o.writeBoolean(p!=null);if(p!=null)p.save(o);}for(int p:palette)o.writeInt(p);for(String a:actions)o.writeUTF(a);o.writeInt(voiceNames.size());for(String n:voiceNames)o.writeUTF(n);}
    public void load(DataInput i)throws IOException{boundFailure=null;retainedNotice=null;modalNotice=pendingCheckbox=buttonOpen=framePanelVisible=false;exactCheckboxFlags=false;gridWaiting=i.readBoolean();saveGrid=i.readBoolean();gridHoverSound=i.readInt();gridClickSound=i.readInt();for(int n=0;n<8;n++)gridBounds[n]=i.readInt();grid.clear();for(int n=count(i,50);n>0;n--){Button b=new Button();b.load(i);grid.add(b);}waiting=i.readBoolean();version=i.readUTF();hovered=i.readInt();pressed=i.readInt();pointerX=i.readInt();pointerY=i.readInt();messageX=i.readInt();messageY=i.readInt();inputMode=i.readInt();confirmX=i.readInt();confirmY=i.readInt();confirmKind=i.readInt();activeConfirmation=i.readInt();confirmChecked=i.readBoolean();underlay=null;if(i.readBoolean()){underlay=new Panel();underlay.load(i);}if((underlay!=null)!=(activeConfirmation>=0)||activeConfirmation>=20)throw new IOException("Invalid confirmation state");current=new Panel();current.load(i);for(int n=0;n<templates.length;n++){templates[n]=null;if(i.readBoolean()){templates[n]=new Panel();templates[n].load(i);}}for(int n=0;n<palette.length;n++)palette[n]=i.readInt();for(int n=0;n<actions.length;n++)actions[n]=i.readUTF();voiceNames.clear();int n=count(i,256);for(int j=0;j<n;j++)voiceNames.add(i.readUTF());if(hovered< -1||hovered>=current.buttons.size()||pressed< -1||pressed>=current.buttons.size())throw new IOException("Invalid UI focus");defaultPalette(true);}
    private static int count(DataInput i,int max)throws IOException{int n=i.readInt();if(n<0||n>max)throw new IOException("UI object limit");return n;}
}
