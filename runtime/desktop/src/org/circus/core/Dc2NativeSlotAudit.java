package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only actual DC2 Windows slot admission and resource/PC projection.
 * Loading the resulting plan is a separate Session/platform integration test.
 * No original executable is launched and no user dialogue is emitted. */
public final class Dc2NativeSlotAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require readonly_DC2_GAME owned_OUTPUT");
        Path game=Paths.get(args[0]).toRealPath(),out=DesktopRouteAudit.outputOutsideGame(game,args[1]);
        GameResources files=new GameResources(new LocalGameStorage(game),"dc2.exe");CircusResources data=new CircusResources(files,Profiles.get("dc2-3363"));
        List<String> paths=new ArrayList<>();Path savedata=game.resolve("AdvData/SaveData");if(Files.isDirectory(savedata))try(DirectoryStream<Path> stream=Files.newDirectoryStream(savedata)){for(Path path:stream)if(Files.isRegularFile(path)&&path.getFileName().toString().matches("(?i)dc2[0-9]+\\.dat")){if(!path.toRealPath().startsWith(game))throw new IOException("Native slot link outside selected game");paths.add(game.relativize(path).toString().replace('\\','/'));}}Collections.sort(paths);check(!paths.isEmpty(),"selected DC2 directory contains original story slots");
        List<Object> slots=new ArrayList<>();Set<String> unique=new HashSet<>();int images=0;
        for(String path:paths){
            byte[] input;try(InputStream in=Files.newInputStream(game.resolve(path))){input=Binary.read(in,1024*1024);}String hash=Binary.sha256(input);Dc2NativeSlot slot=Dc2NativeSlot.parse(input);MesScript caller=data.scripts().script(slot.scriptName());Dc2NativeSlot.Plan plan=slot.normalTextPlan(caller);
            check(Arrays.equals(input,slot.originalBytes()),"original bytes roundtrip without rewrite");check(slot.frameBytes().length==8004,"original frame is8004 bytes");
            check(plan.savedWaitKind==7&&plan.nativeWaitKind==-1,"actual ordinary wait is restored by replaying a text checkpoint");
            check(plan.checkpointOpcode==0x34||plan.checkpointOpcode==0x4b,"actual supported voice/body checkpoint class");
            check(plan.resumeCodePc==plan.checkpointCodePc&&plan.resumeFilePc==caller.versionOffset+plan.checkpointCodePc,"actual resume uses header table plus version offset");
            check(plan.resumeCodePc!=plan.savedRawCodePc,"actual native checkpoint cursor differs from saved post-wait raw PC");
            check(slot.ordinaryBytes().length==1000,"independent ordinary byte bank retained");
            int[] log=slot.activeLogWords();check(log.length>0&&(log[log.length-1]&0x7fff)==plan.checkpointIndex,"actual selected checkpoint is represented in saved log");
            for(int word:log)check((word&0x7fff)<caller.headerOffsets.length,"saved log checkpoint exists in independently identified caller");
            List<Object> graphics=new ArrayList<>();
            for(Dc2NativeSlot.StringField field:new Dc2NativeSlot.StringField[]{Dc2NativeSlot.StringField.BACKGROUND,Dc2NativeSlot.StringField.ACTOR1,Dc2NativeSlot.StringField.ACTOR2,Dc2NativeSlot.StringField.OVERLAY1,Dc2NativeSlot.StringField.OVERLAY2,Dc2NativeSlot.StringField.RESOURCE3,Dc2NativeSlot.StringField.RESOURCE4,Dc2NativeSlot.StringField.RESOURCE5}){
                String resource=slot.resource(field);if(resource.isEmpty())continue;Crx.Image image=data.image(resource);check(image.width>0&&image.height>0,"original slot resource decodes");images++;
                graphics.add(Json.obj("field",field.toString(),"resource",resource,"width",image.width,"height",image.height,"decoded_sha256",Binary.sha256(image.pixels)));
            }
            int music=slot.dword(Dc2NativeSlot.Dword.LOGICAL_MUSIC);if(music>=0)check(!data.named("music",music).isEmpty(),"original music index resolves in selected catalog");
            unique.add(hash);slots.add(Json.obj("path",path,"source_sha256",hash,"input_bytes",input.length,"script",plan.script,"script_sha256",plan.sha256,"version_offset",caller.versionOffset,"saved_raw_code_pc",plan.savedRawCodePc,"checkpoint_index",plan.checkpointIndex,"checkpoint_opcode",plan.checkpointOpcode,"checkpoint_code_pc",plan.checkpointCodePc,"resume_code_pc",plan.resumeCodePc,"resume_file_pc",plan.resumeFilePc,"native_wait_kind",plan.nativeWaitKind,"candidate_flag",plan.candidateFlag,"argument0",plan.argument0,"argument1",plan.argument1,"saved_log_count",log.length,"graphics",graphics,"logical_music",music,"effect_kind",slot.dword(Dc2NativeSlot.Dword.EFFECT_KIND),"effect_strength",slot.dword(Dc2NativeSlot.Dword.EFFECT_STRENGTH)));
        }
        Map<String,Object> result=Json.obj("pass",true,"checks",checks,"profile",data.profileId,"target_sha256",Binary.sha256(files.read("dc2.exe",64*1024*1024)),"slot_count",paths.size(),"unique_slot_images",unique.size(),"resource_images_decoded",images,"slots",slots,"original_exe_executed",false,"original_game_directory_written",false,"scope","Version-bound actual frame parsing, checkpoint projection and referenced resource admission. Not a claim that parser alone restores a playable VM; Session/platform must consume this plan and replay to the following07.");
        Files.write(out.resolve("result.json"),(Json.text(result)+"\n").getBytes("UTF-8"));System.out.println("Dc2NativeSlotAudit PASS "+checks+"; original slots="+paths.size()+" resources="+images);
    }
}
