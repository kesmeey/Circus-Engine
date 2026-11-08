package org.circus.runtime;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Real registered DocumentsProvider integration, without mocked resolvers or
 * original game assets. Missing catalog remains a strict failure. */
public final class DocumentTreeInstrumentation extends Instrumentation {
    private int checks;private Bundle arguments;private final StringBuilder log=new StringBuilder();
    private void check(boolean value,String why)throws IOException{checks++;if(!value)throw new IOException(why);log.append("PASS ").append(why).append('\n');}
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    private DocumentTree tree(String scenario,boolean saves)throws IOException{return new DocumentTree(getTargetContext().getContentResolver(),DocumentsContract.buildTreeDocumentUri(DocumentTreeFixtureProvider.AUTHORITY,DocumentTreeFixtureProvider.root(scenario)),saves);}
    private GameResources resources(String scenario)throws IOException{return new GameResources(tree(scenario,false),"@none");}
    private Profile profile(DocumentTree tree,GameResources files,File cache)throws IOException{return GameVersionCache.detect(tree,files,cache);}
    @Override public void onStart(){Bundle result=new Bundle();Path out=null;try{
        out=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"documenttree-audit-");File cache=out.resolve("profile-cache").toFile();DocumentTreeFixtureProvider.hideNames=false;
        for(String scenario:new String[]{"flat","nested","unknown"}){
            DocumentTree tree=tree(scenario,false);GameResources files=new GameResources(tree,"@none");Profile p=profile(tree,files,cache);
            check(p.id.equals("dc2sc-3b69"),"Real provider "+scenario+" independently identifies synthetic SC");
            check(Arrays.equals(files.read("AdvData/DAT/NAMES.DAT",20),new byte[20]),"Real provider "+scenario+" reads lower-case names.dat through canonical resource path");
            CircusResources data=new CircusResources(files,p);check(data.names.table("images").isEmpty(),"Real provider "+scenario+" catalog parses without an invented fallback");
            try(Archive.Reader reader=files.openReader("AdvData/DAT/NAMES.DAT")){check(reader.size()==20&&reader.read(4,3).length==3,"Real PFD supports bounded seek/read "+scenario);}
            check(!files.paths().contains("advdata/savedata/system.dat"),"Normal game indexing excludes original saves "+scenario);
        }
        check(new GameResources(tree("flat",true),"@none").paths().contains("advdata/savedata/system.dat"),"Explicit native import indexes SaveData without changing normal play");
        DocumentTree missing=tree("missing",false);GameResources absent=new GameResources(missing,"@none");Profile missingProfile=profile(missing,absent,cache);check(missingProfile.id.equals("dc2sc-3b69"),"SC profile can be identified even when NAMES is physically absent");
        boolean failed=false;try{new CircusResources(absent,missingProfile);}catch(FileNotFoundException expected){failed=expected.getMessage().equals("AdvData/DAT/NAMES.DAT");}check(failed,"Genuinely absent Names reproduces the exact configuration error and stays rejected");
        DocumentTree first=tree("flat",false);GameResources original=new GameResources(first,"@none");check(profile(first,original,cache).id.equals("dc2sc-3b69"),"Warm real provider profile cache before a catalog disappears");
        DocumentTreeFixtureProvider.hideNames=true;DocumentTree changed=tree("flat",false);GameResources disappeared=new GameResources(changed,"@none");check(profile(changed,disappeared,cache).id.equals("dc2sc-3b69"),"Unchanged MES cache does not assert catalog presence");failed=false;try{new CircusResources(disappeared,Profiles.get("dc2sc-3b69"));}catch(FileNotFoundException expected){failed=true;}check(failed,"A cached SC signature never masks a removed catalog");DocumentTreeFixtureProvider.hideNames=false;
        DocumentTree reordered=tree("reordered",false);GameResources reorderedFiles=new GameResources(reordered,"@none");Profile reorderedProfile=profile(reordered,reorderedFiles,cache);check(reorderedProfile.id.equals("dc2sc-3b69"),"Reordered DAT-only cursor still independently identifies SC from MES");
        boolean baseline=arguments!=null&&"true".equals(arguments.getString("baseline"));
        if(baseline){failed=false;try{new CircusResources(reorderedFiles,reorderedProfile);}catch(FileNotFoundException expected){failed=expected.getMessage().equals("AdvData/DAT/NAMES.DAT");}check(failed,"Old positional index reproduces SC-identified/Names-missing with an existing provider file");}
        else{check(new CircusResources(reorderedFiles,reorderedProfile).names.table("images").isEmpty(),"Reordered named cursor columns retain the actual catalog file");check(reorderedFiles.displayPath("AdvData/DAT/NAMES.DAT").equals("aDvDaTa/DaT/names.dat"),"Index retains exact displayed filename with column reordering");}
        failed=false;try{tree("badcolumns",false);}catch(IOException expected){failed=true;}catch(IllegalArgumentException expected){failed=baseline;}check(failed,"Provider omitting required identity columns is rejected");
        result.putString("stream",log+"CHECKS "+checks+"\nOUTPUT "+out+"\nOriginal game bytes used: false\n");Files.write(out.resolve("result.txt"),result.getString("stream").getBytes("UTF-8"));finish(-1,result);
    }catch(Throwable failure){log.append("FAIL ").append(failure).append('\n');result.putString("stream",log.toString());try{if(out!=null)Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(IOException ignored){}finish(0,result);}finally{DocumentTreeFixtureProvider.hideNames=false;}}
}
