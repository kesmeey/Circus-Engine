package org.circus.core;

import java.io.*;
import java.util.*;

/** Original ActionParser track syntax, sampled without Unity/LeanTween dependencies. */
public final class Dc3rAnimation {
    static final class Key {double from,to,start,end;}
    private final Map<Character,List<Key>> tracks=new HashMap<>();
    public final boolean pack;
    public final double durationSeconds;
    public Dc3rAnimation(String text)throws IOException {
        if(text.length()>65536)throw new IOException("Animation expression limit");boolean p=false;double duration=0;
        for(String part:text.split(":")){if(part.isEmpty())continue;char type=part.charAt(0);if(type=='P'){p=true;continue;}if(type=='Q')continue;if("TXYWH".indexOf(type)<0)throw new IOException("Unknown action track "+type);List<Key> keys=new ArrayList<>();tracks.put(type,keys);Cursor c=new Cursor(part.substring(1));double clock=0,previous=0;
            while(!c.end()){if(c.peek()=='/'){c.at++;continue;}if(keys.size()>=4096)throw new IOException("Animation key limit");Key k=new Key();k.start=clock;
                if(c.peek()=='w'){c.at++;k.from=k.to=previous;k.end=clock+c.integer()/12.0;}
                else {k.from=k.to=c.integer();k.end=clock;if(!c.end()&&c.peek()=='.'){c.at++;k.to=c.integer();k.end=clock+Math.abs(k.to-k.from)/12.0;}if(!c.end()&&c.peek()=='@'){c.at++;int speed=c.integer();if(speed<=0)throw new IOException("Invalid action speed");k.end=clock+Math.abs(k.to-k.from)/speed/12.0;}}
                if(k.end<clock||k.end>86400)throw new IOException("Invalid action duration");previous=k.to;clock=k.end;keys.add(k);
            }duration=Math.max(duration,clock);
        }pack=p;durationSeconds=duration;
    }
    public boolean has(char track){return tracks.containsKey(track)&&!tracks.get(track).isEmpty();}
    public double value(char track,double time,double fallback){List<Key> keys=tracks.get(track);if(keys==null||keys.isEmpty())return fallback;double result=0;for(int i=keys.size()-1;i>=0;i--){Key k=keys.get(i);if(time>k.end)return k.to;if(time>k.start)return k.from+(k.to-k.from)*(time-k.start)/(k.end-k.start);result=k.from;}return result;}
    private static final class Cursor {final String value;int at;Cursor(String s){value=s;}boolean end(){return at>=value.length();}char peek(){return value.charAt(at);}int integer()throws IOException{int start=at;if(!end()&&peek()=='-')at++;int digits=at;while(!end()&&peek()>='0'&&peek()<='9')at++;if(at==digits)throw new IOException("Missing action number at "+start);try{return Integer.parseInt(value.substring(start,at));}catch(NumberFormatException e){throw new IOException("Action integer limit",e);}}}
}
