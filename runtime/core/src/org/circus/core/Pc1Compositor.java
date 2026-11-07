package org.circus.core;

import java.io.*;
import java.util.*;

/** Current PC1 image-pool/backing-buffer services, independent of any platform.
 * Image0 is mutable backing storage, not a Windows displayed bitmap.407000
 * requests invalidation; it neither synchronously paints nor clears dirty.
 * Native frame/globals and higher UI layers keep their independent owners. */
public final class Pc1Compositor {
    private static final int FORMAT=0x50314f32,MAX_JOURNAL=65536;
    private static final long MAX_STORAGE=256L*1024*1024;
    public static final int BACKGROUND_HANDLE=0x43d418,BACKGROUND_X=0x9d96bc,BACKGROUND_Y=0x9d96c0;
    public static final int COMPOSITION_PENDING=0x9b9f18,CACHED_SELECTION=0x94fa28,VISIBILITY=0xbb844c;
    public static final int BACKGROUND_NAME_HEAD=0x9d860c,PENDING_COUNT=0x9d96e4,PENDING_BAKED=0xbb8424;
    /** An adapter to existing canonical owners, not a second copy of fields. */
    public interface State {
        int readInt(int nativeAddress)throws IOException;
        void writeInt(int nativeAddress,int value)throws IOException;
        void writeByte(int nativeAddress,int value)throws IOException;
    }
    /** Required native consumers; absence raises an exact unsupported boundary.
     * Implementations draw through this compositor and update their own state. */
    public interface Layers {
        void rebuildBackground(Pc1Compositor compositor,State state)throws IOException; //411D30
        void grid(Pc1Compositor compositor,State state,int mode)throws IOException; //40B580
        void buttons(Pc1Compositor compositor,State state,int mode)throws IOException; //40AA50
    }
    public static final class Invalidation {
        public final long serial;
        public final int target;
        public final Pc1Raster.Rect rectangle;
        public final boolean erase=false;
        private Invalidation(long serial,int target,Pc1Raster.Rect rectangle){this.serial=serial;this.target=target;this.rectangle=rectangle;}
    }
    /** Ephemeral typed projection. Raw shape/name/pointer bytes belong only
     *to pool; each consumer projects the current record afresh. */
    private static final class Image {
        final int width,height,stride,format;final boolean occupied;
        final NativeRgbImage rgb;final NativeInverseAlphaImage alpha;
        Image(int width,int height,int stride,int format,boolean occupied,NativeRgbImage rgb,NativeInverseAlphaImage alpha){this.width=width;this.height=height;this.stride=stride;this.format=format;this.occupied=occupied;this.rgb=rgb;this.alpha=alpha;}
        boolean loaded(){return occupied;}
    }
    private static final Pc1HeapLease.Heap PORTABLE=new Pc1HeapLease.Heap(){
        public Pc1HeapLease allocate(int bytes)throws IOException{throw new IOException("PC1 native uninitialized allocation requires an explicit heap provider; portable imports only supply known image bytes");}
        public boolean release(Pc1HeapLease lease)throws IOException{if(lease.addressKind()!=Pc1HeapLease.AddressKind.MANAGED)throw new IOException("PC1 physical allocation release requires its actual heap provider");return true;}
    };
    private final Pc1ImagePool pool;
    private final boolean portable;
    private int active;
    private Pc1Raster.Clip baseClip,clip;
    private Pc1Raster.Rect dirty;
    private long serial;
    private final List<Invalidation> journal=new ArrayList<>();
    public interface InvalidationSink {void invalidate(Pc1Raster.Rect rectangle)throws IOException;}
    private InvalidationSink invalidationSink;
    public void bindInvalidation(InvalidationSink sink)throws IOException{if(sink==null||invalidationSink!=null)throw new IOException("PC1 invalidation requires unique platform owner");invalidationSink=sink;}
    private Pc1Compositor(Pc1ImagePool pool,boolean portable){this.pool=pool;this.portable=portable;}
    /** Loaded-PE state before401000/4076B0/407680. No invented front pixels or
     *early clip/dirty initialization. The initial dirty tuple is the actual
     *CHS data image (800,0,600,0), not the later resetDirty tuple. */
    public static Pc1Compositor deferred(Pc1ImagePool pool)throws IOException{Pc1Compositor result=new Pc1Compositor(requiredPool(pool),false);result.active=0;result.baseClip=result.clip=new Pc1Raster.Clip(0,0,0,0);result.dirty=new Pc1Raster.Rect(800,0,600,0);return result;}
    /** Explicit portable known-front import. Has no native heap/pointer claim. */
    public Pc1Compositor(NativeRgbImage initialFront)throws IOException{this(new Pc1ImagePool(PORTABLE),true);installRgb(0,initialFront);fullClip();resetDirty();}
    /** Explicit front snapshot plus a real native heap for subsequent loads.
     *Use the pool constructor below for an actual shared DIB front. */
    public Pc1Compositor(NativeRgbImage initialFront,Pc1HeapLease.Heap heap)throws IOException{this(new Pc1ImagePool(heap),false);installRgb(0,initialFront);fullClip();resetDirty();}
    /** Native startup must already import its actual external image0 lease. */
    public Pc1Compositor(Pc1ImagePool pool)throws IOException{this(requiredPool(pool),false);if(!pool.occupied(0)||pool.field(0,Pc1ImagePool.PIXEL_FORMAT)!=0)throw new IOException("PC1 image0 requires an explicit known RGB24 allocation");fullClip();resetDirty();}
    private static Pc1ImagePool requiredPool(Pc1ImagePool pool)throws IOException{if(pool==null)throw new IOException("PC1 canonical image pool missing");return pool;}
    public Pc1ImagePool pool(){return pool;}
    public Pc1HeapLease imageLease(int handle)throws IOException{return pool.allocation(handle);}
    private static void handle(int value)throws IOException{if(value<0||value>=100)throw new IOException("PC1 compositor handle outside owned100 images: "+value);}
    private Image image(int handle)throws IOException{
        handle(handle);int w=pool.field(handle,12),h=pool.field(handle,16),stride=pool.field(handle,8),format=pool.field(handle,20);boolean loaded=pool.occupied(handle);NativeRgbImage rgb=null;NativeInverseAlphaImage alpha=null;
        if(loaded&&(format==0||format==1)){Pc1HeapLease.Storage bytes=pool.pixelStorage(handle);int bottom=pool.pixelBottomOffset(handle);if(format==0)rgb=NativeRgbImage.view(w,h,stride,bytes,0,bottom);else alpha=NativeInverseAlphaImage.view(w,h,stride,bytes,0,bottom);}
        return new Image(w,h,stride,format,loaded,rgb,alpha);
    }
    private Image source(int handle)throws IOException{return handle==-1?null:image(handle);}
    private Image targetImage()throws IOException{Image value=image(active);if(value.width<=0||value.height<=0)throw new IOException("PC1 active target has no known image metadata");return value;}
    private NativeRgbImage targetRgb()throws IOException{Image value=targetImage();if(value.rgb==null)throw new IOException("PC1 raster destination is not a loaded RGB24 image");return value.rgb;}
    private void capacity(int handle,int length)throws IOException{long old=0;if(pool.occupied(handle)){Pc1HeapLease lease=pool.allocation(handle);if(lease.addressKind()==Pc1HeapLease.AddressKind.MANAGED)old=lease.length();}if(pool.knownAllocationBytes()-old+length>MAX_STORAGE)throw new IOException("PC1 compositor known image storage limit");}
    private void install(int handle,int width,int height,int stride,int format,byte[] bytes)throws IOException{
        handle(handle);capacity(handle,bytes.length);
        if(pool.occupied(handle)&&pool.field(handle,12)==width&&pool.field(handle,16)==height&&pool.field(handle,8)==stride&&pool.field(handle,20)==format){Pc1HeapLease.Storage memory=pool.pixelStorage(handle);for(int n=0;n<bytes.length;n++)memory.write(n,bytes[n]);return;}
        pool.importManaged(handle,width,height,stride,format,bytes);
    }
    /** Explicit known-pixel producer; matching native allocations are updated
     *in place. This does not pretend to implement uninitialized401050. */
    public void installRgb(int handle,NativeRgbImage source)throws IOException{if(source==null)throw new IOException("PC1 compositor RGB import missing");install(handle,source.width,source.height,source.stride,0,source.bgr24());}
    public void installAlpha(int handle,NativeInverseAlphaImage source)throws IOException{if(handle==0||source==null)throw new IOException("PC1 image0 requires RGB24 backing");install(handle,source.width,source.height,source.stride,1,source.abgr32());}
    public int allocateRgb(NativeRgbImage source,int mode)throws IOException{for(int n=mode==0?20:0,end=mode==0?100:20;n<end;n++)if(!pool.occupied(n)){installRgb(n,source);return n;}return -1;}
    public int allocateAlpha(NativeInverseAlphaImage source,int mode)throws IOException{for(int n=mode==0?20:0,end=mode==0?100:20;n<end;n++)if(!pool.occupied(n)){installAlpha(n,source);return n;}return -1;}
    /**402000 raw base-buffer copy. Native path allocates before copying. */
    public int duplicate(int handle)throws IOException{
        if(handle==-1||!pool.occupied(handle))return -1;int w=width(handle),h=height(handle),stride=stride(handle),fmt=format(handle);
        if(portable){Image image=image(handle);return image.rgb!=null?allocateRgb(image.rgb,0):image.alpha!=null?allocateAlpha(image.alpha,0):unsupportedDuplicate();}
        int created=pool.allocate(w,h,fmt,0);if(created==-1)return -1;long bytes=(long)stride*h;if(bytes<0||bytes>MAX_STORAGE)throw new IOException("PC1 duplicate raw memcpy exceeds known allocation");Pc1HeapLease.Storage from=pool.pixelStorage(handle),to=pool.pixelStorage(created);
        for(int at=0;at<bytes;){if((long)at+4<=bytes){int word=0;for(int n=0;n<4;n++)word|=from.read(at+n)<<(n*8);for(int n=0;n<4;n++)to.write(at+n,word>>>(n*8));at+=4;}else{to.write(at,from.read(at));at++;}}return created;
    }
    private static int unsupportedDuplicate()throws IOException{throw new IOException("PC1 portable duplicate does not have a typed pixel owner");}
    public void freeChecked(int handle)throws IOException{pool.free(handle);}
    /** Existing callers keep their signature; checked native services should
     *use freeChecked. Failure always retains its exact IOException cause. */
    public void free(int handle){try{freeChecked(handle);}catch(IOException error){throw new UncheckedIOException(error);}}
    public boolean loaded(int handle)throws IOException{handle(handle);return pool.occupied(handle);}
    public int format(int handle)throws IOException{handle(handle);return pool.field(handle,20);}
    public int width(int handle)throws IOException{handle(handle);return pool.field(handle,12);}
    public int height(int handle)throws IOException{handle(handle);return pool.field(handle,16);}
    public int stride(int handle)throws IOException{handle(handle);return pool.field(handle,8);}
    public NativeRgbImage rgb(int handle)throws IOException{Image image=image(handle);return image.rgb==null?null:image.rgb.copy();}
    public NativeInverseAlphaImage alpha(int handle)throws IOException{Image image=image(handle);return image.alpha==null?null:image.alpha.copy();}
    public NativeRgbImage front()throws IOException{return rgb(0);}
    public int activeTarget(){return active;}
    /**409480:−1 does nothing. An empty in-range slot selects image0. */
    public void target(int handle)throws IOException{if(handle==-1)return;handle(handle);active=pool.occupied(handle)?handle:0;fullClip();}
    public Pc1Raster.Clip clip(){return clip;}
    public Pc1Raster.Clip baseClip(){return baseClip;}
    /**4076B0 sets both base and current clip to the active target dimensions. */
    public void fullClip()throws IOException{baseClip=clip=new Pc1Raster.Clip(0,0,width(active),height(active));}
    /**407700 clamps only left/top below0 and right/bottom above dimensions.
     * Empty, inverted and off-edge bounds retain the actual native values. */
    public void setClip(int left,int top,int right,int bottom)throws IOException{baseClip=clip=new Pc1Raster.Clip(Math.max(0,left),Math.max(0,top),Math.min(width(active),right),Math.min(height(active),bottom));}
    /**407780 intersects the saved base, not the preceding current clip. */
    public void intersectClip(int left,int top,int right,int bottom){clip=new Pc1Raster.Clip(Math.max(baseClip.left,left),Math.max(baseClip.top,top),Math.min(baseClip.right,right),Math.min(baseClip.bottom,bottom));}
    /**407680 uses current target dimensions, even for an offscreen target. */
    public void resetDirty()throws IOException{dirty=new Pc1Raster.Rect(width(active),height(active),0,0);}
    public Pc1Raster.Rect rawDirty(){return dirty;}
    /** Direct DWORD globals, not calls to409480/407700/407780/407580.
     * A native byte alias can change one coordinate/handle without clamping,
     *resetting a clip, unioning dirty, selecting a target, or drawing pixels. */
    public int nativeGlobal(int address)throws IOException{
        switch(address){
        case 0x94f8d8:return active;
        case 0x4304e8:return dirty.left;case 0x4304ec:return dirty.top;case 0x4304f0:return dirty.right;case 0x4304f4:return dirty.bottom;
        case 0xbc5f5c:return clip.right;case 0xbc5f60:return clip.left;case 0xbc5f64:return clip.bottom;case 0xbc5f68:return clip.top;
        case 0xbc5f6c:return baseClip.bottom;case 0xbc5f70:return baseClip.top;case 0xbc5f74:return baseClip.right;case 0xbc5f78:return baseClip.left;
        default:throw new IOException("PC1 compositor unknown native global0x"+Integer.toHexString(address));
        }
    }
    public void nativeGlobal(int address,int value)throws IOException{
        switch(address){
        case 0x94f8d8:active=value;return;
        case 0x4304e8:dirty=new Pc1Raster.Rect(value,dirty.top,dirty.right,dirty.bottom);return;
        case 0x4304ec:dirty=new Pc1Raster.Rect(dirty.left,value,dirty.right,dirty.bottom);return;
        case 0x4304f0:dirty=new Pc1Raster.Rect(dirty.left,dirty.top,value,dirty.bottom);return;
        case 0x4304f4:dirty=new Pc1Raster.Rect(dirty.left,dirty.top,dirty.right,value);return;
        case 0xbc5f5c:clip=new Pc1Raster.Clip(clip.left,clip.top,value,clip.bottom);return;
        case 0xbc5f60:clip=new Pc1Raster.Clip(value,clip.top,clip.right,clip.bottom);return;
        case 0xbc5f64:clip=new Pc1Raster.Clip(clip.left,clip.top,clip.right,value);return;
        case 0xbc5f68:clip=new Pc1Raster.Clip(clip.left,value,clip.right,clip.bottom);return;
        case 0xbc5f6c:baseClip=new Pc1Raster.Clip(baseClip.left,baseClip.top,baseClip.right,value);return;
        case 0xbc5f70:baseClip=new Pc1Raster.Clip(baseClip.left,value,baseClip.right,baseClip.bottom);return;
        case 0xbc5f74:baseClip=new Pc1Raster.Clip(baseClip.left,baseClip.top,value,baseClip.bottom);return;
        case 0xbc5f78:baseClip=new Pc1Raster.Clip(value,baseClip.top,baseClip.right,baseClip.bottom);return;
        default:throw new IOException("PC1 compositor unknown native global0x"+Integer.toHexString(address));
        }
    }
    /**407580 unions every raw rectangle, including empty/inverted, only for0. */
    public void touch(int left,int top,int right,int bottom){if(active==0)dirty=new Pc1Raster.Rect(Math.min(dirty.left,left),Math.min(dirty.top,top),Math.max(dirty.right,right),Math.max(dirty.bottom,bottom));}
    private Pc1Raster.Rect painted(Pc1Raster.Rect r){touch(r.left,r.top,r.right,r.bottom);return r;}
    /**4075E0 mutates an empty dirty rectangle to0,0,1,1; otherwise clamps
     * it to the current active target. It does not reset dirty afterwards. */
    public Pc1Raster.Rect dirtyForPresent()throws IOException{
        if(dirty.empty())dirty=new Pc1Raster.Rect(0,0,1,1);else{Image i=targetImage();dirty=new Pc1Raster.Rect(Math.max(0,dirty.left),Math.max(0,dirty.top),Math.min(i.width,dirty.right),Math.min(i.height,dirty.bottom));}return dirty;
    }
    /**407000 records InvalidateRect(erase=false), then resets clip only.
     * No captured/displayed frame is implied by an invalidation request. */
    public Invalidation present()throws IOException{
        if((invalidationSink==null&&journal.size()>=MAX_JOURNAL)||serial==Long.MAX_VALUE)throw new IOException("PC1 invalidation journal limit");Invalidation event=new Invalidation(++serial,active,dirtyForPresent());if(invalidationSink==null)journal.add(event);else invalidationSink.invalidate(event.rectangle);fullClip();return event;
    }
    public List<Invalidation> invalidations(){return Collections.unmodifiableList(new ArrayList<>(journal));}
    public List<Invalidation> drainInvalidations(){List<Invalidation> result=invalidations();journal.clear();return result;}
    public long invalidationSerial(){return serial;}
    /** Native handle guards precede raster calls: absent/wrong-copy-format
     * sources cause neither target access nor407580 side effects. */
    public Pc1Raster.Rect copy(int x,int y,int handle)throws IOException{
        if(handle==-1||!pool.occupied(handle)||format(handle)!=0)return null;
        int w=width(handle),h=height(handle);
        if(w==0||h==0||width(active)==0||height(active)==0){
            //407880 computes/calls407580 before its empty copy-loop test.
            //An allocated1-byte guard with zero extent is valid metadata;
            //no typed pixel image or target pixel dereference is needed.
            Pc1Raster.Rect r=new Pc1Raster.Rect(Math.max(x,clip.left),Math.max(y,clip.top),Math.min(x+w,clip.right),Math.min(y+h,clip.bottom));
            if(r.empty())return painted(r);
        }
        Image i=source(handle);return i.rgb==null?null:painted(Pc1Raster.copy(targetRgb(),clip,i.rgb,x,y));
    }
    public Pc1Raster.Rect feedback(int x,int y,int handle,int ratio)throws IOException{Image i=source(handle);return i==null||i.rgb==null?null:painted(Pc1Raster.feedback(targetRgb(),clip,i.rgb,x,y,ratio));}
    public Pc1Raster.Rect scale(int handle,int ratio)throws IOException{Image i=source(handle);return i==null||i.rgb==null?null:painted(Pc1Raster.scale(targetRgb(),clip,i.rgb,ratio));}
    public Pc1Raster.Rect transparent(int x,int y,int handle)throws IOException{Image i=source(handle);if(i==null||!i.loaded())return null;return painted(i.format==0?Pc1Raster.transparent(targetRgb(),clip,i.rgb,x,y):Pc1AlphaRaster.draw(targetRgb(),clip,i.alpha,x,y));}
    public Pc1Raster.Rect opacity(int x,int y,int handle,int ratio)throws IOException{Image i=source(handle);if(i==null||!i.loaded())return null;return painted(i.format==0?Pc1Raster.opacity(targetRgb(),clip,i.rgb,x,y,ratio):Pc1AlphaRaster.opacity(targetRgb(),clip,i.alpha,x,y,ratio));}
    public Pc1Raster.Rect masked(int x,int y,int handle,int maskHandle)throws IOException{
        if(maskHandle==-1)return transparent(x,y,handle);Image i=source(handle);if(i==null||!i.loaded())return null;Image m=source(maskHandle);if(m==null||!m.loaded())return null;if(i.rgb==null||m.rgb==null)throw new IOException("PC1 explicit mask path requires independently reviewed RGB24 source/mask");return painted(Pc1Raster.masked(targetRgb(),clip,i.rgb,x,y,m.rgb));
    }
    public Pc1Raster.Rect fill(int left,int top,int right,int bottom,int rgb)throws IOException{return painted(Pc1Raster.fill(targetRgb(),clip,left,top,right,bottom,rgb));}
    public Pc1Raster.Rect fillBlend16(int left,int top,int right,int bottom,int rgb,int ratio)throws IOException{return painted(Pc1Raster.fillBlend16(targetRgb(),clip,left,top,right,bottom,rgb,ratio));}
    /**408F00/4091A0 share the actual active image and407580 dirty owner.
     *An equal-edge rectangle still touches dirty when the native kernel does. */
    public Pc1GlyphRaster.Draw glyph(int x,int y,int size,byte[] coverage,int sourceOffset,int rgb)throws IOException{
        Pc1GlyphRaster.Draw drawn=Pc1GlyphRaster.draw(targetRgb(),clip,x,y,size,coverage,sourceOffset,rgb);
        if(drawn.touched)touch(drawn.rectangle.left,drawn.rectangle.top,drawn.rectangle.right,drawn.rectangle.bottom);
        return drawn;
    }
    public Pc1GlyphRaster.Draw decoration(int left,int top,int right,int bottom,int rgb,int weight)throws IOException{
        Pc1GlyphRaster.Draw drawn=Pc1GlyphRaster.decoration(targetRgb(),clip,left,top,right,bottom,rgb,weight);
        if(drawn.touched)touch(drawn.rectangle.left,drawn.rectangle.top,drawn.rectangle.right,drawn.rectangle.bottom);
        return drawn;
    }
    private static Layers layers(Layers value,int entry)throws IOException{if(value==null)throw new IOException("Unimplemented PC1 compositor consumer"+Integer.toHexString(entry));return value;}
    /**412090 reads coordinates/handle after a pending411D30 callback returns. */
    public void redrawBackground(State state,Layers layers)throws IOException{
        if(state==null)throw new IOException("PC1 compositor state adapter missing");if(state.readInt(COMPOSITION_PENDING)!=0)layers(layers,0x411d30).rebuildBackground(this,state);copy(state.readInt(BACKGROUND_X),state.readInt(BACKGROUND_Y),state.readInt(BACKGROUND_HANDLE));
    }
    /**4120C0 preserves exact byte/DWORD field writes and layer-call ordering.
     * Grid/buttons/text/timers are not silently supplied by this owner. */
    public void prepareUi(int param1,int param2,State state,Layers layers)throws IOException{
        if(state==null)throw new IOException("PC1 compositor state adapter missing");state.writeInt(CACHED_SELECTION,-1);state.writeInt(VISIBILITY,0);fullClip();
        if(param2!=0){if(state.readInt(COMPOSITION_PENDING)!=0)layers(layers,0x411d30).rebuildBackground(this,state);target(state.readInt(BACKGROUND_HANDLE));if(param1!=0)layers(layers,0x40b580).grid(this,state,0);layers(layers,0x40aa50).buttons(this,state,0);target(0);state.writeByte(BACKGROUND_NAME_HEAD,0);state.writeInt(PENDING_COUNT,0);state.writeInt(PENDING_BAKED,0);state.writeInt(COMPOSITION_PENDING,1);}
        else{redrawBackground(state,layers);if(param1!=0)layers(layers,0x40b580).grid(this,state,1);layers(layers,0x40aa50).buttons(this,state,1);}
    }
    private static void rect(DataOutput out,Pc1Raster.Rect r)throws IOException{out.writeInt(r.left);out.writeInt(r.top);out.writeInt(r.right);out.writeInt(r.bottom);}
    private static Pc1Raster.Rect rect(DataInput in)throws IOException{return new Pc1Raster.Rect(in.readInt(),in.readInt(),in.readInt(),in.readInt());}
    private static void clip(DataOutput out,Pc1Raster.Clip r)throws IOException{rect(out,new Pc1Raster.Rect(r.left,r.top,r.right,r.bottom));}
    private static Pc1Raster.Clip clip(DataInput in)throws IOException{Pc1Raster.Rect r=rect(in);return new Pc1Raster.Clip(r.left,r.top,r.right,r.bottom);}
    public void save(DataOutput out)throws IOException{
        out.writeInt(FORMAT);out.writeBoolean(portable);pool.save(out);out.writeInt(active);clip(out,baseClip);clip(out,clip);rect(out,dirty);out.writeLong(serial);out.writeInt(journal.size());for(Invalidation event:journal){out.writeLong(event.serial);out.writeInt(event.target);rect(out,event.rectangle);}
    }
    public static Pc1Compositor load(DataInput in)throws IOException{return load(in,PORTABLE,saved->{if(saved.kind!=Pc1HeapLease.AddressKind.MANAGED)throw new IOException("PC1 physical image snapshot requires its actual heap rebinder");return Pc1HeapLease.managed(saved.backingBytes(),saved.authority);});}
    public static Pc1Compositor load(DataInput in,Pc1HeapLease.Heap heap,Pc1ImagePool.Rebinder rebinder)throws IOException{
        Snapshot image=loadImage(in);Pc1Compositor result=new Pc1Compositor(new Pc1ImagePool(heap),image.portable);result.restoreInto(image,rebinder);return result;
    }
    public static final class Snapshot {
        private final boolean portable;private final Pc1ImagePool.Image pool;private final int active;private final Pc1Raster.Clip baseClip,clip;private final Pc1Raster.Rect dirty;private final long serial;private final List<Invalidation> journal;
        private Snapshot(boolean portable,Pc1ImagePool.Image pool,int active,Pc1Raster.Clip baseClip,Pc1Raster.Clip clip,Pc1Raster.Rect dirty,long serial,List<Invalidation> journal){this.portable=portable;this.pool=pool;this.active=active;this.baseClip=baseClip;this.clip=clip;this.dirty=dirty;this.serial=serial;this.journal=journal;}
        public Pc1ImagePool.Image poolImage(){return pool;}
    }
    /** Parse before creating any physical lease or installing Guest layout. */
    public static Snapshot loadImage(DataInput in)throws IOException{
        if(in.readInt()!=FORMAT)throw new IOException("PC1 compositor format: old duplicated image owner is not silently migrated");boolean portable=in.readBoolean();Pc1ImagePool.Image pool=Pc1ImagePool.loadImage(in);int active=in.readInt();Pc1Raster.Clip baseClip=clip(in),clip=clip(in);Pc1Raster.Rect dirty=rect(in);long serial=in.readLong();if(serial<0)throw new IOException("PC1 compositor invalidation serial");int count=in.readInt();if(count<0||count>MAX_JOURNAL)throw new IOException("PC1 invalidation journal limit");long previous=0;List<Invalidation> journal=new ArrayList<>();
        for(int n=0;n<count;n++){long at=in.readLong();int target=in.readInt();Pc1Raster.Rect r=rect(in);if(at<=previous||at>serial)throw new IOException("PC1 compositor journal order");previous=at;journal.add(new Invalidation(at,target,r));}return new Snapshot(portable,pool,active,baseClip,clip,dirty,serial,journal);
    }
    /** The same empty pool/compositor may have fixed views predeclared before
     *StaticImage finalization. Installation preserves those object identities. */
    public void restoreInto(Snapshot image,Pc1ImagePool.Rebinder rebinder)throws IOException{
        if(image==null||portable!=image.portable)throw new IOException("PC1 staged compositor owner contract differs");pool.restoreInto(image.pool,rebinder);active=image.active;baseClip=image.baseClip;clip=image.clip;dirty=image.dirty;serial=image.serial;journal.clear();journal.addAll(image.journal);
    }
}
