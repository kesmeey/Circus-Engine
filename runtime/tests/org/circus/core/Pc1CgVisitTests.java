package org.circus.core;

import java.io.*;
import java.util.*;

/** Current41ADA0+4252C0 actual-instruction fixtures, not Unicode comparison. */
public final class Pc1CgVisitTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static byte[] bytes(String value){byte[] b=new byte[value.length()/2];for(int n=0;n<b.length;n++)b[n]=(byte)Integer.parseInt(value.substring(n*2,n*2+2),16);return b;}
    private static final class Catalog implements Pc1CgVisit.Catalog {byte[][] rows;int count,locale,localeReads;public int count(){return count;}public int localeMode(){localeReads++;return locale;}public int nameByte(int ordinal,int at)throws IOException{long p=(long)ordinal*128+at;int row=(int)(p/128),offset=(int)(p%128);if(row>=rows.length)throw new IOException("unowned native catalog neighbor");return rows[row][offset]&255;}}
    // BEGIN MACHINE VECTORS
    private static final String[] VECTORS={
        "ASCII_case|43675f416230312e435258|6e6f,63675f61423031|2|1",
        "first_match_only|666f6f|464f4f,666f6f|2|0",
        "no_basename|53595354454d5c43675f416230312e637278|43675f41623031,73797374656d5c63675f61623031|2|1",
        "firstdot_directory|6469722e77697468646f745c666f6f2e637278|666f6f,444952|2|1",
        "leading_dot_empty|2e666f6f|6f74686572,|2|1",
        "terminator|58006578747261|58|1|0",
        "nonASCII_unchanged|c04162|e06162,c06142|2|1",
        "no_unicode_case|c9|e9|1|",
        "zero_count|666f6f||0|",
        "negative_count|666f6f||-1|",
        "prefix_is_not_match|666f6f|666f6f626172|1|",
        "empty_matches_empty|||1|0"
    };
    // END MACHINE VECTORS
    public static int run()throws Exception{
        count=0;check(VECTORS.length==12,"all CG machine programs");
        for(String vector:VECTORS){String[] f=vector.split("\\|",-1);Catalog catalog=new Catalog();String[] names=f[2].isEmpty()?(Integer.parseInt(f[3])>0?new String[]{""}:new String[0]):f[2].split(",",-1);catalog.rows=new byte[names.length][128];for(int n=0;n<names.length;n++){byte[] value=bytes(names[n]);System.arraycopy(value,0,catalog.rows[n],0,value.length);}catalog.count=Integer.parseInt(f[3]);List<Integer> changes=new ArrayList<>();Pc1CgVisit.visit(bytes(f[1]),catalog,(index,value)->{check(value==1,"native CG low-byte mark");changes.add(index);});StringBuilder changed=new StringBuilder();for(int index:changes){if(changed.length()>0)changed.append(',');changed.append(index);}check(changed.toString().equals(f[4]),f[0]+" original marked index/first match");if(catalog.count<=0)check(catalog.localeReads==0,"empty catalog does not touch CRT locale");}
        Catalog locale=new Catalog();locale.rows=new byte[1][128];locale.count=1;locale.locale=1;boolean failed=false;try{Pc1CgVisit.visit(new byte[]{'A'},locale,(i,v)->{});}catch(IOException precise){failed=precise.getMessage().contains("42534C");}check(failed,"non-C CRT locale is required, not approximated by Unicode");
        final int[] exact={0};Pc1CgVisit.visit(new byte[0],locale,(i,v)->exact[0]++);check(exact[0]==1,"non-C locale exact bytes bypass42534C");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("Pc1CgVisitTests assertions: "+run());}
}

