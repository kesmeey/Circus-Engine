package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import org.circus.core.*;

/** Runs the shared original-menu audit with Canvas/ART, using isolated audit saves. */
public final class UnityMenuPerformanceInstrumentation extends Instrumentation {
    private String pack,run,seed;private int dialogues;private boolean snapshotEveryRound;
    public void onCreate(Bundle args){super.onCreate(args);pack=args.getString("pack","dc3r-menu.pack");run=args.getString("run","audit");seed=args.getString("seed");dialogues=Integer.parseInt(args.getString("dialogues","1"));snapshotEveryRound="all".equals(args.getString("snapshots"));start();}
    public void onStart(){Bundle result=new Bundle();try{
        if(!run.matches("[a-zA-Z0-9_-]{1,60}")||dialogues<1||dialogues>1000)throw new IOException("Invalid menu audit arguments");
        Path files=getTargetContext().getFilesDir().toPath().toRealPath(),input=files.resolve(pack).normalize();if(!input.startsWith(files))throw new IOException("Audit pack escapes app directory");
        Path out=files.resolve("dc3r-menu-audit").resolve(run);if(Files.exists(out))throw new IOException("Audit output already exists; use a fresh run name");
        Path seedInput=seed==null?null:files.resolve(seed).normalize();if(seedInput!=null&&!seedInput.startsWith(files))throw new IOException("Audit seed escapes app directory");
        UnityMenuPerformanceAudit.Meter meter=new UnityMenuPerformanceAudit.Meter(RangeStorage.replay(input));
        UnityMenuPerformanceAudit audit=new UnityMenuPerformanceAudit(new UnityCircusResources(new GameResources(meter)),meter,out);audit.run(dialogues,seedInput,snapshotEveryRound);
        result.putString("stream",new String(Files.readAllBytes(out.resolve("result.txt")),"UTF-8")+"OUTPUT "+out+"\n");finish(-1,result);
    }catch(Throwable t){result.putString("stream","FAIL "+t+"\n");finish(0,result);}}
}
