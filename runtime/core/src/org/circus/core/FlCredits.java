package org.circus.core;

import java.io.*;
import java.util.*;

/** D.C.II Fall in Love 408210/409210. This owner is independent of PC2's GEND strips.
 * The 409855 caller schedules a 20 ms poll; alpha advances once only when delta>70.
 * Draw records describe the native calls, including their order and the final blank handle. */
public final class FlCredits {
    public static final int POLL_MS=20,HEIGHT=10188,ROUTES=15;
    public static final float SPEED=Float.intBitsToFloat(0x3d6a7040);
    private static final String[] PREFIX={"ged","aft/ed_af_akn","aft/ed_af_mya","aft/ed_af_myk","aft/ed_af_erk","aft/ed_af_ais","aft/ed_af_mhr","drm/ed_dm_akn","drm/ed_dm_mya","drm/ed_dm_myk","drm/ed_dm_erk","etc/ed_au","koi/ed_ku","ask/ed_su","etc/ed_au","etc/ed_au"};
    private static final int[] COUNTS={0,17,17,17,17,17,17,9,9,9,9,20,20,20,20,20};
    /** Native zero/unloaded ending slot: it adds no image pixels to the retained front. */
    public static final class Layer {
        public final String image;
        public final int x,y,weight16;
        /** Large raw CRX must be clipped while reading; do not first allocate a full ARGB image. */
        public final boolean rawClip;
        private Layer(String image,int x,int y,int weight,boolean raw){this.image=image;this.x=x;this.y=y;weight16=weight;rawClip=raw;}
    }
    public static final class Frame {
        /** Native made no draw call yet; preserve the captured screen. */
        public final boolean capture;
        /** Compatibility diagnostic only.40C220 sets clipping, never a black clear. */
        public final boolean black;
        public final List<Layer> layers;
        private Frame(boolean capture,List<Layer> layers){this.capture=capture;black=false;this.layers=Collections.unmodifiableList(layers);}
    }
    public boolean active,visible;
    public long generation;
    /** Native stores the argument verbatim: all nonzero modes animate, but only mode1 can skip. */
    public int variant,route,stage,alpha,index;
    public boolean fading,leadDone;
    public long elapsed;
    private int base,alphaBase,pollRemainder,completion;
    private boolean firstPoll=true;
    private float interval;
    private Frame frame=capture();
    private final List<Frame> journal=new ArrayList<Frame>();

