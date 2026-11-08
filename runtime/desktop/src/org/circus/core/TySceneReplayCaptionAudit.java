package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Read-only TY58 replay captions beside GBK dialogue, through original menus. */
public final class TySceneReplayCaptionAudit {
    private static int checks;
    private static void check(boolean ok,String why)throws IOException{checks++;if(!ok)throw new IOException(why);}
    private static String hash(String value)throws IOException{return Binary.sha256(value.getBytes("UTF-8"));}
    private static Set<String> actions(DesktopLegacy e){Set<String> a=new LinkedHashSet<>();for(DesktopEngine.Control c:e.controls())if(!c.action.isEmpty())a.add(c.action);return a;}
    private static void settle(DesktopLegacy e,boolean story)throws Exception{
        for(int n=0;n<10000;n++){e.tick(1000);Dc2Host h=e.host();if(e.vm().status()==MesVm.Status.FAULT)throw new IOException(e.vm().error());
            if(e.menuWaiting()||story&&h.textWaiting&&!h.body.isEmpty())return;
            if(h.presentationWaiting){e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);continue;}
            for(int c=0;c<6;c++)if(e.audioActive(c)&&!e.audioLoop(c))e.audioCompleted(c,e.audioGeneration(c));
            if(!e.movie().isEmpty())e.vm().signalHost(e.ticket(),MesVm.HostSignal.MEDIA_COMPLETE);
            if(h.remainingMillis()<0)e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);
        }throw new IOException("TY replay UI did not settle");
    }
    private static void choose(DesktopLegacy e,String action,boolean story)throws Exception{check(actions(e).contains(action),"Original native action is present: "+action);e.choose(action);settle(e,story);}
    private static Map<Integer,List<byte[]>> sourceTitles(MesScript source)throws IOException{
        Map<Integer,List<byte[]>> map=new LinkedHashMap<>();int id=-1;
        for(MesScript.Record r:source.records()){
            String op=source.profile.operation(r.opcode);if(op.equals("pc.grid.item"))id=source.instruction(r.offset).args[0];
            if(r.payloadOwner>=0&&source.profile.operation(source.at(r.payloadOwner).opcode).equals("pc.grid.caption")){
                if(id<0)throw new IOException("Caption has no preceding native list entry");List<byte[]> list=map.get(id);if(list==null){list=new ArrayList<>();map.put(id,list);}list.add(r.bytes);
            }
        }return map;
    }
    private static Map<String,Object> page(DesktopLegacy e,CircusResources data,Path out,String name,boolean baseline)throws Exception{
        PcHost h=(PcHost)e.host();MesScript source=data.scripts().script(e.vm().scriptName());Map<Integer,List<byte[]>> original=sourceTitles(source);List<Object> rows=new ArrayList<>();int wrong=0,fields=0;
        for(ScriptList.Item item:h.list.items){List<byte[]> pair=original.get(item.id);check(pair!=null&&pair.size()==2,"Each original scenario has locked and unlocked native caption branches");
            String locked=TextCodec.cp932(pair.get(0)),expected=TextCodec.cp932(pair.get(1));check(locked.replace("？","").isEmpty(),"The first source branch is the native locked-title mask");check(h.read(98,item.id)!=0,"Owned audit marks this original scenario unlocked: "+item.id+" action="+item.action+" seen="+h.read(98,item.id));
            for(Dc2Ui.Quad q:item.sprites)if(q.type==2){fields++;if(!q.text.equals(expected))wrong++;check(q.text.equals(baseline?e.codec.decode(pair.get(1)):expected),"Active native caption follows its field-specific encoding");}
            rows.add(Json.obj("id",item.id,"action",item.action,"expected_CP932_sha256",hash(expected),"native_date_sha256",hash(PcHost.sceneListDate(h.sceneListName(item)))));
        }
        check(fields==h.list.items.size()*2&&fields>0,"All normal/hover text layers were checked");check(baseline?wrong==fields:wrong==0,baseline?"Old GBK path reproduces every active replay title error":"Every active replay title matches independent CP932 source");
        BufferedImage image=e.render();try{check(image.getWidth()==800&&image.getHeight()==600,"Original TY replay canvas rendered");ImageIO.write(image,"png",out.resolve(name+".png").toFile());}finally{image.flush();}
        return Json.obj("script",source.name,"pc",e.vm().offset(),"source_sha256",source.sha256,"items",h.list.items.size(),"text_layers",fields,"wrong_fields",wrong,"rows",rows,"frame_sha256",Binary.sha256(Files.readAllBytes(out.resolve(name+".png"))));
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2||args.length>3)throw new IOException("Require readonly_TY_GAME owned_OUTPUT [--baseline]");boolean baseline=args.length==3&&args[2].equals("--baseline");Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toString());System.setProperty("dc.cacheRoot",out.resolve("cache").toString());
        GameResources files=new GameResources(new LocalGameStorage(game),"dc2ty_chinese(simplified)_patch.exe");Profile profile=GameVersion.detect(files).profile();check(profile.id.equals("dc2ty-5f69"),"Independent TY profile required");CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.select(files,profile,"auto","");check(codec.charset.name().equalsIgnoreCase("GBK"),"Known localized body selects GBK");
        Map<String,Object> report=Json.obj("profile",profile.id,"baseline",baseline,"original_executable_run",false,"original_game_files_written",false,"original_prose_exported",false,"media_completion_simulated",true,"scope","TY58 replay captions; actual native title/extra/scene/next actions. All scenario-seen flags are synthetic isolated audit state, not original completed progress. No original-EXE pixel/font/audio equivalence claim.");List<Object> sourceFiles=new ArrayList<>(),pages=new ArrayList<>();report.put("source_files",sourceFiles);report.put("pages",pages);
        try{
            PcHost direct=new TyHost(data,codec,1);direct.attach(new Variables(direct,profile.variableModel));int fields=0;
            for(String path:new TreeSet<>(files.paths()))if(path.startsWith("advdata/mes/")&&path.endsWith(".mes")){
                byte[] raw=files.read(path,64*1024*1024);MesScript script=new MesScript(path,raw,profile);int count=0;
                for(MesScript.Record r:script.records())if(r.payloadOwner>=0&&profile.operation(script.at(r.payloadOwner).opcode).equals("pc.grid.caption")){
                    String expected=TextCodec.cp932(r.bytes);direct.list.clear();direct.list.add(new int[]{0,0,0,0,80,60,10,0},10);direct.position(script,r.payloadOwner);int[] a=script.instruction(r.payloadOwner).args;direct.command("pc.grid.caption",a,r.bytes);
                    for(int n=1;n<=2;n++){Dc2Ui.Quad q=direct.list.last().sprites.get(n);check(q.text.equals(baseline?codec.decode(r.bytes):expected),"Production TY58 field follows reviewed source encoding");check(q.width==r.bytes.length*14&&q.x==a[0]&&q.y==a[1],"Native raw-byte width and caption origin retained");}fields++;count++;
                }
                if(count>0){sourceFiles.add(Json.obj("path",path,"bytes",raw.length,"sha256",Binary.sha256(raw),"caption_fields",count));check(Binary.sha256(files.read(path,raw.length)).equals(Binary.sha256(raw)),"Original caption source remains byte-identical");}
            }
            check(fields==258&&sourceFiles.size()==4,"All4 original replay files and258 native caption fields checked");report.put("caption_source_files",sourceFiles.size());report.put("caption_fields",fields);
            DesktopLegacy e=new DesktopLegacy(data,codec,true);settle(e,false);choose(e,"02",false);PcHost host=(PcHost)e.host();for(int n=0;n<1024;n++)host.write(98,n,1);choose(e,"extra",false);choose(e,"scene",false);check(e.vm().scriptName().equals("sce_hyd"),"Natural native Scene Replay opens Hydrangea list");pages.add(page(e,data,out,"01-hyd-first",baseline));
            for(String group:new String[]{"set","mhr","myu"}){choose(e,group,false);check(e.vm().scriptName().equals("sce_"+group),"Original category action selects its actual source menu");pages.add(page(e,data,out,"01-"+group+"-first",baseline));}choose(e,"hyd",false);byte[] first=e.snapshot();
            choose(e,"next",false);pages.add(page(e,data,out,"02-hyd-next",baseline));DesktopLegacy restored=new DesktopLegacy(data,codec,true);for(int n=0;n<1024;n++)((PcHost)restored.host()).write(98,n,1);restored.restore(first);pages.add(page(restored,data,out,"03-cold-restored",baseline));e=restored;
            for(int n=0;n<3;n++)choose(e,"next",false);pages.add(page(e,data,out,"04-hyd-target-page",baseline));host=(PcHost)e.host();check(host.list.items.get(9).id==39,"Fourth native page row9 binds catalog39");check(host.sceneListName(host.list.items.get(9)).equals("hyd_b_028_pst"),"Original row binds the exact reported script");choose(e,"@slot:9",true);
            MesScript exact=data.scripts().script("hyd_b_028_pst");MesScript.Record body=exact.at(0x1f2c),wait=exact.at(body.end);byte[] decoded=exact.nativeText(body);check(body.opcode==0x4c&&decoded.length==61,"Exact reported location is the native61-byte body");check(wait.opcode==7,"Source body is followed by the original message wait");String expected=codec.decode(decoded);check(e.vm().scriptName().equals(exact.name)&&e.body().equals(expected)&&e.vm().offset()==wait.end,"Original natural replay reaches the reported text and following native wait through production VM/host");
            try{TextCodec.identity().decode(decoded);throw new IOException("Japanese codec incorrectly accepted the reported invalid byte sequence");}catch(IOException invalid){check(invalid.getMessage().startsWith("Invalid "),"Explicit Japanese codec fails strictly at the same source record");}
            BufferedImage frame=e.render();try{ImageIO.write(frame,"png",out.resolve("05-reported-body-GBK.png").toFile());}finally{frame.flush();}
            report.put("reported_body",Json.obj("script",exact.name,"script_sha256",exact.sha256,"instruction_pc",body.offset,"successful_vm_pc",e.vm().offset(),"native_bytes",decoded.length,"GBK_body_sha256",hash(e.body()),"native_menu_actions",Arrays.asList("02","extra","scene","next","next","next","@slot:9"),"Japanese_decode_rejected",true));report.put("result",baseline?"OLD_BEHAVIOR_REPRODUCED":"PASS");
        }catch(Exception failure){report.put("result","FAIL");report.put("error",failure.toString());throw failure;}finally{report.put("checks",checks);Files.write(out.resolve("report.json"),(Json.text(report)+"\n").getBytes("UTF-8"));}
        System.out.println(Json.text(Json.obj("result",report.get("result"),"checks",checks,"caption_files",sourceFiles.size(),"caption_fields",report.get("caption_fields"))));
    }
}
