package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real OS ANSI queries on explicitly owned fixtures. No game process runs. */
public final class Pc1WindowsPathsAudit {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Roots implements Pc1NativePaths.Memory {
        final Map<Integer,Byte> bytes=new HashMap<>();
        void cstring(int address,byte[] raw){for(int n=0;n<raw.length;n++)bytes.put(address+n,raw[n]);bytes.put(address+raw.length,(byte)0);}
        public int read(int address)throws IOException{Byte value=bytes.get(address);if(value==null)throw new IOException("unowned native path global"+Integer.toHexString(address));return value&255;}
        public void write(int address,int value)throws IOException{if(!bytes.containsKey(address))throw new IOException("unowned native path write");bytes.put(address,(byte)value);}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Pc1WindowsPathsAudit DLL OWNED_FIXTURE_DIRECTORY");
        Pc1WindowsPaths windows=new Pc1WindowsPaths(Paths.get(args[0]));Path fixture=Paths.get(args[1]).toAbsolutePath().normalize(),primary=fixture.resolve("primary"),fallback=fixture.resolve("fallback");
        Files.createDirectories(primary.resolve("AdvData/GRP"));Files.createDirectories(fallback.resolve("AdvData/GRP"));Path first=primary.resolve("AdvData/GRP/主图.crx"),second=fallback.resolve("AdvData/GRP/备用.crx");
        byte[] marker={1,2,3,4};Files.write(first,marker);Files.write(second,marker);
        check(windows.ansiCodePage()>0,"actual WindowsACP");check(windows.attributes(windows.encode(first.toString()))!=-1,"GetFileAttributesA finds nonASCII fixture");check((windows.attributes(windows.encode(primary.toString()))&16)!=0,"directory attribute remains a successful native query");
        check(windows.attributes(windows.encode(primary.resolve("missing.crx").toString()))==-1,"actual absent path returnsFFFFFFFF");
        byte[] raw=windows.encode(first.toString()),suffix=Arrays.copyOf(raw,raw.length+6);suffix[raw.length]=0;Arrays.fill(suffix,raw.length+1,suffix.length,(byte)'X');check(windows.attributes(raw)==windows.attributes(suffix),"nativeA stops at firstNUL");
        check(windows.exactPath(raw).equals(first)&&Arrays.equals(raw,windows.encode(windows.decode(raw))),"ACP byte/path round trip");check(windows.exactPath(windows.workingDirectory()).toAbsolutePath().equals(Paths.get("").toAbsolutePath()),"actual GetCurrentDirectoryA");
        Roots roots=new Roots();roots.cstring(Pc1NativePaths.PRIMARY_ROOT,windows.encode(primary.toString()));roots.cstring(Pc1NativePaths.FALLBACK_ROOT,windows.encode(fallback.toString()));roots.cstring(Pc1NativePaths.DATA_PREFIX,new byte[]{'\\','A','d','v','D','a','t','a','\\'});roots.cstring(Pc1NativePaths.SEPARATOR,new byte[]{'\\'});
        Pc1ResourcePaths paths=new Pc1ResourcePaths(roots,windows);byte[] grp=windows.encode("GRP"),ext=windows.encode(".crx");
        Pc1ResourcePaths.Resolved found=paths.asset(grp,windows.encode("主图.old"),ext,260);check(found.nativePath().equals(first)&&Arrays.equals(Files.readAllBytes(found.nativePath()),marker),"40E5A0 primary resolves and reads real fixture");
        found=paths.asset(grp,windows.encode("备用.old"),ext,260);check(found.nativePath().equals(second)&&Arrays.equals(Files.readAllBytes(found.nativePath()),marker),"40E5A0 actual missing primary selects fallback");
        found=paths.asset(new byte[0],raw,windows.encode(".ignored"),260);check(Arrays.equals(found.rawPath(),raw),"absolute rawname bypasses extension and category");
        check(paths.root(new byte[0],windows.encode("anything"),new byte[0],260).nativePath().equals(fallback.resolve("anything")),"40E870 usesroot separator and unqueried fallback");
        boolean failed=false;try{paths.asset(grp,windows.encode("主图"),ext,8);}catch(IOException precise){failed=precise.getMessage().contains("unowned");}check(failed,"path scratch does not grow beyond original caller allocation");
        System.out.println("PASS "+count+" real Windows ANSI path checks; ACP="+windows.ansiCodePage());
    }
}
