package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;
import org.json.JSONObject;

/** Explicit device support action. Default is preview; apply also requires an
 * existing configured library entry and the caller's exact source SHA256. */
public final class DeviceProgressImportInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private static Map<String,Object> counts(NativeProgress.Summary s){Map<String,Object> m=new LinkedHashMap<>();m.put("completion",s.completion);m.put("cg",s.cg);m.put("scenes",s.scenes);m.put("music",s.music);m.put("total",s.total);return m;}
    @Override public void onStart(){Bundle result=new Bundle();Map<String,Object> report=new LinkedHashMap<>();Path output=null;try{
        String id=args.getString("library_id"),profile=args.getString("profile_id"),relative=Binary.safePath(args.getString("source")),expected=args.getString("source_sha256");boolean apply="true".equals(args.getString("apply"));
        if(id==null||profile==null||expected==null||!expected.matches("[0-9a-f]{64}"))throw new IOException("Require explicit library_id, profile_id, source and source_sha256");
        GameLibrary.Entry entry=null;for(GameLibrary.Entry candidate:new GameLibrary(getTargetContext()).entries())if(candidate.id.equals(id)){entry=candidate;break;}
        if(entry==null||!entry.configured||!profile.equals(entry.profile))throw new IOException("Configured target game does not match explicit request");
        NativeSaveImport.Request request=NativeSaveImport.inspect(getTargetContext(),entry);if(!request.sources.contains(relative))throw new IOException("Source is not a recognized system progress path in the selected game");
        NativeSaveImport.Preview preview=request.prepare(relative);if(!preview.sourceHash.equals(expected))throw new IOException("Explicit source hash does not match; no import performed");
        output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"device-progress-support-");
        report.put("profile",profile);report.put("source",relative);report.put("source_sha256",expected);report.put("source_flags",counts(preview.progress.source));report.put("preview_additions",counts(preview.additions));report.put("apply_requested",apply);
        if(apply){NativeSaveImport.Result imported=NativeSaveImport.apply(preview);NativeProgress.Target persisted=NativeSaveImport.readTarget(preview);if(preview.progress.preview(persisted).total!=0)throw new IOException("Persisted import does not contain the selected progress");report.put("added",counts(imported.added));report.put("backup",imported.backup==null?JSONObject.NULL:imported.backup.toString());report.put("destination",preview.directory.resolve("progress.dat").toString());report.put("persisted_sha256",Binary.sha256(Files.readAllBytes(preview.directory.resolve("progress.dat"))));}
        report.put("pass",true);String json=new JSONObject(report).toString(2);Files.write(output.resolve("report.json"),json.getBytes(StandardCharsets.UTF_8));result.putString("stream","REPORT "+new JSONObject(report).toString()+"\n");finish(-1,result);
    }catch(Throwable failure){report.put("pass",false);report.put("error",failure.toString());String json=new JSONObject(report).toString();try{if(output!=null)Files.write(output.resolve("report.json"),json.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}result.putString("stream","REPORT "+json+"\n");finish(0,result);}}
}
