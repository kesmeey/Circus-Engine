package org.circus.runtime;
import android.app.Instrumentation;
import android.os.Bundle;
import android.net.Uri;
import java.io.IOException;
import java.util.*;
import org.circus.core.*;

/** ART story-flow audit. Media completion is simulated here, tested separately. */
public final class OriginalInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();StringBuilder log=new StringBuilder();try{
        String tree=getTargetContext().getSharedPreferences("library",0).getString("game_tree",null);if(tree==null)tree=getTargetContext().getSharedPreferences("MainActivity",0).getString("game_tree",null);if(tree==null)throw new IOException("Select the original data directory first");
        Dc3Resources data=new Dc3Resources(new GameResources(new DocumentTree(getTargetContext().getContentResolver(),Uri.parse(tree))));TextCodec codec=TextConfiguration.load(data.files);
        for(int route=0;route<7;route++)for(int policy=0;policy<2;policy++){
            String entry=LauncherActivity.ENTRIES[route];Dc3Host h=new Dc3Host(data,codec);MesVm vm=new MesVm(data.scripts(),h,entry);int steps=0,waits=0,restores=0;Set<String> scripts=new HashSet<>();
            for(;steps<100000;steps++){scripts.add(vm.scriptName());MesVm.Status status=vm.run(1);if(status==MesVm.Status.FAULT)throw new IOException(vm.error());if(status==MesVm.Status.HALTED)break;
                if(status==MesVm.Status.WAIT_CHOICE){int selected=policy==0?0:h.choices().size()-1;vm.choose(h.choices().get(selected).label(),selected);}
                if(status==MesVm.Status.WAIT_HOST){waits++;if(waits%200==0){byte[] saved=vm.snapshot();Dc3Host candidate=new Dc3Host(data,codec);MesVm restored=new MesVm(data.scripts(),candidate,entry);restored.restore(saved);if(!Arrays.equals(saved,restored.snapshot()))throw new IOException("ART snapshot changed");vm=restored;h=candidate;restores++;}
                    for(int channel=0;channel<6;channel++)h.audioCompleted(channel,h.audioGeneration[channel]);vm.signalHost(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE);vm.elapse(Math.max(1,h.remainingMillis()));if(vm.status()==MesVm.Status.WAIT_HOST){vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
                }
            }
            if(vm.status()!=MesVm.Status.HALTED||!h.titleRequested)throw new IOException("Route did not return to title "+entry);
            log.append("PASS ART route=").append(route+1).append(" policy=").append(policy).append(" scripts=").append(scripts.size()).append(" steps=").append(steps).append(" waits=").append(waits).append(" saves=").append(restores).append('\n');
        }
        log.append(BranchAudit.run(data,codec,false));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable e){result.putString("stream",log+"FAIL "+e+"\n");finish(0,result);}}
}
