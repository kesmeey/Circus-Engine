package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Isolated audit for the reported DC2PC Dacapo BGM allocation failure. */
public final class Dc2PcAudioMemoryAudit {
    public static final String SCRIPT="1ind_0120_e1_skr";
    private final DesktopLegacy engine;
    private final StringBuilder log=new StringBuilder();
    private int checks;
    private static volatile byte[][] pressure;

    public Dc2PcAudioMemoryAudit(DesktopLegacy engine){this.engine=engine;}
    public static String prepareScene(DesktopLegacy engine,Path output)throws Exception{return new Dc2PcAudioMemoryAudit(engine).prepareScene(output);}
    private void check(boolean ok,String description)throws IOException{
        if(!ok)throw new IOException(description+" at "+engine.scriptName()+" @0x"+Integer.toHexString(engine.scriptOffset()));
        checks++;log.append("PASS ").append(description).append('\n');
    }
    private void step()throws Exception{
        for(int channel=0;channel<6;channel++)if(!engine.audioLoop(channel))engine.audioCompleted(channel,engine.audioGeneration(channel));
        engine.mediaCompleted(engine.ticket());engine.tick(1000);
    }
    private List<String> actions(){List<String> result=new ArrayList<>();for(DesktopEngine.Control c:engine.controls())result.add(c.action);return result;}
    private void settleTitle()throws Exception{
        for(int turn=0;turn<3000;turn++){
            step();
            if(engine.menuWaiting()){
                List<String> actions=actions();
                if(actions.contains("03")){engine.choose("03");continue;}
                if(actions.contains("start"))return;
                throw new IOException("Unexpected title actions: "+actions);
            }
            engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
            engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("Original DC2PC title did not settle");
    }
    private void shot(Path output,String name)throws IOException{
        engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.TEXT_COMPLETE);engine.playbackMillis(600000);
        BufferedImage image=engine.render();
        try(OutputStream out=Files.newOutputStream(output.resolve(name+".png"))){ImageIO.write(image,"png",out);}finally{image.flush();}
    }
    /** Runs START first, then an explicit audit-only native transfer to the reported script.
     * This is a scene reproduction, not a claim that the complete route was played. */
    public String prepareScene(Path output)throws Exception{
        Files.createDirectories(output);check(engine.resources.profileId.equals("dc2pc-5769"),"DC2PC profile");settleTitle();
        check(engine.menuWaiting()&&actions().contains("start"),"original START initialization and title");
        shot(output,"title");
        // Equivalent to the VM's mode-1 transfer, while retaining original START UI setup.
        check(engine.host().beforeTransfer(1,SCRIPT),"native scene-transfer reset");engine.vm().enterSystem(SCRIPT);
        boolean before=false,at=false;
        for(int turn=0;turn<5000;turn++){
            step();
            if(!engine.scriptName().equalsIgnoreCase(SCRIPT))throw new IOException("Unexpected transfer before reported scene: "+engine.scriptName());
            int pc=engine.scriptOffset();
            if(engine.textWaiting()&&pc>=0x23ea&&pc<=0x23ed&&!before){
                check(engine.body().contains("平静下来后"),"reported preceding narration");shot(output,"before-bgm42");before=true;
            }
            if(engine.textWaiting()&&pc>=0x244a&&pc<=0x244d){
                check(before,"preceding narration executed before BGM change");
                check(engine.audio(0).equalsIgnoreCase(engine.resources.named("music",42))&&engine.audioActive(0),"original opcode selects BGM 42");
                check(engine.body().contains("已经不能和义之一起了吗"),"next dialogue reached at the reported BGM change");
                shot(output,"at-bgm42");Files.write(output.resolve("at-bgm42.state"),engine.snapshot());at=true;break;
            }
            if(engine.menuWaiting())throw new IOException("Unexpected menu within reported script: "+actions());
            engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.TEXT_COMPLETE);
            engine.vm().signalHost(engine.ticket(),MesVm.HostSignal.ADVANCE);
        }
        check(at,"reported script reaches 0x244a/0x244d");
        log.append("SCENE_REPRO direct transfer after original title; media completion simulated; no original EXE launched\n");
        Files.write(output.resolve("scene-result.json"),(Json.text(Json.obj("pass",true,"checks",checks,"script",engine.scriptName(),"pc",engine.scriptOffset(),"music",engine.audio(0),"images",new ArrayList<>(engine.loadedImages),"direct_transfer_after_title",true,"media_completion_simulated",true,"windows_game_executed",false))+"\n").getBytes("UTF-8"));
        Files.write(output.resolve("scene-result.txt"),log.toString().getBytes("UTF-8"));return log.toString();
    }

    private static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private static String hash(Path file)throws IOException{
        MessageDigest digest=sha256();byte[] block=new byte[65536];
        try(InputStream in=Files.newInputStream(file)){for(int n;(n=in.read(block))>=0;)if(n>0)digest.update(block,0,n);}return hex(digest.digest());
    }
    private static void le(OutputStream out,long value,int count)throws IOException{for(int n=0;n<count;n++)out.write((int)(value>>>(8*n)));}
    /** Independent RIFF-header construction and source hashing; never materializes a full track. */
    private static String expectedWave(Archive.Source source,byte[] header)throws IOException{
        long length=Binary.u32(header,4);int channels=Binary.u16(header,14),rate=(int)Binary.u32(header,16),bits=Binary.u16(header,26);
        ByteArrayOutputStream head=new ByteArrayOutputStream(44);head.write(new byte[]{82,73,70,70});le(head,length+36,4);head.write(new byte[]{87,65,86,69,102,109,116,32});le(head,16,4);le(head,1,2);le(head,channels,2);le(head,rate,4);le(head,rate*channels*bits/8,4);le(head,channels*bits/8,2);le(head,bits,2);head.write(new byte[]{100,97,116,97});le(head,length,4);
        MessageDigest digest=sha256();digest.update(head.toByteArray());
        for(long offset=0;offset<length;){int count=(int)Math.min(65536,length-offset);byte[] block=source.read(28+offset,count);if(block.length!=count)throw new EOFException("Truncated expected PCM");digest.update(block);offset+=count;}return hex(digest.digest());
    }
    /** Real 042 plus the largest local DC2PC track, with optional retained heap pressure. */
    public static String verifyAudio(CircusResources resources,Path output,int pressureMiB)throws Exception{
        if(!resources.profileId.equals("dc2pc-5769"))throw new IOException("Expected DC2PC");
        if(pressureMiB<0||pressureMiB>160)throw new IOException("Audit pressure must be 0..160 MiB");
        Files.createDirectories(output);StringBuilder result=new StringBuilder();List<Object> rows=new ArrayList<>();
        // Small separate arrays avoid making the test about VM large-object placement.
        pressure=new byte[pressureMiB][];for(int n=0;n<pressure.length;n++)pressure[n]=new byte[1024*1024];
        try{
            for(int track:new int[]{42,43}){
                String reference=resources.named("music",track),path="AdvData/"+reference.replace('\\','/')+".PCM";
                Archive.Source source=resources.files.source(path);byte[] header=source.read(0,28);
                if(Binary.u32(header,0)!=0x4d435058L||header[8]!=0)throw new IOException("Expected original raw XPCM for track "+track);
                long length=Binary.u32(header,4);String expected=expectedWave(source,header);Path wav=output.resolve("music-"+track+".wav");long start=System.nanoTime(),elapsed;String actual;long wavLength;
                try{
                    try(OutputStream out=Files.newOutputStream(wav)){resources.writeAudio(reference,out);}
                    elapsed=System.nanoTime()-start;actual=hash(wav);wavLength=Files.size(wav);if(wavLength!=length+44||!expected.equals(actual))throw new IOException("Streamed audio differs for track "+track);
                }finally{Files.deleteIfExists(wav);}
                rows.add(Json.obj("track",track,"source",reference,"pcm_bytes",length,"wav_bytes",wavLength,"sha256",actual,"write_ms",elapsed/1e6));
                result.append("PASS track ").append(track).append(" PCM ").append(length).append(" WAV ").append(wavLength).append(" SHA256 ").append(actual).append('\n');
            }
            result.append("DC2PC AUDIO STREAM PASS TRACKS 2 PRESSURE_MIB ").append(pressureMiB).append(" HEAP_LIMIT ").append(Runtime.getRuntime().maxMemory()).append('\n');
            Files.write(output.resolve("audio-result.json"),(Json.text(Json.obj("pass",true,"pressure_mib",pressureMiB,"heap_limit",Runtime.getRuntime().maxMemory(),"tracks",rows,"audio_playback_verified",false))+"\n").getBytes("UTF-8"));
            Files.write(output.resolve("audio-result.txt"),result.toString().getBytes("UTF-8"));return result.toString();
        }finally{pressure=null;}
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("game-directory output-directory [scene|audio|all] [pressure-MiB]");
        Path output=Paths.get(args[1]);String mode=args.length>2?args[2]:"all";int pressure=args.length>3?Integer.parseInt(args[3]):0;
        GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])),"@none");CircusResources resources=new CircusResources(files,Profiles.get("dc2pc-5769"));
        System.setProperty("dc.saveRoot",output.resolve("saves").toString());
        if(mode.equals("audio")||mode.equals("all"))System.out.print(verifyAudio(resources,output.resolve("audio"),pressure));
        if(mode.equals("scene")||mode.equals("all"))System.out.print(new Dc2PcAudioMemoryAudit(new DesktopLegacy(resources,new TextCodec("","","GBK"),true)).prepareScene(output.resolve("scene")));
    }
}
