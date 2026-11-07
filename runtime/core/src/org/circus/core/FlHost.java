package org.circus.core;

import java.io.*;
import java.util.*;

/** Independently identified DC2FL.exe host (9C69), not PC2's service table. */
public final class FlHost extends PcHost {
    public final FlCredits flCredits=new FlCredits();
    public UiCapture flCreditsUnderlay;
    public final FlActorMotion flActorMotion=new FlActorMotion();
    public final FlComposition flComposition=new FlComposition();
    public interface CompositionCapture {UiCapture capture(int mask,boolean messageText,boolean hideActors)throws IOException;}
    public CompositionCapture compositionCapture;
    private boolean compositionPending,compositionActorsDirty,compositionMessageVisible;
    private final int[] dirtyRect={800,600,0,0};
    private final int[][] lastActorRect=new int[5][4];
    private final boolean[] lastActorValid=new boolean[5];
    /** Native display triples A96BC8/CC/D0; stage triples remain in scene.character. */
    public final int[][] liveActors=new int[5][3];
    private final int[][] savedActorStage=new int[4][3];
    private final int[] loadedWidths=new int[5];
    private final int[] loadedHeights=new int[5],loadedPrezoom=new int[5];
    private final Map<String,Integer> rawWidths=new HashMap<String,Integer>();
    private final Map<String,Integer> rawHeights=new HashMap<String,Integer>();
    private int creditsSourceSerial;
    /** 4220D0 has five character owners; the common scene exposes the first four. */
    public String fifthCharacter="",savedFifthCharacter="";
    public final int[] fifthCoordinates={0,0,100,0,0,0},savedFifthCoordinates=new int[6];
    public boolean fifthSaved;
    private int fifthRestoreXOffset;
    public FlHost(CircusResources r,TextCodec c){super(r,c);if(!r.profile.id.equals("dc2fl-9c69"))throw new IllegalArgumentException("FL profile required");initLive();}
    public FlHost(CircusResources r,TextCodec c,int tick){super(r,c,tick);if(!r.profile.id.equals("dc2fl-9c69"))throw new IllegalArgumentException("FL profile required");initLive();}
    private void initLive(){for(int[] p:liveActors)p[2]=100;scene.bindActorProjection(this::projectFrame);}
    public int fifthDrawXOffset(){return fifthRestoreXOffset;}
    public boolean endingVisible(){return flCredits.active||flCredits.visible&&creditsSourceSerial==scene.transitionSerial;}
    public boolean compositionVisible(){return flComposition.active||flComposition.drawn&&flComposition.sourceSerial==scene.transitionSerial;}
    @Override public void enterSystem()throws IOException {
        for(int n=0;n<4;n++)for(int field=0;field<3;field++)savedActorStage[n][field]=scene.character(n,field);
        savedFifthCharacter=fifthCharacter;System.arraycopy(fifthCoordinates,0,savedFifthCoordinates,0,6);fifthSaved=true;super.enterSystem();
    }
    @Override public boolean beforeTransfer(int mode,String name)throws IOException{boolean result=super.beforeTransfer(mode,name);flCredits.releaseFrame();flCreditsUnderlay=null;flComposition.clear();clearCompositionPending();compositionMessageVisible=false;if(mode==1)fifthCharacter="";return result;}
    @Override public int gridSlot(int index)throws IOException{return read(99,0)*5+index;}
    @Override public boolean waiting(){return flComposition.active||flActorMotion.active||flCredits.active||super.waiting();}
    @Override public void elapse(long ms){super.elapse(ms);flCredits.advance(ms);try{applyCompletion();flComposition.advance(ms,read(99,12),ctrlActive());if(flActorMotion.advance(ms,read(99,12),ctrlActive(),variables==null?0:variables.read(3,52),liveActors)){scene.publish(ui,0,0);projectLive();}}catch(IOException e){throw new IllegalStateException("FL native updater",e);}}
    private void applyCompletion()throws IOException{int flag=flCredits.takeCompletion();if(flag!=0)write(100,flag,1);}
    /** Native button2 is right mouse. Keyboard/ADVANCE never enters this service. */
    public boolean endingInput(int mouseButton)throws IOException {
        if(!flCredits.input(mouseButton,read(100,52),read(100,53)))return false;play(0,"",false);logicalMusic(-1);return true;
    }
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException{if(flComposition.active||flCredits.active||flActorMotion.active)return false;return super.signal(expected,signal);}
    private int[] stage(int n){return n==4?fifthCoordinates:scene.character(n);}
    private void stage(int n,int[] p){if(n==4)System.arraycopy(p,0,fifthCoordinates,0,6);else scene.character(n,p);}
    private String character(int n){return n==4?fifthCharacter:scene.slots[2+n];}
    private boolean ctrlActive()throws IOException{return motionSkipPressed&&read(99,25)==0;}
    private int rawWidth(String name)throws IOException {
        Integer cached=rawWidths.get(name);if(cached!=null)return cached;
        int width,height;
        if(name.indexOf(',')<0){
            String path=name;int end=path.toLowerCase(Locale.ROOT).endsWith(".crx")?path.length()-4:path.length();
            if(end>0&&(path.charAt(end-1)=='@'||path.charAt(end-1)=='*'))path=path.substring(0,end-1)+path.substring(end);
            if(!path.toLowerCase(Locale.ROOT).endsWith(".crx"))path+=".CRX";
            byte[] header=resources.files.slice("AdvData/GRP/"+Binary.nativeResourcePath(path),0,20);
            if(Binary.u32(header,0)==0x47585243L){width=Binary.u16(header,8);height=Binary.u16(header,10);}else{Crx.Image image=resources.image(name);width=image.width;height=image.height;}
        }else{Crx.Image image=resources.image(name);width=image.width;height=image.height;}
        rawWidths.put(name,width);rawHeights.put(name,height);return width;
    }
    private int correction(int n)throws IOException {
        int width=loadedWidths[n],zoom=stage(n)[2];
        return zoom==100?(width>=882?-41:0):((long)width*(200-zoom)/100>800?-41:0);
    }
    /** Publish preserves all live triples across later text/transition/UI commits. */
    private void projectFrame(Dc2Scene.Frame frame){for(int n=0;n<4;n++){System.arraycopy(liveActors[n],0,frame.actors[n],0,3);for(int field=3;field<6;field++)frame.actors[n][field]=scene.character(n,field);lastActorValid[n]=!frame.slots[2+n].isEmpty();if(lastActorValid[n])lastActorRect[n]=actorRect(n,false);}}
    private void projectLive(){projectFrame(scene.shown);}
    public int[] fifthDisplayed(){int[] out=fifthCoordinates.clone();System.arraycopy(liveActors[4],0,out,0,3);return out;}
    private void loadedActor(int n,boolean restored)throws IOException {
        int[] p=stage(n);if(p[2]==0){p[2]=100;stage(n,p);}String name=character(n);int width=name.isEmpty()?0:rawWidth(name),height=name.isEmpty()?0:rawHeights.get(name);loadedPrezoom[n]=0;
        if(!ctrlActive()&&p[2]!=100&&p[2]<200){width=(int)((long)width*p[2]/100);height=(int)((long)height*p[2]/100);loadedPrezoom[n]=p[2];}loadedWidths[n]=width;loadedHeights[n]=height;
        System.arraycopy(p,0,liveActors[n],0,3);liveActors[n][0]+=restored?1:correction(n);
    }
    private int[] actorRect(int n,boolean staged){
        int[] p=stage(n);int zoom=staged?p[2]:liveActors[n][2],x=staged?p[0]+(zoom==100?(loadedWidths[n]>=882?-41:0):((long)loadedWidths[n]*(200-zoom)/100>800?-41:0)):liveActors[n][0],y=staged?p[1]:liveActors[n][1],w=loadedWidths[n],h=loadedHeights[n];
        if(zoom==100){if(w>882)x+=p[3]-(int)((long)(w*100/882)*p[3]/100);if(h>756)y+=p[4]-(int)((long)(h*100/756)*p[4]/100);}
        else{x+=p[3]-(int)((long)p[3]*zoom/100);y+=p[4]-(int)((long)p[4]*zoom/100);if(loadedPrezoom[n]==0){w=(int)((long)w*zoom/100);h=(int)((long)h*zoom/100);}}
        return new int[]{x,y,x+w,y+h};
    }
    private void union(int[] rect){dirtyRect[0]=Math.min(dirtyRect[0],rect[0]);dirtyRect[1]=Math.min(dirtyRect[1],rect[1]);dirtyRect[2]=Math.max(dirtyRect[2],rect[2]);dirtyRect[3]=Math.max(dirtyRect[3],rect[3]);compositionActorsDirty=true;}
    private void actorBefore(int n){if(lastActorValid[n])union(lastActorRect[n]);}
    private void actorAfter(int n){if(!character(n).isEmpty())union(actorRect(n,true));}
    private void clearCompositionPending(){compositionPending=compositionActorsDirty=false;dirtyRect[0]=800;dirtyRect[1]=600;dirtyRect[2]=dirtyRect[3]=0;}
    private UiCapture captureComposition(int mask,boolean message,boolean hidden)throws IOException {
        if(compositionCapture!=null)return compositionCapture.capture(mask,message,hidden);
        Dc2Scene.Frame frame=scene.shown;if(hidden){Dc2Scene.Frame copy=new Dc2Scene.Frame();ByteArrayOutputStream bytes=new ByteArrayOutputStream();frame.save(new DataOutputStream(bytes));copy.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));Arrays.fill(copy.slots,2,10,"");frame=copy;}
        return SceneCaptures.legacy(frame,scene,ui,variables,read(99,10)!=0,uiCapture);
    }
    private void flushComposition()throws IOException {
        if(!compositionPending&&!compositionActorsDirty)return;
        if(flComposition.active)throw new IOException("FL composition already active");
        boolean actors=compositionActorsDirty,prepare=compositionPending||actors&&compositionMessageVisible&&messageMode()!=0;
        UiCapture old=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);flComposition.clear();
        int l=Math.max(0,Math.min(800,dirtyRect[0])),t=Math.max(0,Math.min(600,dirtyRect[1])),r=Math.max(l,Math.min(800,dirtyRect[2])),b=Math.max(t,Math.min(600,dirtyRect[3]));
        scene.publish(ui,0,0);UiCapture prepared=prepare?captureComposition(0x0f,false,actors):null;if(prepare)compositionMessageVisible=false;
        UiCapture target=captureComposition(0xff,compositionMessageVisible,false);
        flComposition.start(old,target,prepared,scene.transitionSerial,actors,l,t,r,b);clearCompositionPending();flCredits.releaseFrame();flCreditsUnderlay=null;nextTicket();
    }
    @Override public boolean command(String op,int[] a,byte[] raw)throws IOException {
        // ADVMENU@58 uses selector18. FL's4228F0/case16 chain has no
        // selector18 store or call: it takes its native default return.
        if(op.equals("pc.persist")&&a.length>=1&&a[0]==18)return true;
        if(op.equals("pc.transition")&&a.length>=1&&a[0]==255){if(a.length<2||a[1]!=0)throw new IOException("FL255 nonzero initial actor phase not reviewed");flushComposition();return true;}
        // ADVMENU@5E uses0D/15: native composes41D710 and optionally restores
        // its message layer, but does not arm a transition wait or present timer.
        if(op.equals("pc.transition")&&a.length>=1&&a[0]==15){scene.publish(ui,0,0);clearCompositionPending();flComposition.clear();flCredits.releaseFrame();flCreditsUnderlay=null;return true;}
        if(op.equals("pc.background")){
            boolean handled=super.command(op,a,raw);for(int n=0;n<4;n++){scene.slots[2+n]="";loadedWidths[n]=loadedHeights[n]=loadedPrezoom[n]=0;}clearCompositionPending();compositionPending=true;compositionMessageVisible=false;return handled;
        }
        if(op.equals("fl.actor_position")){
            if(a.length!=4)throw new IOException("FL64 arguments");int n=a[2];
            if(n>=0&&n<5){int[] p=stage(n);p[0]=a[0];p[1]=a[1];p[2]=100;stage(n,p);return true;}
            if(n==255){String name=scene.slots[2];scene.slots[2]=scene.slots[3];scene.slots[3]=name;return true;}
            if(n<0)throw new IOException("FL64 negative descriptor alias not reviewed "+n);
            return true; //424A60: other selectors have no store.
        }
        if(op.equals("fl.actor_transform")){
            if(a.length!=4)throw new IOException("FL65 arguments");int mode=a[3];
            if(mode==255){for(int n=0;n<2;n++)if(a[n]!=-1){int[] p=stage(n);p[2]=a[n];stage(n,p);}return true;}
            if(mode>=0&&mode<5){flActorMotion.start(mode,a[0]+correction(mode),a[1]);nextTicket();return true;}
            if(mode>=16&&mode<20){int n=mode-16;int[] p=stage(n);p[2]=a[0];p[3]=a[1];p[4]=a[2];stage(n,p);return true;}
            if(mode>=20)return true; //424B7D default return.
            throw new IOException("FL65 alias selector not reviewed "+mode);
        }
        if(op.equals("pc.command")){
            if(a.length<2)throw new IOException("FL service arguments");
            switch(a[0]){
            case 3:case 4:case 5:case 10:return true; // Native 4090DF default return.
            case 6:saveGrid=a[1]==0;list.textStyle=2;return true; //406D50, five entries per page.
            case 7:list.textStyle=4;return true; //406E60 scene list.
            case 9:list.textStyle=3;return true; //406E70 music list.
            case 11:{
                if(flCredits.active)throw new IOException("FL ending owner already active");
                flCreditsUnderlay=nativeFrameCapture!=null?nativeFrameCapture.capture():SceneCaptures.legacy(scene.shown,scene,ui,variables,read(99,10)!=0,uiCapture);
                if(captureRenderer!=null)flCreditsUnderlay=captureRenderer.flatten(flCreditsUnderlay);
                if(flCreditsUnderlay==null)throw new IOException("FL ending initial front missing");creditsSourceSerial=scene.transitionSerial;
                if(a[1]!=0&&variables==null)throw new IOException("FL ending route requires its VM ordinary bank owner");flCredits.start(a[1],a[1]==0?0:variables.read(3,50));pcCredits.active=false;nextTicket();return true;
            }
            case 8:throw new IOException("FL service8 native type0 coroutine is not reviewed");
            case 1:case 2:throw new IOException("FL calendar/rank service is not reviewed "+a[0]);
            //Other selectors hit the FL table's native default, not PC2's map20.
            default:return true;
            }
        }
        if(op.equals("pc.character")&&a.length>0){
            int n=a[0];String name=TextCodec.cp932(raw);
            if(n>=0&&n<5&&!name.isEmpty()&&!name.equals("."))actorBefore(n);
            if(n==4){
                if(name.equals("--")){fifthCharacter=savedFifthCharacter;System.arraycopy(savedFifthCoordinates,0,fifthCoordinates,0,3);fifthRestoreXOffset=1;}
                else if(name.equals("-")){fifthCharacter="";fifthRestoreXOffset=0;}
                else if(!name.isEmpty()&&!name.equals(".")){fifthCharacter=imageName(name);fifthRestoreXOffset=0;unlock("images",fifthCharacter,97);}
                if(fifthCoordinates[2]==0)fifthCoordinates[2]=100;loadedActor(4,name.equals("--"));actorAfter(4);scene.revision++;return true;
            }
            if(n>=0&&n<4&&name.equals("--")){
                scene.slots[2+n]=systemScene==null?"":systemScene.slots[2+n];scene.characterPrefix(n,savedActorStage[n],3);if(scene.character(n,2)==0)scene.character(n,2,100);loadedActor(n,true);actorAfter(n);scene.revision++;return true;
            }
            Dc2Scene.Frame before=scene.shown;boolean handled=super.command(op,a,raw);if(n>=0&&n<4&&!name.isEmpty()&&!name.equals(".")){loadedActor(n,false);actorAfter(n);}if(scene.shown!=before)projectLive();return handled;
        }
        // An actual subsequent native presentation replaces the final ending frame.
        if(!flCredits.active&&(op.equals("pc.transition")||op.equals("ui.commit")||op.equals("dm.ui.commit")||op.equals("dm.scene.commit"))){flCredits.releaseFrame();flCreditsUnderlay=null;}
        Dc2Scene.Frame before=scene.shown;int quads=ui.quads.size();boolean handled=super.command(op,a,raw);
        if(scene.shown!=before){projectLive();clearCompositionPending();flComposition.clear();}else if(ui.quads.size()!=quads)compositionPending=true;
        if(op.equals("text.body"))compositionMessageVisible=!body.isEmpty();
        // Both native0D/0 and0D/13 complete through423D38, which clears
        // CD0410 independently of the retained message bytes themselves.
        if(op.equals("pc.transition")&&a.length>0&&(a[0]==0||a[0]==13))compositionMessageVisible=false;
        return handled;
    }
    @Override public void copyPersistentFrom(Dc2Host other)throws IOException{super.copyPersistentFrom(other);if(!(other instanceof FlHost))throw new IOException("FL persistent host mismatch");compositionCapture=((FlHost)other).compositionCapture;}
    @Override public void save(DataOutput out)throws IOException {
        super.save(out);out.writeInt(0x464c4832);flCredits.save(out);UiCapture.save(out,flCreditsUnderlay);
        out.writeUTF(fifthCharacter);out.writeUTF(savedFifthCharacter);for(int x:fifthCoordinates)out.writeInt(x);for(int x:savedFifthCoordinates)out.writeInt(x);out.writeBoolean(fifthSaved);out.writeInt(fifthRestoreXOffset);for(int[] p:liveActors)for(int x:p)out.writeInt(x);flActorMotion.save(out);for(int[] p:savedActorStage)for(int x:p)out.writeInt(x);out.writeInt(creditsSourceSerial);for(int x:loadedWidths)out.writeInt(x);
        for(int x:loadedHeights)out.writeInt(x);for(int x:loadedPrezoom)out.writeInt(x);for(int n=0;n<5;n++){out.writeBoolean(lastActorValid[n]);for(int x:lastActorRect[n])out.writeInt(x);}out.writeBoolean(compositionPending);out.writeBoolean(compositionActorsDirty);out.writeBoolean(compositionMessageVisible);for(int x:dirtyRect)out.writeInt(x);flComposition.save(out);
    }
    @Override public void load(DataInput in)throws IOException {
        super.load(in);if(in.readInt()!=0x464c4832)throw new IOException("FL host state version");flCredits.load(in);flCreditsUnderlay=UiCapture.load(in);
        fifthCharacter=in.readUTF();savedFifthCharacter=in.readUTF();for(int n=0;n<6;n++)fifthCoordinates[n]=in.readInt();for(int n=0;n<6;n++)savedFifthCoordinates[n]=in.readInt();fifthSaved=in.readBoolean();fifthRestoreXOffset=in.readInt();for(int[] p:liveActors)for(int n=0;n<3;n++)p[n]=in.readInt();flActorMotion.load(in);for(int[] p:savedActorStage)for(int n=0;n<3;n++)p[n]=in.readInt();creditsSourceSerial=in.readInt();for(int n=0;n<5;n++)loadedWidths[n]=in.readInt();rawWidths.clear();
        for(int n=0;n<5;n++)loadedHeights[n]=in.readInt();for(int n=0;n<5;n++)loadedPrezoom[n]=in.readInt();for(int n=0;n<5;n++){lastActorValid[n]=in.readBoolean();for(int k=0;k<4;k++)lastActorRect[n][k]=in.readInt();}compositionPending=in.readBoolean();compositionActorsDirty=in.readBoolean();compositionMessageVisible=in.readBoolean();for(int n=0;n<4;n++)dirtyRect[n]=in.readInt();flComposition.load(in);rawHeights.clear();
        if(fifthRestoreXOffset!=0&&fifthRestoreXOffset!=1||flCredits.visible&&flCredits.frame().capture&&flCreditsUnderlay==null||pcCredits.active)throw new IOException("FL saved owner mismatch");
    }
}
