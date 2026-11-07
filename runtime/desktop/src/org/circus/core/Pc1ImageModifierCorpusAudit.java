package org.circus.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
/** Exact ANSI filename predicate for reusing the already frozen1698-file
 *loader result after a change confined to the private modifier consumer. */
public final class Pc1ImageModifierCorpusAudit {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IOException("Require paths.dll explicit readonly GAME_ROOT");Pc1WindowsPaths windows=new Pc1WindowsPaths(Paths.get(args[0]));Path root=Paths.get(args[1]).toAbsolutePath().normalize().resolve("AdvData/GRP");List<Path> paths;try(Stream<Path> walk=Files.walk(root)){paths=walk.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".crx")).sorted().collect(Collectors.toList());}int modifiers=0;
        for(Path path:paths){String relative=root.relativize(path).toString();byte[] raw=windows.encode(relative.substring(0,relative.length()-4));int point=0;for(int n=0;n<raw.length;n++)if(raw[n]=='.'||raw[n]=='/'||raw[n]=='\\')point=n;point=raw.length>0&&raw[point]=='.'?point:raw.length;if(point>0&&(raw[point-1]=='@'||raw[point-1]=='*'))modifiers++;}
        byte[] settings=new byte[Pc1NativeSettings.SIZE];Pc1NativeMemory bus=new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int n){return settings[n]&255;}public void write(int n,int value){settings[n]=(byte)value;}});Pc1GraphicsMemory state=new Pc1GraphicsMemory(bus);int index=state.readInt(0x94c61c),flag=state.readInt(0x94c62c+index*4);
        if(paths.size()!=1698||modifiers!=0||index!=0||flag!=0)throw new IOException("Cannot reuse asset result: a modifier branch may be entered");System.out.println("Pc1ImageModifierCorpusAudit PASS:1698 exact ANSI names,0 modifier branches; configured audit initial dither index0/flag0; full asset-body decoder code unchanged");
    }
}
