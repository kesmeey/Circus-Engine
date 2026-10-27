package org.circus.runtime;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import org.circus.core.Binary;

/** Atomic per-directory saves. UI/audio positions wrap the portable VM snapshot. */
final class GameSaves {
    final File directory;
    static final class Save {byte[] core;String script;long when,ticket,revealTime,movieMillis;int page;boolean revealed;int[] audio=new int[6];}
    GameSaves(Context context,String tree)throws IOException {
        this(context,tree,"dc3dd");
    }
    GameSaves(Context context,String tree,String profile)throws IOException {
        if(!profile.matches("[a-z0-9-]+"))throw new IOException("Invalid save namespace");try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(tree.getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder();for(int i=0;i<12;i++)key.append(String.format(Locale.ROOT,"%02x",digest[i]));directory=new File(context.getFilesDir(),profile+"-"+key);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建存档目录");}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    File file(int slot){return new File(directory,slot==0?"auto.cas":"slot"+slot+".cas");}
    boolean exists(int slot){return file(slot).isFile()||new File(file(slot)+".bak").isFile();}
    String label(int slot){String prefix=slot==0?"自动存档":"存档 "+slot;try{Save s=read(slot);return prefix+"  ·  "+s.script+"  ·  "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(s.when));}catch(FileNotFoundException e){return prefix+"  ·  空";}catch(IOException e){return prefix+"  ·  无法读取";}}
    void write(int slot,Save s)throws IOException {
        AtomicFile file=new AtomicFile(file(slot));FileOutputStream stream=null;
        try{stream=file.startWrite();DataOutputStream out=new DataOutputStream(stream);out.writeInt(0x43415332);out.writeUTF(s.script);out.writeLong(s.when);out.writeLong(s.ticket);out.writeInt(s.page);out.writeBoolean(s.revealed);out.writeLong(s.revealTime);out.writeLong(s.movieMillis);for(int n:s.audio)out.writeInt(n);out.writeInt(s.core.length);out.write(s.core);out.flush();file.finishWrite(stream);}catch(IOException e){file.failWrite(stream);throw e;}
    }
    Save read(int slot)throws IOException {
        try(DataInputStream in=new DataInputStream(new AtomicFile(file(slot)).openRead())){int version=in.readInt();if(version!=0x43415331&&version!=0x43415332)throw new IOException("存档版本不兼容");Save s=new Save();s.script=in.readUTF();s.when=in.readLong();s.ticket=in.readLong();s.page=in.readInt();s.revealed=in.readBoolean();s.revealTime=in.readLong();s.movieMillis=version==0x43415332?in.readLong():0;if(s.movieMillis<0||s.movieMillis>86400000)throw new IOException("影片位置无效");if(s.page<0||s.page>10000||s.revealTime<0)throw new IOException("存档页面无效");for(int i=0;i<6;i++){s.audio[i]=in.readInt();if(s.audio[i]<0)throw new IOException("存档音频位置无效");}int n=in.readInt();if(n<0||n>4*1024*1024)throw new IOException("存档大小无效");s.core=new byte[n];in.readFully(s.core);if(in.read()!=-1)throw new IOException("存档尾部数据无效");return s;}
    }
}
