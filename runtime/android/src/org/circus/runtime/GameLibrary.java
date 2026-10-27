package org.circus.runtime;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Durable directory grants and per-game choices. Does not own game files or saves. */
final class GameLibrary {
    private static final Object LOCK=new Object();
    private final AtomicFile file;
    static final class Entry {
        String id,tree,title,profile="",executable="",encoding="auto",configuration="";
        boolean configured;long opened;
        Entry(String tree){this.id=UUID.randomUUID().toString();this.tree=tree;this.title=label(tree);}
        Entry(JSONObject o)throws JSONException{
            id=o.getString("id");tree=o.getString("tree");title=o.getString("title");profile=o.optString("profile");
            executable=o.optString("executable");encoding=o.optString("encoding","auto");configuration=o.optString("configuration");configured=o.optBoolean("configured");opened=o.optLong("opened");
        }
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("tree",tree).put("title",title).put("profile",profile).put("executable",executable).put("encoding",encoding).put("configuration",configuration).put("configured",configured).put("opened",opened);}
        static String label(String tree){try{String path=DocumentsContract.getTreeDocumentId(Uri.parse(tree));return path.substring(Math.max(path.lastIndexOf('/'),path.lastIndexOf(':'))+1);}catch(Exception e){return tree;}}
    }
    private static final class State {String selected="";final List<Entry> entries=new ArrayList<>();}
    GameLibrary(Context context)throws IOException{
        this(new File(context.getFilesDir(),"game-library.json"));
        synchronized(LOCK){if(!exists()){
            String old=context.getSharedPreferences("library",0).getString("game_tree",null);
            if(old==null)old=context.getSharedPreferences("MainActivity",0).getString("game_tree",null);
            State s=new State();if(old!=null){Entry e=new Entry(old);s.entries.add(e);s.selected=e.id;}write(s);
        }}
    }
    GameLibrary(File path){file=new AtomicFile(path);}
    private boolean exists(){return file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists();}
    private State read()throws IOException{
        State s=new State();if(!exists())return s;
        try{JSONObject o=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));if(o.getInt("version")!=1)throw new IOException("不支持的游戏列表版本");s.selected=o.optString("selected");JSONArray a=o.getJSONArray("games");Set<String> ids=new HashSet<>(),trees=new HashSet<>();
            for(int i=0;i<a.length();i++){Entry e=new Entry(a.getJSONObject(i));if(!ids.add(e.id)||!trees.add(e.tree))throw new IOException("游戏列表有重复记录");s.entries.add(e);}return s;
        }catch(JSONException e){throw new IOException("游戏列表损坏，原文件已保留",e);}
    }
    private void write(State s)throws IOException{
        byte[] data;try{JSONArray a=new JSONArray();for(Entry e:s.entries)a.put(e.json());data=new JSONObject().put("version",1).put("selected",s.selected).put("games",a).toString(2).getBytes(StandardCharsets.UTF_8);}catch(JSONException e){throw new IOException(e);}
        FileOutputStream out=null;try{out=file.startWrite();out.write(data);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    List<Entry> entries()throws IOException{synchronized(LOCK){State s=read();s.entries.sort((a,b)->{if(a.id.equals(b.id))return 0;if(a.id.equals(s.selected))return -1;if(b.id.equals(s.selected))return 1;return Long.compare(b.opened,a.opened);});return s.entries;}}
    Entry get(String id)throws IOException{synchronized(LOCK){for(Entry e:read().entries)if(e.id.equals(id))return e;throw new IOException("游戏记录已移除，请重新选择目录");}}
    Entry add(String tree)throws IOException{synchronized(LOCK){State s=read();for(Entry e:s.entries)if(e.tree.equals(tree)){s.selected=e.id;write(s);return e;}if(s.entries.size()>=100)throw new IOException("游戏列表已满，请先移除不用的记录");Entry e=new Entry(tree);s.entries.add(e);s.selected=e.id;write(s);return e;}}
    void configure(Entry choice)throws IOException{synchronized(LOCK){State s=read();Entry e=find(s,choice.id);if(!e.tree.equals(choice.tree))throw new IOException("目录已变更，请重新打开配置");e.profile=choice.profile;e.executable=choice.executable;e.encoding=choice.encoding;e.configuration=choice.configuration;e.configured=true;s.selected=e.id;write(s);}}
    void opened(String id)throws IOException{synchronized(LOCK){State s=read();Entry e=find(s,id);e.opened=System.currentTimeMillis();s.selected=id;write(s);}}
    void relink(String id,String tree)throws IOException{synchronized(LOCK){State s=read();for(Entry e:s.entries)if(e.tree.equals(tree)&&!e.id.equals(id))throw new IOException("该目录已在列表中");Entry e=find(s,id);e.tree=tree;e.title=Entry.label(tree);e.configured=false;s.selected=id;write(s);}}
    void remove(String id)throws IOException{synchronized(LOCK){State s=read();s.entries.removeIf(e->e.id.equals(id));if(s.selected.equals(id))s.selected="";write(s);}}
    private static Entry find(State s,String id)throws IOException{for(Entry e:s.entries)if(e.id.equals(id))return e;throw new IOException("游戏记录已移除");}
}
