package org.circus.core;
import java.util.Locale;
/** PC1 41CA30/43DBE8 and PC2 427460/50EA80 native character tables. */
public final class PcVoice {
 private PcVoice(){}
 private static final String[] TAG0={"nem","sak","kot","mih","moe","mak","ali","nan","tam","izu","kud","yor","kas","asu","sug","koy","uta","mis","pir"};
 private static final int[] ID0={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,11,6};
 private static final String[] TAG1={"otm","yme","mnt","kko","nnk","anz","akn","mya","myk","erk","ais","mhr","wtr","sgn","skr","hrm","mik","myu","yto","snb","mak","yki","hks","rdj","sys","yzu","sin","jun","ysy","rio","flr","jim","mgm","mki","rno","bot","kai","mdr","ayk","syo"};
 private static final int[] ID1={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15,15};
 public static int character(String resource,boolean second){
  String name=resource.replace('/', '\\').toLowerCase(Locale.ROOT);String[] tags=second?TAG1:TAG0;int[] ids=second?ID1:ID0;
  if(second){int at=name.lastIndexOf('_');if(at>=0){String tail=name.substring(at+1);for(int n=0;n<tags.length;n++)if(tail.startsWith(tags[n]))return ids[n];}return 9;}
  for(int at=0;at<name.length();at++)if(name.charAt(at)=='\\')for(int n=0;n<tags.length;n++)if(name.startsWith(tags[n],at+1))return ids[n];return 17;
 }
 public static boolean forced(String resource,boolean second){for(String part:resource.replace('/', '\\').toUpperCase(Locale.ROOT).split("\\\\"))if(second?part.startsWith("SYS"):part.startsWith("SYSTEM")||part.startsWith("OMAKE")||part.startsWith("GAYA"))return true;return false;}
}
