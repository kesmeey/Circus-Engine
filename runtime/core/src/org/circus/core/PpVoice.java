package org.circus.core;

import java.io.*;

/** Independently reviewed PP_CN 432010/4320A0/432450 and DirectSound gain 430DA0. */
public final class PpVoice {
    public static final int DEFAULT_VOLUME=27;
    private static final String[] ROLES={"rcc","srr","hmn","sra","aoi","tmj","smm","yuk"};
    private PpVoice(){}
    public static final class Request {
        public final String resource;public final int role,pan;public final boolean forced;private final boolean present;
        private Request(String resource,int role,int pan,boolean forced,boolean present){this.resource=resource;this.role=role;this.pan=pan;this.forced=forced;this.present=present;}
        public boolean allowed(Variables.Special state)throws IOException{return present&&state.read(99,5)!=0&&(forced||state.read(100,60+role)==0);}
    }
    public static int role(String reference){
        String name=reference;int last=-1;
        for(int i=0;i<name.length();i++){if(name.charAt(i)=='\\'&&name.regionMatches(true,i+1,"SYS",0,3))return 8;if(name.charAt(i)=='_')last=i;}
        if(last>=0)for(int n=0;n<ROLES.length;n++)if(name.regionMatches(true,last+1,ROLES[n],0,3))return n;return 8;
    }
    public static boolean forced(String reference){
        if(reference.startsWith("#"))return true;String name=reference;
        for(int i=0;i<name.length();i++)if((i==0||name.charAt(i-1)=='\\')&&name.regionMatches(true,i,"SYS",0,3))return true;return false;
    }
    /** 432450 is the actual commit for both prepared ordinary voice and immediate system voice. */
    public static Request request(String reference){
        String source=reference;int pan=source.startsWith("<")?-1000:source.startsWith(">")?1000:0;
        if(source.startsWith("<")||source.startsWith(">")||source.startsWith("="))source=source.substring(1);
        int role=role(source);String resource=source;
        if(!source.regionMatches(true,0,"VOICE\\",0,6)){int separator=source.indexOf(';');if(separator>=0){resource=source.substring(separator+1);role=source.charAt(0)-'a';}}
        // '#' is a force marker but the original loader retains it in the resource name.
        return new Request(resource,role,pan,forced(source),!reference.isEmpty());
    }
    public static int hundredthDb(int masterDb,int volume){int level=volume*2;if(level<1)return -9999;if(level>60)level=60;int db=masterDb+(level-60)*83;return db< -9998?-9999:db;}
    public static float gain(int masterDb,int volume){return(float)Math.pow(10,hundredthDb(masterDb,volume)/2000.0);}
}
