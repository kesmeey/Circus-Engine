package org.circus.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

/** PC platform adapter: original MES control flow, original atlas UI, shared VM. */
public final class DesktopLegacy extends DesktopEngine {
    public final Dc2Session session;
    private final DesktopNativeFiles nativeFiles;
    private final DesktopImageCache<Picture> images=new DesktopImageCache<>(Picture::residentBytes);
    private final DesktopCaptures captures;private SceneCaptures.Legacy captureState;
    private static final class MotionRaster {int count,backgroundWidth,backgroundHeight;int[] pixels,prepared,background;boolean backgroundLoaded;BufferedImage image;}
    private final Map<Dc2Motion,MotionRaster> motionRasters=new LinkedHashMap<Dc2Motion,MotionRaster>(4,.75f,true){protected boolean removeEldestEntry(Map.Entry<Dc2Motion,MotionRaster> entry){return size()>4;}};
    private long textClock,textTicket=-1;
    /** Newest story line; the system menu clears {@code body}, so a save made from SAVE.mes still needs it. */
    private String lastLine="";
    private int savedAutoGeneration;
    public int hovered=-1;
    public String problem="";
    static final class Picture {final Crx.Image raw;final BufferedImage image;BufferedImage opaque;Picture(BufferedImage b){raw=new Crx.Image(0,0,b.getWidth(),b.getHeight(),32,new byte[0],null);image=b;}Picture(Crx.Image r){raw=r;image=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,r.width,r.height,r.argb(true),0,r.width);}static long constructionBytes(Crx.Image r){return rawBytes(r)+8L*r.width*r.height;}private static long rawBytes(Crx.Image r){return 1024L+r.pixels.length+(r.palette==null?0:4L*r.palette.length);}long residentBytes(){return rawBytes(raw)+4L*image.getWidth()*image.getHeight()+(opaque==null?0:4L*opaque.getWidth()*opaque.getHeight());}}

    private boolean toolbar;private Dc2Ui.Button dragged;private int dragKind,pressedButton=-1;
    private String pendingSystemAction;
    private int pendingSystemEvent;
    private final Dc2NativeData nativeData;
    public DesktopLegacy(CircusResources data,TextCodec codec,boolean audit)throws Exception {
        super(data,codec,audit,PcHost.supports(data.profile)?800:1024,PcHost.supports(data.profile)?600:576);
        nativeFiles=data.profileId.equals("dcpc-2063")?new DesktopNativeFiles(saveDirectory):null;
        final Path progress=saveDirectory.resolve("progress.dat");
        session=new Dc2Session(data,codec,h->{
            if(nativeFiles!=null)((PcHost)h).bindNativeSettingsIo(nativeFiles,"system.dat");
            if(!audit&&Files.exists(progress))try(DataInputStream in=new DataInputStream(Files.newInputStream(progress))){h.loadPersistent(in);}
            // Native bank100 persists immediately. Its newest raw file takes
            // precedence over an older portable account saved at another time.
            if(nativeFiles!=null&&!audit&&Files.exists(nativeFiles.directory().resolve("system.dat")))((PcHost)h).reloadNativeSettingsFromBoundIo();
        });captures=new DesktopCaptures(width,height,this::drawCapture);host().captureRenderer=captures;nativeData=PcHost.supports(data.profile)?null:new Dc2NativeData(data,codec);
        if(data.profileId.equals("dc2-3363")||data.profileId.equals("dcpc-2063"))((PcHost)host()).nativeFrameCapture=this::captureFramebuffer;
        if(pc1()!=null)session.pc1CandidateCapture=()->captureSaveMetadata(false).encode();
        scanSaves();
    }
    private UiCapture captureFramebuffer()throws IOException{BufferedImage front=render();try{return UiCapture.pixels(width,height,front.getRGB(0,0,width,height,null,0,width));}finally{front.flush();}}
    public Dc2Host host(){return session.host;}
    public MesVm vm(){return session.vm;}
    private Pc1Runtime pc1(){return host() instanceof PcHost?((PcHost)host()).pc1:null;}
    private boolean timedInput(){return host() instanceof PcHost&&((PcHost)host()).hasTimedInput();}
    public void tick(long elapsed)throws Exception {
        Dc2Host h=host();h.fullscreen=fullscreenRequested;if(h instanceof PcHost)((PcHost)h).motionSkipPressed=rapid;session.vm.elapse(elapsed);
        if(h.presentationWaiting||h.textWaiting){if(textTicket!=h.waitTicket()){textClock=0;textTicket=h.waitTicket();}textClock+=elapsed;if(textClock>=textDuration())session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);}
        if(h.returnRequested){session.returnToStory();finishSystemAudio();h=host();}
        MesVm.Status status=session.vm.run(1000);if(status==MesVm.Status.FAULT)throw new IOException(session.vm.error());session.observeTransfer();if(h.windowRequest>=0){fullscreenRequested=h.windowRequest!=0;h.windowRequest=-1;}
        session.captureHistory();
        if(!session.inSystem()&&!h.body.isEmpty())lastLine=h.body;
        if(!session.inSystem()&&(h.textWaiting||h.presentationWaiting)&&!h.history.entries.isEmpty()){Dc2History.Entry entry=h.history.entries.get(h.history.entries.size()-1);if(entry.scene.length==0){ByteArrayOutputStream b=new ByteArrayOutputStream();h.scene.shown.save(new DataOutputStream(b));entry.scene=b.toByteArray();}}
        if(openPendingSystem())return;
        if(h.history.rewindRequest>=0){session.rewindHistory(h.history.rewindRequest);auto=rapid=false;}
        if(h.saveRequest>=0){writeSlot(h.saveRequest);h.completeSave();session.vm.elapse(0);}
        if(h.loadRequest>=0){readSlot(h.loadRequest);return;}
        if(h.autosaveGeneration!=savedAutoGeneration&&!session.inSystem()&&h.textWaiting){savedAutoGeneration=h.autosaveGeneration;if(!audit)writeSlot(h instanceof PcHost?0:2);}
        if(pendingSystemAction==null&&(pc1()==null||!pc1().historyActive())&&!h.ui.waiting&&(rapid&&h.skipAllowed||(auto||h instanceof PcHost&&h.read(99,13)>=2)&&textClock>autoDelay()&&!h.audioActive[5]))session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
    }
    public java.util.List<Dc2Ui.Button> buttons(){Dc2Host h=host();if(pc1()!=null&&pc1().historyActive())return hidden?Collections.<Dc2Ui.Button>emptyList():pc1().storyPrefix();if(h.ui.waiting){java.util.List<Dc2Ui.Button> bs=new ArrayList<>(h.ui.buttons);if(h.gridWaiting)for(int n=0;n<h.grid.size();n++)bs.add(h.gridControl(n));return bs;}return !h.body.isEmpty()&&!hidden?pc1()!=null?pc1().storyPrefix():h.ui.buttonTemplates.get(host() instanceof PcHost?(h.messageMode()==0?1:4):(toolbar?6:1)):Collections.emptyList();}
    public void scroll(int units)throws IOException{if(pc1()!=null&&pc1().returning())return;if(host().history.active&&!(host() instanceof PcHost)){host().history.scroll(units);host().historyButtons();return;}if(host() instanceof PcHost){PcHost h=(PcHost)host();if(h.gridWaiting&&h.list.active())h.listOffset(h.list.clamp(h.listOffset()+units*h.list.columns,h.grid.size()));}}
    public void click(int x,int y,boolean right)throws Exception {
        Dc2Host h=host();
        if(pc1()!=null&&pc1().returning())return;
        if(h instanceof PcHost&&((PcHost)h).nameInput.active){PcHost p=(PcHost)h;if(right){p.nameAction("@name:cancel");return;}for(Control b:controls())if(b.contains(x,y)){p.nameAction(b.action);return;}return;}
        if(hidden){hidden=false;return;}
        if(right&&pc1()!=null&&pc1().closeHistory())return;
        if(right&&pc1()!=null&&pc1().waitKind()==9){requestSystem("ADVMENU",0);return;}
        if(!right&&pc1()!=null)for(Pc1TextLayout.Region region:pc1().dictionaryRegions())if(x>=region.left&&x<region.right&&y>=region.top&&y<region.bottom)throw new IOException("PC1 native dictionary :w requires the fixed jisho MES and its raw49 query service; this bridge is not implemented");
        if(h instanceof PcHost&&((PcHost)h).hasOriginalBackgroundInputWait()){
            if(!session.vm.activateMenu(h.waitTicket(),right?-1:-2))session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
            return;
        }
        if(!right&&drag(x,y,true)){endDrag();return;}
        if(!right&&h instanceof PcHost&&h.gridWaiting){PcHost p=(PcHost)h;if(p.list.active()){int offset=p.list.scrollAt(x,y,p.gridBounds,p.listOffset(),p.grid.size());if(offset>=0){p.listOffset(offset);return;}}}
        java.util.List<Dc2Ui.Button> bs=buttons();
        if(!right)for(int n=bs.size()-1;n>=0;n--){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom&&!b.action.isEmpty()){
            if(pc1()!=null&&(pc1().historyActive()||pc1().waitKind()==9&&n<pc1().storyPrefix().size())){h.toolbarSound(b);action(b.action);return;}
            if(h instanceof PcHost&&h.history.active&&(b.action.equals(":close")||b.action.equals(":change"))){action(b.action);return;}
            for(Dc2Ui.Quad q:b.sprites)if(q.state==5){int value=(int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));session.vm.variables.write(q.sy,q.width,value);}
            if(b.action.startsWith("@slot:"))session.vm.activateGrid(h.waitTicket(),Integer.parseInt(b.action.substring(6)));else if(h.ui.waiting)session.vm.activateMenu(h.waitTicket(),n);else{h.toolbarSound(b);action(b.action);}return;}}
        if(!right&&pc1()!=null&&pc1().historyActive()){pc1().replayHistoryVoice();return;}
        if(h instanceof PcHost&&(pc1()==null||pc1().waitKind()!=7&&pc1().waitKind()!=9)&&!h.confirming&&(timedInput()||h.ui.waiting)&&!h.ui.actions[right?0:1].isEmpty()){session.vm.activateMenu(h.waitTicket(),right?-1:-2);return;}
        if(right&&h.ui.waiting)session.vm.activateMenu(h.waitTicket(),-1);
        else if(!h.ui.waiting){if(right)requestSystem("ADVMENU",0);else session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
    }
    public boolean drag(int x,int y,boolean begin)throws Exception{
        if(pc1()!=null&&pc1().returning())return false;
        if(begin){dragged=null;dragKind=0;Dc2Host h=host();if(!h.ui.waiting)return false;if(h.history.active&&!(h instanceof PcHost)&&x>=944&&x<984&&y>=112&&y<444)dragKind=1;else if(h instanceof PcHost&&h.gridWaiting&&((PcHost)h).list.active()){int[] b=((PcHost)h).gridBounds;if(x>=b[0]-4&&x<b[2]+4&&y>=b[1]&&y<b[3])dragKind=2;}if(dragKind==0)for(Dc2Ui.Button b:h.ui.buttons)if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)for(Dc2Ui.Quad q:b.sprites)if(q.state==5)dragged=b;}
        if(dragKind==1){Dc2History h=host().history;int max=Math.max(0,h.entries.size()-4);h.cursor=Math.max(0,Math.min(max,(int)Math.round((y-112)*max/300.0)));host().historyButtons();return true;}
        if(dragKind==2){PcHost h=(PcHost)host();int[] b=h.gridBounds;int cy=Math.max(b[1],Math.min(b[3]-1,y));h.listOffset(y>=b[3]?h.list.clamp(Integer.MAX_VALUE,h.grid.size()):h.list.scrollAt((b[0]+b[2])/2,cy,b,h.listOffset(),h.grid.size()));return true;}
        if(dragged==null)return false;Dc2Ui.Quad cfg=null,stops=null;for(Dc2Ui.Quad q:dragged.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)return false;
        int value=(int)Math.round(Math.max(0,Math.min(1,(x-cfg.x)/(double)Math.max(1,cfg.y-cfg.x)))*Math.max(0,cfg.sx-1));if(stops!=null){int[] ends={stops.x,stops.y,stops.sx,stops.sy};value=0;while(value<3&&x>=ends[value])value++;}
        vm().variables.write(cfg.sy,cfg.width,value);return true;
    }
    public void endDrag()throws Exception{dragKind=0;if(dragged==null)return;String action=dragged.action;dragged=null;if(!action.isEmpty())for(int n=0;n<host().ui.buttons.size();n++)if(host().ui.buttons.get(n).action.equals(action)){vm().activateMenu(ticket(),n);break;}}
    protected void cancelDrag(){dragged=null;dragKind=0;}
    protected void pressed(int x,int y,boolean down){if(pc1()!=null&&pc1().returning())return;pressedButton=-1;if(down){java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)pressedButton=n;}}host().ui.pressed=pressedButton<host().ui.buttons.size()?pressedButton:-1;}
    public void action(String a)throws Exception {
        if(pc1()!=null&&pc1().returning())return;
        String key=a.toLowerCase(Locale.ROOT);
        if(pc1()!=null){if(key.equals(":u"))key=":up";else if(key.equals(":d"))key=":down";}
        if(key.equals(":close")||key.equals(":clear")||key.equals(":change")){hidden=true;return;}
        if(host() instanceof PcHost&&(key.equals(":up")||key.equals(":backlog"))){requestSystem("@history");return;}
        if(host() instanceof PcHost&&key.equals(":down")){if(pc1()!=null){if(pc1().historyNext())return;if(pc1().waitKind()==9)return;}vm().signalHost(ticket(),MesVm.HostSignal.ADVANCE);return;}
        if(key.equals(":full")){fullscreenRequested=!fullscreenRequested;return;}
        if(key.equals(":n")){toolbar=!toolbar;host().write(99,227,toolbar?1:0);return;}
        if(key.equals(":auto")){auto=!auto;toolbar=false;return;}
        if(key.equals(":rapid")){rapid=!rapid;toolbar=false;return;}
        if(key.equals(":qsave")||key.equals(":qs")){writeSlot(1);toolbar=false;return;}
        if(key.equals(":qload")||key.equals(":ql")){if(Files.exists(slot(1)))readSlot(1);toolbar=false;return;}
        String script=key.equals(":backlog")?"LOG":key.equals(":torisetu")?"MANUAL":key.equals(":option")&&host() instanceof PcHost?"OPTION":key.equals(":menu")||key.equals(":option")?"ADVMENU":null;
        if(script!=null){requestSystem(script);return;}
        throw new IOException("Unimplemented original system action "+a);
    }
    /** Input may arrive after ADVANCE releases a wait but before the next tick runs MES. */
    private void requestSystem(String action)throws IOException {
        requestSystem(action,action.equals("ADVMENU")?1:2);
    }
    private void requestSystem(String action,int eventKind)throws IOException {
        if(session.inSystem())return;
        auto=rapid=false;
        // No story toolbar exists during startup, movies or a title transition.
        if(host().body.isEmpty()&&!host().ui.waiting)return;
        if(pendingSystemAction==null){pendingSystemAction=action;pendingSystemEvent=eventKind;}
        openPendingSystem();
    }
    private boolean openPendingSystem()throws IOException {
        if(pendingSystemAction==null)return false;
        if(session.inSystem()||vm().status()==MesVm.Status.HALTED){pendingSystemAction=null;return false;}
        if(host().presentationWaiting)vm().signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);
        // Resolve the released wait, but never advance a dialogue or complete media here.
        if(vm().run(1000)==MesVm.Status.FAULT)throw new IOException(vm().error());
        if(vm().scriptName().equalsIgnoreCase("MAIN")||vm().scriptName().equalsIgnoreCase("START")){pendingSystemAction=null;return false;}
        boolean nativeHistory=pendingSystemAction.equals("@history");
        if(!host().textWaiting&&(nativeHistory?pc1()==null||pc1().waitKind()!=9:!host().ui.waiting))return false;
        String action=pendingSystemAction;pendingSystemAction=null;
        if(nativeHistory&&pc1()!=null){pc1().historyPrevious();toolbar=auto=rapid=false;return true;}
        session.captureHistory();beginSystemAudio();
        if(nativeHistory){session.openNativeSystem();((PcHost)host()).openHistory();}
        else session.openSystem(action,pendingSystemEvent);
        toolbar=auto=rapid=false;return true;
    }
    @Override public boolean modalKey(int code)throws IOException{
        Pc1Runtime nativeText=pc1();if(nativeText!=null&&nativeText.returning())return true;if(nativeText==null||nativeText.waitKind()!=7&&nativeText.waitKind()!=9)return false;
        if(code==KeyEvent.VK_PAGE_UP){nativeText.historyPrevious();auto=rapid=false;return true;}
        if(code==KeyEvent.VK_PAGE_DOWN){nativeText.historyNext();return true;}
        if(code==KeyEvent.VK_ESCAPE&&nativeText.historyActive()){nativeText.closeHistory();return true;}
        if(code==KeyEvent.VK_ENTER&&nativeText.historyActive()){nativeText.replayHistoryVoice();return true;}
        if(code==KeyEvent.VK_ENTER&&nativeText.waitKind()==9)throw new IOException("PC1 native choice Enter requires the unimplemented40A6D0 focus owner");
        if(code==KeyEvent.VK_SPACE){hidden=!hidden;return true;}
        return false;
    }
    public void hover(int x,int y)throws IOException{if(pc1()!=null&&pc1().returning())return;int old=hovered;hovered=-1;java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)hovered=n;}if(host().ui.waiting)host().hoverMenu(hovered<host().ui.buttons.size()?hovered:-1);else if(old!=hovered&&hovered>=0){if(host() instanceof PcHost)((PcHost)host()).hoverButton(bs.get(hovered));else if(bs.get(hovered).hoverSound!=255)host().play(1,resources.named("sound",bs.get(hovered).hoverSound),false);}if(pc1()!=null)host().ui.hovered=hovered;if(host() instanceof PcHost){Pc2Map m=((PcHost)host()).map;if(m.active&&m.selected<0){m.hovered=-1;for(int n=0;n<m.regions().length;n++){int[] r=m.regions()[n];if(x>=r[0]&&x<r[2]&&y>=r[1]&&y<r[3])m.hovered=n;}}}}
    public java.util.List<Control> controls(){if(pc1()!=null&&pc1().returning())return Collections.emptyList();java.util.List<Control> out=new ArrayList<>();for(Dc2Ui.Button b:buttons())if(!b.action.isEmpty())out.add(new Control(b.action,b.left,b.top,b.right,b.bottom));if(host() instanceof PcHost&&((PcHost)host()).nameInput.active){PcNameInput name=((PcHost)host()).nameInput;if(name.confirming)return out;for(int n=0;n<8;n++){int x=336+(n/4)*192+(n%4)*32;out.add(new Control("@name:field:"+n,x,40,x+32,72));}java.util.List<String> rows=name.visibleRows();for(int r=0;r<rows.size();r++)for(int c=0;c<Math.min(17,rows.get(r).length());c++){int x=280+c*24,y=96+r*24+(r>=4?8:0);if(rows.get(r).charAt(c)!='　')out.add(new Control("@name:char:"+(r*17+c),x,y,x+24,y+24));}}return out;}
    private int textSpeed()throws IOException{return host().read(99,host() instanceof PcHost?13:auto?27:26);}
    private long textDuration()throws IOException{return TextPlayback.duration(resources.profile.id,textSpeed(),body().codePointCount(0,body().length()));}
    private long autoDelay()throws IOException{if(host() instanceof PcHost){int bytes=body().getBytes(codec.charset).length;return bytes*(host().read(99,13)==3?200L/3:100L);}return Math.max(100,20-host().read(99,29))*100L;}
    public String visibleBody()throws IOException{return body().substring(0,TextPlayback.visible(resources.profile.id,textSpeed(),body(),playbackMillis(),host().textRendered||!host().presentationWaiting));}
    public long playbackMillis(){return textTicket==ticket()?textClock:0;}
    public void playbackMillis(long value){textTicket=ticket();textClock=value;}
    public String body(){return host().body;}
    public boolean textWaiting(){return host().textWaiting||host().presentationWaiting;}
    public boolean menuWaiting(){return host().ui.waiting;}
    public boolean actorVisible(){for(int n=2;n<=9;n++)if(!host().scene.shown.slots[n].isEmpty())return true;return false;}
    public boolean exitRequested(){return host().exitRequested;}
    public long ticket(){return host().waitTicket();}
    public String movie(){return host().movie;}
    public int movieGeneration(){return host().movieGeneration;}
    public String audio(int ch){return host().audio[ch];}
    public int audioGeneration(int ch){return host().audioGeneration[ch];}
    public boolean audioLoop(int ch){return host().audioLoop[ch];}
    public boolean audioActive(int ch){return host().audioActive[ch];}
    public void audioCompleted(int ch,int generation){host().audioCompleted(ch,generation);}
    public int pan(int ch){return ch==5?host().voicePan:0;}
    public float volume(int ch)throws IOException{if(ch==5&&resources.profileId.equals("dc2-3363"))return host().voiceGain();int enabled=ch==0?14:ch==5?5:15,level=ch==0?7:ch==5?9:8;return host().read(99,enabled)==0?0:(ch==0?host().musicFadeGain():ch==5?host().voiceGain():1)*Math.max(0,Math.min(1,host().read(99,level)/30f));}
    public byte[] snapshot()throws IOException{return pc1()==null?session.storySnapshot():session.snapshot();}
    public void restore(byte[] bytes)throws IOException{cancelPointer();session.restore(bytes);host().ui.pressed=-1;savedAutoGeneration=host().autosaveGeneration;textTicket=-1;hidden=toolbar=auto=rapid=false;pendingSystemAction=null;scanSaves();}
    private Path slot(int n)throws IOException{if(n<0||n>255)throw new IOException("Invalid save slot "+n);return saveDirectory.resolve("slot-"+n+".sav");}
    private void writeSlot(int n)throws IOException{
        if(pc1()==null)save(slot(n));
        else{
            if(host().saveRequest!=n)throw new IOException("PC1 quick-save input capture is not mapped; use the original Save menu or F5");
            session.requirePc1SaveCandidate();byte[] metadata=session.pc1CandidateMetadata();
            if(metadata.length==0)throw new IOException("PC1 checkpoint has no platform preview/audio metadata");
            DesktopSave s=DesktopSave.decode(metadata);if(s.state.length!=0)throw new IOException("PC1 checkpoint metadata contains an unexpected VM");
            Calendar now=new GregorianCalendar();Pc1Frame.SaveMetadata local=new Pc1Frame.SaveMetadata(now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH),now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE));
            s.state=session.pc1CandidateSnapshot(local);s.icon=vm().variables.read(9,20);s.created=System.currentTimeMillis();writeSave(slot(n),s);
        }
        scanSaves();persist();
    }
    private void readSlot(int n)throws IOException{restore(slot(n));}
    private void scanSaves()throws IOException{host().availableSaves.clear();host().saveLabels.clear();thumbnails.clear();for(int n=0;n<256;n++)if(Files.exists(slot(n))){host().availableSaves.add(n);host().saveLabels.put(n,new java.text.SimpleDateFormat("MM/dd HH:mm").format(new Date(Files.getLastModifiedTime(slot(n)).toMillis())));scanThumbnail(n,slot(n));}}
    public String saveCaption(){return host().checkpointLabel.isEmpty()?(body().isEmpty()?lastLine:body()):codec.apply(host().checkpointLabel);}
    public BufferedImage preview()throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{if(host().systemScene==null&&host().scene.nativeMotion!=null)g.drawImage(motion(host().scene.nativeMotion),0,0,null);else scene(g,host().systemScene!=null?host().systemScene:host().scene.shown);}finally{g.dispose();}return image;}
    public void persist()throws IOException{if(audit)return;ByteArrayOutputStream b=new ByteArrayOutputStream();host().savePersistent(new DataOutputStream(b));atomic(saveDirectory.resolve("progress.dat"),b.toByteArray());}
    private Picture picture(String name)throws IOException {
        if(name==null||name.isEmpty())return null;if(name.equals("@ui-capture"))return new Picture(captures.resolve(host().uiCapture));
        Picture p=images.get(name);if(p==null){
            try{images.beforeLoad();Crx.Image raw=resources.image(name);images.reserve(Picture.constructionBytes(raw));p=new Picture(raw);}catch(java.io.FileNotFoundException|java.nio.file.NoSuchFileException missing){
                // A repacked install can lack an optional sheet; the native loader also leaves the
                // slot empty. Keep the name so audits can list it instead of failing the frame.
                missingImages.add(name);return null;
            }
            images.put(name,p);loadedImages.add(name);
        }
        return p;
    }
    private int color(int style){return (captureState==null?host().ui.palette:captureState.palette)[style&63];}
    private BufferedImage drawCapture(int kind,DataInputStream input)throws IOException {
        if(kind!=UiCapture.LEGACY)throw new IOException("Wrong legacy UI capture profile");SceneCaptures.Legacy saved=captureState;captureState=new SceneCaptures.Legacy(input);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);scene(g,captureState.frame);}finally{g.dispose();captureState=saved;}return image;
    }
    private void at(Graphics2D g,String name,int x,int y)throws IOException {Picture p=picture(name);if(p!=null)g.drawImage(p.image,x+p.raw.x,y+p.raw.y,null);}
    private void text(Graphics2D g,String s,int x,int y,int lineHeight,int style,int width){
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,22));Color foreground=new Color(color(style),true);
        // PC1 417A5E / PC2 421CA6 / DM 4024E8 draw style 0x80 at (+1,+1), using palette 0; PC2 uses 9 for black text.
        boolean shadow=(style&0x80)!=0;Color shade=shadow?new Color(color(host() instanceof PcHost&&((PcHost)host()).second&&(style&63)==0?9:0),true):foreground;
        g.setColor(foreground);int at=x,base=y+22;for(int pos=0;pos<s.length();){int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=Character.charCount(cp);int w=g.getFontMetrics().stringWidth(c);if(cp==10||at+w>x+width){at=x;base+=Math.max(22,lineHeight);if(cp==10)continue;}if(shadow){g.setColor(shade);g.drawString(c,at+1,base+1);g.setColor(foreground);}g.drawString(c,at,base);at+=w;}
    }
    private void quad(Graphics2D g,Dc2Ui.Quad q,String[] slots,boolean sprite)throws IOException {
        if(!visibleQuad(q))return;
        if(q.binding!=0&&session.vm.variables.read(q.binding>>8,q.binding&255)==0)return;
        if(q.type==2){String value=q.text;if(q.nativeTextOffset!=-1){if(!(host() instanceof PcHost)||((PcHost)host()).pc1==null||captureState!=null)throw new IOException("Native type10 draw requires the live PC1 loaded-code owner");value=((PcHost)host()).pc1.resolveDrawText(q);}if(!value.isEmpty())text(g,codec.apply(value),q.x,q.y,sprite?23:Math.max(22,q.width),sprite?q.flags:q.height,width-q.x);return;}
        if(q.width<=0||q.height<=0)return;
        // A "restore" blit copies the scene background back over a rectangle where a hover bubble
        // or label used to sit: D.C.II's alarm clock stores three of them as button sprites with
        // source == destination (meza_nin_20 40EB/417D/41FB, slot 0 = the background texture). The
        // native widget paints into the background layer, but this renderer rebuilds the
        // background every frame, so the copy is a no-op — drawing it over the panel quad that was
        // laid down before the buttons would punch a background hole through the panel's left edge.
        if(q.type==0&&q.slot()==1&&q.sx==q.x&&q.sy==q.y)return;
        Composite old=g.getComposite();if(q.opaque())g.setComposite(AlphaComposite.SrcOver.derive(80/255f));
        if(q.type==1){g.setColor(new Color(color(q.flags),true));g.fillRect(q.x,q.y,q.width,q.height);}
        else if(q.type==0){Picture p=picture(slots[q.slot()]);if(p!=null){int sx=q.sx,sy=q.sy;if(q.animation!=0&&q.frames>0&&q.period>0)sx+=(int)(host().scene.effectClock/(34L*q.period)%q.frames)*q.step;int l=Math.max(0,sx),t=Math.max(0,sy),r=Math.min(p.image.getWidth(),sx+q.width),b=Math.min(p.image.getHeight(),sy+q.height);if(l<r&&t<b)g.drawImage(p.image,q.x+l-sx,q.y+t-sy,q.x+r-sx,q.y+b-sy,l,t,r,b,null);}}
        else throw new IOException("Desktop draw type pending: "+q.type);
        g.setComposite(old);
    }
    private boolean visibleQuad(Dc2Ui.Quad q)throws IOException{
        if(q.nativeCondition==0)return true;if(captureState!=null)throw new IOException("Frozen PC1 quad retains a live native condition");
        if(!(host() instanceof PcHost)||((PcHost)host()).pc1==null)throw new IOException("Native conditional quad requires PC1");return ((PcHost)host()).pc1.visible(q);
    }
    private void scene(Graphics2D g,Dc2Scene.Frame f)throws IOException {
        scene(g,f,f.x,f.y);
    }
    private void scene(Graphics2D g,Dc2Scene.Frame f,int x,int y)throws IOException {
        boolean original=resources.profileId.equals("dc2-3363");
        if(original){
            BufferedImage composed=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D cg=composed.createGraphics();
            try{cg.setColor(Color.BLACK);cg.fillRect(0,0,width,height);at(cg,f.slots[1],x,y);for(Dc2Ui.Quad q:f.quads)quad(cg,q,f.slots,false);nativeParticles(composed,1);actors(cg,f,false);nativeParticles(composed,0);}finally{cg.dispose();}
            g.drawImage(composed,0,0,null);return;
        }
        g.setColor(Color.BLACK);g.fillRect(0,0,width,height);at(g,f.slots[1],original?x:-x,original?y:-y);
        boolean pc2=resources.profileId.equals("dc2pc-5769");
        actors(g,f,pc2);drawEffects(g);for(Dc2Ui.Quad q:f.quads)quad(g,q,f.slots,false);
    }
    private void nativeParticles(BufferedImage canvas,int layer)throws IOException{
        Dc2Particles value=captureState==null?host().scene.nativeParticles:captureState.particles;
        if(value==null||!value.loaded())return;Picture atlas=picture(value.atlas());if(atlas==null)return;
        int[] pixels=canvas.getRGB(0,0,width,height,null,0,width);value.paint(pixels,width,height,atlas.raw.argb(),atlas.raw.width,atlas.raw.height,atlas.raw.bpp,layer);canvas.setRGB(0,0,width,height,pixels,0,width);
    }
    private void actors(Graphics2D g,Dc2Scene.Frame f,boolean pc2)throws IOException{
        // PC2 41A950 visits actor slots 0..3, so a later close-up covers earlier actors.
        for(int order=0;order<4;order++){int n=pc2?order:3-order;int[] a=f.actors[n];for(int slot:new int[]{n+2,n+6}){String name=slot==n+6?resources.characterOverlay(f.slots[n+2],f.slots[slot]):f.slots[slot];Picture p=picture(name);if(p==null)continue;
            if(pc2){PcActorLayout r=PcActorLayout.dc2pc(a,p.raw.width,p.raw.height);g.drawImage(p.image,r.x,r.y,r.x+r.width,r.y+r.height,0,0,p.raw.width,p.raw.height,null);}
            else{AffineTransform tr=g.getTransform();g.translate(a[0]+a[3],a[1]+a[4]);g.scale(a[2]/100.0,a[2]/100.0);g.drawImage(p.image,p.raw.x-a[3],p.raw.y-a[4],null);g.setTransform(tr);}
        }}
    }
    private BufferedImage motion(Dc2Motion value)throws IOException {
        MotionRaster raster=motionRasters.get(value);
        if(raster==null){
            raster=new MotionRaster();BufferedImage initial=value.previous==null?captures.resolve(value.initial):motion(value.previous);
            raster.pixels=initial.getRGB(0,0,width,height,null,0,width);raster.image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);motionRasters.put(value,raster);
        }
        java.util.List<Dc2Motion.Draw> draws=value.draws();
        while(raster.count<draws.size()){
            Dc2Motion.Draw draw=draws.get(raster.count++);
            if(draw.kind==Dc2Motion.FULL){while(raster.count<draws.size()&&draws.get(raster.count).kind==Dc2Motion.FULL)draw=draws.get(raster.count++);if(value.prepareOnly&&value.prepared!=null)raster.pixels=captures.resolve(value.prepared).getRGB(0,0,width,height,null,0,width);else{Graphics2D g=raster.image.createGraphics();try{scene(g,value.source,draw.x,draw.y);}finally{g.dispose();}raster.pixels=raster.image.getRGB(0,0,width,height,null,0,width);}}
            else if(draw.kind==Dc2Motion.PREPARE){
                if(raster.prepared==null){BufferedImage composed;if(value.prepared!=null)composed=captures.resolve(value.prepared);else{composed=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=composed.createGraphics();try{scene(g,value.source);}finally{g.dispose();}}raster.prepared=composed.getRGB(0,0,width,height,null,0,width);}
                Dc2Motion.blit(raster.pixels,width,height,raster.prepared,width,height,0,0,draw.alpha);
            }else{if(!raster.backgroundLoaded){Picture background=picture(value.background);if(background!=null&&background.raw.bpp==24){raster.backgroundWidth=background.image.getWidth();raster.backgroundHeight=background.image.getHeight();raster.background=background.image.getRGB(0,0,raster.backgroundWidth,raster.backgroundHeight,null,0,raster.backgroundWidth);}raster.backgroundLoaded=true;}if(raster.background!=null)Dc2Motion.blit(raster.pixels,width,height,raster.background,raster.backgroundWidth,raster.backgroundHeight,draw.x,draw.y,draw.alpha);}
        }
        raster.image.setRGB(0,0,width,height,raster.pixels,0,width);return raster.image;
    }
    private void drawEffects(Graphics2D g)throws IOException{
        Dc2Scene s=host().scene;SceneCaptures.Legacy frozen=captureState;
        if(frozen==null?host().read(99,10)==0:!frozen.effects)return;
        long t=(frozen==null?s.effectClock:frozen.clock)/34;int particleKind=frozen==null?s.particleKind:frozen.particleKind,particleStrength=frozen==null?s.particleStrength:frozen.particleStrength,overlayKind=frozen==null?s.overlayKind:frozen.overlayKind;String[] slots=frozen==null?s.slots:frozen.frame.slots;
        if(overlayKind==7){Picture p=picture(slots[15]);if(p!=null){g.setComposite(AlphaComposite.SrcOver.derive(96/255f));int x=-(int)(t%p.image.getWidth());for(;x<width;x+=p.image.getWidth())g.drawImage(p.image,x,0,null);g.setComposite(AlphaComposite.SrcOver);}}
        if(particleKind==0)return;Picture p=picture(slots[particleKind==2?17:16]);if(p==null)return;int level=particleStrength&15,count=level>=5?50:new int[]{0,5,10,15,30}[level];
        for(int n=0;n<count;n++){int w=particleKind==2?8:32,h=particleKind==2?80:24,sy=particleKind==2?0:n%6*28;if(sy+h>p.image.getHeight())continue;int sx=particleKind==2?0:(int)(t/(4+n%8)%5)*w;int x=(int)((n*193+Math.sin((t*(4+n%8)+n*71)*Math.PI/180)*(24+n%24)+width)%width),y=(int)((n*137+t*(particleKind==2?32:2+n%4))%(height+64))-32;if(particleKind==9)y=height-y;g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    }
    interface Pc1GlyphPainter {void draw(Graphics2D g,byte[] raw,int x,int y,int palette,int size)throws IOException;}
    private static void pc1Rect(BufferedImage target,int left,int top,int right,int bottom,int index,int weight,int[] palette,java.util.List<String> trace){
        if(trace!=null)trace.add("r,"+left+","+top+","+right+","+bottom+","+index+","+weight);
        int l=Math.max(0,left),t=Math.max(0,top),r=Math.min(target.getWidth(),right),b=Math.min(target.getHeight(),bottom);if(l>=r||t>=b)return;int[] pixels=target.getRGB(l,t,r-l,b-t,null,0,r-l);int src=palette[index&63];
        for(int n=0;n<pixels.length;n++){int old=pixels[n],red=(((old>>>16&255)*(16-weight)+(src>>>16&255)*weight)>>>4)&255,green=(((old>>>8&255)*(16-weight)+(src>>>8&255)*weight)>>>4)&255,blue=(((old&255)*(16-weight)+(src&255)*weight)>>>4)&255;pixels[n]=0xff000000|red<<16|green<<8|blue;}
        target.setRGB(l,t,r-l,b-t,pixels,0,r-l);
    }
    private static void pc1Paint(Graphics2D g,Pc1GlyphPainter painter,byte[] raw,int x,int y,int palette,int size,java.util.List<String> trace)throws IOException{
        if(trace!=null){StringBuilder bytes=new StringBuilder();for(byte value:raw){if(value==0)break;int v=value&255;if(v<16)bytes.append('0');bytes.append(Integer.toHexString(v));}trace.add("g,"+x+","+y+","+palette+","+size+","+bytes);}
        painter.draw(g,raw.clone(),x,y,palette,size);
    }
    /** Current CN4170C0 command consumer. Glyph rasterization is a platform
     *callback; rectangles use exact native24-bit channel arithmetic. */
    static void rasterPc1Text(BufferedImage target,BufferedImage underlay,Pc1TextLayout.Snapshot journal,java.util.List<int[]> batches,java.util.List<int[]> patches,int[] palette,int shading,Pc1GlyphPainter painter,java.util.List<String> trace)throws IOException{
        if(journal==null||batches==null||patches==null||palette==null||palette.length!=64||painter==null)throw new IOException("PC1 glyph journal raster input");
        java.util.List<Pc1TextLayout.Glyph> glyphs=journal.glyphs();int patchIndex=0,last=0;Graphics2D g=target.createGraphics();try{
            for(int[] batch:batches){if(batch.length!=2||batch[0]<last||batch[1]<batch[0]||batch[1]>glyphs.size())throw new IOException("PC1 glyph batch range");last=batch[1];
                while(patchIndex<patches.size()&&patches.get(patchIndex)[0]<=batch[0]){int[] p=patches.get(patchIndex++);if(p.length!=6||p[0]!=batch[0]||p[5]!=0||underlay==null||underlay.getWidth()!=target.getWidth()||underlay.getHeight()!=target.getHeight())throw new IOException("PC1 text underlay patch owner");int l=Math.max(0,p[1]),t=Math.max(0,p[2]),r=Math.min(target.getWidth(),p[3]),b=Math.min(target.getHeight(),p[4]);if(l<r&&t<b)target.setRGB(l,t,r-l,b-t,underlay.getRGB(l,t,r-l,b-t,null,0,r-l),0,r-l);if(trace!=null)trace.add("p,"+p[1]+","+p[2]+","+p[3]+","+p[4]);}
                for(int pass=0;pass<2;pass++)for(int n=batch[0];n<batch[1];n++){
                    Pc1TextLayout.Glyph value=glyphs.get(n);if((value.fontSize==12)!=(pass==1)||value.right<=0||value.x>=target.getWidth()||value.bottom<=0||value.y>=target.getHeight())continue;
                    int x=value.x,y=value.y,size=value.fontSize;byte[] raw=value.rawCharacter();
                    if(pass==0){if((value.style&0x40)!=0){int index=(value.style>>>8)&63;if((value.style&0x4000)!=0||shading!=0){int end=value.right,weight=(value.style&0x4000)!=0?16:shading;if((value.decor&1)!=0){pc1Rect(target,x,y+3,x+1,value.bottom-3,index,weight,palette,trace);pc1Rect(target,x+1,y+1,x+2,value.bottom-1,index,weight,palette,trace);x+=2;}if((value.decor&2)!=0){end=value.right-2;pc1Rect(target,end,y+1,value.right-1,value.bottom-1,index,weight,palette,trace);pc1Rect(target,value.right-1,y+3,value.right,value.bottom-3,index,weight,palette,trace);}pc1Rect(target,x,y,end,value.bottom,index,weight,palette,trace);}y+=2;}}
                    // 417379 sign-extends byte1 before comparing to+9C, so
                    //that apparent decompiler alternative cannot be taken.
                    else if((raw[0]&255)==0x81&&(raw[1]&255)==0x45){raw=new byte[]{0x3f,0x3f,0,0};x-=8;y-=5;size=28;}
                    if((value.style&0x80)!=0)pc1Paint(g,painter,raw,x+1,y+1,0,size,trace);pc1Paint(g,painter,raw,x,y,value.style&63,size,trace);
                }
            }
            if(patchIndex!=patches.size())throw new IOException("PC1 unbound text patch");
        }finally{g.dispose();}
    }
    private void paintPc1Glyph(Graphics2D g,byte[] raw,int x,int y,int index,int size)throws IOException{
        paintPc1Glyph(g,raw,x,y,index,size,codec,host().ui.palette);
    }
    static void paintPc1Glyph(Graphics2D g,byte[] raw,int x,int y,int index,int size,TextCodec codec,int[] palette)throws IOException{
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));g.setColor(new Color(palette[index&63],true));int length=0;while(length<raw.length&&raw[length]!=0)length++;
        for(int p=0;p<length;){int first=raw[p]&255,bytes=first>=0x81&&first<=0xfe?2:1;if(p+bytes>length)throw new IOException("PC1 glyph has an unresolved native font byte sequence");String value=codec.decode(Arrays.copyOfRange(raw,p,p+bytes));g.drawString(value,x,y+size);x+=bytes==2?size:size/2;p+=bytes;}
    }
    private void drawPc1Text(BufferedImage target,Pc1Runtime runtime)throws IOException{
        Pc1TextLayout.Snapshot journal=runtime.textLayout();if(journal==null)throw new IOException("PC1 visible message has no native glyph journal");java.util.List<int[]> patches=runtime.textPatches();BufferedImage underlay=null;
        try{if(!patches.isEmpty()){underlay=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);underlay.setRGB(0,0,width,height,target.getRGB(0,0,width,height,null,0,width),0,width);}rasterPc1Text(target,underlay,journal,runtime.glyphBatches(),patches,host().ui.palette,host().read(99,16),this::paintPc1Glyph,null);}finally{if(underlay!=null)underlay.flush();}
    }
    public BufferedImage render()throws IOException {
        if(pc1()!=null){UiCapture.Pixels front=pc1().returnPixels();if(front!=null){if(front.width!=width||front.height!=height)throw new IOException("PC1 return framebuffer canvas mismatch");BufferedImage copy=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);copy.setRGB(0,0,width,height,front.argb,0,width);return copy;}}
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=result.createGraphics();try{
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);Dc2Host h=host();
            boolean mapBackdrop=h instanceof PcHost&&((PcHost)h).dc2Map.backdrop;
            if(h instanceof PcHost&&(((PcHost)h).dc2Map.active||mapBackdrop)){drawDc2Map(g,(PcHost)h);if(((PcHost)h).dc2Map.active)return result;}
            else{if(h instanceof PcHost&&((PcHost)h).frozenOriginalBatch()!=null){g.drawImage(captures.resolve(((PcHost)h).frozenOriginalBatch()),0,0,null);return result;}if(h instanceof PcHost&&((PcHost)h).map.active){drawMap(g,(PcHost)h);return result;}if(h.scene.nativeMotion!=null)g.drawImage(motion(h.scene.nativeMotion),0,0,null);else scene(g,h.scene.shown);if(h instanceof PcHost&&((PcHost)h).sceneCommandDeferred())return result;drawTransition(g);}
            if(h instanceof PcHost&&((PcHost)h).pcCredits.active){PcCredits c=((PcHost)h).pcCredits;int first=(int)c.scroll()/c.stripHeight();g.setColor(Color.BLACK);g.fillRect(0,0,width,height);for(int n=first;n<=Math.min(c.lastStrip(),first+1);n++)at(g,c.image(n),0,(int)(n*c.stripHeight()-c.scroll()));return result;}if(h.credits.active()){drawCredits(g);return result;}
            if(!mapBackdrop&&!h.body.isEmpty()&&!hidden){
                boolean nativePc1=h instanceof PcHost&&((PcHost)h).pc1!=null;
                Dc2Ui.Quad body=null,name=null;for(Dc2Ui.Quad q:h.ui.quadTemplates.get(h.messageMode()==0?1:4)){if(!nativePc1||q.type!=2)quad(g,q,h.scene.slots,false);if(visibleQuad(q)&&q.type==2&&q.width>2)body=q;}
                if(!h.speaker.isEmpty())for(Dc2Ui.Quad q:h.ui.quadTemplates.get(h.messageMode()==0?2:5)){if(!nativePc1||q.type!=2)quad(g,q,h.scene.slots,false);if(visibleQuad(q)&&q.type==2&&q.width>2)name=q;}
                if(nativePc1){for(int n=18;n<=19;n++)at(g,h.scene.shown.slots[n],h.scene.portraitX,h.scene.portraitY);drawPc1Text(result,((PcHost)h).pc1);}
                else{if(name!=null)text(g,h.speaker,name.x,name.y,name.width,name.height,700);if(body!=null)text(g,visibleBody(),body.x,body.y,body.width,body.height,width-40-body.x);for(int n=18;n<=19;n++)at(g,h.scene.shown.slots[n],h.scene.portraitX,h.scene.portraitY);}
            }
            if(h instanceof PcHost&&((PcHost)h).nameInput.active)drawName(g,((PcHost)h).nameInput);
            java.util.List<Dc2Ui.Button> bs=buttons();int buttonHover=pc1()!=null?h.ui.hovered:hovered,buttonPress=pc1()!=null?h.ui.pressed:pressedButton;for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);boolean slider=false;for(Dc2Ui.Quad q:b.sprites)if(q.state==5)slider=true;for(Dc2Ui.Quad q:b.sprites)if(q.state==0||q.state==99||(q.state==1&&n==buttonHover)||(q.state==2&&!slider&&(b.action.equals(":c")?h.confirmationChecked():n==buttonPress)))quad(g,q,h.scene.slots,true);}
            for(Dc2Ui.Button b:bs)drawSlider(g,b);
            if(h.gridWaiting)drawGrid(g);
            if(h.history.active&&!(h instanceof PcHost))drawHistory(g);
            if(h.musicRoom.active)drawMusicRoom(g);
        }finally{g.dispose();}return result;
    }
    private void drawName(Graphics2D g,PcNameInput name)throws IOException{
        for(int n=0;n<14;n++){if(n>=10&&name.pages()==1)continue;int[] r=name.rectangle(n),a=name.atlas(r[4]);chip(g,10,a[0],a[1],r[2]-r[0],r[3]-r[1],r[0],r[1]);}
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,32));g.setColor(Color.WHITE);g.drawString(name.family,336,72);g.drawString(name.given,528,72);
        g.setColor(new Color(255,255,255,180));g.drawRect(336+name.field*192+name.cursor*32,40,31,33);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,22));java.util.List<String> rows=name.visibleRows();for(int r=0;r<rows.size();r++)for(int c=0;c<Math.min(17,rows.get(r).length());c++){
            String ch=rows.get(r).substring(c,c+1);int x=280+24*c,y=96+24*r+(r>=4?8:0);g.setColor(new Color(0,70,110));g.drawString(ch,x+2,y+24);g.setColor(Color.WHITE);g.drawString(ch,x,y+22);
        }
        int[] a=name.atlas(name.page+41);chip(g,10,a[0],a[1],16,32,520,288);chip(g,10,160,304,16,32,536,288);a=name.atlas(name.pages()+30);chip(g,10,a[0],a[1],16,24,552,296);
    }
    public boolean editingText(){return !(pc1()!=null&&pc1().returning())&&host() instanceof PcHost&&((PcHost)host()).nameInput.active;}
    public void typeText(String text){if(editingText())((PcHost)host()).nameInput.insert(text);}
    public void editKey(int code)throws IOException{if(!editingText())return;PcHost p=(PcHost)host();if(code==KeyEvent.VK_BACK_SPACE||code==KeyEvent.VK_DELETE)p.nameAction("@name:button:4");else if(code==KeyEvent.VK_LEFT)p.nameAction("@name:button:6");else if(code==KeyEvent.VK_RIGHT)p.nameAction("@name:button:5");else if(code==KeyEvent.VK_ENTER)p.nameAction("@name:button:7");else if(code==KeyEvent.VK_ESCAPE)p.nameAction("@name:cancel");}
    private void mapChip(Graphics2D g,String name,int x,int y,int sx,int sy,int w,int h)throws IOException{Picture p=picture(name);if(p!=null)g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    private void drawDc2Map(Graphics2D g,PcHost h)throws IOException{
        for(Dc2Map.Sprite sprite:h.dc2Map.sprites(vm().variables)){
            Picture p=picture(sprite.resource);if(p==null)continue;BufferedImage image=p.image;
            if(sprite.opaque){if(p.opaque==null){images.reserve(p.residentBytes()+8L*p.raw.width*p.raw.height);int[] pixels=p.raw.argb();for(int n=0;n<pixels.length;n++)pixels[n]|=0xff000000;p.opaque=new BufferedImage(p.raw.width,p.raw.height,BufferedImage.TYPE_INT_ARGB);p.opaque.setRGB(0,0,p.raw.width,p.raw.height,pixels,0,p.raw.width);images.put(sprite.resource,p);}image=p.opaque;}
            g.drawImage(image,sprite.x,sprite.y,sprite.x+sprite.width,sprite.y+sprite.height,sprite.sx,sprite.sy,sprite.sx+sprite.width,sprite.sy+sprite.height,null);
        }
    }
    private void drawMap(Graphics2D g,PcHost h)throws IOException{
        Pc2Map m=h.map;at(g,m.background(),0,0);int hover=m.selected>=0?m.selected:m.hovered;
        for(int n=0;n<10;n++){if(session.vm.variables.read(5,n)==0)continue;int location=session.vm.variables.read(5,n+20);if(location<0||location>=7)throw new IOException("Map actor location "+location);int[] r=(m.school?Pc2Map.SCHOOL_ACTOR:Pc2Map.ISLAND_ACTOR)[location];if(r[2]==r[0])continue;
            int shift=session.vm.variables.read(5,50)==1?-120:m.school?55:10,dy=0;if(m.school&&location==0){shift-=50;dy=100;}for(int later=n+1;later<10;later++)if(session.vm.variables.read(5,later+20)==location){shift+=120;break;}
            int[] periods=Pc2Map.DURATIONS[n];long total=0;for(int t:periods)total+=t;long t=m.elapsed%total;int frame=0;while(frame<periods.length-1&&t>periods[frame])t-=periods[frame++];
            mapChip(g,"MAP/chip_"+Pc2Map.ACTORS[n],r[0]+shift,r[1]+dy,frame%5*168,frame/5*152+(location==hover?760:0),168,152);
        }
        for(int[] r:m.regions())if(r[2]>r[0])mapChip(g,"MAP/movestop",r[0],r[1],816,0,24,24);
        if(hover>=0){mapChip(g,m.chip(),136,524,0,hover*56,424,56);int character=-1;for(int n=0;n<10;n++)if(session.vm.variables.read(5,20+n)==hover)character=n;
            if(character>=0&&session.vm.variables.read(5,character)!=0)mapChip(g,"MAP/movestop",0,440,character%6*136,(m.school?256:160)+(character>=6?320:0),136,160);
        }
        if(m.selected>=0){int k=0;for(Dc2Ui.Button b:h.ui.buttons){mapChip(g,m.chip(),b.left,b.top,k*96,hovered==k?464:393,96,72);k++;}}
    }
    private void drawTransition(Graphics2D g)throws IOException{
        Dc2Scene s=host().scene;if(s.transitionRemaining<=0||s.transitionDuration<=0)return;
        if(s.transitionKind==221||s.transitionKind==222){float p=s.transitionRemaining/(float)s.transitionDuration;g.setColor(new Color(0f,0f,0f,s.transitionKind==221?p:1-p));g.fillRect(0,0,width,height);return;}
        BufferedImage old=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D prev=old.createGraphics();scene(prev,s.previous);prev.dispose();
        if(s.transitionKind>=130&&s.transitionKind<=132){int variant=s.transitionKind-130,ticks=(int)((s.transitionDuration-s.transitionRemaining)/40),gain=0;for(int n=2;n<=ticks;n++)gain+=Math.min(16,n);boolean second=((PcHost)host()).second;
            int[] pixels=old.getRGB(0,0,width,height,null,0,width);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int v=variant==0?0:(second?8:0)-x/4-(variant==2?y/4:0);int alpha=255-255*Math.max(0,Math.min(16,v+gain))/16;int i=y*width+x;pixels[i]=(pixels[i]&0xffffff)|(alpha<<24);}old.setRGB(0,0,width,height,pixels,0,width);
        }else g.setComposite(AlphaComposite.SrcOver.derive(s.transitionRemaining/(float)s.transitionDuration));
        g.drawImage(old,0,0,null);g.setComposite(AlphaComposite.SrcOver);
    }
    private void drawSlider(Graphics2D g,Dc2Ui.Button b)throws IOException{
        Dc2Ui.Quad cfg=null,stops=null;for(Dc2Ui.Quad q:b.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)return;
        int index=host().ui.buttons.indexOf(b),atlasState=index==pressedButton?26:index==hovered?25:24;boolean hasAtlas=false;for(Dc2Ui.Quad q:b.sprites)if(q.state==atlasState)hasAtlas=true;if(!hasAtlas)atlasState=24;
        int value=Math.max(0,Math.min(cfg.sx-1,vm().variables.read(cfg.sy,cfg.width))),dx=(cfg.y-cfg.x)*value/Math.max(1,cfg.sx-1),left=cfg.x,right=cfg.x+dx;
        if(stops!=null){int[] ends={cfg.x,stops.x,stops.y,stops.sx,stops.sy};left=ends[Math.min(3,value)];right=ends[Math.min(3,value)+1];}
        for(Dc2Ui.Quad q:b.sprites){Shape clip=g.getClip();AffineTransform tr=g.getTransform();if(q.state==2||q.state==12){g.clipRect(left,0,Math.max(0,right-left),height);quad(g,q,host().scene.slots,true);}else if(q.state==6){g.translate(dx,0);quad(g,q,host().scene.slots,true);}else if(q.state==atlasState){Picture p=picture(host().scene.slots[q.slot()]);if(p!=null){int w=q.width/Math.max(1,cfg.sx),sx=q.sx+w*value;g.drawImage(p.image,q.x,q.y,q.x+w,q.y+q.height,sx,q.sy,sx+w,q.sy+q.height,null);}}g.setClip(clip);g.setTransform(tr);}
    }
    private void drawCredits(Graphics2D g)throws IOException{Dc2Credits r=host().credits;g.setColor(Color.BLACK);g.fillRect(0,0,width,height);Shape old=g.getClip();g.clipRect(80,160,400,300);at(g,r.image(true,r.picture()),80,160);g.setClip(old);at(g,"ED/photo",80,160);int y=-(int)(r.scroll()%600);at(g,r.image(false,r.strip()),480,y);if(r.strip()+1<r.strips)at(g,r.image(false,r.strip()+1),480,y+600);}
    private void drawGrid(Graphics2D g)throws IOException {
        if(host() instanceof PcHost){drawPcGrid(g,(PcHost)host());return;}
        Picture p=picture(host().scene.slots[10]);if(p==null)return;int page=host().read(99,0);
        for(int n=0;n<host().grid.size();n++){Dc2Ui.Button b=host().grid.get(n);int slot=1+page*10+n,sx=slot==1?1008:0,sy=slot==3?84:slot<=2?0:168;
            g.drawImage(p.image,b.left,b.top,b.right,b.bottom,sx,sy,Math.min(sx+336,p.image.getWidth()),sy+84,null);
            if(slot>3){int number=slot-3;for(int k=0;k<2;k++){int d=k==0?number/10:number%10,x=b.left+52+k*16;g.drawImage(p.image,x,b.top+28,x+16,b.top+52,336+d*16,508,352+d*16,532,null);}}
            String label=host().saveLabels.get(slot);if(label!=null)text(g,label,b.left+120,b.top+24,22,13,b.right-b.left-136);
        }
    }
    private void drawPcGrid(Graphics2D g,PcHost h)throws IOException{
        if(h.list.active()){int offset=h.list.clamp(h.listOffset(),h.grid.size());for(int n=0;n<h.grid.size();n++){ScriptList.Item item=h.list.at(offset,n,h.grid.size());if(item==null)continue;Dc2Ui.Button b=h.grid.get(n);java.awt.geom.AffineTransform old=g.getTransform();g.translate(b.left,b.top);boolean selected=hovered==h.ui.buttons.size()+n,sceneList=h.second&&h.list.textStyle==4;String name=sceneList?h.sceneListName(item):"";
        if(sceneList&&PcHost.sceneListSpecial(name))chip(g,12,232,selected?56:0,232,56,0,0);
        for(Dc2Ui.Quad q:ScriptList.visible(item.sprites,selected?1:99)){
            if(q.type!=2){quad(g,q,h.scene.slots,true);continue;}
            // 41169A..41177D: -1 Y hides a caption; -1 X centers it. Text is
            // clipped to the cell, independently of the atlas rectangle.
            if(q.y==-1)continue;Shape clip=g.getClip();g.clipRect(0,0,b.right-b.left-(sceneList?12:0),b.bottom-b.top);int x=q.x==-1?(b.right-b.left-q.width)/2:q.x;
            if(sceneList){String date=PcHost.sceneListDate(name);pcSceneText(g,codec.apply(q.text),q.width/14,x,q.y,b.right-b.left-x);pcSceneText(g,date,date.getBytes(Binary.SJIS).length,x,6,b.right-b.left-x);}else text(g,codec.apply(q.text),x,q.y,q.height,q.flags,b.right-b.left-x);g.setClip(clip);
        }g.setTransform(old);}return;}
        Picture p=picture(h.scene.slots[10]);if(p==null)return;
        for(int n=0;n<h.grid.size();n++){Dc2Ui.Button b=h.grid.get(n);int slot=h.gridSlot(n),sx=hovered==h.ui.buttons.size()+n?288:0;
            int sy=h.second?(slot==0?0:slot==1?(h.saveGrid?72:216):144):(h.saveGrid?0:72);
            if(!h.second&&slot<2)sx=0;
            g.drawImage(p.image,b.left,b.top,b.right,b.bottom,sx,sy,sx+288,sy+72,null);
            DesktopSave meta=metadata(slot(slot));if(meta!=null&&slot>=2&&meta.icon>=0&&meta.icon<16){chip(g,11,meta.icon*56,312,56,56,b.left+8,b.top+8);}
            String label=h.saveLabels.get(slot);if(label!=null)roomText(g,label,b.left+(h.second?72:104),b.top+(h.second?50:42),180,16,0xffffffff);
            if(meta!=null&&!meta.caption.isEmpty()){Shape clip=g.getClip();g.clipRect(b.left+64,b.top,224,42);roomText(g,meta.caption,b.left+(h.second?72:64),b.top+(h.second?10:6),216,18,0xffffffff);g.setClip(clip);}
        }
    }
    /** 407DD6..40814E uses palette 7 for an eight-neighbour outline and palette 14 for the text. */
    private void pcSceneText(Graphics2D g,String value,int rawLength,int x,int y,int width)throws IOException{int size=host().variables.read(3,30)!=0?(rawLength>20?14:16):host().variables.read(3,31)!=0?16:28;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dy!=0)roomText(g,value,x+dx,y+dy,width,size,color(7));roomText(g,value,x,y,width,size,color(14));}
    private void chip(Graphics2D g,int slot,int sx,int sy,int w,int h,int x,int y)throws IOException{Picture p=picture(host().scene.slots[slot]);if(p!=null)g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    private void roomText(Graphics2D g,String value,int x,int y,int w,int size,int color){g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));g.setColor(new Color(color,true));int at=x,base=y+size;for(char ch:value.toCharArray()){int cw=g.getFontMetrics().charWidth(ch);if(ch=='\n'||at+cw>x+w){at=x;base+=size+4;if(ch=='\n')continue;}g.drawString(String.valueOf(ch),at,base);at+=cw;}}
    private void drawHistory(Graphics2D g)throws IOException{
        Dc2History h=host().history;for(int row=0;row<4&&row+h.cursor<h.entries.size();row++){int index=row+h.cursor,y=25+row*133;Dc2History.Entry e=h.entries.get(index);chip(g,10,228,0,680,124,200,y-4);Shape clip=g.getClip();g.clipRect(200,y-4,680,124);
            Integer face=nativeData==null?null:nativeData.faces.get(e.speaker);if(face!=null)chip(g,10,face==24?864:168+(face%6)*116,face==24?480:132+(face/6)*116,116,116,240,y);else roomText(g,e.speaker,212,y+12,135,20,0xff9a386d);
            roomText(g,e.body,360,y+5,500,22,index==h.selected?0xff99325b:0xff663b63);if(!e.voice.isEmpty())chip(g,10,84,120,24,24,332,y+6);g.setClip(clip);
        }chip(g,10,988,0,32,332,948,112);chip(g,10,908,0,40,24,944,88);chip(g,10,948,0,40,24,944,444);chip(g,10,908,72,32,32,948,112+(int)(300L*h.cursor/Math.max(1,h.entries.size()-4)));
        if(h.selected>=0&&h.selected<h.entries.size()){byte[] bytes=h.entries.get(h.selected).scene;if(bytes.length>0){Dc2Scene.Frame f=new Dc2Scene.Frame();f.load(new DataInputStream(new ByteArrayInputStream(bytes)));AffineTransform old=g.getTransform();Shape clip=g.getClip();g.clipRect(28,184,160,90);g.translate(28,184);g.scale(160.0/width,90.0/height);scene(g,f);g.setTransform(old);g.setClip(clip);}}
    }
    private void drawMusicRoom(Graphics2D g)throws IOException{Dc2MusicRoom r=host().musicRoom;roomText(g,String.format(Locale.ROOT,"%03d  %s",r.selected+1,r.data.musicTitles.get(r.selected)),42,201,320,19,0xffa73563);for(int row=0;row<10&&row+r.top<r.data.musicTitles.size();row++){int index=row+r.top,y=78+row*41;boolean selected=index==r.selected;chip(g,10,491,selected?159:77,59,37,435,y);roomText(g,String.format(Locale.ROOT,"%03d",index+1),450,y+7,47,18,selected?0xffcc416e:0xff95536b);roomText(g,r.data.musicTitles.get(index),504,y+6,440,20,0xffa73563);}chip(g,10,436,88,10,388,960,88);chip(g,10,392,32,27,32,951,88+350*r.top/63);}
}