    private static Frame capture(){return new Frame(true,new ArrayList<Layer>());}
    private static Layer layer(String image,int y,int weight,boolean raw){return new Layer(image,0,y,weight,raw);}
    private void draw(Layer... layers){if(journal.size()>=100000)throw new IllegalStateException("FL credits draw journal limit");frame=new Frame(false,new ArrayList<Layer>(Arrays.asList(layers)));journal.add(frame);}
    public Frame frame(){return frame;}
    /** Every native draw in order, applied to the captured physical front. No implicit clear. */
    public List<Frame> draws(){return Collections.unmodifiableList(journal);}
    public int slideCount(){return route>=0&&route<COUNTS.length?COUNTS[route]:0;}
    public float intervalMillis(){return interval;}
    public int timerRemainder(){return pollRemainder;}
    /** Loaded original resources, excluding the native zero handle used by the final non-DRM draw. */
    public List<String> images(){
        if(variant==0)return Collections.singletonList("ed/ged");
        List<String> out=new ArrayList<String>();for(int i=0;i<=slideCount()+2;i++)out.add(image(i));return Collections.unmodifiableList(out);
    }
    public String image(int n){
        if(variant==0)return n==0?"ed/ged":"";
        if(n==0)return "bg/white";
        if(n==1)return route<7?"ed/aft":route<11?"ed/drm":"ed/ult";
        int count=slideCount();
        if(n>=2&&n<count+2)return String.format(Locale.ROOT,"ed/%s%02d",PREFIX[route],n-1);
        if(n==count+2)return drm()?"bg/white":"ed/logo";
        return "";
    }
    private boolean drm(){return route>=7&&route<=10;}
    public void start(int mode,int route)throws IOException {
        if(mode<0||mode>255)throw new IOException("FL ending mode "+mode);
        // Route0's zero-count table enters a free-then-fallthrough path. It is not used
        // by the reviewed MED callers and must not silently borrow another route.
        if(mode!=0&&(route<1||route>=COUNTS.length))throw new IOException("FL slideshow has no reviewed route "+route);
        if(generation==Long.MAX_VALUE)throw new IOException("FL ending generation exhausted");generation++;journal.clear();
        variant=mode;this.route=mode==0?0:route;active=visible=true;stage=alpha=index=0;
        fading=leadDone=false;elapsed=0;base=alphaBase=pollRemainder=completion=0;
        interval=mode==0?SPEED:(float)(this.route<7?85000:this.route<11?119000:111000)/(slideCount()+2);
        firstPoll=true;frame=capture();
    }
    /** Clear render ownership when a later native scene draw replaces this screen. */
    public void releaseFrame(){visible=false;frame=capture();journal.clear();}
    /** Native frees its resources without clearing or repainting the last frame. */
    public void stop(){active=false;}
    public int takeCompletion(){int value=completion;completion=0;return value;}
    public boolean canSkip(int mouseButton,int flag52,int flag53){return active&&mouseButton==2&&(variant==1&&flag52==1||variant==0&&flag53==1);}
    public boolean input(int mouseButton,int flag52,int flag53){if(!canSkip(mouseButton,flag52,flag53))return false;stop();return true;}
    /** A single native updater call, including a zero-time diagnostic poll. */
    public void elapse(long ms){
        if(!active)return;elapsed=(elapsed+(Math.max(0,ms)&0xffffffffL))&0xffffffffL;firstPoll=false;pollRemainder=0;pollAt((int)elapsed);
    }
    /** The 409855 wait loop sets 5383A4=20ms. Delayed timer events coalesce;
     * one host call never invents many intermediate alpha updates. */
    public void advance(long ms){
        if(!active)return;long delta=Math.max(0,ms);elapsed=(elapsed+(delta&0xffffffffL))&0xffffffffL;
        boolean due=firstPoll||delta>=POLL_MS||pollRemainder+delta%POLL_MS>=POLL_MS;
        pollRemainder=(int)((pollRemainder+delta%POLL_MS)%POLL_MS);
        if(due){firstPoll=false;pollAt((int)elapsed);}
    }
    /** One exact native poll, useful for boundary verification and nonuniform host clocks. */
    public void pollAt(int now){
        if(!active)return;
        if(variant==0){
            int delta=now-base;
            if(!leadDone){if(delta>3000){base=now;leadDone=true;}return;}
            float row=(float)(delta*SPEED);
            if(row<HEIGHT-600){draw(layer("ed/ged",-(int)row,16,true));return;}
            // 4092AE subtracts height from-600. This fully offscreen call writes
            // no pixels;40C220 is only a clip, so the preceding front survives.
            draw(layer("ed/ged",-600-HEIGHT,16,true));complete(53);return;
        }
        if(stage==0){
            if(!fading){draw(layer(image(0),0,16,false));if(now-base>3000){alphaBase=now;fading=true;}return;}
            blend(0,1);if(tickAlpha(now)){stage=1;fading=false;alpha=0;}return;
        }
        if(stage==1){
            if(!fading){draw(layer(image(1),0,16,false));int threshold=route<7?9000:route<11?17000:10700;if(now-base>threshold){alphaBase=now;fading=true;}return;}
            blend(1,2);if(tickAlpha(now)){stage=2;fading=false;alpha=0;base=now;index=2;}return;
        }
        if(stage==3){
            if(!fading){draw(layer(image(10),0,16,false));if(now-base>3000){alphaBase=now;fading=true;}return;}
            blend(10,11);if(tickAlpha(now)){draw(layer(image(11),0,16,false));complete(52);}return;
        }
        int current=fading?index:(now-base)/(int)interval+2;
        if(current!=index&&current!=-1){alpha=0;fading=true;index=current;alphaBase=now;}
        int limit=slideCount()+(drm()?2:3);
        if(drm()&&current>=limit-1&&!fading){stage=3;base=now;return;}
        if(current>=limit-1&&!fading){draw(layer(image(limit),0,16,false));complete(52);return;}
        if(fading){blend(index-1,index);if(tickAlpha(now)){fading=false;alpha=0;}}
        else draw(layer(image(index),0,16,false));
    }
    private void blend(int oldIndex,int newIndex){draw(layer(image(oldIndex),0,16-alpha,false),layer(image(newIndex),0,alpha,false));}
    private boolean tickAlpha(int now){if(now-alphaBase<=70)return false;alphaBase=now;return ++alpha>=16;}
    private void complete(int flag){completion=flag;active=false;}

