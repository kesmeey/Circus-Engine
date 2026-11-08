package org.circus.core;

import java.io.*;
import java.util.*;

/** Hash-reviewed DCDX7769 consumer. Native-only modal services stay explicit failures. */
public final class DcdxHost extends PcHost {
    public final int[][] liveActors=new int[5][3];
    public final DcdxActorMotion dcdxActorMotion=new DcdxActorMotion();
    public final DcdxMenu dcdxMenu=new DcdxMenu();
    public final DcdxBanner dcdxBanner=new DcdxBanner();
    public final int[] fifthStage={0,0,100,0,0,0};
    public String fifthCharacter="";
    private String savedFifthCharacter="";
    private final int[] savedFifthStage={0,0,100};
    private final int[][] systemStage=new int[4][3];
    private byte[] systemAction,modalAction;
    private boolean modalTimerInitialized;
    private int modalClockMs,modalClockFrame;
    private long modalIdleMillis;
    public DcdxHost(CircusResources data,TextCodec codec){this(data,codec,(int)(System.nanoTime()/1000000L));}
    public DcdxHost(CircusResources data,TextCodec codec,int tick){
        super(data,codec,tick);if(!data.profile.id.equals("dcdx-7769")||!dreamXmas||!second||pc1!=null)throw new IllegalArgumentException("DCDX profile required");
        // Native2136-byte template446018 stores these SJIS names at+4C/+60.
        familyName="朝倉";givenName="純一";for(int[] p:liveActors)p[2]=100;scene.bindActorProjection(this::projectActors);
    }
    private void projectActors(Dc2Scene.Frame frame){for(int n=0;n<4;n++){System.arraycopy(liveActors[n],0,frame.actors[n],0,3);try{frame.actors[n][5]=variables==null?0:variables.read(3,51+n);}catch(IOException failure){throw new IllegalStateException("DCDX frozen actor alpha",failure);}}}
    private int[] stage(int n){return n==4?fifthStage:scene.character(n);}
    private void stage(int n,int[] p){if(n==4)System.arraycopy(p,0,fifthStage,0,6);else scene.character(n,p);}
    @Override public void enterSystem()throws IOException{for(int n=0;n<4;n++)for(int k=0;k<3;k++)systemStage[n][k]=scene.character(n,k);savedFifthCharacter=fifthCharacter;System.arraycopy(fifthStage,0,savedFifthStage,0,3);super.enterSystem();}
    /**4067C0 has ten rows: quick slots0/1 are fixed; eight new slots/page. */
    @Override public int gridSlot(int row)throws IOException{return row<2?row:read(99,0)*8+row;}
    public boolean menuVisible(){return dcdxMenu.visible&&dcdxMenu.sourceSerial==scene.transitionSerial;}
    public boolean bannerVisible(){return dcdxBanner.visible&&dcdxBanner.sourceSerial==scene.transitionSerial;}
    private static boolean captureReference(String[] slots){for(String name:slots)if("@ui-capture".equals(name))return true;return false;}
    /**A later native presentation replaces the retained physical owner. Keep
     *immutable captures that still back an actual stage/front/transition/caller. */
    private void releaseReplacedSurfaces(){
        if(!dcdxBanner.active&&dcdxBanner.visible&&!bannerVisible())dcdxBanner.clear();
        if(!dcdxMenu.active&&dcdxMenu.visible&&!menuVisible())dcdxMenu.clear();
        if(uiCapture!=null&&!captureReference(scene.slots)&&!captureReference(scene.shown.slots)&&!(scene.transitionRemaining>0&&captureReference(scene.previous.slots))&&!(systemScene!=null&&captureReference(systemScene.slots)))uiCapture=null;
    }
    private static long modalDelta(long a,long b){a=Math.max(0,a);b=Math.max(0,b);return a>=1000||b>=1000?1000+(a%1000+b%1000)%1000:a+b;}
    private void initModalTimer(){if(!modalTimerInitialized){modalClockMs=modalClockFrame=0;modalIdleMillis=0;modalTimerInitialized=true;}}
    private UiCapture bannerBackground()throws IOException{if(scene.slots[1].equals("@ui-capture")){if(uiCapture==null)throw new IOException("DCDX banner captured background absent");return uiCapture;}if(!ui.quads.isEmpty())throw new IOException("DCDX banner background has unreviewed unbaked pending records");Crx.Image raw=resources.image(scene.slots[1]);if(raw.bpp!=24||raw.width!=1024||raw.height!=576)throw new IOException("DCDX banner native BG bitmap geometry/format");return UiCapture.pixels(1024,576,raw.argb(false));}
    private void startBanner(int mode)throws IOException{if(mode<0||mode>3)throw new IOException("DCDX banner mode "+mode);UiCapture old=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);UiCapture bg=bannerBackground();String atlas=scene.slots[10];if(atlas.isEmpty())throw new IOException("DCDX banner native selector6 atlas absent");initModalTimer();dcdxBanner.syncClock(modalClockMs,modalClockFrame);scene.publish(ui,0,0);dcdxMenu.clear();dcdxBanner.start(mode,old,bg,atlas,scene.transitionSerial);ui.waiting=false;remaining=0;presentationWaiting=textWaiting=false;nextTicket();}
    private void refreshMenu(){ui.buttons.clear();ui.buttons.addAll(dcdxMenu.controls());ui.hovered=-1;for(int n=0;n<ui.buttons.size();n++)if(ui.buttons.get(n).binding==dcdxMenu.hovered)ui.hovered=n;ui.pressed=-1;}
    private void menuSound(int sound)throws IOException{play(1,resources.named("sound",sound),false);}
    /**VK13/37..40 are the original wheel events, without translating to flags. */
    public boolean nativeMenuKey(int key)throws IOException{if(!dcdxMenu.active)return false;String action=dcdxMenu.key(key);if(action!=null){menuSound(200);dcdxMenu.release();modalAction=action.getBytes(Binary.SJIS);}refreshMenu();return true;}
    @Override public byte[] pollMenuAction(){if(modalAction!=null){byte[] action=modalAction;modalAction=null;ui.waiting=false;return action;}return super.pollMenuAction();}
    @Override public boolean internalMenuAction(long expected,int button)throws IOException{if(dcdxMenu.active&&expected==waitTicket()){if(button<0)return true;if(button>=ui.buttons.size())return false;int item=ui.buttons.get(button).binding;if(dcdxMenu.turning()||item!=dcdxMenu.front){dcdxMenu.click(item);refreshMenu();return true;}return false;}return super.internalMenuAction(expected,button);}
    @Override public byte[] menuAction(long expected,int button)throws IOException{if(dcdxMenu.active){if(expected!=waitTicket()||button<0||button>=ui.buttons.size()||dcdxMenu.turning())return null;String action=dcdxMenu.click(ui.buttons.get(button).binding);return action==null?null:action.getBytes(Binary.SJIS);}return super.menuAction(expected,button);}
    @Override public void acceptMenuAction(int button)throws IOException{boolean menu=dcdxMenu.active;super.acceptMenuAction(button);if(menu)dcdxMenu.release();}
    @Override public void hoverMenu(int button)throws IOException{if(!dcdxMenu.active){super.hoverMenu(button);return;}int item=button<0?-1:ui.buttons.get(button).binding,old=dcdxMenu.hovered;dcdxMenu.hover(item);if(old!=dcdxMenu.hovered&&item>=0)menuSound(201);refreshMenu();}
    @Override protected void captureUi()throws IOException{if(nativeFrameCapture==null&&!menuVisible()){super.captureUi();return;}UiCapture front=nativeFrameCapture!=null?nativeFrameCapture.capture():dcdxMenu.capture(resources);uiCapture=front;scene.slots[1]="@ui-capture";Arrays.fill(scene.slots,2,10,"");scene.backgroundX(0);scene.backgroundY(0);scene.publish(ui,0,0);}
    @Override public byte[] automaticMenuAction(){if(systemAction==null)return super.automaticMenuAction();byte[] action=systemAction;systemAction=null;return action;}
    @Override public boolean waiting(){return modalAction!=null||dcdxMenu.active||dcdxBanner.active||dcdxActorMotion.active||super.waiting();}
    @Override public void elapse(long ms){super.elapse(ms);releaseReplacedSurfaces();if(dcdxMenu.active){if(dcdxMenu.advance(modalDelta(modalIdleMillis,ms)))refreshMenu();modalIdleMillis=0;modalClockMs=dcdxMenu.clockMillis();modalClockFrame=dcdxMenu.clockFrame();}else if(dcdxBanner.active){dcdxBanner.advance(modalDelta(modalIdleMillis,ms));modalIdleMillis=0;modalClockMs=dcdxBanner.clockMillis();modalClockFrame=dcdxBanner.clockFrame();}else if(modalTimerInitialized)modalIdleMillis=modalDelta(modalIdleMillis,ms);if(!dcdxActorMotion.active)return;try{if(dcdxActorMotion.advance(ms,read(99,12),motionSkipPressed&&read(99,25)==0,variables.read(9,82),variables.read(9,83),variables.read(9,84),liveActors))scene.publish(ui,0,0);if(dcdxActorMotion.takeClearModifiers()){variables.write(9,82,0);variables.write(9,83,0);}}catch(IOException failure){throw new IllegalStateException("DCDX actor wait65",failure);}}
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException{if(dcdxMenu.active)return expected==waitTicket()&&signal==MesVm.HostSignal.ADVANCE&&nativeMenuKey(13);if(dcdxActorMotion.active||dcdxBanner.active)return false;return super.signal(expected,signal);}
    //41EDF0 is called by wait2E before its next loader, not by the body4C case.
    @Override protected void unlock(String table,String name,int bank)throws IOException{if(!table.equals("scenario_scripts"))super.unlock(table,name,bank);}
    @Override public boolean beforeTransfer(int mode,String name)throws IOException{if(mode!=2&&script!=null)super.unlock("scenario_scripts",script.name,98);boolean accepted=super.beforeTransfer(mode,name);if(accepted)dcdxActorMotion.cancel();return accepted;}
    @Override public boolean command(String op,int[] a,byte[] raw)throws IOException {
        //16h has no branch for18; both actual system-menu scripts use that native default.
        if(op.equals("pc.persist")&&a.length==2&&a[0]==18)return true;
        if(op.equals("dcdx.command")){
            if(a.length<2)throw new IOException("DCDX1C arguments");
            if(a[0]==6){saveGrid=a[1]==0;list.textStyle=2;return true;} //4067C0
            if(a[0]==7){list.textStyle=4;return true;} //4068C0
            if(a[0]==9){list.textStyle=3;return true;} //4068D0
            if(a[0]==12){startBanner(a[1]);return true;}
            if(a[0]==50){if(a[1]!=0)throw new IOException("DCDX native voice-selection wheel remains unimplemented");initModalTimer();scene.publish(ui,0,0);dcdxMenu.start(a[1],scene.slots[1],scene.slots[10],scene.slots[11],scene.slots[12],scene.transitionSerial);dcdxMenu.syncClock(modalClockMs,modalClockFrame);dcdxBanner.clear();ui.waiting=true;remaining=0;presentationWaiting=textWaiting=false;refreshMenu();nextTicket();return true;}
            if(a[0]==53){initModalTimer();scene.publish(ui,0,0);dcdxMenu.resume(scene.transitionSerial);dcdxMenu.syncClock(modalClockMs,modalClockFrame);dcdxBanner.clear();ui.waiting=true;remaining=0;presentationWaiting=textWaiting=false;refreshMenu();nextTicket();return true;}
            if(a[0]==52){int index=variables.read(3,40)+60;boolean on=read(100,index)!=0;write(100,index,on?0:1);systemAction=(on?"on":"off").getBytes(Binary.SJIS);return true;} //40B29E/41BAA0
            throw new IOException("DCDX1C native modal service not reviewed/implemented "+Arrays.toString(a));
        }
        if(op.equals("dcdx.external_program"))throw new IOException("DCDX optional external MINI executable is outside the native VN runtime");
        if(op.equals("dcdx.actor_position")){
            if(a.length!=4)throw new IOException("DCDX64 arguments");int n=a[2];
            if(n>=0&&n<5){int[] p=stage(n);p[0]=a[0];p[1]=a[1];p[2]=100;stage(n,p);return true;}
            throw new IOException("DCDX64 descriptor alias not yet reviewed "+n);
        }
        if(op.equals("dcdx.actor_transform")){
            if(a.length!=4)throw new IOException("DCDX65 arguments");int mode=a[3];
            if(mode>=16&&mode<20){int n=mode-16;int[] p=stage(n);p[2]=a[0];p[3]=a[1];p[4]=a[2];stage(n,p);return true;}
            if(mode==255){for(int n=0;n<2;n++)if(a[n]!=-1){int[] p=stage(n);p[2]=a[n];stage(n,p);}return true;}
            if(mode>=0&&mode<5){dcdxActorMotion.start(mode,a[0],a[1]);remaining=0;presentationWaiting=textWaiting=ui.waiting=false;nextTicket();return true;}
            throw new IOException("DCDX65 independent motion consumer not yet implemented "+Arrays.toString(a));
        }
        if(op.equals("dcdx.scroll")){
            if(a.length!=4)throw new IOException("DCDX62 arguments");
            if(a[2]==1){scene.backgroundX(a[0]);scene.backgroundY(a[1]);scene.scrollStep=0;return true;}
            if(a[2]==2){scene.portraitX=a[0];scene.portraitY=a[1];return true;}
            if(a[2]>=4&&a[2]<=7)return true; //Native62 default has no store in this range.
            throw new IOException("DCDX62 native scrolling consumer not yet implemented "+Arrays.toString(a));
        }
        if(op.equals("pc.character_overlay")&&a.length>0&&a[0]>=0&&a[0]<4&&TextCodec.cp932(raw).equals("--")){int n=a[0];scene.slots[6+n]=systemScene==null?"":systemScene.slots[6+n];scene.revision++;return true;} //4248E0 restores the resource only.
        if(op.equals("pc.character")&&a.length>0){
            int n=a[0];String name=TextCodec.cp932(raw);
            if(n==4){if(name.equals("--")){if(!savedFifthCharacter.isEmpty())throw new IOException("DCDX fifth nonempty saved owner has no renderer");fifthCharacter=savedFifthCharacter;System.arraycopy(savedFifthStage,0,fifthStage,0,3);System.arraycopy(fifthStage,0,liveActors[4],0,3);scene.revision++;return true;}if(name.isEmpty()||name.equals("-")){fifthCharacter="";scene.revision++;return true;}throw new IOException("DCDX nonempty fifth character rendering remains unimplemented");}
            if(n<0||n>=4)throw new IOException("DCDX nonordinary character owner not yet reviewed "+n);
            if(name.equals("--")){scene.slots[2+n]=systemScene==null?"":systemScene.slots[2+n];scene.characterPrefix(n,systemStage[n],3);}
            else super.command(op,a,raw);
            int[] p=scene.character(n);if(p[2]==0){p[2]=100;scene.character(n,p);}if(!name.isEmpty())System.arraycopy(p,0,liveActors[n],0,3);return true;
        }
        if(op.equals("pc.transition")&&a.length>0&&a[0]==15){scene.publish(ui,0,0);return true;}
        return super.command(op,a,raw);
    }
    @Override public void save(DataOutput out)throws IOException{releaseReplacedSurfaces();super.save(out);out.writeInt(0x44584831);for(int[] p:liveActors)for(int x:p)out.writeInt(x);for(int x:fifthStage)out.writeInt(x);for(int[] p:systemStage)for(int x:p)out.writeInt(x);out.writeUTF(fifthCharacter);out.writeUTF(savedFifthCharacter);for(int x:savedFifthStage)out.writeInt(x);dcdxActorMotion.save(out);out.writeBoolean(systemAction!=null);if(systemAction!=null)out.writeUTF(new String(systemAction,Binary.SJIS));dcdxMenu.save(out);out.writeBoolean(modalAction!=null);if(modalAction!=null)out.writeUTF(new String(modalAction,Binary.SJIS));dcdxBanner.save(out);out.writeBoolean(modalTimerInitialized);out.writeInt(modalClockMs);out.writeInt(modalClockFrame);out.writeLong(modalIdleMillis);}
    @Override public void load(DataInput in)throws IOException{super.load(in);if(in.readInt()!=0x44584831)throw new IOException("DCDX host snapshot version");for(int[] p:liveActors)for(int n=0;n<3;n++)p[n]=in.readInt();for(int n=0;n<6;n++)fifthStage[n]=in.readInt();for(int[] p:systemStage)for(int n=0;n<3;n++)p[n]=in.readInt();fifthCharacter=in.readUTF();savedFifthCharacter=in.readUTF();for(int n=0;n<3;n++)savedFifthStage[n]=in.readInt();if(!fifthCharacter.isEmpty()||!savedFifthCharacter.isEmpty())throw new IOException("DCDX nonempty fifth character snapshot requires its renderer");dcdxActorMotion.load(in);systemAction=in.readBoolean()?in.readUTF().getBytes(Binary.SJIS):null;if(systemAction!=null&&!Arrays.equals(systemAction,"on".getBytes(Binary.SJIS))&&!Arrays.equals(systemAction,"off".getBytes(Binary.SJIS)))throw new IOException("DCDX system action state");dcdxMenu.load(in);modalAction=in.readBoolean()?in.readUTF().getBytes(Binary.SJIS):null;if(modalAction!=null&&!Arrays.asList("story","ard","3do","end").contains(new String(modalAction,Binary.SJIS)))throw new IOException("DCDX modal action state");dcdxBanner.load(in);modalTimerInitialized=in.readBoolean();modalClockMs=in.readInt();modalClockFrame=in.readInt();modalIdleMillis=in.readLong();if(modalClockMs<0||modalClockMs>=1000||modalClockFrame!=((modalClockMs+1)*30-1)/1000||modalIdleMillis<0||modalIdleMillis>=2000||dcdxMenu.active&&dcdxBanner.active)throw new IOException("DCDX shared modal timer state");}
}
