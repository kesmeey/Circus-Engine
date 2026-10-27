package org.circus.core;
import java.io.*;
import java.util.*;

/** User choices must survive renaming and must never fall back to another EXE. */
final class ResourceSelectionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Work{void run()throws Exception;}
    private static void reject(Work w)throws Exception{try{w.run();throw new AssertionError("Invalid explicit choice accepted");}catch(IOException expected){checks++;}}
    static int run()throws Exception{
        checks=0;Map<String,byte[]> files=new HashMap<>();files.put("wrapper/AdvData/MES/START.MES",new byte[]{0,0,0,0,(byte)0xa8,(byte)0xa5,0});files.put("wrapper/Custom_CHS.exe",new byte[64]);files.put("wrapper/Original.exe",new byte[64]);
        files.put("wrapper/custom.json","\uFEFF{\"character_substitution\":{\"enable\":true,\"source_characters\":\"漢\",\"target_characters\":\"汉\"}}".getBytes("UTF-8"));
        GameResources.Storage storage=new GameResources.Storage(){public Set<String> paths(){return files.keySet();}public byte[] read(String p,int limit)throws IOException{byte[] b=files.get(p);if(b==null||b.length>limit)throw new IOException(p);return b.clone();}public Archive.Source source(String p)throws IOException{byte[] b=read(p,Integer.MAX_VALUE);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long o,int n)throws IOException{Binary.range(o,n,b.length);return Arrays.copyOfRange(b,(int)o,(int)o+n);}};}};
        files.put("wrapper/AdvData/GRP/NAMES.DAT",new byte[20]);
        GameResources auto=new GameResources(storage);check(auto.executableCandidates().equals(Arrays.asList("custom_chs.exe","original.exe")),"EXE inventory independent of game filename prefix");
        GameResources manual=new GameResources(storage,"Custom_CHS.exe");check(manual.executableCandidates().equals(Collections.singletonList("Custom_CHS.exe")),"only explicit executable offered");check(manual.displayPath("custom_chs.exe").equals("wrapper/Custom_CHS.exe"),"display preserves original filename case");
        check(new GameResources(storage,"@none").executableCandidates().isEmpty(),"data-only opt out");reject(()->new GameResources(storage,"missing.exe"));reject(()->new GameResources(storage,"../Original.exe"));
        Profile dd=Profiles.get("dc3dd-a5a8");reject(()->new Dc3NativeData(new CircusResources(manual,dd),TextCodec.identity()));
        TextCodec codec=GameText.select(manual,dd,"GBK","custom.json");check(codec.apply("漢").equals("汉")&&codec.charset.name().equalsIgnoreCase("GBK"),"explicit encoding and BOM mapping combined");
        check(GameText.select(manual,dd,"auto","@none").apply("漢").equals("漢"),"mapping opt out");reject(()->GameText.select(manual,dd,"auto","missing.json"));reject(()->GameText.select(manual,dd,"bad-encoding","@none"));
        return checks;
    }
}
