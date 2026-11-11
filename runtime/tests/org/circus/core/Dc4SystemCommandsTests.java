package org.circus.core;

import java.io.*;
import java.util.*;

/** Synthetic PH service fixtures: native slot result, settings-copy boundary,
 *43-track endpoint/filter/order and portable cold-restore continuation. */
public final class Dc4SystemCommandsTests {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void u32(ByteArrayOutputStream out,int value){for(int n=0;n<4;n++)out.write(value>>>(n*8));}
    private static Dc4Host host()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();u32(bytes,0);u32(bytes,44);
        for(int n=0;n<44;n++){byte[] entry=new byte[48],name=String.format(Locale.ROOT,"MUSIC/%03d",n).getBytes(Binary.SJIS);System.arraycopy(name,0,entry,0,name.length);bytes.write(entry);}
        u32(bytes,0);u32(bytes,0);u32(bytes,0);final byte[] names=bytes.toByteArray();
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return Collections.singleton("AdvData/GRP/NAMES.DAT");}
            public byte[] read(String p,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String p){return new Archive.Source(){public long size(){return names.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,names.length);return Arrays.copyOfRange(names,(int)at,(int)at+count);}};}
        });
        Profile profile=Profiles.get("dc4ph-abb6");Dc4Host host=new Dc4Host(new CircusResources(files,profile),TextCodec.identity(),true);host.attach(new Variables(host,profile.variableModel));return host;
    }
    private static void call(Dc4SystemCommands service,Dc4Host host,int a,int b)throws IOException{service.command(host,new int[]{a,b});}
    private static void publish(Dc4Host host)throws IOException{host.command("wait.duration",new int[]{1,0},new byte[0]);}
    public static int run()throws Exception{
        checks=0;Dc4Host h=host();Dc4SystemCommands s=new Dc4SystemCommands();
        check(h.dialogue.familyName.equals("常坂")&&h.dialogue.givenName.equals("一登"),"PH name constants do not inherit DD's protagonist");
        h.state.write(99,254,2);call(s,h,19,0);check(h.variable(9,87)==1,"Missing slot sets ordinary9:87 to1");
        h.availableSaves.add(2);call(s,h,19,0);check(h.variable(9,87)==0,"Existing slot sets ordinary9:87 to0");
        call(s,h,6,0);check(h.ui.saveGrid,"Service6 mode0 requests save grid");call(s,h,6,1);check(!h.ui.saveGrid,"Service6 nonzero requests load grid");
        byte[] original=h.state.settingsSnapshot();call(s,h,100,0);call(s,h,215,0);call(s,h,7,0);call(s,h,7,1);call(s,h,20,0);call(s,h,20,1);check(Arrays.equals(original,h.state.settingsSnapshot()),"Reviewed default and thumbnail/redraw requests do not reset MES state");
        h.variable(5,0,20);h.variable(5,1,90);h.variable(5,2,90);h.variable(5,3,2);call(s,h,2,0);check(h.variable(3,0)==1,"Max service uses native first maximum on ties");
        h.state.voiceCount=1;h.state.write(94,0,70);h.state.write(100,54,7);call(s,h,18,0);h.state.write(100,54,8);h.state.write(94,0,40);call(s,h,18,1);check(h.variable(3,0)==0,"Comparison excludes100:54 and voice records outside native0x4B80 backup");
        call(s,h,18,0);h.state.write(99,7,18);call(s,h,18,1);check(h.variable(3,0)==1,"An in-region audio preference changes native comparison");
        h.state.write(99,7,60);call(s,h,18,0);h.state.write(99,7,21);h.state.write(94,0,30);call(s,h,18,2);check(h.state.read(99,7)==60&&h.state.read(94,0)==30,"Rollback restores native backup while retaining current out-of-region voice preference");
        call(s,h,200,0);check(s.musicOrder().length==43&&s.musicOrder()[0]==1&&s.musicOrder()[42]==43,"PH owns all43 contiguous table entries");
        h.variable(7,98,0);call(s,h,200,1);publish(h);check(h.audio[0].equals("MUSIC/001")&&!h.audioLoop[0]&&h.state.read(99,254)==0,"First room selection queues native music1 as single play");
        h.variable(7,98,42);call(s,h,200,1);publish(h);check(h.audio[0].equals("MUSIC/043")&&h.state.read(99,254)==42,"Final room selection is native43 rather than DD88-track projection");
        call(s,h,200,2);check(h.variable(7,98)==0,"Next wraps final43rd selection to first");call(s,h,200,3);check(h.variable(7,98)==42,"Previous wraps first to43rd");
        call(s,h,201,255);call(s,h,200,6);check(h.variable(3,0)==144,"Native favorite count400 is stored through ordinary byte setter");
        call(s,h,201,254);h.state.write(99,246,40);call(s,h,201,2);call(s,h,202,2);check(h.variable(3,0)==1&&h.state.musicFavorites[43]==1,"Favorite queries map visible offset to PH track43");
        h.state.write(100,21,1);h.variable(7,98,0);call(s,h,200,0);check(h.variable(7,98)==42&&h.state.read(99,254)==42,"Favorite-only rebuild finds the last catalogue favorite");
        h.state.write(100,26,2);h.state.write(100,21,0);h.variable(7,98,42);call(s,h,200,0);int[] shuffled=s.musicOrder();Set<Integer> unique=new HashSet<>();for(int n:shuffled)unique.add(n);check(unique.size()==43&&shuffled[42]==43,"Shuffle preserves current track and retains the entire PH permutation");
        call(s,h,200,4);check(!h.musicRoom.caption,"PH native mode4 does not inherit DD's guessed top-caption bake");
        h.state.write(99,187,55);call(s,h,200,99);check(h.musicRoom.seek==55&&h.musicRoom.seekGeneration==1,"Seek service projects only current platform music metrics");
        call(s,h,18,0);ByteArrayOutputStream bytes=new ByteArrayOutputStream();s.save(new DataOutputStream(bytes));Dc4SystemCommands restored=new Dc4SystemCommands();restored.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),h.state);check(Arrays.equals(restored.musicOrder(),s.musicOrder())&&restored.musicCatalogueIndex()==s.musicCatalogueIndex(),"Cold service restore retains43-track order and selected resource");
        h.state.write(99,7,19);call(restored,h,18,2);check(h.state.read(99,7)==60,"Cold restore retains a pending native settings rollback");
        try{call(s,h,98,0);throw new AssertionError("Unreviewed PH temporary settings service silently accepted");}catch(IOException expected){check(expected.getMessage().contains("PH native system service [98, 0]"),"Unreviewed service remains an explicit diagnostic");}
        int[][] audio=Dc4SystemCommands.defaultRegisters(2);boolean bgm=false,se=false;for(int[] pair:audio){bgm|=pair[0]==7&&pair[1]==60;se|=pair[0]==8&&pair[1]==70;}check(bgm&&se,"PH audio defaults come from its own native table");
        check(Dc4SystemCommands.defaultRegisters(3).length==0&&Dc4SystemCommands.defaultRegisters(4).length==0,"Display-resync and checkbox categories do not invent register assignments");
        return checks;
    }
    public static void main(String[] args)throws Exception{System.out.println("Dc4SystemCommandsTests "+run()+" PASS");}
}