    public void save(DataOutput o)throws IOException {
        o.writeInt(0x464c4332);o.writeBoolean(active);o.writeBoolean(visible);o.writeInt(variant);o.writeInt(route);o.writeInt(stage);o.writeInt(alpha);o.writeInt(index);
        o.writeBoolean(fading);o.writeBoolean(leadDone);o.writeLong(elapsed);o.writeInt(base);o.writeInt(alphaBase);o.writeInt(pollRemainder);o.writeInt(completion);o.writeFloat(interval);o.writeBoolean(firstPoll);
        o.writeLong(generation);saveFrame(o,frame);o.writeInt(journal.size());for(Frame f:journal)saveFrame(o,f);
    }
    private static void saveFrame(DataOutput o,Frame f)throws IOException{o.writeBoolean(f.capture);o.writeInt(f.layers.size());for(Layer l:f.layers){o.writeUTF(l.image);o.writeInt(l.x);o.writeInt(l.y);o.writeInt(l.weight16);o.writeBoolean(l.rawClip);}}
    public void load(DataInput in)throws IOException {
        FlCredits value=new FlCredits();value.loadValue(in);
        active=value.active;visible=value.visible;variant=value.variant;route=value.route;stage=value.stage;alpha=value.alpha;index=value.index;
        fading=value.fading;leadDone=value.leadDone;elapsed=value.elapsed;base=value.base;alphaBase=value.alphaBase;pollRemainder=value.pollRemainder;completion=value.completion;interval=value.interval;firstPoll=value.firstPoll;frame=value.frame;generation=value.generation;journal.clear();journal.addAll(value.journal);
    }
    private void loadValue(DataInput in)throws IOException {
        if(in.readInt()!=0x464c4332)throw new IOException("FL ending snapshot version");
        active=in.readBoolean();visible=in.readBoolean();variant=in.readInt();route=in.readInt();stage=in.readInt();alpha=in.readInt();index=in.readInt();
        fading=in.readBoolean();leadDone=in.readBoolean();elapsed=in.readLong();base=in.readInt();alphaBase=in.readInt();pollRemainder=in.readInt();completion=in.readInt();interval=in.readFloat();firstPoll=in.readBoolean();
        generation=in.readLong();
        if(variant<0||variant>255||route<0||route>=COUNTS.length||variant!=0&&route==0||stage<0||stage>3||alpha<0||alpha>16||index<0||index>32||elapsed<0||elapsed>0xffffffffL||pollRemainder<0||pollRemainder>=POLL_MS||completion!=0&&completion!=52&&completion!=53||Float.isNaN(interval)||interval<0||generation<0||active&&!visible||completion!=0&&active)throw new IOException("Invalid FL ending state");
        float expected=variant==0?SPEED:(float)(route<7?85000:route<11?119000:111000)/(slideCount()+2);
        if((active||visible)&&Float.floatToRawIntBits(interval)!=Float.floatToRawIntBits(expected))throw new IOException("FL ending rate/profile mismatch");
        Set<String> allowed=new HashSet<String>(images());frame=readFrame(in,allowed);int count=in.readInt();if(count<0||count>100000)throw new IOException("FL ending journal count");for(int n=0;n<count;n++){Frame f=readFrame(in,allowed);if(f.capture)throw new IOException("FL ending journal contains a non-draw");journal.add(f);}
        if(frame.capture!=(journal.isEmpty()))throw new IOException("FL ending front/journal mismatch");
    }
    private Frame readFrame(DataInput in,Set<String> allowed)throws IOException {
        boolean captured=in.readBoolean();int count=in.readInt();if(count<0||count>2||captured&&count!=0||!captured&&count==0)throw new IOException("FL ending frame count");List<Layer> layers=new ArrayList<Layer>();
        for(int n=0;n<count;n++){String image=in.readUTF();int x=in.readInt(),y=in.readInt(),weight=in.readInt();boolean raw=in.readBoolean();if(x!=0||weight<0||weight>16||!image.isEmpty()&&!allowed.contains(image)||raw!=(variant==0)||variant!=0&&y!=0)throw new IOException("Invalid FL ending draw");layers.add(new Layer(image,x,y,weight,raw));}return new Frame(captured,layers);
    }
}
