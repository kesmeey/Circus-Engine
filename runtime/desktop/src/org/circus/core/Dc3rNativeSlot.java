package org.circus.core;

import java.io.*;
import java.util.*;
import org.circus.core.NativeProto.Field;
import org.circus.core.NativeProto.Message;

/** Read-only translation of DC3R's fixed32/protobuf SaveGame, not a generic serializer. */
public final class Dc3rNativeSlot {
    public final String caption,scriptName;
    public final long created;
    public final int position;
    private final Message game,script,memory,graphics,scene,audio;
    private static final String[] CATEGORY={"system","bg","event","stand","face","bgm","se","voice","anim"};
    public Dc3rNativeSlot(byte[] input)throws IOException{
        Message save=NativeProto.framed(input,8*1024*1024);only(save,1,2,3,4);
        game=save.required(1).message();only(game,1,2,3,4,5);
        script=game.required(1).message();memory=game.required(2).message();graphics=game.required(3).message();scene=game.required(4).message();audio=game.required(5).message();
        only(script,1,2,3,4,5,6,7,8);scriptName=CircusScrScript.normalizeName(str(script,8,""));position=num(script,5,0);
        if(scriptName.isEmpty()||position<0)throw new IOException("Invalid native script position");
        caption=str(save,3,"");long raw=save.required(4).int64(),ticks=raw&0x3fffffffffffffffL;
        if(ticks>3155378975999999999L)throw new IOException("Invalid native save date");
        // Local DateTime.ToBinary stores UTC ticks under its kind bits; UTC is already UTC.
        created=(ticks-621355968000000000L)/10000;
        // Native SaveGame constructs an empty History, and native Load ignores it.
        if(save.optional(2)!=null)save.optional(2).message();
    }
    void apply(DesktopUnity engine)throws IOException{
        Dc3rHost h=engine.host;only(memory,1,2);h.flags.keySet().removeIf(k->k<655360);
        for(Map.Entry<Integer,Message> e:intMap(memory,1,100000).entrySet()){
            int flag=e.getKey();if(flag<0||flag>=655360)throw new IOException("Global flag in native local memory: "+flag);
            h.flags.put(flag,num(e.getValue(),2,0));
        }
        h.flags.put((5<<16)+1,1); // GameMemory.RestoreLocalMemorystate always enables AllowSkip.
        // Validate the chapter caption even when SaveTitle was separately serialized.
        str(memory,2,"");
        restoreVm(engine);
        only(scene,1,2,3,4,5,6,7,8,9,10,11,12);
        rejectPresent(scene,9,"loaded animation pack");rejectPresent(scene,10,"pending action entity");
        for(Field wait:scene.fields(6)){int v=wait.int32();if(v<0||v>12)throw new IOException("Invalid native wait type");}
        if(!scene.fields(6).isEmpty())throw new IOException("Native slot contains suspended system-script waits");
        if(scene.optional(11)!=null)real(scene,11,0);
        if(scene.optional(4)!=null){applyGraphics(engine,scene.optional(4).message());h.command(37,new CircusScrVm.Value[0],engine.script);}
        applyGraphics(engine,graphics);
        int[] face=point(message(scene,1)),from=point(message(scene,2)),offset=point(message(scene,3));
        h.faceX=face[0];h.faceY=face[1];h.fromX=from[0];h.fromY=from[1];h.putX=offset[0];h.putY=offset[1];
        for(Map.Entry<Integer,Message> e:intMap(scene,8,4096).entrySet())h.colors.put(e.getKey(),color(message(e.getValue(),2),0xff000000));
        h.choices.clear();if(scene.fields(5).size()>50)throw new IOException("Native choice count limit");
        for(Field f:scene.fields(5)){Message choice=f.message();only(choice,1,2);String label=str(choice,1,"");if(!label.isEmpty())new CircusScrScript(scriptName,engine.data.script(scriptName)).label(label);h.choices.add(new Dc3rHost.Choice(label,str(choice,2,"")));}
        // SetSceneSettings follows RestoreGraphicsState in the original loader.
        sakura(h,message(scene,7));h.rain=bool(scene,12,false);
        restoreAudio(engine);
        // LoadSaveGameState clears waits and calls FadeUpdate, which hides the old message window.
        h.command(49,new CircusScrVm.Value[0],engine.script);h.textVisible=false;h.speaker=h.body="";
        h.menuWaiting=h.choiceWaiting=false;h.transitionRequested=false;h.transitionDuration=0;h.transitionMask="";h.screenBreakType=-1;
        h.ticket++;h.sceneRevision++;h.transitionRevision++;
    }
    private void restoreVm(DesktopUnity e)throws IOException{
        CircusScrScript code=new CircusScrScript(scriptName,e.data.script(scriptName));
        if(position>code.instructions.length)throw new IOException("Native script position outside "+scriptName+": "+position);
        List<Integer> expr=integers(script,1);List<Field> params=script.fields(2);if(expr.size()>65536||params.size()>65536)throw new IOException("Native VM stack limit");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x43535631);out.writeInt(CircusScrVm.Status.RUNNING.ordinal());out.writeLong(0);out.writeLong(0);out.writeInt(0);
        out.writeUTF(scriptName);out.writeInt(position);out.writeInt(num(script,3,0));out.writeInt(num(script,4,0));out.writeInt(num(script,6,0));out.writeUTF(str(script,7,""));
        // Deserialize constructs Stack(array); LoadSaveGameState then DeepClone reverses
        // it once more through ToArray/new Stack. Match that final bottom-to-top order.
        out.writeInt(expr.size());for(int i=expr.size()-1;i>=0;i--)out.writeInt(expr.get(i));out.writeInt(params.size());
        // CsData.IsFlag affects ToString's diagnostic notation only. Native script
        // actions read CsData.Val directly; the flag identifier is not dereferenced here.
        for(int i=params.size()-1;i>=0;i--){Message p=params.get(i).message();only(p,1,2,3,4);boolean string=bool(p,3,false);bool(p,4,false);out.writeBoolean(string);if(string)out.writeUTF(str(p,2,""));else out.writeInt(num(p,1,0));}
        e.script.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private static void applyGraphics(DesktopUnity e,Message g)throws IOException{
        only(g,1,2,3,4,5);Dc3rHost h=e.host;
        for(int i=0;i<13;i++){h.layers[i]=new Dc3rHost.Layer();h.layers[i].priority=i;}
        h.works.clear();Message wc=message(g,3);only(wc,1);
        if(wc!=null){Set<String> seen=new HashSet<>();for(Field f:wc.fields(1)){
            Message entry=f.message();only(entry,1,2);String key=str(entry,1,"");if(key.isEmpty()||!seen.add(key))throw new IOException("Invalid native work key");Message work=entry.required(2).message();only(work,1);String ref=reference(0,str(work,1,""));e.data.texture(ref);h.works.put(key,ref);if(seen.size()>4096)throw new IOException("Native work count limit");
        }}
        for(Map.Entry<Integer,Message> pair:intMap(g,1,13).entrySet()){
            int slot=pair.getKey();if(slot<0||slot>=13)throw new IOException("Native layer index "+slot);
            Message m=message(pair.getValue(),2);if(m!=null)h.layers[slot]=layer(e,m,slot);
        }
        h.back=new Dc3rHost.Panel();h.fore=new Dc3rHost.Panel();h.fogBack=new Dc3rHost.Panel();h.fogFore=new Dc3rHost.Panel();h.menu=new Dc3rHost.Panel();
        for(Map.Entry<Integer,Message> pair:intMap(g,2,8).entrySet()){
            int slot=pair.getKey();if(slot<0||slot>7)throw new IOException("Native object group "+slot);
            // Native FadeUpdate discards message/name/icon/toolbar groups on loading.
            if(slot>=3&&slot<=5)continue;
            Dc3rHost.Panel p=panel(e,message(pair.getValue(),2),0);
            switch(slot){case 0:h.back=p;break;case 1:h.fore=p;break;case 2:h.fogFore=p;break;case 6:h.menu=p;break;case 7:h.fogBack=p;break;default:break;}
        }
        sakura(h,message(g,4));h.rain=bool(g,5,false);
    }
    private static Dc3rHost.Layer layer(DesktopUnity e,Message m,int slot)throws IOException{
        only(m,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19);
        Dc3rHost.Layer l=new Dc3rHost.Layer();l.priority=slot;if(!bool(m,14,false))return l;
        float[] current=vector(message(m,17)),source=vector(message(m,4)),target=vector(message(m,5)),scale=vector(message(m,6)),center=vector(message(m,7));
        if(!near(scale[0],scale[1])||scale[0]<0||scale[0]>100)throw new IOException("Native nonuniform/invalid layer scale");
        l.scale=Math.round(scale[0]*100);if(!near(l.scale/100f,scale[0]))throw new IOException("Native layer scale precision");
        l.pivotX=rounded(-center[0]);l.pivotY=rounded(center[1]);l.x=rounded(current[0]+center[0]);l.y=rounded(600-current[1]-center[1]);l.priority=rounded(-current[2]);
        // PositionOffset is already baked into CurrentPosition; it must not be applied twice.
        vector(message(m,8));int rgba=color(message(m,9),0xffffffff);
        if(rgba==0xffffffff)l.tint=0;else if(rgba==0xffffe3cf||rgba==0xffffe3d0)l.tint=1;else if(rgba==0xffccccff)l.tint=2;else throw new IOException("Unsupported native layer color 0x"+Integer.toHexString(rgba));
        int type=num(m,16,0);String first=str(m,1,""),second=str(m,2,"");str(m,3,"");
        // Native StandardLayer cannot serialize a render texture. A fresh layer falls back to black.
        if(first.equals("(RT)")){l.resource="bg/black";l.second="";}else{l.resource=reference(type,first);l.second=second.isEmpty()?"":reference(type,second);}
        e.data.texture(l.resource);if(!l.second.isEmpty())e.data.texture(l.second);
        float duration=real(m,10,0),elapsed=real(m,11,0);if(duration<0||duration>86400||elapsed<0||elapsed>172800)throw new IOException("Invalid native layer movement time");
        if(bool(m,12,false)&&duration>elapsed){l.moveX0=rounded(source[0]+center[0]);l.moveY0=rounded(600-source[1]-center[1]);l.moveX1=rounded(target[0]+center[0]);l.moveY1=rounded(600-target[1]-center[1]);l.moveTime=Math.round(duration*1000);l.moveElapsed=Math.round(elapsed*1000);l.ease=bool(m,18,false);}
        if(num(m,19,0)!=0)throw new IOException("Native pending layer shake is unsupported");
        // RefreshImmediate clears NeedFadeOut on native load; DontResetPos is forced true.
        bool(m,13,false);bool(m,15,false);return l;
    }
    private static Dc3rHost.Panel panel(DesktopUnity e,Message m,int depth)throws IOException{
        Dc3rHost.Panel p=new Dc3rHost.Panel();if(m==null)return p;only(m,1);if(depth>8||m.fields(1).size()>4096)throw new IOException("Native object group limit");
        for(Field f:m.fields(1)){
            Message obj=f.message();int[] pos=point(message(obj,1));Field subtype=null;
            for(Field a:obj.fields())if(a.number!=1){if(subtype!=null||a.number<500||a.number>506)throw new IOException("Invalid native object subtype");subtype=a;}
            if(subtype==null)throw new IOException("Missing native object subtype");Message v=subtype.message();
            switch(subtype.number){
            case 500:{only(v,1,2,3,4);Dc3rHost.Quad q=new Dc3rHost.Quad();q.x=pos[0];q.y=pos[1];q.work=str(v,2,"");String resource=e.host.works.get(q.work);if(resource==null)throw new IOException("Undefined native work "+q.work);Message rect=message(v,1);only(rect,1,2,3,4);q.sx=rounded(real(rect,1,0));q.sy=rounded(real(rect,2,0));q.width=rounded(real(rect,3,0));q.height=rounded(real(rect,4,0));if(q.sx<0||q.sy<0||q.width<0||q.height<0||q.width>32768||q.height>32768)throw new IOException("Invalid native put rectangle");q.opaque=!bool(v,3,false);if(!q.opaque)throw new IOException("Native transparent put requires unsupported opacity");Message anime=message(v,4);if(anime!=null){only(anime,1,2,3,4,5,6);rejectPresent(anime,5,"embedded action animation");rejectPresent(anime,6,"embedded pack animation");q.animation=num(anime,1,0);if((q.animation&15)>4)throw new IOException("Unsupported native put animation "+q.animation);q.animA=num(anime,2,0);q.animB=num(anime,3,0);q.animC=num(anime,4,0);}p.quads.add(q);break;}
            case 502:{only(v,1,2,3,4,5,6,7,8,9,10,11,12,13);rejectPresent(v,4,"animated string");if(num(v,8,0)!=0||bool(v,10,false)||bool(v,13,false))throw new IOException("Unsupported native string layout");Dc3rHost.Text t=new Dc3rHost.Text();t.x=pos[0];t.y=pos[1];t.leading=num(v,1,23);t.style=num(v,2,0);t.text=str(v,3,"");t.width=num(v,6,570);t.fontSize=num(v,12,23);if(t.fontSize==0)t.fontSize=23;if(t.fontSize<1||t.fontSize>200||t.width<0||t.width>32768)throw new IOException("Invalid native text size");int color=color(message(v,11),0xffffffff);if(color!=0xffffffff)t.color=color;p.texts.add(t);break;}
            default:throw new IOException("Native persistent object subtype "+subtype.number+" is unsupported");
            }
        }return p;
    }
    private void restoreAudio(DesktopUnity e)throws IOException{
        only(audio,1);Dc3rHost h=e.host;int[] channel={0,5,2,3,4,1};for(int ch=0;ch<6;ch++){h.audio[ch]="";h.audioActive[ch]=h.audioLoop[ch]=false;h.audioGeneration[ch]++;}
        for(Map.Entry<Integer,Message> pair:intMap(audio,1,6).entrySet()){
            int nativeChannel=pair.getKey();if(nativeChannel<0||nativeChannel>=6)throw new IOException("Invalid native audio channel");Message m=message(pair.getValue(),2);only(m,1,2,3,4,5);String name=str(m,1,"");real(m,2,1);real(m,3,0);
            if(bool(m,4,false)&&bool(m,5,false)){if(name.isEmpty())throw new IOException("Active native audio has no resource");int ch=channel[nativeChannel];String ref=(ch==0?"bgm/":ch==5?"voice/":"se/")+safe(name);e.data.audio(ref);h.audio[ch]=ref;h.audioActive[ch]=h.audioLoop[ch]=true;}
        }
    }
    private static void sakura(Dc3rHost h,Message m)throws IOException{only(m,1,2,3);h.sakura=num(m,1,0);h.sakuraX=num(m,2,0);h.sakuraY=num(m,3,0);}
    private static String reference(int type,String name)throws IOException{if(type<0||type>=CATEGORY.length||type>=5&&type<=7)throw new IOException("Invalid native image type "+type);return CATEGORY[type]+"/"+safe(name);}
    private static String safe(String value)throws IOException{if(value.isEmpty()||value.length()>2048)throw new IOException("Invalid native resource name");return Binary.safePath(value.replace('\\','/')).toLowerCase(Locale.ROOT);}
    private static Map<Integer,Message> intMap(Message m,int field,int max)throws IOException{Map<Integer,Message> map=new LinkedHashMap<>();if(m!=null)for(Field f:m.fields(field)){Message e=f.message();only(e,1,2);int key=num(e,1,0);if(map.put(key,e)!=null)throw new IOException("Duplicate native dictionary key "+key);if(map.size()>max)throw new IOException("Native dictionary limit");}return map;}
    private static List<Integer> integers(Message m,int field)throws IOException{List<Integer> values=new ArrayList<>();for(Field f:m.fields(field)){if(f.wire==0)values.add(f.int32());else{byte[] bytes=f.bytes();int at=0;while(at<bytes.length){long value=0;boolean end=false;for(int n=0;n<10;n++){if(at==bytes.length)throw new IOException("Truncated native packed integer");int b=bytes[at++]&255;if(n==9&&(b&0xfe)!=0)throw new IOException("Native packed integer overflow");value|=(long)(b&127)<<(7*n);if((b&128)==0){end=true;break;}}int signed=(int)value;if(!end||value!=signed&&value!=(signed&0xffffffffL))throw new IOException("Native packed int32 overflow");values.add(signed);if(values.size()>65536)throw new IOException("Native VM stack limit");}}if(values.size()>65536)throw new IOException("Native VM stack limit");}return values;}
    private static Message message(Message m,int field)throws IOException{return m==null||m.optional(field)==null?null:m.optional(field).message();}
    private static int num(Message m,int field,int fallback)throws IOException{return m==null||m.optional(field)==null?fallback:m.optional(field).int32();}
    private static float real(Message m,int field,float fallback)throws IOException{return m==null||m.optional(field)==null?fallback:m.optional(field).float32();}
    private static String str(Message m,int field,String fallback)throws IOException{return m==null||m.optional(field)==null?fallback:m.optional(field).string(32768);}
    private static boolean bool(Message m,int field,boolean fallback)throws IOException{int v=num(m,field,fallback?1:0);if(v!=0&&v!=1)throw new IOException("Invalid native boolean");return v!=0;}
    private static int[] point(Message m)throws IOException{only(m,1,2);return new int[]{num(m,1,0),num(m,2,0)};}
    private static float[] vector(Message m)throws IOException{only(m,1,2,3);return new float[]{real(m,1,0),real(m,2,0),real(m,3,0)};}
    private static int color(Message m,int fallback)throws IOException{if(m==null)return fallback;only(m,1,2,3,4);return component(real(m,4,1))<<24|component(real(m,1,0))<<16|component(real(m,2,0))<<8|component(real(m,3,0));}
    private static int component(float n)throws IOException{if(n<0||n>1)throw new IOException("Invalid native color");return Math.round(n*255);}
    private static int rounded(float n)throws IOException{if(!Float.isFinite(n)||Math.abs(n)>1000000)throw new IOException("Native geometry limit");return Math.round(n);}
    private static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
    private static void only(Message m,int...allowed)throws IOException{if(m!=null)for(Field f:m.fields()){boolean ok=false;for(int n:allowed)if(n==f.number){ok=true;break;}if(!ok)throw new IOException("Unsupported native field "+f.number);}}
    private static void rejectPresent(Message m,int field,String what)throws IOException{if(m!=null&&m.optional(field)!=null)throw new IOException("Native save contains "+what);}
}
