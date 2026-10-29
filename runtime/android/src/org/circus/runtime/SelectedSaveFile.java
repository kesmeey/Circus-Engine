package org.circus.runtime;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import org.circus.core.Binary;

/** One user-selected document. Its display name never determines its format or destination. */
final class SelectedSaveFile {
    interface Source {
        String name();
        byte[] read(int limit)throws IOException;
    }
    static Source open(ContentResolver resolver,Uri uri)throws IOException {
        if(uri==null||!"content".equalsIgnoreCase(uri.getScheme()))throw new IOException("请选择手机中的原版系统存档文件");
        String display="所选存档文件";
        try(Cursor cursor=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(column>=0&&!cursor.isNull(column))display=label(cursor.getString(column));}
        }catch(SecurityException ex){throw new IOException("无法读取所选存档，请重新选择文件",ex);}
        catch(RuntimeException ignored){/* Some providers omit optional document metadata. */}
        final String name=display;
        return new Source(){
            public String name(){return name;}
            public byte[] read(int limit)throws IOException{
                if(limit<0)throw new IOException("Invalid save import size limit");
                try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new IOException("无法打开所选存档文件");return Binary.read(in,limit);}
                catch(SecurityException ex){throw new IOException("所选存档的读取权限已失效，请重新选择文件",ex);}
            }
        };
    }
    static byte[] read(Source source,int limit)throws IOException{
        if(source==null)throw new IOException("请选择原版系统存档文件");
        byte[] bytes;
        try{bytes=source.read(limit);}catch(IOException failure){if(failure.getMessage()!=null&&failure.getMessage().startsWith("Input exceeds limit "))throw new IOException("所选存档文件过大，当前游戏允许的大小不超过 "+limit+" 字节。请确认选择的是系统进度文件。",failure);throw failure;}
        if(bytes==null||bytes.length>limit)throw new IOException("所选存档文件超过大小限制："+limit+" 字节");
        return bytes;
    }
    static String label(String name){
        if(name==null||name.trim().isEmpty())return "所选存档文件";
        StringBuilder out=new StringBuilder();for(int n=0;n<name.length()&&out.length()<256;n++){char c=name.charAt(n);out.append(Character.isISOControl(c)?' ':c);}return out.toString();
    }
    private SelectedSaveFile(){}
}
