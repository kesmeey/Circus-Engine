package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Read-only original-save conversion checks; all generated files stay in the supplied audit directory. */
public final class Dc3rNativeSlotAudit {
    private static int checks;
    private static void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);checks++;System.out.println("PASS "+why);}
    private static void varint(OutputStream out,long value)throws IOException{while((value&~127L)!=0){out.write((int)value&127|128);value>>>=7;}out.write((int)value);}
    private static byte[] scalar(int wire,long value)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();if(wire==0)varint(out,value);else for(int n=0;n<(wire==5?4:8);n++)out.write((int)(value>>>(8*n)));return out.toByteArray();}
    private static byte[] frame(byte[] bytes){byte[] out=new byte[bytes.length+4];for(int n=0;n<4;n++)out[n]=(byte)(bytes.length>>>(n*8));System.arraycopy(bytes,0,out,4,bytes.length);return out;}
    private static void field(OutputStream out,int number,int wire,byte[] payload)throws IOException{varint(out,((long)number<<3)|wire);if(wire==2)varint(out,payload.length);out.write(payload);}
    private static byte[] payload(NativeProto.Field field)throws IOException{switch(field.wire){case 0:return scalar(0,field.int64());case 2:return field.bytes();case 5:return scalar(5,Float.floatToRawIntBits(field.float32()));case 1:return scalar(1,Double.doubleToRawLongBits(field.float64()));default:throw new IOException("Unexpected fixture wire type");}}
    /** Replace a single first matching nested field, retaining all unrelated original fields. */
    private static byte[] replace(byte[] bytes,int[] path,int at,int wire,byte[] replacement)throws IOException{
        NativeProto.Message message=NativeProto.framed(frame(bytes),16*1024*1024);ByteArrayOutputStream out=new ByteArrayOutputStream();boolean done=false;
        for(NativeProto.Field f:message.fields()){
            if(!done&&f.number==path[at]){done=true;if(at+1==path.length)field(out,f.number,wire,replacement);else field(out,f.number,2,replace(f.bytes(),path,at+1,wire,replacement));}
            else field(out,f.number,f.wire,payload(f));
        }
        if(!done){if(at+1!=path.length)throw new IOException("Fixture path missing");field(out,path[at],wire,replacement);}
        return out.toByteArray();
    }
    private static byte[] mutate(byte[] nativeFile,int[] path,int wire,byte[] bytes)throws IOException{return frame(replace(Arrays.copyOfRange(nativeFile,4,nativeFile.length),path,0,wire,bytes));}
    private static void little(OutputStream out,long value,int count)throws IOException{for(int i=0;i<count;i++)out.write((int)(value>>>(8*i)));}
    private static void align(ByteArrayOutputStream out){while((out.size()&3)!=0)out.write(0);}
    /** One synthetic TextAsset patch; original data remains read-only and is never written out. */
    private static byte[] textBundle(String name,byte[] content)throws IOException{
        ByteArrayOutputStream object=new ByteArrayOutputStream();byte[] label=name.getBytes("UTF-8");little(object,label.length,4);object.write(label);align(object);little(object,content.length,4);object.write(content);
        ByteArrayOutputStream metadata=new ByteArrayOutputStream();metadata.write("5.5.0f3\0".getBytes("US-ASCII"));little(metadata,5,4);metadata.write(0);little(metadata,1,4);little(metadata,49,4);metadata.write(new byte[19]);little(metadata,1,4);align(metadata);little(metadata,1,8);little(metadata,0,4);little(metadata,object.size(),4);little(metadata,0,4);
        ByteArrayOutputStream asset=new ByteArrayOutputStream();DataOutputStream a=new DataOutputStream(asset);a.writeInt(metadata.size());a.writeInt(20+metadata.size()+object.size());a.writeInt(17);a.writeInt(20+metadata.size());a.writeInt(0);a.write(metadata.toByteArray());a.write(object.toByteArray());
        ByteArrayOutputStream index=new ByteArrayOutputStream();DataOutputStream i=new DataOutputStream(index);i.write(new byte[16]);i.writeInt(1);i.writeInt(asset.size());i.writeInt(asset.size());i.writeShort(0);i.writeInt(1);i.writeLong(0);i.writeLong(asset.size());i.writeInt(0);i.writeBytes("CAB-import-test\0");
        ByteArrayOutputStream bundle=new ByteArrayOutputStream();DataOutputStream b=new DataOutputStream(bundle);b.writeBytes("UnityFS\0");b.writeInt(6);b.writeBytes("5.x.x\0");b.writeBytes("5.5.0f3\0");b.writeLong(46+index.size()+asset.size());b.writeInt(index.size());b.writeInt(index.size());b.writeInt(64);b.write(index.toByteArray());b.write(asset.toByteArray());return bundle.toByteArray();
    }
    private static void versionCheck(UnityCircusResources resources,byte[] nativeFile)throws Exception{
        byte[] changed=resources.script("start").clone();changed[changed.length-1]^=1;final byte[] patch=textBundle("start",changed);final GameResources base=resources.files;final String patchPath=resources.unity.directory+"patch0";
        GameResources.Storage storage=new GameResources.Storage(){
            public Set<String> paths(){Set<String> all=new HashSet<>(base.paths());all.add(patchPath);return all;}
            public byte[] read(String path,int limit)throws IOException{if(path.equals(patchPath)){if(patch.length>limit)throw new IOException("Fixture patch size");return patch.clone();}return base.read(path,limit);}
            public Archive.Source source(final String path)throws IOException{if(!path.equals(patchPath))return base.source(path);return new Archive.Source(){public long size(){return patch.length;}public byte[] read(long offset,int count)throws IOException{Binary.range(offset,count,patch.length);return Arrays.copyOfRange(patch,(int)offset,(int)offset+count);}};}
        };
        UnityCircusResources variant=new UnityCircusResources(new GameResources(storage,"@none"));check(Arrays.equals(changed,variant.script("start")),"in-memory target variant changes exactly one START byte");
        try{DesktopUnity.convertNative(variant,nativeFile,null);throw new IOException("Version guard accepted modified START");}catch(IOException expected){check(expected.getMessage().contains("尚未验证"),"slots-only import rejects an unverified target version before conversion");}
    }
    private static void reject(UnityCircusResources resources,byte[] nativeFile,byte[] persistent,String why)throws Exception{
        try{DesktopUnity.convertNative(resources,nativeFile,persistent);throw new IOException("Accepted invalid import: "+why);}catch(IOException expected){if(expected.getMessage().startsWith("Accepted invalid import:"))throw expected;check(true,why+" rejected: "+expected.getMessage());}
    }
    private static void media(DesktopUnity engine)throws Exception{for(int ch=0;ch<6;ch++)if(engine.audioActive(ch)&&!engine.audioLoop(ch))engine.audioCompleted(ch,engine.audioGeneration(ch));if(!engine.movie().isEmpty())engine.mediaCompleted(engine.ticket());}
    private static final Set<String> prepared=new HashSet<>();
    private static void controls(DesktopUnity e)throws IOException{for(Dc3rHost.Button b:e.host.buttons())for(List<Dc3rHost.Quad> qs:Arrays.asList(b.normal,b.hover,b.pressed))for(Dc3rHost.Quad q:qs){String ref=q.resource.isEmpty()?e.host.works.get(q.work):q.resource;if(ref!=null&&!ref.isEmpty()&&!ref.startsWith("@capture/")&&prepared.add(ref))e.data.image(ref);}}
    private static void frame(DesktopUnity e)throws Exception{e.tick(1000);for(int ch=0;ch<6;ch++)if(e.audioActive(ch)&&prepared.add("audio:"+e.audio(ch)))e.data.audio(e.audio(ch));media(e);BufferedImage image=e.render();image.flush();controls(e);}
    private static void menu(DesktopUnity e)throws Exception{for(int n=0;n<2000;n++){frame(e);if(e.menuWaiting())return;if(e.script.status()==CircusScrVm.Status.WAIT_CLICK)e.click(1,1,false);}throw new IOException("Original import menu timeout");}
    private static DesktopUnity originalLoad(UnityCircusResources resources,DesktopSave save,byte[] persistent,String name,int position)throws Exception{
        DesktopUnity e=new DesktopUnity(resources,true);e.host.loadPersistent(new DataInputStream(new ByteArrayInputStream(persistent)));Files.createDirectories(e.saveDirectory);Files.write(e.saveDirectory.resolve("slot-101.sav"),save.encode());
        // A fresh instance observes newly imported slot metadata just as a newly launched Activity does.
        e=new DesktopUnity(resources,true);e.host.loadPersistent(new DataInputStream(new ByteArrayInputStream(persistent)));
        for(int n=0;n<20;n++){menu(e);boolean title=false;for(DesktopEngine.Control c:e.controls())if(c.action.equals("sce_start")){title=true;break;}if(title)break;boolean selected=false;for(String action:new String[]{"window","01"})for(DesktopEngine.Control c:e.controls())if(c.action.equals(action)){e.choose(action);selected=true;break;}if(!selected)throw new IOException("Unexpected original startup menu");}
        e.choose("load");menu(e);boolean slot=false;for(Dc3rHost.Button b:e.host.buttons())if(b.action.equals("selsave")&&b.flagValue==-2){e.click((b.left+b.right)/2,(b.top+b.bottom)/2,false);slot=true;break;}check(slot,"original LOAD exposes imported automatic slot");
        for(int n=0;n<1000;n++){frame(e);if(e.scriptName().equals(name)&&e.scriptOffset()==position){check(true,"original title LOAD and confirmation restore imported native position");return e;}if(e.menuWaiting()){boolean confirm=false;for(DesktopEngine.Control c:e.controls())if(c.action.equals(":y")){e.choose(":y");confirm=true;break;}if(!confirm)throw new IOException("Original LOAD did not show confirmation");}}
        throw new IOException("Original LOAD failed to resume native slot");
    }
    private static String pixelHash(BufferedImage image){int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(pixels.length*4);for(int color:pixels)bytes.putInt(color);return Binary.sha256(bytes.array());}
    public static void main(String[] args)throws Exception{
        if(args.length<3||args.length>5)throw new IllegalArgumentException("Dc3rNativeSlotAudit GAME_DIRECTORY NATIVE_SAVE OUTPUT_DIRECTORY [RANGE_PACK] [BASE_PACK]");
        Path output=Paths.get(args[2]).toAbsolutePath();Files.createDirectories(output);System.setProperty("dc.saveRoot",output.resolve("phone-saves").toString());
        GameResources.Storage original=new LocalGameStorage(Paths.get(args[0]));final RangeStorage recording=args.length>3?RangeStorage.recording(original):null;GameResources.Storage storage=recording==null?original:recording;
        if(args.length>4){
            final RangeStorage base=RangeStorage.replay(Paths.get(args[4]));final GameResources.Storage fallback=storage;
            storage=new GameResources.Storage(){
                public Set<String> paths(){return fallback.paths();}
                public byte[] read(String path,int limit)throws IOException{try{return base.read(path,limit);}catch(IOException absent){return fallback.read(path,limit);}}
                public Archive.Source source(final String path)throws IOException{
                    return new Archive.Source(){
                        public long size()throws IOException{try{return base.source(path).size();}catch(IOException absent){return fallback.source(path).size();}}
                        public byte[] read(long offset,int length)throws IOException{try{return base.source(path).read(offset,length);}catch(IOException absent){return fallback.source(path).read(offset,length);}}
                    };
                }
            };
        }
        try{
        UnityCircusResources resources=new UnityCircusResources(new GameResources(storage,"@none"));byte[] nativeFile=Files.readAllBytes(Paths.get(args[1]));String sourceHash=Binary.sha256(nativeFile);
        NativeProto.Message script=NativeProto.framed(nativeFile,16*1024*1024).required(1).message().required(1).message();String expectedName=CircusScrScript.normalizeName(script.required(8).string(1024));int expectedPosition=script.required(5).int32();
        Dc3rHost phone=new Dc3rHost();phone.writeFlag((99<<16)+7,6);phone.writeFlag((100<<16)+7,1);phone.viewedCg.add("phone-only-cg");phone.viewedScripts.add("phone-only-scene");ByteArrayOutputStream bytes=new ByteArrayOutputStream();phone.savePersistent(new DataOutputStream(bytes));byte[] persistent=bytes.toByteArray();
        String property=System.getProperty("dc.saveRoot");boolean hadSaveDirectory=Files.exists(output.resolve("phone-saves"));
        DesktopSave converted=DesktopUnity.convertNative(resources,nativeFile,persistent);check(converted.state.length>0,"native slot produces runtime state");check(converted.preview.length>0,"converted slot has a preview");
        check(property.equals(System.getProperty("dc.saveRoot")),"conversion does not change process save root");
        check(hadSaveDirectory||!Files.exists(output.resolve("phone-saves")),"conversion does not create a save directory");
        Dc3rNativeSlot metadata=new Dc3rNativeSlot(nativeFile);check(converted.caption.equals(metadata.caption)&&converted.created==metadata.created,"native title and timestamp retained");
        for(long position:converted.position)check(position==0,"native audio has no stored playback cursor");
        Files.write(output.resolve("converted.sav"),converted.encode());DesktopSave decoded=DesktopSave.decode(converted.encode());check(Arrays.equals(decoded.state,converted.state),"converted save container checksum roundtrip");
        DesktopUnity engine=originalLoad(resources,decoded,persistent,expectedName,expectedPosition);
        check(engine.scriptName().equals(expectedName)&&engine.scriptOffset()==expectedPosition,"original script instruction restored exactly");check(engine.script.callDepth()==0,"native restore clears quick-script callers");
        check(engine.host.readFlag((99<<16)+7)==6&&engine.host.readFlag((100<<16)+7)==1,"phone audio preferences and completion survive slot restore");
        check(engine.host.viewedCg.contains("phone-only-cg")&&engine.host.viewedScripts.contains("phone-only-scene"),"phone gallery and scene sets survive import");
        for(int ch=0;ch<6;ch++)check(!engine.audioActive(ch),"inactive and non-looping native channel "+ch+" stops on restore");
        BufferedImage initial=engine.render();ImageIO.write(initial,"png",output.resolve("restored-scene.png").toFile());initial.flush();
        int dialogues=0;String prior="";for(int turn=0;turn<3000&&dialogues<5;turn++){
            frame(engine);
            if(engine.textWaiting()){String key=engine.scriptName()+":"+engine.scriptOffset()+":"+engine.ticket();if(!key.equals(prior)){prior=key;dialogues++;System.out.println("DIALOGUE "+key+" "+engine.body());}if(dialogues<5)engine.click(1,1,false);}
            else if(engine.menuWaiting())throw new IOException("Unexpected imported menu "+engine.scriptName());else if(engine.script.status()==CircusScrVm.Status.WAIT_CLICK)engine.click(1,1,false);
        }
        check(dialogues==5,"native slot advances to five original dialogue waits");byte[] roundtrip=engine.snapshot();String body=engine.body(),name=engine.scriptName();int position=engine.scriptOffset();BufferedImage before=engine.render();String beforeHash=pixelHash(before);before.flush();engine.click(1,1,false);engine.tick(1000);engine.restore(roundtrip);BufferedImage after=engine.render();check(body.equals(engine.body())&&name.equals(engine.scriptName())&&position==engine.scriptOffset()&&beforeHash.equals(pixelHash(after)),"imported playthrough can save/restore exact position and pixels");ImageIO.write(after,"png",output.resolve("dialogue-5.png").toFile());after.flush();
        reject(resources,Arrays.copyOf(nativeFile,nativeFile.length-1),persistent,"truncated native file");
        reject(resources,mutate(nativeFile,new int[]{1,1,5},0,scalar(0,Integer.MAX_VALUE)),persistent,"instruction outside original script");
        reject(resources,mutate(nativeFile,new int[]{1,1,8},2,"../start".getBytes("UTF-8")),persistent,"unsafe script name");
        reject(resources,mutate(nativeFile,new int[]{1,3,1,2,6,1},5,scalar(5,0x7fc00000)),persistent,"non-finite layer scale");
        reject(resources,mutate(nativeFile,new int[]{1,2,1,1},0,scalar(0,(99<<16)+7)),persistent,"global flag smuggled into local memory");
        reject(resources,mutate(nativeFile,new int[]{1,3,1,2,6,1},5,scalar(5,Float.floatToIntBits(2f))),persistent,"nonuniform native layer scale");
        reject(resources,mutate(nativeFile,new int[]{1,4,10},2,new byte[0]),persistent,"unsupported pending native action");
        byte[] loop=mutate(nativeFile,new int[]{1,5,1,2,5},0,scalar(0,1));DesktopSave loopSave=DesktopUnity.convertNative(resources,loop,persistent);engine.restore(loopSave.state);
        check(engine.audioActive(0)&&engine.audioLoop(0)&&engine.audio(0).equals("bgm/bgm001")&&loopSave.position[0]==0,"native looping BGM restarts at beginning");
        byte[] transformed=nativeFile;
        int[][] transformPaths={{1,3,1,2,17,1},{1,3,1,2,17,2},{1,3,1,2,7,1},{1,3,1,2,7,2},{1,3,1,2,6,1},{1,3,1,2,6,2},{1,3,1,2,8,1}};
        float[] transformValues={10,590,2,3,.5f,.5f,99};for(int i=0;i<transformPaths.length;i++)transformed=mutate(transformed,transformPaths[i],5,scalar(5,Float.floatToRawIntBits(transformValues[i])));
        DesktopSave transformedSave=DesktopUnity.convertNative(resources,transformed,persistent);engine.restore(transformedSave.state);Dc3rHost.Layer layer=engine.host.layers[0];
        double left=layer.x+layer.pivotX*(1-layer.scale/100.0),top=layer.y+layer.pivotY*(1-layer.scale/100.0);
        check(left==11&&top==8.5,"native current position, scale and mesh origin match Unity coordinates without duplicating offset");
        byte[] disabledSkip=mutate(nativeFile,new int[]{1,2,1,2},0,scalar(0,0));engine.restore(DesktopUnity.convertNative(resources,disabledSkip,persistent).state);check(engine.host.readFlag((5<<16)+1)==1,"native loader re-enables AllowSkip");
        ByteArrayOutputStream withStacks=new ByteArrayOutputStream();for(NativeProto.Field f:script.fields())if(f.number!=1&&f.number!=2)field(withStacks,f.number,f.wire,payload(f));
        field(withStacks,1,0,scalar(0,10));field(withStacks,1,0,scalar(0,20));
        ByteArrayOutputStream number=new ByteArrayOutputStream();field(number,1,0,scalar(0,7));field(number,4,0,scalar(0,1));field(withStacks,2,2,number.toByteArray());
        ByteArrayOutputStream string=new ByteArrayOutputStream();field(string,2,2,"parameter".getBytes("UTF-8"));field(string,3,0,scalar(0,1));field(withStacks,2,2,string.toByteArray());
        byte[] nonemptyStacks=mutate(nativeFile,new int[]{1,1},2,withStacks.toByteArray());engine.restore(DesktopUnity.convertNative(resources,nonemptyStacks,persistent).state);
        ByteArrayOutputStream vmBytes=new ByteArrayOutputStream();engine.script.save(new DataOutputStream(vmBytes));DataInputStream vm=new DataInputStream(new ByteArrayInputStream(vmBytes.toByteArray()));
        vm.readInt();vm.readInt();vm.readLong();vm.readLong();check(vm.readInt()==0,"native stack conversion has no quick-script frame");vm.readUTF();vm.readInt();vm.readInt();vm.readInt();vm.readInt();vm.readUTF();
        check(vm.readInt()==2&&vm.readInt()==20&&vm.readInt()==10,"expression stack order matches native deserialize then DeepClone");
        check(vm.readInt()==2&&vm.readBoolean()&&vm.readUTF().equals("parameter")&&!vm.readBoolean()&&vm.readInt()==7,"parameter order and IsFlag preserve native raw Val without dereferencing");
        versionCheck(resources,nativeFile);
        check(sourceHash.equals(Binary.sha256(Files.readAllBytes(Paths.get(args[1])))),"original native save remains byte-identical");System.out.println("PASS "+checks+" native slot checks; audio/movie completion simulated; original EXE not executed");
        }finally{if(recording!=null){recording.write(Paths.get(args[3]));System.out.println("RECORDED_RANGE_BYTES "+recording.recordedBytes());}}
    }
}
