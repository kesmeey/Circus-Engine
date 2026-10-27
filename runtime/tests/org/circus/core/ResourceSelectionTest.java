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
        CircusResources dm=new CircusResources(auto,Profiles.get("dc2dm-9d72"));
        String cap="BST/E_NNK_HAT2,BST/E_NNK",close="BST/NNK_0C_202_B@,BST/NNK_C";
        check(dm.characterOverlay(close,cap).equals("BST/E_NNK_HAT2_B@,BST/E_NNK"),"DM reported close-up uses matching night accessory");
        String enlarged=dm.characterOverlay(close,cap);
        check(dm.characterOverlay(close,enlarged).equals(enlarged),"DM resolving an already adjusted accessory is idempotent");
        check(dm.characterOverlay("BST/NNK_0C_202,BST/NNK_C",enlarged).equals(cap),"DM retained accessory loses close-up and tint when actor changes");
        check(dm.characterOverlay("BST/NNK_0C_202*,BST/NNK_C",enlarged).equals("BST/E_NNK_HAT2*,BST/E_NNK"),"DM retained accessory follows warm tint");
        check(dm.characterOverlay("BST/NNK_0C_202_b,BST/NNK_C",cap).equals("BST/E_NNK_HAT2_B,BST/E_NNK"),"DM close-up suffix is case insensitive");
        check(dm.characterOverlay("BST/NNK_0C_202_B@.CRX,BST/NNK_C", "BST/E_NNK_HAT2*.CRX,BST/E_NNK").equals("BST/E_NNK_HAT2_B@.CRX,BST/E_NNK"),"DM overlay keeps explicit extension and archive");
        check(dm.characterOverlay("actor_B@", "accessory").equals("accessory_B@"),"DM loose accessory follows actor");
        check(dm.characterOverlay("actor,archive_B@", "accessory,other_B*").equals("accessory,other_B*"),"archive suffixes do not change variant or tint");
        check(dm.characterOverlay("",cap).equals(cap)&&dm.characterOverlay(close,"").isEmpty(),"empty actor/accessory stays empty or unchanged");
        files.put("wrapper/AdvData/DAT/NAMES.DAT",new byte[20]);
        for(String p:new String[]{"dc3dd-a5a8","dcpc-2063","dc2pc-5769"})check(new CircusResources(new GameResources(storage),Profiles.get(p)).characterOverlay(close,cap).equals(cap),"DM accessory rule does not change "+p);
        return checks;
    }
}
