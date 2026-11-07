package org.circus.core;

/** D.C.II 2006 41EDF0/41EE60/41E5D0; separate from both P.C. character tables. */
public final class Dc2Voice {
    private Dc2Voice(){}
    private static final String[] TAGS={"otm","yme","mnt","kko","nnk","anz","akn","mya","myk","wtr","sgn","skr","yzu","myu","mik","gai","gse","hks","hrm","jun","sin","yto","yki","ysy","syo","rdj"};
    public static int character(String raw){
        for(int at=0;at<raw.length();at++)if(raw.charAt(at)=='\\')for(int n=0;n<TAGS.length;n++)if(raw.regionMatches(true,at+1,TAGS[n],0,3))return Math.min(n,15);
        return 17;
    }
    public static boolean forced(String raw){
        for(int at=0;at<raw.length();){if(raw.regionMatches(true,at,"SYSTEM",0,6)||raw.regionMatches(true,at,"OMAKE",0,5)||raw.regionMatches(true,at,"GAYA",0,4))return true;int next=raw.indexOf('\\',at);if(next<0)break;at=next+1;}
        return false;
    }
    public static int hundredthDb(int master,int level){if(level==0)return -10000;int value=master+100*level-3000;return Math.max(-3000,Math.min(0,value));}
    public static float gain(int master,int level){return (float)Math.pow(10,hundredthDb(master,level)/2000.0);}
}
