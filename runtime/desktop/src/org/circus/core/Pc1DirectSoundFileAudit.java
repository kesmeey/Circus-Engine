package org.circus.core;
import java.io.*;import java.nio.file.*;import java.util.*;
/**Controlled own-file Win32 HANDLE tag test; no original files or target PE. */
public final class Pc1DirectSoundFileAudit {
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IOException("Expected DLL and caller-owned audit directory");Path root=Paths.get(args[1]).toAbsolutePath().normalize();if(!root.toString().contains("pc1-audio-alias-stage28"))throw new IOException("Audit files require own evidence directory");Files.createDirectories(root);Path file=root.resolve("handle-tags.bin");byte[] content={0x37,0x19,(byte)0xa4};Files.write(file,content);int checks=0;
  try(Pc1DirectSound ds=Pc1DirectSound.deferred(Paths.get(args[0]),null)){
   long[] actual=ds.fileHandleTagsForAudit(file);if(actual.length!=40)throw new AssertionError("packet");checks++;
   StringBuilder report=new StringBuilder("{\"scope\":\"actual Win32 CreateFileA/ReadFile/CloseHandle on self-authored own file only\",\"rows\":[");
   for(int k=0;k<4;k++){long[] row=Arrays.copyOfRange(actual,k*10,k*10+10);if(k!=0)report.append(',');report.append(Arrays.toString(row));System.out.println("fileTag="+Arrays.toString(row));if(row[0]!=k||row[1]==0||row[1]==-1||row[2]!=(row[1]|k))throw new AssertionError("controlled actual handle");checks++;if(row[3]!=1||row[4]!=0||row[5]!=1||row[6]!=0x37||row[7]!=1||row[8]!=0)throw new AssertionError("Observed platform no longer accepts tag"+k+"; do not assume invalid HANDLE");checks++;}
   report.append("]}");Files.write(root.resolve("handle-tags-actual.json"),report.toString().getBytes("UTF-8"));if(!Arrays.equals(Files.readAllBytes(file),content))throw new AssertionError("readonly original own content");checks++;
  }System.out.println("Pc1DirectSoundFileAudit PASS "+checks+"; actual own-file HANDLE low2 tag bits, no EXE/original file write");
 }
}
