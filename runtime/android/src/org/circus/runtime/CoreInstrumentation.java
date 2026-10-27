package org.circus.runtime;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.InputStream;
import org.circus.core.*;

/** On-device ART tests only use our synthetic assets, never installed games. */
public final class CoreInstrumentation extends Instrumentation {
    private String treeUri;
    @Override public void onCreate(Bundle args){super.onCreate(args);treeUri=args==null?null:args.getString("synthetic_tree_uri");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        String report=CoreTests.run(name->{try(InputStream in=getTargetContext().getAssets().open("demo/"+Binary.safePath(name))){return Binary.read(in,64*1024*1024);}});
        String mapping="{\"character_substitution\":{\"enable\":true,\"source_characters\":\"漢語\",\"target_characters\":\"汉文\"},\"tunnel_decoder\":{\"enable\":false}}";
        if(!TextConfiguration.parse(mapping.getBytes(java.nio.charset.StandardCharsets.UTF_8)).apply("漢語").equals("汉文"))throw new AssertionError("Android external text mapping");
        boolean tunnelRejected=false;try{TextConfiguration.parse("{\"tunnel_decoder\":{\"enable\":true}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException expected){tunnelRejected=true;}
        if(!tunnelRejected)throw new AssertionError("Unsupported tunnel config accepted");report+="; Android character map and tunnel rejection PASS";
        GameSavesTest.run(getTargetContext());report+="; CAS2/CAS1 saves, atomic rollback and invalid envelope rejection PASS";
        report+="; Android Canvas "+GraphicsTest.run()+" pixel contracts PASS";
        if(treeUri!=null){
            DocumentTree tree=new DocumentTree(getTargetContext().getContentResolver(),android.net.Uri.parse(treeUri));GameResources resources=new GameResources(tree);
            Archive crm=resources.archive("sample.crm");if(resources.image("sample.crm",crm.entries.get(1).nameBytes).argb()[5]!=0xff21160b)throw new AssertionError("SAF difference image mismatch");
            Archive pck=resources.archive("sample.pck");if(resources.audio("sample.pck",pck.entries.get(0).nameBytes).sampleRate!=22050)throw new AssertionError("SAF audio mismatch");
            MesScript script=new MesScript("main",resources.read("main.mes",1024*1024),Profiles.get("circus-demo-v1"));if(script.records().size()<10)throw new AssertionError("SAF MES read mismatch");
            report+="; SAF seek/read, PCK/CRM, CRXD and PCM PASS";
        }
        result.putString("stream",report+"\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(0,result);}}
}
