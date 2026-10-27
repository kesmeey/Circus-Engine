package org.circus.core;
import java.nio.file.*;
import java.util.*;
public final class XpcmAudit {
 public static void main(String[] a)throws Exception {
  Path folder=Paths.get(a[0]);for(int n=0;n<4;n++){byte[] decoded=Xpcm.decode(Files.readAllBytes(folder.resolve("case"+n+".pcm"))).payload;byte[] expected=Files.readAllBytes(folder.resolve("case"+n+".oracle"));if(!Arrays.equals(decoded,expected))throw new AssertionError("XPCM integer reference mismatch "+n);System.out.println("PASS XPCM extra="+n+" "+Binary.sha256(decoded));}
 }
}
