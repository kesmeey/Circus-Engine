package org.circus.core;

import java.io.IOException;
import java.util.*;

/** Explicit synthetic fixture initialization. A MES marker or a codec is not
 *evidence that an arbitrary installed JP/CHS executable has these defaults. */
public final class Pc1TestDefaults {
    private Pc1TestDefaults(){}
    // Fixture journals must not retain every complete Runtime until process
    // exit. PcHost uses Object identity equality; values do not reference keys.
    private static final Map<PcHost,List<Pc1NativeSettings.EffectKind>> audioEvents=new WeakHashMap<>();
    public static <T extends Dc2Host>T bind(T host)throws IOException{
        if(host instanceof PcHost&&((PcHost)host).pc1!=null){
            final PcHost pc=(PcHost)host;pc.loadNativeSettings(Pc1NativeSettings.currentChsDefaults().nativeBytes());
            // State/control fixtures journal the explicit device boundary.
            // They do not claim execution of a PCM/MCI/timer backend.
            final List<Pc1NativeSettings.EffectKind> events=new ArrayList<>();audioEvents.put(pc,events);
            pc.bindNativeSettingsAudioEffects((effect,settings)->events.add(effect.kind));
        }
        return host;
    }
    public static List<Pc1NativeSettings.EffectKind> audioEvents(PcHost host){List<Pc1NativeSettings.EffectKind> events=audioEvents.get(host);return events==null?Collections.<Pc1NativeSettings.EffectKind>emptyList():Collections.unmodifiableList(events);}
}
