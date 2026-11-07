package org.circus.core;
import java.io.*;
import java.util.*;

/** Current7988... native packed/tail/stride and transfer-call references. */
public final class Pc1ReturnSurfaceTests {
 private static int count;
 private static void check(boolean b,String why){count++;if(!b)throw new AssertionError(why);}
 private interface Work {void run()throws Exception;}
 private static void reject(Work work,String expected)throws Exception{boolean bad=false;try{work.run();}catch(IOException e){bad=String.valueOf(e.getMessage()).contains(expected);}check(bad,"Reject "+expected);}
 private static int[] pixels(String hex){int[] p=new int[hex.length()/8];for(int i=0;i<p.length;i++)p[i]=(int)Long.parseLong(hex.substring(i*8,i*8+8),16);return p;}
 private static UiCapture capture(int value)throws Exception{return UiCapture.pixels(2,2,new int[]{value,value,value,value});}
 private static byte[] save(Pc1ReturnSurface value)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();value.save(new DataOutputStream(b));return b.toByteArray();}
 private static Pc1ReturnSurface load(byte[] bytes)throws Exception{return Pc1ReturnSurface.load(new DataInputStream(new ByteArrayInputStream(bytes)));}
 private static final String[][] PIXELS={
        {"1","1","3","ff112233","ffe1c080","ff383f41"},
        {"1","1","6","ff112233","ffe1c080","ff5f5d4f"},
        {"1","1","8","ff112233","ffe1c080","ff797159"},
        {"1","1","10","ff112233","ffe1c080","ff938463"},
        {"1","2","3","ff112233ff583f94","ffe1c080ffec0393","ff383f41ff733393"},
        {"1","2","6","ff112233ff583f94","ffe1c080ffec0393","ff5f5d4fff8f2893"},
        {"1","2","8","ff112233ff583f94","ffe1c080ffec0393","ff797159ffa22193"},
        {"1","2","10","ff112233ff583f94","ffe1c080ffec0393","ff938463ffb41993"},
        {"2","1","3","ff112233ff387540","ffe1c080ff42d1a5","ff383f41ff398652"},
        {"2","1","6","ff112233ff387540","ffe1c080ff42d1a5","ff5f5d4fff3b9765"},
        {"2","1","8","ff112233ff387540","ffe1c080ff42d1a5","ff797159ff3da372"},
        {"2","1","10","ff112233ff387540","ffe1c080ff42d1a5","ff938463ff3eae7f"},
        {"2","2","3","ff112233ff387540ff583f94ff7f92a1","ffe1c080ff42d1a5ffec0393ff4d14b8","ff383f41ff398652ff733393ff757aa5"},
        {"2","2","6","ff112233ff387540ff583f94ff7f92a1","ffe1c080ff42d1a5ffec0393ff4d14b8","ff5f5d4fff3b9765ff8f2893ff6c62a9"},
        {"2","2","8","ff112233ff387540ff583f94ff7f92a1","ffe1c080ff42d1a5ffec0393ff4d14b8","ff797159ff3da372ffa22193ff6653ac"},
        {"2","2","10","ff112233ff387540ff583f94ff7f92a1","ffe1c080ff42d1a5ffec0393ff4d14b8","ff938463ff3eae7fffb41993ff5f43af"},
        {"3","1","3","ff112233ff387540ff5fc84d","ffe1c080ff42d1a5ffa3e2ca","ff383f41ff398652ff6bcc64"},
        {"3","1","6","ff112233ff387540ff5fc84d","ffe1c080ff42d1a5ffa3e2ca","ff5f5d4fff3b9765ff78d17b"},
        {"3","1","8","ff112233ff387540ff5fc84d","ffe1c080ff42d1a5ffa3e2ca","ff797159ff3da372ff81d58b"},
        {"3","1","10","ff112233ff387540ff5fc84d","ffe1c080ff42d1a5ffa3e2ca","ff938463ff3eae7fff89d89b"},
        {"3","2","3","ff112233ff387540ff5fc84dff583f94ff7f92a1ffa6e5ae","ffe1c080ff42d1a5ffa3e2caffec0393ff4d14b8ffae25dd","ff383f41ff398652ff6bcc64ff733393ff757aa5ffa7c1b6"},
        {"3","2","6","ff112233ff387540ff5fc84dff583f94ff7f92a1ffa6e5ae","ffe1c080ff42d1a5ffa3e2caffec0393ff4d14b8ffae25dd","ff5f5d4fff3b9765ff78d17bff8f2893ff6c62a9ffa99dbf"},
        {"3","2","8","ff112233ff387540ff5fc84dff583f94ff7f92a1ffa6e5ae","ffe1c080ff42d1a5ffa3e2caffec0393ff4d14b8ffae25dd","ff797159ff3da372ff81d58bffa22193ff6653acffaa85c5"},
        {"3","2","10","ff112233ff387540ff5fc84dff583f94ff7f92a1ffa6e5ae","ffe1c080ff42d1a5ffa3e2caffec0393ff4d14b8ffae25dd","ff938463ff3eae7fff89d89bffb41993ff5f43afffab6dcb"},
        {"4","1","3","ff112233ff387540ff5fc84dff861b5a","ffe1c080ff42d1a5ffa3e2caff04f3ef","ff383f41ff398652ff6bcc64ff6d4375"},
        {"4","1","6","ff112233ff387540ff5fc84dff861b5a","ffe1c080ff42d1a5ffa3e2caff04f3ef","ff5f5d4fff3b9765ff78d17bff556c91"},
        {"4","1","8","ff112233ff387540ff5fc84dff861b5a","ffe1c080ff42d1a5ffa3e2caff04f3ef","ff797159ff3da372ff81d58bff4587a4"},
        {"4","1","10","ff112233ff387540ff5fc84dff861b5a","ffe1c080ff42d1a5ffa3e2caff04f3ef","ff938463ff3eae7fff89d89bff34a2b7"},
        {"4","2","3","ff112233ff387540ff5fc84dff861b5aff583f94ff7f92a1ffa6e5aeffcd38bb","ffe1c080ff42d1a5ffa3e2caff04f3efffec0393ff4d14b8ffae25ddff0f3602","ff383f41ff398652ff6bcc64ff6d4375ff733393ff757aa5ffa7c1b6ffa93798"},
        {"4","2","6","ff112233ff387540ff5fc84dff861b5aff583f94ff7f92a1ffa6e5aeffcd38bb","ffe1c080ff42d1a5ffa3e2caff04f3efffec0393ff4d14b8ffae25ddff0f3602","ff5f5d4fff3b9765ff78d17bff556c91ff8f2893ff6c62a9ffa99dbfff853775"},
        {"4","2","8","ff112233ff387540ff5fc84dff861b5aff583f94ff7f92a1ffa6e5aeffcd38bb","ffe1c080ff42d1a5ffa3e2caff04f3efffec0393ff4d14b8ffae25ddff0f3602","ff797159ff3da372ff81d58bff4587a4ffa22193ff6653acffaa85c5ff6e375e"},
        {"4","2","10","ff112233ff387540ff5fc84dff861b5aff583f94ff7f92a1ffa6e5aeffcd38bb","ffe1c080ff42d1a5ffa3e2caff04f3efffec0393ff4d14b8ffae25ddff0f3602","ff938463ff3eae7fff89d89bff34a2b7ffb41993ff5f43afffab6dcbff563647"},
        {"5","1","3","ff112233ff387540ff5fc84dff861b5affad6e67","ffe1c080ff42d1a5ffa3e2caff04f3efff650414","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57"},
        {"5","1","6","ff112233ff387540ff5fc84dff861b5affad6e67","ffe1c080ff42d1a5ffa3e2caff04f3efff650414","ff5f5d4fff3b9765ff78d17bff556c91ff924647"},
        {"5","1","8","ff112233ff387540ff5fc84dff861b5affad6e67","ffe1c080ff42d1a5ffa3e2caff04f3efff650414","ff797159ff3da372ff81d58bff4587a4ff89393d"},
        {"5","1","10","ff112233ff387540ff5fc84dff861b5affad6e67","ffe1c080ff42d1a5ffa3e2caff04f3efff650414","ff938463ff3eae7fff89d89bff34a2b7ff802b33"},
        {"5","2","3","ff112233ff387540ff5fc84dff861b5affad6e67ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffec0393ff4d14b8ffae25ddff0f3602ff704727","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ff733393ff757aa5ffa7c1b6ffa93798ffdb7ea9"},
        {"5","2","6","ff112233ff387540ff5fc84dff861b5affad6e67ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffec0393ff4d14b8ffae25ddff0f3602ff704727","ff5f5d4fff3b9765ff78d17bff556c91ff924647ff8f2893ff6c62a9ffa99dbfff853775ffc2718b"},
        {"5","2","8","ff112233ff387540ff5fc84dff861b5affad6e67ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffec0393ff4d14b8ffae25ddff0f3602ff704727","ff797159ff3da372ff81d58bff4587a4ff89393dffa22193ff6653acffaa85c5ff6e375effb26977"},
        {"5","2","10","ff112233ff387540ff5fc84dff861b5affad6e67ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffec0393ff4d14b8ffae25ddff0f3602ff704727","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffb41993ff5f43afffab6dcbff563647ffa16063"},
        {"7","1","3","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265e","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ffd1a068ffd3177a"},
        {"7","1","6","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265e","ff5f5d4fff3b9765ff78d17bff556c91ff924647ffce805dffab1a73"},
        {"7","1","8","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265e","ff797159ff3da372ff81d58bff4587a4ff89393dffcd6b56ff911d6f"},
        {"7","1","10","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265e","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffcb554fff761f6b"},
        {"7","2","3","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265effec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ffd1a068ffd3177aff733393ff757aa5ffa7c1b6ffa93798ffdb7ea9ff3dc4bbff3f3bcc"},
        {"7","2","6","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265effec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971","ff5f5d4fff3b9765ff78d17bff556c91ff924647ffce805dffab1a73ff8f2893ff6c62a9ffa99dbfff853775ffc2718bff5faba1ff3c46b7"},
        {"7","2","8","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265effec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971","ff797159ff3da372ff81d58bff4587a4ff89393dffcd6b56ff911d6fffa22193ff6653acffaa85c5ff6e375effb26977ff769b90ff3a4da9"},
        {"7","2","10","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265effec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffcb554fff761f6bffb41993ff5f43afffab6dcbff563647ffa16063ff8c8a7fff38549b"},
        {"8","1","3","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678e","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ffd1a068ffd3177aff355e8b"},
        {"8","1","6","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678e","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783","ff5f5d4fff3b9765ff78d17bff556c91ff924647ffce805dffab1a73ff485589"},
        {"8","1","8","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678e","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783","ff797159ff3da372ff81d58bff4587a4ff89393dffcd6b56ff911d6fff554f88"},
        {"8","1","10","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678e","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffcb554fff761f6bff614987"},
        {"8","2","3","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984ef","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ffd1a068ffd3177aff355e8bff733393ff757aa5ffa7c1b6ffa93798ffdb7ea9ff3dc4bbff3f3bccff7082de"},
        {"8","2","6","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984ef","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96","ff5f5d4fff3b9765ff78d17bff556c91ff924647ffce805dffab1a73ff485589ff8f2893ff6c62a9ffa99dbfff853775ffc2718bff5faba1ff3c46b7ff7880cd"},
        {"8","2","8","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984ef","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96","ff797159ff3da372ff81d58bff4587a4ff89393dffcd6b56ff911d6fff554f88ffa22193ff6653acffaa85c5ff6e375effb26977ff769b90ff3a4da9ff7e7fc2"},
        {"8","2","10","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984ef","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffcb554fff761f6bff614987ffb41993ff5f43afffab6dcbff563647ffa16063ff8c8a7fff38549bff837db7"},
        {"9","1","3","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9b","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ffd1a068ffd3177aff355e8bff67a49d"},
        {"9","1","6","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9b","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8","ff5f5d4fff3b9765ff78d17bff556c91ff924647ffce805dffab1a73ff485589ff858f9f"},
        {"9","1","8","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9b","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8","ff797159ff3da372ff81d58bff4587a4ff89393dffcd6b56ff911d6fff554f88ff9981a1"},
        {"9","1","10","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9b","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffcb554fff761f6bff614987ffad72a3"},
        {"9","2","3","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9bff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984efff90d7fc","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96fff48bbb","ff383f41ff398652ff6bcc64ff6d4375ff9f5a57ffd1a068ffd3177aff355e8bff67a49dff733393ff757aa5ffa7c1b6ffa93798ffdb7ea9ff3dc4bbff3f3bccff7082deffa2c8ef"},
        {"9","2","6","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9bff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984efff90d7fc","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96fff48bbb","ff5f5d4fff3b9765ff78d17bff556c91ff924647ffce805dffab1a73ff485589ff858f9fff8f2893ff6c62a9ffa99dbfff853775ffc2718bff5faba1ff3c46b7ff7880cdffb5bae3"},
        {"9","2","8","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9bff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984efff90d7fc","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96fff48bbb","ff797159ff3da372ff81d58bff4587a4ff89393dffcd6b56ff911d6fff554f88ff9981a1ffa22193ff6653acffaa85c5ff6e375effb26977ff769b90ff3a4da9ff7e7fc2ffc2b1db"},
        {"9","2","10","ff112233ff387540ff5fc84dff861b5affad6e67ffd4c174fffb1481ff22678eff49ba9bff583f94ff7f92a1ffa6e5aeffcd38bbfff48bc8ff1bded5ff4231e2ff6984efff90d7fc","ffe1c080ff42d1a5ffa3e2caff04f3efff650414ffc61539ff27265eff883783ffe948a8ffec0393ff4d14b8ffae25ddff0f3602ff704727ffd1584cff326971ff937a96fff48bbb","ff938463ff3eae7fff89d89bff34a2b7ff802b33ffcb554fff761f6bff614987ffad72a3ffb41993ff5f43afffab6dcbff563647ffa16063ff8c8a7fff38549bff837db7ffcea7d3"}
 };
 private static final String[][] PHASES={
        {"speed0","0","false","-1","-999","1:3,2:3,3:3,4:3,5:3,6:3,7:3,8:3,9:3,10:3,11:3,12:3,13:3,14:3,15:3,16:3","0","16","0","0"},
        {"speed1","1","false","-1","-999","2:6,4:6,6:6,8:6,10:6,12:6,14:6,16:6","0","16","0","0"},
        {"speed2","2","false","-1","-999","4:8,8:8,12:8,16:8","0","16","0","0"},
        {"speed3","3","false","-1","-999","8:10,16:10","0","16","0","0"},
        {"speed4","4","false","-1","-999","1:3,2:3,3:3,4:3,5:3,6:3,7:3,8:3,9:3,10:3,11:3,12:3,13:3,14:3,15:3,16:3","0","16","0","0"},
        {"speed-1","-1","false","-1","-999","1:3,2:3,3:3,4:3,5:3,6:3,7:3,8:3,9:3,10:3,11:3,12:3,13:3,14:3,15:3,16:3","0","16","0","0"},
        {"ctrl","0","true","-1","-999","8:10,16:10","0","16","0","0"},
        {"u32wrap","2","false","-1","-999","4:8,8:8,12:8,16:8","0","16","0","0"},
        {"speed_changes","0","false","-1","3","1:3,9:10,16:10","0","16","0","0"},
        {"interrupt_first","0","false","1","-999","1:3","1","1","1","1"},
        {"interrupt_final","3","false","2","-999","8:10,16:10","1","16","1","1"}
 };
 public static int run()throws Exception {
  count=0;
  for(String[] row:PIXELS){int w=Integer.parseInt(row[0]),h=Integer.parseInt(row[1]),ratio=Integer.parseInt(row[2]),speed=ratio==3?0:ratio==6?1:ratio==8?2:3;UiCapture from=UiCapture.pixels(w,h,pixels(row[3])),to=UiCapture.pixels(w,h,pixels(row[4]));Pc1ReturnSurface v=Pc1ReturnSurface.begin(from,to,speed,false);check(Arrays.equals(v.image().pixels().argb,pixels(row[5])),"Native4079A0 packed/tail/stride RGB "+w+"x"+h+" ratio"+ratio);check(v.feedbackFrames()==1&&v.lastRatio()==ratio&&v.phaseElapsed()==0&&v.active(),"Immediate first feedback phase");}
  UiCapture from=capture(0xff204060),to=capture(0xffa08040);
  for(String[] row:PHASES){int speed=Integer.parseInt(row[1]),abort=Integer.parseInt(row[3]),change=Integer.parseInt(row[4]);boolean ctrl=Boolean.parseBoolean(row[2]);Pc1ReturnSurface v=Pc1ReturnSurface.begin(from,to,speed,ctrl);List<String> draws=new ArrayList<>();while(v.active()){draws.add(v.progress()+":"+v.lastRatio());if(v.feedbackFrames()==abort){v.interrupt();break;}if(v.feedbackFrames()==1&&change!= -999)speed=change;check(v.elapse(40,speed,ctrl)==40,"Native frame interval "+row[0]);}check(String.join(",",draws).equals(row[5]),"Native feedback/progress journal "+row[0]);check(v.progress()==Integer.parseInt(row[7]),"Native9CCA8C "+row[0]);check(v.flagsHeld()==(Integer.parseInt(row[8])!=0)&&v.flagsHeld()==(Integer.parseInt(row[9])!=0),"Native BB8434/94C698 final flags "+row[0]);check(v.interrupted()==(Integer.parseInt(row[6])!=0)&&v.captureRetired()==v.committed(),"Interruption retains pending capture "+row[0]);byte[] bytes=save(v);check(Arrays.equals(bytes,save(load(bytes))),"Phase/front/target codec "+row[0]);}
  Pc1ReturnSurface v=Pc1ReturnSurface.begin(from,to,0,false);check(v.progress()==1&&v.image().pixels().argb[0]!=from.pixels().argb[0],"First RGB feedback already happened before waiting");check(v.elapse(600,0,false)==600&&v.progress()==16&&v.active()&&v.feedbackFrames()==16,"Sixteen progress frames are still awaiting final40ms");UiCapture penultimate=v.image();check(!Arrays.equals(penultimate.pixels().argb,to.pixels().argb),"Lastfeedback is not exact-copy target");check(v.elapse(39,0,false)==39&&v.active()&&v.phaseElapsed()==39&&v.image()==penultimate,"Lastfeedback holds for39ms");check(v.elapse(9999,0,false)==1&&v.committed()&&!v.flagsHeld()&&v.captureRetired()&&v.image()==to,"Finalboundary exact copy, flags reset and remaining time returned");check(Arrays.equals(penultimate.pixels().argb,load(save(Pc1ReturnSurface.begin(from,to,0,false))).image().pixels().argb)==false,"Earlier immutable images do not alias later feedback");check(v.elapse(Long.MAX_VALUE,3,true)==0&&v.image()==to,"Committedfront survives logical time and temporary capture retirement");v.interrupt();check(v.committed(),"Late interrupt cannot undo committed pixels");
  Pc1ReturnSurface one=Pc1ReturnSurface.begin(from,to,0,false),pieces=Pc1ReturnSurface.begin(from,to,0,false);one.elapse(777,0,false);for(int n=0;n<777;n++)pieces.elapse(1,0,false);check(Arrays.equals(save(one),save(pieces)),"Large and partitioned elapsed time have identical front/phase");
  Pc1ReturnSurface interrupted=Pc1ReturnSurface.begin(from,to,3,false);interrupted.elapse(40,3,false);check(interrupted.progress()==16&&interrupted.active(),"Fastfinal feedback remains interruptible");interrupted.interrupt();UiCapture partial=interrupted.image();check(interrupted.interrupted()&&!interrupted.captureRetired()&&interrupted.flagsHeld()&&interrupted.elapse(1000,3,false)==0&&interrupted.image()==partial,"Interruptatfinalframe never exactcopies or releases capture");
  Pc1ReturnSurface paused=Pc1ReturnSurface.begin(from,to,1,false);paused.elapse(17,1,false);Pc1ReturnSurface resumed=load(save(paused));check(resumed.phaseElapsed()==17&&resumed.progress()==2,"Codec retains local frame clock");paused.elapse(1000,2,false);resumed.elapse(1000,2,false);check(Arrays.equals(save(paused),save(resumed)),"Resume does not repeat a feedback frame");
  Pc1ReturnSurface same=Pc1ReturnSurface.begin(to,to,0,false);check(same.active()&&same.elapse(639,0,false)==639&&same.active(),"Equal bitmaps do not bypass native timing");same.elapse(1,0,false);check(same.committed(),"Equal bitmaps commit onlyafter640ms");
  Pc1ReturnSurface raster=Pc1ReturnSurface.begin(from,to,0,false);UiCapture.Pixels direct=raster.frontPixels();check(direct.width==2&&direct.height==2&&Arrays.equals(direct.argb,raster.image().pixels().argb),"Direct raster matches immutable capture without a zlib requirement");int original=direct.argb[0];direct.argb[0]=0;raster.elapse(0,0,false);check(raster.frontPixels().argb[0]==original,"Direct pixels cannot mutate the owned front surface");
  reject(()->Pc1ReturnSurface.begin(capture(0x80204060),to,0,false),"opaque");reject(()->Pc1ReturnSurface.begin(from,UiCapture.pixels(1,1,new int[]{0xff000000}),0,false),"dimensions differ");reject(()->Pc1ReturnSurface.begin(null,to,0,false),"raster");
  final byte[] badFormat=save(resumed);badFormat[0]=0;reject(()->load(badFormat),"format");final byte[] invalid=save(resumed);invalid[11]=17;reject(()->load(invalid),"phase");
  return count;
 }
 public static void main(String[] args)throws Exception{System.out.println("PC1 return surface assertions: "+run());}
}
