package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Read-only SC native menu captions beside localized story text; no original EXE execution. */
public final class ScSceneReplayCaptionAudit {
    private static int checks;
    private static void check(boolean ok,String text)throws IOException{checks++;if(!ok)throw new IOException(text);}
    private static byte[] caption(byte[] bytes){return bytes.length>2&&bytes[0]=='@'&&bytes[1]=='s'?Arrays.copyOfRange(bytes,2,bytes.length):bytes;}
    private static String hash(String text)throws IOException{return Binary.sha256(text.getBytes("UTF-8"));}
    private static Set<String> actions(DesktopLegacy e){Set<String> result=new LinkedHashSet<String>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())result.add(c.action);return result;}
    private static void settle(DesktopLegacy e,boolean story)throws Exception{
        for(int n=0;n<10000;n++){
            e.tick(1000);Dc2Host h=e.host();if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()||story&&h.textWaiting&&!h.body.isEmpty())return;
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }
        throw new IOException("SC UI did not settle "+e.vm().scriptName()+"@"+Integer.toHexString(e.vm().offset()));
    }
    private static void choose(DesktopLegacy e,String action,boolean story)throws Exception{check(actions(e).contains(action),"Original menu action "+action+" is present");e.choose(action);settle(e,story);}
    private static List<String> expected(MesScript script)throws IOException{
        List<String> result=new ArrayList<String>();for(MesScript.Record r:script.records())if(r.payloadOwner>=0&&script.profile.operation(script.at(r.payloadOwner).opcode).equals("ui.button_text"))result.add(TextCodec.cp932(caption(r.bytes)));return result;
    }
    private static List<String> shown(PcHost host){List<String> result=new ArrayList<String>();for(Dc2Ui.Button b:host.ui.buttons)for(Dc2Ui.Quad q:b.sprites)if(q.type==2)result.add(q.text);return result;}
    private static Map<String,Object> page(DesktopLegacy e,CircusResources data,Path out,String name,boolean baseline)throws Exception{
        MesScript source=data.scripts().script(e.vm().scriptName());List<String> original=expected(source),actual=shown((PcHost)e.host());
        check(!original.isEmpty()&&original.size()==actual.size(),"Actual native menu instantiates its expected caption fields");
        List<String> sourceHashes=new ArrayList<String>(),shownHashes=new ArrayList<String>();int wrong=0;for(int n=0;n<original.size();n++){sourceHashes.add(hash(original.get(n)));shownHashes.add(hash(actual.get(n)));if(!original.get(n).equals(actual.get(n)))wrong++;}
        check(baseline?wrong>0:wrong==0,baseline?"Old runtime reproduces incorrectly decoded native captions":"All actual native menu captions match independent raw CP932 source");
        BufferedImage frame=e.render();try{check(frame.getWidth()==800&&frame.getHeight()==600,"Scene replay uses the actual800x600 game renderer");ImageIO.write(frame,"png",out.resolve(name+".png").toFile());}finally{frame.flush();}
        return Json.obj("script",source.name,"source_sha256",source.sha256,"captions",actual.size(),"CP932_expected_sha256",sourceHashes,"actual_caption_sha256",shownHashes,"incorrect_fields",wrong,"frame_sha256",Binary.sha256(Files.readAllBytes(out.resolve(name+".png"))));
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2||args.length>3)throw new IOException("Require readonly_SC_GAME owned_OUTPUT [--baseline]");boolean baseline=args.length==3&&args[2].equals("--baseline");
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        GameResources files=new GameResources(new LocalGameStorage(game));Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2sc-3b69"),"Exact native SC profile required");CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.select(files,profile,"auto","");check(codec.charset.name().equalsIgnoreCase("GBK"),"Localized source selects GBK prose independently of native UI");
        Map<String,Object> report=Json.obj("profile",profile.id,"baseline",baseline,"original_executable_run",false,"original_game_files_written",false,"original_prose_exported",false,"media_completion_simulated",true,"scope","Known localized SC corpus. Synthetic owned scene-seen flags expose original gallery labels; no claim these are original completed progress. Native51 menu captions and original natural gallery input; no original-EXE pixel/font fidelity comparison.");
        List<Object> sourceFiles=new ArrayList<Object>(),pages=new ArrayList<Object>();report.put("source_files",sourceFiles);report.put("pages",pages);int nonASCII=0,captionFiles=0;ScHost direct=new ScHost(data,codec,1);direct.attach(new Variables(direct,profile.variableModel));
        try{
            for(String path:new TreeSet<String>(files.paths()))if(path.toLowerCase(Locale.ROOT).startsWith("advdata/mes/")&&path.toLowerCase(Locale.ROOT).endsWith(".mes")){
                byte[] raw=files.read(path,64*1024*1024);MesScript script;try{script=new MesScript(path,raw,profile);}catch(IOException malformed){if(path.toLowerCase(Locale.ROOT).endsWith("/drm_0621_b1_kko.mes")){report.put("known_truncated_script_excluded",Json.obj("path",path,"sha256",Binary.sha256(raw),"error",malformed.getMessage()));continue;}throw malformed;}
                int count=0;for(MesScript.Record record:script.records())if(record.payloadOwner>=0&&profile.operation(script.at(record.payloadOwner).opcode).equals("ui.button_text")){
                    byte[] label=caption(record.bytes);boolean high=false;for(byte b:label)if(b<0)high=true;if(!high)continue;
                    String expected=TextCodec.cp932(label);direct.ui.buttons.clear();direct.ui.buttons.add(new Dc2Ui.Button());direct.position(script,record.payloadOwner);direct.command("ui.button_text",script.instruction(record.payloadOwner).args,record.bytes);Dc2Ui.Quad q=direct.ui.last().sprites.get(0);
                    check(q.text.equals(baseline?codec.decode(label):expected),"Production51 caption follows its selected field encoding");check(q.width==record.bytes.length*14+1,"Native raw-byte caption width preserved");count++;nonASCII++;
                }
                if(count>0){captionFiles++;sourceFiles.add(Json.obj("path",path,"bytes",raw.length,"sha256",Binary.sha256(raw),"nonASCII_51_captions",count));check(Binary.sha256(files.read(path,raw.length)).equals(Binary.sha256(raw)),"Caption source file remains unchanged");}
            }
            check(captionFiles==53&&nonASCII==2733,"All53 reviewed native-menu files and2733 nonASCII51 fields covered");report.put("caption_source_files",captionFiles);report.put("nonASCII_51_captions",nonASCII);
            DesktopLegacy e=new DesktopLegacy(data,codec,false);PcHost host=(PcHost)e.host();for(int n=0;n<1024;n++)host.write(98,n,1);settle(e,false);if(actions(e).contains("03"))choose(e,"03",false);if(actions(e).contains("window"))choose(e,"window",false);choose(e,"extra",false);choose(e,"scene",false);
            check(e.vm().scriptName().equals("af_otm_00"),"Natural title/extra/scene enters the reported Otome replay page");pages.add(page(e,data,out,"01-otome-first",baseline));
            String firstDate=shown(host).get(3);check(baseline?!firstDate.equals("03月14日"):firstDate.equals("03月14日"),"The reported first-date field is independently checked");report.put("first_date_expected_utf8_sha256",hash("03月14日"));report.put("first_date_actual_utf8_sha256",hash(firstDate));byte[] saved=e.snapshot();
            choose(e,"next",false);check(e.vm().scriptName().equals("af_otm_01"),"Original next-page button transfers normally");pages.add(page(e,data,out,"02-otome-next",baseline));
            e.restore(saved);check(e.vm().scriptName().equals("af_otm_00"),"Snapshot restores the original menu script and Unicode caption fields");pages.add(page(e,data,out,"03-restored",baseline));
            choose(e,"00",true);check(e.host().textWaiting&&!e.body().isEmpty(),"Original replay action reaches localized story through the same GBK codec");report.put("replay_story",Json.obj("script",e.vm().scriptName(),"pc",e.vm().offset(),"body_sha256",hash(e.body())));
            report.put("result",baseline?"OLD_BEHAVIOR_REPRODUCED":"PASS");
        }catch(Exception failure){report.put("result","FAIL");report.put("error",failure.toString());throw failure;}finally{report.put("checks",checks);Files.write(out.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));}
        System.out.println(Json.text(Json.obj("result",report.get("result"),"checks",checks,"caption_source_files",captionFiles,"nonASCII_51_captions",nonASCII)));
    }
}
