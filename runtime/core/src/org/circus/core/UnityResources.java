package org.circus.core;

import java.io.*;
import java.util.*;

/** Read-only Unity 5.5 resources shared by the desktop and Android interpreters. */
public final class UnityResources {
    public static final class Texture {
        public final String name;public final int width,height,format,mipCount;public final byte[] data;
        Texture(String n,int w,int h,int f,int m,byte[] d){name=n;width=w;height=h;format=f;mipCount=m;data=d;}
        /** Unity texture data starts at the bottom row; the renderer consumes top-down BGRA. */
        public Crx.Image image()throws IOException{
            byte[] out=new byte[width*height*4];
            if(format==10||format==12)decodeDxt(data,out,width,height,format==12);
            else {
                int stride=format==1?1:format==3?3:4;if(data.length<width*height*stride)throw new EOFException("Unity texture pixels");
                for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=(y*width+x)*stride,q=((height-1-y)*width+x)*4;
                    if(format==1){out[q]=out[q+1]=out[q+2]=(byte)255;out[q+3]=data[p];}
                    else if(format==3||format==4){out[q]=data[p+2];out[q+1]=data[p+1];out[q+2]=data[p];out[q+3]=format==4?data[p+3]:(byte)255;}
                    else if(format==5){out[q]=data[p+3];out[q+1]=data[p+2];out[q+2]=data[p+1];out[q+3]=data[p];}
                    else if(format==14){System.arraycopy(data,p,out,q,4);}
                    else throw new IOException("Unsupported Unity TextureFormat "+format);
                }
            }
            return new Crx.Image(0,0,width,height,32,out,null);
        }
    }
    public static final class Audio {
        public final String name;public final int channels,sampleRate,bitsPerSample,compression,subsound;public final float duration;public final byte[] data;
        Audio(String n,int c,int r,int b,int format,int sub,float secs,byte[] d){name=n;channels=c;sampleRate=r;bitsPerSample=b;compression=format;subsound=sub;duration=secs;data=d;}
    }
    private static final class BundleIndex {
        final UnityBundle bundle;final UnitySerializedFile file;final Map<Integer,Map<String,UnitySerializedFile.ObjectInfo>> names=new HashMap<>();
        final Map<String,UnitySerializedFile.ObjectInfo> paths=new HashMap<>();final Set<String> ambiguous=new HashSet<>();final Map<String,List<UnitySerializedFile.ObjectInfo>> collisions=new HashMap<>();
        BundleIndex(UnityBundle b,UnitySerializedFile f)throws IOException{bundle=b;file=f;Map<Long,UnitySerializedFile.ObjectInfo> ids=new HashMap<>();for(UnitySerializedFile.ObjectInfo o:f.objects)ids.put(o.pathId,o);
            for(UnitySerializedFile.ObjectInfo o:f.objects)if(o.classId==142){UnityBinary r=new UnityBinary(f.read(o),f.littleEndian);r.string();r.skip(r.count(1000000,"preload")*12);int count=r.count(1000000,"container");
                for(int i=0;i<count;i++){String key=r.string().toLowerCase(Locale.ROOT);r.skip(8);int fileId=r.i32();long id=r.i64();if(fileId!=0)continue;UnitySerializedFile.ObjectInfo target=ids.get(id);if(target==null)throw new IOException("Unity container target missing");
                    for(int slash=-1;slash<key.length();slash=key.indexOf('/',slash+1)){String alias=key.substring(slash+1);putPath(alias,target);int dot=alias.lastIndexOf('.');if(dot>=0)putPath(alias.substring(0,dot),target);if(slash==key.lastIndexOf('/'))break;}
                }
            }
        }
        private void collision(String key,UnitySerializedFile.ObjectInfo old,UnitySerializedFile.ObjectInfo object){if(old!=null&&old.pathId!=object.pathId){ambiguous.add(key);List<UnitySerializedFile.ObjectInfo> values=collisions.get(key);if(values==null){values=new ArrayList<>();values.add(old);collisions.put(key,values);}boolean present=false;for(UnitySerializedFile.ObjectInfo value:values)if(value.pathId==object.pathId)present=true;if(!present)values.add(object);}}
        private void putPath(String key,UnitySerializedFile.ObjectInfo object){collision(key,paths.put(key,object),object);}
        synchronized Map<String,UnitySerializedFile.ObjectInfo> index(int type)throws IOException{
            Map<String,UnitySerializedFile.ObjectInfo> result=names.get(type);if(result!=null)return result;result=new LinkedHashMap<>();
            for(UnitySerializedFile.ObjectInfo o:file.objects)if(o.classId==type){String key=file.name(o).toLowerCase(Locale.ROOT);collision(key,result.put(key,o),o);}
            names.put(type,result);return result;
        }
        synchronized UnitySerializedFile.ObjectInfo find(int type,String name)throws IOException{String key=name.replace('\\','/').toLowerCase(Locale.ROOT);Map<String,UnitySerializedFile.ObjectInfo> named=names.get(type);
            // Container aliases already identify their exact object. Avoid reading every
            // AudioClip name from a huge raw bundle through Android's document provider.
            if(!paths.containsKey(key)){if(key.indexOf('/')>=0)throw new FileNotFoundException("Unity container path "+name);named=index(type);}
            if(ambiguous.contains(key)){
                // Some original voice bundles contain duplicate byte-identical clips in separate
                // script folders. Only collapse after comparing complete audio metadata and data.
                List<UnitySerializedFile.ObjectInfo> candidates=collisions.get(key);boolean same=type==83&&candidates!=null&&!candidates.isEmpty();Audio first=null;
                if(same)for(UnitySerializedFile.ObjectInfo candidate:candidates){if(candidate.classId!=83){same=false;break;}Audio next=readAudio(this,candidate);if(first==null)first=next;else if(first.channels!=next.channels||first.sampleRate!=next.sampleRate||first.bitsPerSample!=next.bitsPerSample||first.compression!=next.compression||first.subsound!=next.subsound||Float.floatToIntBits(first.duration)!=Float.floatToIntBits(next.duration)||!Arrays.equals(first.data,next.data)){same=false;break;}}
                if(!same)throw new IOException("Ambiguous Unity asset; use its container path: "+name);UnitySerializedFile.ObjectInfo chosen=candidates.get(0);paths.put(key,chosen);if(named!=null&&named.containsKey(key))named.put(key,chosen);ambiguous.remove(key);
            }
            UnitySerializedFile.ObjectInfo o=paths.get(key);if(o==null&&named!=null)o=named.get(key);if(o==null||o.classId!=type)throw new FileNotFoundException("Unity asset "+name+" (class "+type+")");return o;}
    }
    public final GameResources files;public final String directory;private final Map<String,BundleIndex> bundles=new HashMap<>();
    public UnityResources(GameResources files)throws IOException{
        this.files=files;String prefix=null;for(String path:files.paths())if(path.equals("streamingassets/script0")||path.endsWith("/streamingassets/script0")){String p=path.substring(0,path.length()-7);if(prefix!=null&&!prefix.equals(p))throw new IOException("Multiple Unity games in directory");prefix=p;}
        if(prefix==null&&files.paths().contains("script0"))prefix="";
        if(prefix==null)throw new FileNotFoundException("Unity StreamingAssets/script0");directory=prefix;
    }
    public List<String> bundleNames(){List<String> result=new ArrayList<>();for(String path:files.paths())if(path.startsWith(directory)){String name=path.substring(directory.length());if(name.indexOf('/')<0&&name.matches("[a-z]+[0-9]+"))result.add(name);}Collections.sort(result);return result;}
    private synchronized BundleIndex bundle(String name)throws IOException{
        String key=Binary.safePath(name).toLowerCase(Locale.ROOT);if(key.indexOf('/')>=0)throw new IOException("Expected Unity bundle basename");BundleIndex index=bundles.get(key);if(index!=null)return index;
        UnityBundle bundle=new UnityBundle(files.source(directory+key));UnitySerializedFile file=null;
        for(UnityBundle.Entry e:bundle.entries)if(!e.name.endsWith(".resS")&&!e.name.endsWith(".resource")){if(file!=null)throw new IOException("Multiple serialized files in Unity bundle "+key);file=new UnitySerializedFile(bundle.source(e));}
        if(file==null)throw new IOException("No serialized file in Unity bundle "+key);if(!file.unityVersion.startsWith("5.5."))throw new IOException("Unverified Unity resource layout "+file.unityVersion);
        index=new BundleIndex(bundle,file);bundles.put(key,index);return index;
    }
    public List<String> names(String bundle,int classId)throws IOException{return Collections.unmodifiableList(new ArrayList<>(bundle(bundle).index(classId).keySet()));}
    /** Includes the exact AssetBundle container path and relative suffixes, including voice directories. */
    public boolean contains(String bundle,String name,int classId)throws IOException{BundleIndex b=bundle(bundle);String key=name.replace('\\','/').toLowerCase(Locale.ROOT);UnitySerializedFile.ObjectInfo o=b.paths.get(key);return o!=null?o.classId==classId:key.indexOf('/')<0&&b.index(classId).containsKey(key);}
    public byte[] text(String bundle,String name)throws IOException{BundleIndex b=bundle(bundle);UnitySerializedFile.ObjectInfo o=b.find(49,name);UnityBinary r=new UnityBinary(b.file.read(o),b.file.littleEndian);r.string();return r.bytes(r.count(64*1024*1024,"TextAsset bytes"));}
    public Texture texture(String bundle,String name)throws IOException{
        BundleIndex b=bundle(bundle);UnitySerializedFile.ObjectInfo o=b.find(28,name);UnityBinary r=new UnityBinary(b.file.slice(o,0,Math.min(o.size,4096)),b.file.littleEndian);
        String actual=r.string();int width=r.i32(),height=r.i32();r.i32();int format=r.i32(),mips=r.i32();r.u8();r.align();r.skip(32);int size=r.i32();
        if(width<=0||height<=0||(long)width*height>16777216||mips<1||mips>32||size<0)throw new IOException("Invalid Unity texture geometry");
        int needed;if(format==10||format==12)needed=((width+3)/4)*((height+3)/4)*(format==10?8:16);else if(format==1||format==3||format==4||format==5||format==14)needed=width*height*(format==1?1:format==3?3:4);else throw new IOException("Unsupported Unity TextureFormat "+format);
        byte[] data;
        if(size==0){long offset=r.u32(),bytes=r.u32();String source=r.string();if(bytes<needed)throw new IOException("Truncated streamed Unity texture");UnityBundle.Entry stream=b.bundle.find(source);Binary.range(offset,needed,stream.length);data=b.bundle.read(stream.offset+offset,needed);}
        else{if(size<needed)throw new IOException("Truncated inline Unity texture");data=b.file.slice(o,r.position,needed);}
        return new Texture(actual,width,height,format,mips,data);
    }
    public Crx.Image image(String bundle,String name)throws IOException{return texture(bundle,name).image();}
    public Audio audio(String bundle,String name)throws IOException{
        BundleIndex b=bundle(bundle);return readAudio(b,b.find(83,name));
    }
    private static Audio readAudio(BundleIndex b,UnitySerializedFile.ObjectInfo o)throws IOException{
        UnityBinary r=new UnityBinary(b.file.read(o),b.file.littleEndian);String actual=r.string();r.i32();int channels=r.i32(),rate=r.i32(),bits=r.i32();float duration=r.f32();r.u8();r.align();int sub=r.i32();r.skip(3);r.align();String source=r.string();long offset=r.i64(),size=r.i64();int format=r.i32();
        if(channels<1||channels>8||rate<1000||rate>384000||size<0||size>128*1024*1024||!Float.isFinite(duration)||duration<0)throw new IOException("Invalid Unity audio metadata");byte[] data;
        if(source.isEmpty())data=b.file.slice(o,r.position,(int)size);else{UnityBundle.Entry stream=b.bundle.find(source);Binary.range(offset,size,stream.length);data=b.bundle.read(stream.offset+offset,(int)size);}
        return new Audio(actual,channels,rate,bits,format,sub,duration,data);
    }
    public synchronized void clearCaches(){for(BundleIndex index:bundles.values())index.bundle.clearCache();}
    private static int rgb565(int color){int r=color>>>11,g=color>>>5&63,b=color&31;return ((r<<3|r>>>2)<<16)|((g<<2|g>>>4)<<8)|(b<<3|b>>>2);}
    private static int mix(int a,int b,int wa,int wb,int div){return ((((a>>>16&255)*wa+(b>>>16&255)*wb)/div)<<16)|((((a>>>8&255)*wa+(b>>>8&255)*wb)/div)<<8)|(((a&255)*wa+(b&255)*wb)/div);}
    private static void decodeDxt(byte[] src,byte[] dst,int width,int height,boolean dxt5)throws IOException{
        int p=0;for(int y=0;y<height;y+=4)for(int x=0;x<width;x+=4){int[] alpha=new int[8];long abits=0;if(dxt5){alpha[0]=src[p++]&255;alpha[1]=src[p++]&255;for(int i=0;i<6;i++)abits|=(long)(src[p++]&255)<<(8*i);if(alpha[0]>alpha[1])for(int i=2;i<8;i++)alpha[i]=((8-i)*alpha[0]+(i-1)*alpha[1])/7;else{for(int i=2;i<6;i++)alpha[i]=((6-i)*alpha[0]+(i-1)*alpha[1])/5;alpha[6]=0;alpha[7]=255;}}
            int c0=(src[p++]&255)|(src[p++]&255)<<8,c1=(src[p++]&255)|(src[p++]&255)<<8;int[] c={rgb565(c0),rgb565(c1),0,0};boolean transparent=!dxt5&&c0<=c1;c[2]=mix(c[0],c[1],transparent?1:2,1,transparent?2:3);c[3]=transparent?0:mix(c[0],c[1],1,2,3);long indices=0;for(int i=0;i<4;i++)indices|=(long)(src[p++]&255)<<(8*i);
            for(int j=0;j<16;j++){int xx=x+j%4,yy=y+j/4,ix=(int)(indices>>>(j*2)&3);if(xx>=width||yy>=height)continue;int color=c[ix],a=dxt5?alpha[(int)(abits>>>(j*3)&7)]:transparent&&ix==3?0:255,q=((height-1-yy)*width+xx)*4;dst[q]=(byte)color;dst[q+1]=(byte)(color>>>8);dst[q+2]=(byte)(color>>>16);dst[q+3]=(byte)a;}
        }
    }
}
