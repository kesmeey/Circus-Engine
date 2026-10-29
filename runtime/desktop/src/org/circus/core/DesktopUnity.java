package org.circus.core;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Shared desktop/Android presentation for original CircusScr scripts and UI atlases. */
public final class DesktopUnity extends DesktopEngine {
    public final Dc3rHost host=new Dc3rHost();
    public final CircusScrVm script;
    public final UnityCircusResources data;
    private long textClock,lastTicket=-1;private int transitionRevision=-1;private long transitionClock;
    private BufferedImage lastScene,transitionFrom;private String movieReference="";private int resolvedMovie=-1;private Object executionToken=new Object();
    private byte[] maskLevels;private int[] maskPixels;private String appliedMask="";
    private final float[] breakVx=new float[576],breakVy=new float[576];
    private final Map<String,byte[]> captured=new LinkedHashMap<>();private int captureSerial;private static final int CAPTURE_BUDGET=8*1024*1024;
    private byte[] storyState;private long storyTicket=-1;private BufferedImage storyPreview;private String storyCaption="";private final String[] storyAudio={"","","","","",""};private int savedAutoGeneration;
    private long pictureBytes;private static final long PICTURE_BUDGET=48L*1024*1024;
    private final Map<String,BufferedImage> pictures=new LinkedHashMap<String,BufferedImage>(32,.75f,true);
    private final boolean diskSaves;
    public DesktopUnity(UnityCircusResources r,boolean audit)throws IOException{
        this(r,audit,Paths.get(System.getProperty("dc.saveRoot",audit?"build/desktop/audit-saves":"build/desktop/saves")),true);
    }
    private DesktopUnity(UnityCircusResources r,boolean audit,Path root,boolean diskSaves)throws IOException{
        super(r,TextCodec.identity(),audit,1024,576,r.identity(),root);data=r;this.diskSaves=diskSaves;
        host.configureCatalogues(new String(r.script("cglist"),java.nio.charset.StandardCharsets.UTF_8),new String(r.script("scenelist"),java.nio.charset.StandardCharsets.UTF_8));
        host.textAssets=name->new String(r.script(name),java.nio.charset.StandardCharsets.UTF_8);host.capture=this::capture;
        Map<String,CircusScrScript> cache=new LinkedHashMap<String,CircusScrScript>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,CircusScrScript> e){return size()>24;}};
        script=new CircusScrVm(name->{CircusScrScript s=cache.get(name);if(s==null){s=new CircusScrScript(name,r.script(name));cache.put(name,s);}return s;},host,"start");
        Path progress=saveDirectory.resolve("progress.dat");if(diskSaves&&!audit&&Files.isRegularFile(progress))try(DataInputStream in=new DataInputStream(Files.newInputStream(progress))){host.loadPersistent(in);}
        scanSaves();
    }
    /** Legacy callers may request a MES VM only for a MES backend. */
    public MesVm vm(){throw new UnsupportedOperationException("CircusScr has an independent VM");}
    public Object executionIdentity(){return executionToken;}
    public String scriptName(){return script.scriptName();}
    public int scriptOffset(){return script.offset();}
    public boolean halted(){return script.status()==CircusScrVm.Status.HALTED;}
    public boolean waiting(){return script.status()!=CircusScrVm.Status.RUNNING;}
    protected int saveIcon(){return host.readFlag((9<<16)+20);}
    public void tick(long elapsed)throws Exception{
        script.elapse(elapsed);host.elapse(elapsed,script);transitionClock+=elapsed;
        if(lastTicket!=ticket()){lastTicket=ticket();textClock=0;}else textClock+=elapsed;
        if(textWaiting()&&(rapid||auto&&textClock>Math.max(1200,body().length()*50)&&!audioActive(5)))host.advance(script);
        if(script.run(10000)==CircusScrVm.Status.FAULT)throw new IOException(script.error());
        if(!host.movie.isEmpty()&&resolvedMovie!=host.movieGeneration){movieReference=data.movie(host.movie);resolvedMovie=host.movieGeneration;}
        if(host.transitionRevision!=transitionRevision){transitionRevision=host.transitionRevision;transitionClock=0;maskLevels=null;appliedMask="";if(transitionFrom!=null)transitionFrom.flush();transitionFrom=lastScene;lastScene=null;Random random=new Random(0x554e4954L+transitionRevision);for(int i=0;i<576;i++){breakVx[i]=random.nextFloat()*300-150;breakVy[i]=random.nextFloat()*75-50;}}
        fullscreenRequested=host.fullscreen;
        if(script.callDepth()==0&&(textWaiting()||host.choiceWaiting)&&storyTicket!=ticket()){storyTicket=ticket();storyState=snapshot();storyCaption=body();for(int ch=0;ch<6;ch++)storyAudio[ch]=audio(ch);if(storyPreview!=null){storyPreview.flush();storyPreview=null;}}
        if(host.saveRequest>=0){int slot=host.saveRequest;host.saveRequest=-1;saveStory(saveDirectory.resolve("slot-"+slot+".sav"));script.resume();persist();}
        if(host.loadRequest>=0){int slot=host.loadRequest;host.loadRequest=-1;restore(saveDirectory.resolve("slot-"+slot+".sav"));}
        if(host.persistRequested){host.persistRequested=false;persist();}
        if(storyState!=null&&textWaiting()&&host.autosaveGeneration!=savedAutoGeneration){savedAutoGeneration=host.autosaveGeneration;if(!audit)saveStory(saveDirectory.resolve("slot-101.sav"));}
    }
    private void captureStoryPreview()throws IOException{if(storyState!=null&&storyPreview==null&&script.callDepth()==0)storyPreview=scene();}
    private void saveStory(Path file)throws IOException{if(storyState==null)throw new IOException("No stable story state to save");DesktopSave saved=new DesktopSave();saved.state=storyState;saved.caption=storyCaption;saved.created=System.currentTimeMillis();saved.textElapsed=textClock;saved.icon=saveIcon();if(storyPreview==null&&script.callDepth()==0)storyPreview=scene();if(storyPreview!=null)saved.preview=DesktopSave.thumbnail(storyPreview,256,144);for(int ch=0;ch<6;ch++)saved.audio[ch]=storyAudio[ch];atomic(file,saved.encode());scanSaves();}
    private void scanSaves()throws IOException{host.availableSaves.clear();if(!diskSaves)return;for(int slot=0;slot<=101;slot++){Path file=saveDirectory.resolve("slot-"+slot+".sav");if(!Files.isRegularFile(file))continue;DesktopSave info=DesktopSave.decode(Files.readAllBytes(file));host.availableSaves.put(slot,new Dc3rHost.SaveInfo(new java.text.SimpleDateFormat("yyyy MM/dd HH:mm",Locale.ROOT).format(new Date(info.created)),info.caption));}}

    /** Read-only native save conversion. The caller owns destination paths and atomic writes. */
    public static DesktopSave convertNative(UnityCircusResources data,byte[] nativeSave,byte[] persistent)throws IOException{
        Dc3rNativeProgress.validateTarget(data);
        Dc3rNativeSlot slot=new Dc3rNativeSlot(nativeSave);
        DesktopUnity engine=new DesktopUnity(data,true,Paths.get("."),false);
        try{
            if(persistent!=null&&persistent.length>0)engine.importPersistent(persistent);
            // Native save files omit the message-window and menu definitions installed by START.
            // Run only original startup, without rendering, decoding audio, or executing a story.
            for(int n=0;n<2000&&!engine.host.menuWaiting;n++){
                engine.script.elapse(1000);engine.host.elapse(1000,engine.script);
                if(engine.script.run(10000)==CircusScrVm.Status.FAULT)throw new IOException(engine.script.error());
                if(!engine.host.movie.isEmpty())engine.host.mediaCompleted(engine.script);
                for(int ch=0;ch<6;ch++)if(engine.host.audioActive[ch]&&!engine.host.audioLoop[ch])engine.host.audioCompleted(ch,engine.host.audioGeneration[ch]);
                if(engine.script.instructionsExecuted()>2000000)throw new IOException("Native import startup instruction limit");
            }
            if(!engine.host.menuWaiting||!engine.scriptName().equals("main")||!engine.host.putDefs.containsKey(1))throw new IOException("Native import could not initialize original START UI");
            Dc3rHost clean=new Dc3rHost();clean.colors.putAll(engine.host.colors);
            // Keep START's system initialization; native loading replaces only local memory.
            for(Map.Entry<Integer,Integer> e:engine.host.flags.entrySet())if(e.getKey()>=655360)clean.flags.put(e.getKey(),e.getValue());
            for(Map.Entry<Integer,Dc3rHost.Panel> e:engine.host.putDefs.entrySet())clean.putDefs.put(e.getKey(),e.getValue().copy());
            for(Map.Entry<Integer,Dc3rHost.Panel> e:engine.host.menuDefs.entrySet())clean.menuDefs.put(e.getKey(),e.getValue().copy());
            ByteArrayOutputStream reset=new ByteArrayOutputStream();clean.save(new DataOutputStream(reset));
            engine.host.flags.clear();engine.host.viewedCg.clear();engine.host.viewedScripts.clear();
            engine.host.load(new DataInputStream(new ByteArrayInputStream(reset.toByteArray())));
            if(persistent!=null&&persistent.length>0)engine.importPersistent(persistent);
            slot.apply(engine);
            DesktopSave saved=new DesktopSave();saved.caption=slot.caption;saved.created=slot.created;saved.icon=engine.saveIcon();saved.state=engine.snapshot();
            BufferedImage preview=engine.preview();try{saved.preview=DesktopSave.thumbnail(preview,256,144);}finally{preview.flush();}
            for(int ch=0;ch<6;ch++)saved.audio[ch]=engine.audio(ch);
            if(saved.encode().length>16*1024*1024)throw new IOException("Converted native save exceeds 16 MiB");
            return saved;
        }finally{engine.releaseImages();}
    }
    private void importPersistent(byte[] bytes)throws IOException{if(bytes.length>16*1024*1024)throw new IOException("Persistent import size limit");DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));host.loadPersistent(in);if(in.available()!=0)throw new IOException("Trailing persistent state");}
    private void releaseImages(){for(BufferedImage p:pictures.values())p.flush();pictures.clear();pictureBytes=0;if(lastScene!=null)lastScene.flush();if(transitionFrom!=null)transitionFrom.flush();if(storyPreview!=null)storyPreview.flush();for(BufferedImage p:thumbnails.values())p.flush();thumbnails.clear();captured.clear();}
    private void platformAction()throws IOException{String action=host.platformAction;host.platformAction="";switch(action){case "auto":auto=!auto;rapid=false;break;case "rapid":rapid=!rapid;auto=false;break;case "clear":hidden=true;break;case "qs":saveStory(saveDirectory.resolve("quick.sav"));break;case "ql":Path file=saveDirectory.resolve("quick.sav");if(Files.isRegularFile(file))restore(file);break;case "":break;default:throw new IOException("Unknown Unity platform action "+action);}}
    public void click(int x,int y,boolean right)throws IOException{if(hidden){hidden=false;return;}if(right){captureStoryPreview();host.cancel(script);return;}int button=host.hit(x,y);if(button>=0){captureStoryPreview();host.choose(button,script);platformAction();return;}if(script.status()==CircusScrVm.Status.WAIT_CLICK)host.advance(script);}
    public void hover(int x,int y){host.hover(x,y);}
    public boolean drag(int x,int y,boolean begin)throws IOException{return host.drag(x,y,begin,script);}
    public void endDrag()throws IOException{host.endDrag(script);}
    protected void cancelDrag(){host.cancelDrag();}
    protected void pressed(int x,int y,boolean down){host.pressed=down?host.hit(x,y):-1;}
    public List<Control> controls(){List<Control> result=new ArrayList<>();for(Dc3rHost.Button b:host.buttons())if(!b.action.isEmpty())result.add(new Control(b.action,b.left,b.top,b.right,b.bottom));return result;}
    private BufferedImage picture(String resource)throws IOException{return picture(resource,0);}
    private BufferedImage picture(String resource,int tint)throws IOException{if(resource==null||resource.isEmpty())return null;String key=resource+"#tint="+tint;BufferedImage p=pictures.get(key);if(p==null){if(resource.startsWith("@capture/")){byte[] bytes=captured.get(resource);if(bytes==null)throw new IOException("Missing captured scene "+resource);p=ImageIO.read(new ByteArrayInputStream(bytes));trimPictures(p.getWidth()*(long)p.getHeight()*4);pictures.put(key,p);pictureBytes+=p.getWidth()*(long)p.getHeight()*4;return p;}UnityResources.Texture texture=data.texture(resource);long bytes=texture.width*(long)texture.height*4;
        // Reserve the raw BGRA, ARGB conversion and destination bitmap before allocation.
        trimPictures(bytes);Crx.Image raw=texture.image();int[] colors=raw.argb();if(tint==1||tint==2)for(int i=0;i<colors.length;i++){int c=colors[i],r0=c>>>16&255,g0=c>>>8&255,b0=c&255;if(tint==1){g0=(int)(g0*.89f);b0=(int)(b0*.815f);}else{r0=(int)(r0*.8f);g0=(int)(g0*.8f);}colors[i]=(c&0xff000000)|r0<<16|g0<<8|b0;}p=new BufferedImage(raw.width,raw.height,BufferedImage.TYPE_INT_ARGB);p.setRGB(0,0,raw.width,raw.height,colors,0,raw.width);pictures.put(key,p);pictureBytes+=bytes;loadedImages.add(resource);}return p;}
    private void trimPictures(long bytes){Iterator<BufferedImage> it=pictures.values().iterator();long room=Math.max(0,PICTURE_BUDGET-bytes*3);while(pictureBytes>room&&it.hasNext()){BufferedImage old=it.next();pictureBytes-=old.getWidth()*(long)old.getHeight()*4;it.remove();old.flush();}}
    private long captureBytes(){long bytes=0;for(byte[] png:captured.values())bytes+=png.length;return bytes;}
    private void makeCaptureRoom(int additional)throws IOException{pruneCaptures();if(captured.size()>=16||captureBytes()+additional>CAPTURE_BUDGET){for(String old:new ArrayList<>(captured.keySet())){host.forgetHistoryCapture(old);pruneCaptures();if(captured.size()<16&&captureBytes()+additional<=CAPTURE_BUDGET)break;}}if(captured.size()>=16||captureBytes()+additional>CAPTURE_BUDGET)throw new IOException("Active captured scenes exceed memory limit");}
    private String capture()throws IOException{BufferedImage image=render();ByteArrayOutputStream out=new ByteArrayOutputStream();try{ImageIO.write(image,"png",out);}finally{image.flush();}byte[] png=out.toByteArray();if(png.length>4*1024*1024)throw new IOException("Captured scene exceeds image limit");makeCaptureRoom(png.length);String name="@capture/"+(++captureSerial);captured.put(name,png);return name;}
    private void pruneCaptures()throws IOException{if(captured.isEmpty())return;ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.save(new DataOutputStream(bytes));String refs=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1);captured.keySet().removeIf(name->!refs.contains(""+(char)(name.length()>>>8)+(char)(name.length()&255)+name));}
    private void quad(Graphics2D g,Dc3rHost.Quad input)throws IOException{
        Dc3rHost.Quad q=host.animated(input);String resource=q.resource.isEmpty()?host.works.get(q.work):q.resource;
        if(resource==null)throw new IOException("Undefined Unity work texture "+q.work);BufferedImage p=picture(resource);if(p==null)return;
        AffineTransform transform=g.getTransform();Composite composite=g.getComposite();g.translate(q.x,q.y);g.scale(q.scaleX,q.scaleY);g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,Math.min(1,q.alpha))));
        try{if(q.scrollU!=0){int sx=Math.floorMod(q.sx+(int)(p.getWidth()*q.scrollU),p.getWidth()),left=q.width,x=0;while(left>0){int n=Math.min(left,p.getWidth()-sx);g.drawImage(p,x,0,x+n,q.height,sx,q.sy,sx+n,q.sy+q.height,null);left-=n;x+=n;sx=0;}}
            else g.drawImage(p,0,0,q.width,q.height,q.sx,q.sy,q.sx+q.width,q.sy+q.height,null);
        }finally{g.setTransform(transform);g.setComposite(composite);}
    }
    private void text(Graphics2D g,Dc3rHost.Text t,String override){String value=host.displayText(t,override==null?t.text:override);if(value.isEmpty())return;int size=t.fontSize;g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));int color=t.color!=0?t.color:host.colors.getOrDefault(t.style&63,0xffffffff);g.setColor(new Color(color,true));int x=t.x,y=t.y+size,leading=Math.max(size,t.leading),limit=t.x+(t.width>0?t.width:width-t.x);for(int at=0;at<value.length();){int cp=value.codePointAt(at);String c=new String(Character.toChars(cp));at+=Character.charCount(cp);if(cp=='\r')continue;int w=g.getFontMetrics().stringWidth(c);if(cp=='\n'||x+w>limit){x=t.x;y+=leading;if(cp=='\n')continue;}g.drawString(c,x,y);x+=w;}}
    private void panel(Graphics2D g,Dc3rHost.Panel p,String override)throws IOException{if(p==null)return;for(Dc3rHost.Quad q:p.quads)quad(g,q);for(int i=0;i<p.texts.size();i++)text(g,p.texts.get(i),i==0?override:null);for(Dc3rHost.Button b:p.buttons)button(g,b,0);}
    private void button(Graphics2D g,Dc3rHost.Button b,int state)throws IOException{List<Dc3rHost.Quad> q=host.buttonQuads(b,state);for(Dc3rHost.Quad a:q)quad(g,a);List<Dc3rHost.Text> t=state==2&&!b.pressedText.isEmpty()?b.pressedText:state>0&&!b.hoverText.isEmpty()?b.hoverText:b.normalText;for(Dc3rHost.Text a:t)text(g,a,null);}
    private void layer(Graphics2D g,Dc3rHost.Layer l)throws IOException{BufferedImage p=picture(l.resource,l.tint);if(p==null)return;AffineTransform old=g.getTransform();g.translate(l.x+l.pivotX,l.y+l.pivotY);g.scale(l.scale/100.0,l.scale/100.0);g.translate(-l.pivotX,-l.pivotY);g.drawImage(p,0,0,null);BufferedImage second=picture(l.second,l.tint);if(second!=null)g.drawImage(second,0,0,null);g.setTransform(old);}
    private BufferedImage scene()throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(Color.BLACK);g.fillRect(0,0,width,height);g.translate(host.cameraX,host.cameraY);layer(g,host.layers[0]);panel(g,host.back,null);panel(g,host.fogBack,null);Integer[] order={1,2,3,4,5,6,7,8,9,10,11};Arrays.sort(order,(a,b)->Integer.compare(host.layers[a].priority,host.layers[b].priority));for(int slot:order)layer(g,host.layers[slot]);panel(g,host.fogFore,null);panel(g,host.fore,null);}finally{g.dispose();}return image;}
    public BufferedImage render()throws IOException{BufferedImage image=scene();Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);if(transitionFrom!=null){float progress=host.transitionDuration<=0?1:Math.min(1,transitionClock/(float)host.transitionDuration);if(progress<1){if(host.screenBreakType>=0)drawScreenBreak(g);else if(!host.transitionMask.isEmpty()){drawMask(g,progress);}else{g.setComposite(AlphaComposite.SrcOver.derive(1-progress));g.drawImage(transitionFrom,0,0,null);g.setComposite(AlphaComposite.SrcOver);}}else{transitionFrom.flush();transitionFrom=null;}}
        if(host.textVisible&&!hidden){panel(g,host.putDefs.get(1),body());panel(g,host.putDefs.get(2),host.speaker);panel(g,host.putDefs.get(3),null);layer(g,host.layers[12]);Dc3rHost.Panel toolbar=host.messageMenu();if(toolbar!=null)for(int i=0;i<toolbar.buttons.size();i++)button(g,toolbar.buttons.get(i),i==host.pressed?2:i==host.hovered?1:0);}
        if(host.menuWaiting){for(Dc3rHost.Quad q:host.menu.quads)quad(g,q);for(Dc3rHost.Text t:host.menu.texts)text(g,t,null);for(int i=0;i<host.menu.buttons.size();i++)button(g,host.menu.buttons.get(i),i==host.pressed?2:i==host.hovered?1:0);}
    }finally{g.dispose();}if(lastScene==null)lastScene=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D copy=lastScene.createGraphics();try{copy.drawImage(image,0,0,null);}finally{copy.dispose();}return image;}
    /** Linear grayscale wipe using original mask pixels; shader interpolation remains approximate. */
    private void drawMask(Graphics2D g,float progress)throws IOException{
        if(maskLevels==null||!appliedMask.equals(host.transitionMask)){BufferedImage mask=picture(host.transitionMask);int mw=mask.getWidth(),mh=mask.getHeight();int[] raw=mask.getRGB(0,0,mw,mh,null,0,mw);maskLevels=new byte[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)maskLevels[y*width+x]=(byte)raw[(y*mh/height)*mw+x*mw/width];appliedMask=host.transitionMask;}
        if(maskPixels==null)maskPixels=new int[width*height];transitionFrom.getRGB(0,0,width,height,maskPixels,0,width);float fuzz=Math.max(.001f,host.transitionFuzziness),range=(1-progress)*(1+fuzz);int[] alpha=new int[256];for(int i=0;i<256;i++)alpha[i]=Math.round(255*Math.max(0,Math.min(1,(i/255f+range-1)/fuzz)))<<24;for(int i=0;i<maskPixels.length;i++)maskPixels[i]=(maskPixels[i]&0xffffff)|alpha[maskLevels[i]&255];transitionFrom.setRGB(0,0,width,height,maskPixels,0,width);g.drawImage(transitionFrom,0,0,null);
    }
    private void drawScreenBreak(Graphics2D g){float seconds=transitionClock/1000f-.05f;for(int row=0;row<18;row++)for(int col=0;col<32;col++){int n=row*32+col;float delay=host.screenBreakType==4?0:(float)Math.sqrt(col*col+(row+1)*(row+1))/30f,t=Math.max(0,seconds-delay),alpha=Math.max(0,1-t);if(alpha==0)continue;int sx=col*32,sy=row*32,x=sx+(int)(breakVx[n]*t),y=sy+(int)(breakVy[n]*t);g.setComposite(AlphaComposite.SrcOver.derive(alpha));g.drawImage(transitionFrom,x,y,x+32,y+32,sx,sy,sx+32,sy+32,null);}g.setComposite(AlphaComposite.SrcOver);}
    public BufferedImage preview()throws IOException{return scene();}
    public String body(){return host.body;}
    public boolean textWaiting(){return host.textVisible&&script.status()==CircusScrVm.Status.WAIT_CLICK;}
    public boolean menuWaiting(){return host.menuWaiting;}
    public boolean actorVisible(){for(int i=1;i<11;i++)if(!host.layers[i].resource.isEmpty())return true;return false;}
    public boolean exitRequested(){return host.exitRequested;}
    public long ticket(){return host.ticket;}
    public String movie(){return host.movie.isEmpty()?"":movieReference;}
    public int movieGeneration(){return host.movieGeneration;}
    public void mediaCompleted(long ticket){if(ticket==host.ticket)host.mediaCompleted(script);}
    public String audio(int ch){return host.audio[ch];}
    public int audioGeneration(int ch){return host.audioGeneration[ch];}
    public boolean audioLoop(int ch){return host.audioLoop[ch];}
    public boolean audioActive(int ch){return host.audioActive[ch];}
    public void audioCompleted(int ch,int generation){host.audioCompleted(ch,generation);}
    public float volume(int ch){return host.volume(ch);}
    public long playbackMillis(){return textClock;}
    public void playbackMillis(long value){textClock=Math.max(0,value);}
    public byte[] snapshot()throws IOException{
        pruneCaptures();if(captureBytes()>CAPTURE_BUDGET)throw new IOException("Captured scene memory limit");ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x554e4933);host.save(out);script.save(out);out.writeInt(captureSerial);out.writeInt(captured.size());for(Map.Entry<String,byte[]> e:captured.entrySet()){out.writeUTF(e.getKey());writeBlob(out,e.getValue());}
        // A menu can survive Activity recreation only if its underlying story save
        // context survives too. Story snapshots have no quick-script caller, so they
        // deliberately do not embed the previous story snapshot recursively.
        boolean stable=script.callDepth()>0&&storyState!=null;out.writeBoolean(stable);if(stable){writeBlob(out,storyState);out.writeUTF(storyCaption);for(String ref:storyAudio)out.writeUTF(ref);writeBlob(out,storyPreview==null?new byte[0]:DesktopSave.thumbnail(storyPreview,256,144));}if(bytes.size()>48*1024*1024)throw new IOException("Unity snapshot memory limit");return bytes.toByteArray();
    }
    private static void writeBlob(DataOutput out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] readBlob(DataInputStream in,int limit)throws IOException{int length=in.readInt();if(length<0||length>limit||length>in.available())throw new IOException("Invalid Unity save block");byte[] bytes=new byte[length];in.readFully(bytes);return bytes;}
    public void restore(byte[] bytes)throws IOException{
        if(bytes.length>48*1024*1024)throw new IOException("Unity snapshot memory limit");DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=0x554e4933)throw new IOException("Invalid Unity runtime save");host.load(in);script.load(in);int serial=in.readInt(),count=in.readInt();if(serial<0||count<0||count>16)throw new IOException("Invalid capture count");Map<String,byte[]> restored=new LinkedHashMap<>();int total=0;for(int i=0;i<count;i++){String name=in.readUTF();if(!name.matches("@capture/[0-9]+")||restored.containsKey(name))throw new IOException("Invalid captured scene");byte[] data=readBlob(in,Math.min(4*1024*1024,CAPTURE_BUDGET-total));total+=data.length;restored.put(name,data);}
        byte[] stableState=null,preview=new byte[0];String caption="";String[] audio={"","","","","",""};if(in.readBoolean()){if(script.callDepth()==0)throw new IOException("Nested Unity story snapshot");stableState=readBlob(in,24*1024*1024);caption=in.readUTF();for(int ch=0;ch<6;ch++)audio[ch]=in.readUTF();preview=readBlob(in,1024*1024);}if(in.available()!=0)throw new IOException("Trailing Unity runtime save");BufferedImage restoredPreview=null;if(preview.length>0){restoredPreview=ImageIO.read(new ByteArrayInputStream(preview));if(restoredPreview==null)throw new IOException("Invalid story preview");}captured.clear();captured.putAll(restored);captureSerial=serial;
        Iterator<Map.Entry<String,BufferedImage>> it=pictures.entrySet().iterator();while(it.hasNext()){Map.Entry<String,BufferedImage> e=it.next();if(e.getKey().startsWith("@capture/")){pictureBytes-=e.getValue().getWidth()*(long)e.getValue().getHeight()*4;e.getValue().flush();it.remove();}}
        if(transitionFrom!=null){transitionFrom.flush();transitionFrom=null;}transitionRevision=host.transitionRevision;lastTicket=storyTicket=-1;executionToken=new Object();resolvedMovie=-1;movieReference="";if(storyPreview!=null)storyPreview.flush();storyPreview=restoredPreview;storyState=stableState;storyCaption=caption;System.arraycopy(audio,0,storyAudio,0,6);scanSaves();
    }
    public void persist()throws IOException{if(audit)return;ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));atomic(saveDirectory.resolve("progress.dat"),bytes.toByteArray());}
}
