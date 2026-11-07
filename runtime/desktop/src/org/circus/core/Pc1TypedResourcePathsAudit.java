package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual WindowsA plus explicitly owned CRT/global/local collision fixtures.
 * The original PE is read as data only; no original program is launched. */
public final class Pc1TypedResourcePathsAudit {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    // Exact current CHS preferred-image4402CA..4404CA, not a production default.
    private static final byte[] INITIAL_CTYPE=Base64.getDecoder().decode("AABggnmCIQAAAAAAAACm3wAAAAAAAKGlAAAAAAAAgZ/g/AAAAABAfoD8AAAAAKgDAADBo9qjIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgf4AAAAAAABA/gAAAAAAALUDAADBo9qjIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgf4AAAAAAABB/gAAAAAAALYDAADPouSiGgDlouiiWwAAAAAAAAAAAAAAAAAAAAAAgf4AAAAAAABAfqH+AAAAAFEFAABR2l7aIABf2mraMgAAAAAAAAAAAAAAAAAAAAAAgdPY3uD5AAAxfoH+AAAAAAAAAAAAAAAAygNEAMoDRAAAACAAIAAgACAAIAAgACAAIAAgACgAKAAoACgAKAAgACAAIAAgACAAIAAgACAAIAAgACAAIAAgACAAIAAgACAAIABIABAAEAAQABAAEAAQABAAEAAQABAAEAAQABAAEAAQAIQAhACEAIQAhACEAIQAhACEAIQAEAAQABAAEAAQABAAEACBAIEAgQCBAIEAgQABAAEAAQABAAEAAQABAAEAAQABAAEAAQABAAEAAQABAAEAAQABAAEAEAAQABAAEAAQABAAggCCAIIAggCCAIIAAgACAAIAAgACAAIAAgACAAIAAgACAAIAAgACAAIAAgACAAIAAgACABAAEAAQABAAIAA=");
    static void initialCrtFixture(Pc1NativeMemory bus)throws IOException{
        final byte[] table=INITIAL_CTYPE.clone(),maximum={1,0,0,0};
        bus.bind(0x4402ca,table.length,new Pc1NativeMemory.Region(){public int read(int n){return table[n]&255;}public void write(int n,int v){table[n]=(byte)v;}});
        bus.bind(0x4405cc,maximum.length,new Pc1NativeMemory.Region(){public int read(int n){return maximum[n]&255;}public void write(int n,int v){maximum[n]=(byte)v;}});
    }
    private static Pc1NativeMemory bus()throws IOException{
        final byte[] settings=new byte[Pc1NativeSettings.SIZE];
        return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int n){return settings[n]&255;}public void write(int n,int v){settings[n]=(byte)v;}});
    }
    private static void cstring(Pc1NativePaths.Memory mem,int at,byte[] value)throws IOException{for(int n=0;n<value.length;n++)mem.write(at+n,value[n]);mem.write(at+value.length,0);}
    private static void root(Pc1PathGlobals globals,Pc1WindowsPaths windows,int at,Path root)throws IOException{cstring(globals,at,windows.encode(root.toString()));}
    private static void region(Pc1NativeMemory bus,int at,final byte[] bytes)throws IOException{bus.bind(at,bytes.length,new Pc1NativeMemory.Region(){public int read(int n){return bytes[n]&255;}public void write(int n,int v){bytes[n]=(byte)v;}});}
    private static byte[] peData(byte[] pe,int address,int length)throws IOException{int nt=(int)Binary.u32(pe,0x3c),count=Binary.u16(pe,nt+6),optional=Binary.u16(pe,nt+20),table=nt+24+optional;for(int n=0;n<count;n++){int row=table+40*n,rva=(int)Binary.u32(pe,row+12),raw=(int)Binary.u32(pe,row+20),size=(int)Binary.u32(pe,row+16),delta=address-0x400000-rva;if(delta>=0&&delta+length<=size)return Arrays.copyOfRange(pe,raw+delta,raw+delta+length);}throw new IOException("fixture PE bytes unavailable");}
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IOException("Require paths.dll readonly_PC1_CHS_PE owned_FIXTURE_ROOT");
        byte[] pe=Files.readAllBytes(Paths.get(args[1]));check(Binary.sha256(pe).equals(Pc1StaticImage.TARGET_SHA256),"readonly source identity");check(Arrays.equals(INITIAL_CTYPE,peData(pe,0x4402ca,512)),"explicit CRT fixture equals actual preferred-image bytes");check(Arrays.equals(peData(pe,0x4405cc,4),new byte[]{1,0,0,0}),"actual initial MB_CUR_MAX1");
        Pc1WindowsPaths windows=new Pc1WindowsPaths(Paths.get(args[0]));Path fixture=Paths.get(args[2]).toAbsolutePath().normalize(),primary=fixture.resolve("primary"),fallback=fixture.resolve("fallback"),changed=fixture.resolve("changed");
        for(Path base:new Path[]{primary,fallback,changed})Files.createDirectories(base.resolve("AdvData/GRP"));Path file=primary.resolve("AdvData/GRP/present.crx"),mutated=changed.resolve("AdvData/GRP/changed.crx");Files.write(file,new byte[]{7,8,9});Files.write(mutated,new byte[]{9,8,7});
        Pc1NativeMemory bus=bus();Pc1PathGlobals globals=new Pc1PathGlobals(bus);initialCrtFixture(bus);root(globals,windows,Pc1NativePaths.PRIMARY_ROOT,primary);root(globals,windows,Pc1NativePaths.FALLBACK_ROOT,fallback);Pc1ResourcePaths paths=new Pc1ResourcePaths(globals,windows);byte[] category=windows.encode("GRP"),extension=windows.encode(".crx");
        check(paths.asset(category,windows.encode("present.old"),extension,260).nativePath().equals(file),"actual relative path uses canonical CRT");
        final byte[] classification=new byte[512];classification['c'*2]=8;region(bus,0xe0000000,classification);bus.writeInt32(0x4403c0,0xe0000000);
        check(paths.asset(category,windows.encode("present.old"),extension,260).nativePath().equals(fallback.resolve("AdvData/GRP/present.")),"real pctype at former destination coordinate is not swallowed by local path");
        classification['c'*2]=0;check(paths.asset(category,windows.encode("present.old"),extension,260).nativePath().equals(file),"mutation of same global table immediately changes classification");
        byte[] absolute=windows.encode(file.toString()),addressed=Arrays.copyOf(absolute,absolute.length+1);region(bus,0xe0200000,addressed);
        Pc1ResourcePaths.Resolved direct=paths.assetAddressed(globals,0,0xe0200000,0,260);check(Arrays.equals(direct.rawPath(),absolute),"former e020 source coordinate now reads genuine global name");
        check(paths.asset(null,absolute,null,260).nativePath().equals(file),"absolute input never touches unused null category/extension");
        Pc1ByteSource.Pointer live=(Pc1ByteSource.Pointer)direct.source();live.writeByte(absolute.length-1,'z');check(direct.rawPath()[absolute.length-1]=='z',"Resolved path remains the same live typed local");check(windows.attributes(direct.source())==-1,"actual OS query consumes current typed bytes");
        boolean failed=false;try{direct.source().unsignedByte(absolute.length+1);}catch(IOException e){failed=e.getMessage().contains("unknown");}check(failed,"unwritten local suffix is unknown, not zero-filled");
        final int[] queries={0};final Pc1ByteSource[] observed={null};final byte[] mutableName=windows.encode("missing.old");
        Pc1ResourcePaths callback=new Pc1ResourcePaths(globals,windows,source->{queries[0]++;observed[0]=source;int answer=windows.attributes(source);System.arraycopy(windows.encode("changed.old"),0,mutableName,0,mutableName.length);root(globals,windows,Pc1NativePaths.FALLBACK_ROOT,changed);return answer;});
        Pc1ResourcePaths.Resolved after=callback.asset(category,mutableName,extension,260);check(queries[0]==1&&after.source()==observed[0],"OS leaf and result retain identical local owner; fallback is unqueried");check(after.nativePath().equals(mutated),"fallback late-reads changed name and changed global root");
        check(paths.root(null,absolute,null,260).nativePath().equals(file),"root typed absolute bypass also preserves unused sources");
        check(paths.root(new byte[0],windows.encode("anything"),new byte[0],260).nativePath().equals(changed.resolve("anything")),"root-relative variant uses current fallback separator");
        failed=false;try{paths.asset(category,windows.encode("present"),extension,8);}catch(IOException e){failed=e.getMessage().contains("unowned");}check(failed,"actual caller capacity is enforced without synthesized VA");
        bus.writeInt32(0x4405cc,2);failed=false;try{paths.asset(category,windows.encode("present"),extension,260);}catch(IOException e){failed=e.getMessage().contains("423486");}check(failed,"unimplemented multibyte CRT branch is required");bus.writeInt32(0x4405cc,1);
        Pc1NativeMemory missing=bus();Pc1PathGlobals unseeded=new Pc1PathGlobals(missing);root(unseeded,windows,Pc1NativePaths.PRIMARY_ROOT,primary);root(unseeded,windows,Pc1NativePaths.FALLBACK_ROOT,fallback);failed=false;try{new Pc1ResourcePaths(unseeded,windows).asset(category,windows.encode("present"),extension,260);}catch(IOException e){failed=true;}check(failed,"production canonical owner requires real CRT bytes rather than defaulting");
        System.out.println("Pc1TypedResourcePathsAudit PASS "+checks+"; real WindowsA, same Source callback, global e000/e020 collisions, live CRT; ACP="+windows.ansiCodePage());
    }
}
