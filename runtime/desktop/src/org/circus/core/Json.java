package org.circus.core;

import java.util.*;

/** Small writer for evidence files; the core does not depend on a JSON library. */
public final class Json {
    private Json() {}
    public static Map<String,Object> obj(Object... pairs) {
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);
        return m;
    }
    public static String text(Object value) {
        if(value==null)return "null";
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map){StringJoiner j=new StringJoiner(",","{","}");for(Map.Entry<?,?> e:((Map<?,?>)value).entrySet())j.add(text(e.getKey().toString())+":"+text(e.getValue()));return j.toString();}
        if(value instanceof Iterable){StringJoiner j=new StringJoiner(",","[","]");for(Object v:(Iterable<?>)value)j.add(text(v));return j.toString();}
        if(value.getClass().isArray()){StringJoiner j=new StringJoiner(",","[","]");for(int i=0;i<java.lang.reflect.Array.getLength(value);i++)j.add(text(java.lang.reflect.Array.get(value,i)));return j.toString();}
        StringBuilder b=new StringBuilder("\"");for(char c:value.toString().toCharArray()){switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();
    }
}
