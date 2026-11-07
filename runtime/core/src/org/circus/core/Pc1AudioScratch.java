package org.circus.core;
import java.io.*;import java.util.*;
/**An actual typed local allocation: uninitialized suffix remains unknown.
 *No numeric address is assigned and no data is copied before native writes.*/
public final class Pc1AudioScratch implements Pc1ByteSource.Pointer {
 private final byte[] bytes;private final boolean[] known;
 public Pc1AudioScratch(int capacity)throws IOException{if(capacity<1||capacity>1024*1024)throw new IOException("PC1 audio local allocation extent");bytes=new byte[capacity];known=new boolean[capacity];}
 private void bound(int n,int count)throws IOException{if(n<0||count<0||n>bytes.length-count)throw new IOException("PC1 audio native local access leaves known allocation");}
 public int unsignedByte(int n)throws IOException{bound(n,1);if(!known[n])throw new IOException("PC1 audio reads unknown native local suffix");return bytes[n]&255;}
 public int nativeWord(int n)throws IOException{bound(n,4);int v=0;for(int k=0;k<4;k++)v|=unsignedByte(n+k)<<(k*8);return v;}
 public void writeByte(int n,int v)throws IOException{bound(n,1);bytes[n]=(byte)v;known[n]=true;}
 public void writeWord(int n,int v)throws IOException{bound(n,4);for(int k=0;k<4;k++){bytes[n+k]=(byte)(v>>>(k*8));known[n+k]=true;}}
 public byte[] cstring()throws IOException{for(int n=0;n<bytes.length;n++)if(unsignedByte(n)==0)return Arrays.copyOf(bytes,n);throw new IOException("PC1 audio local has no known terminator");}
}
