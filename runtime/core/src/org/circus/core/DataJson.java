package org.circus.core;

import java.io.IOException;
import java.util.*;

/** Bounded data-only JSON reader. Does not evaluate configuration or load plug-ins. */
public final class DataJson {
    private final String text;private int at;
    private DataJson(String s){text=s;}
    public static Object parse(String s)throws IOException{DataJson p=new DataJson(s);if(s.startsWith("\uFEFF"))p.at++;Object v=p.value(0);p.space();if(p.at!=s.length())throw p.error();return v;}
    private IOException error(){return new IOException("Invalid JSON at character "+at);}
    private void space(){while(at<text.length()&&" \r\n\t".indexOf(text.charAt(at))>=0)at++;}
    private boolean take(char c){space();if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
    private Object value(int depth)throws IOException{
        if(depth>32)throw error();space();if(at>=text.length())throw error();char c=text.charAt(at);
        if(c=='"')return string();
        if(take('{')){Map<String,Object> m=new LinkedHashMap<>();if(take('}'))return m;do{space();String k=string();if(!take(':')||m.containsKey(k))throw error();m.put(k,value(depth+1));}while(take(','));if(!take('}'))throw error();return m;}
        if(take('[')){List<Object> a=new ArrayList<>();if(take(']'))return a;do{a.add(value(depth+1));}while(take(','));if(!take(']'))throw error();return a;}
        for(String literal:new String[]{"true","false","null"})if(text.startsWith(literal,at)){at+=literal.length();return literal.equals("null")?null:literal.equals("true");}
        int start=at;while(at<text.length()&&"-+0123456789.eE".indexOf(text.charAt(at))>=0)at++;String n=text.substring(start,at);
        if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw error();try{return Double.valueOf(n);}catch(NumberFormatException ex){throw error();}
    }
    private String string()throws IOException{
        if(at>=text.length()||text.charAt(at++)!='"')throw error();StringBuilder s=new StringBuilder();
        while(at<text.length()){char c=text.charAt(at++);if(c=='"')return s.toString();if(c<32)throw error();if(c!='\\'){s.append(c);continue;}if(at>=text.length())throw error();c=text.charAt(at++);
            switch(c){case '"':case '\\':case '/':s.append(c);break;case 'b':s.append('\b');break;case 'f':s.append('\f');break;case 'n':s.append('\n');break;case 'r':s.append('\r');break;case 't':s.append('\t');break;case 'u':if(at+4>text.length())throw error();try{s.append((char)Integer.parseInt(text.substring(at,at+4),16));}catch(NumberFormatException e){throw error();}at+=4;break;default:throw error();}}
        throw error();
    }
}
