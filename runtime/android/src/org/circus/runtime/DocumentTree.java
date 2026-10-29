package org.circus.runtime;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import org.circus.core.*;

/** Read-only Storage Access Framework tree. No broad-storage permission or extraction. */
public final class DocumentTree implements GameResources.Storage {
    private final ContentResolver resolver;private final Uri tree;private final boolean includeSaves;
    private final Map<String,Uri> files=new TreeMap<>();
    private final Set<String> displayPaths=new TreeSet<>();
    public DocumentTree(ContentResolver resolver,Uri tree)throws IOException{this(resolver,tree,false);}
    /** SaveData is indexed only for an explicit original-save import. */
    public DocumentTree(ContentResolver resolver,Uri tree,boolean includeSaves)throws IOException{this.resolver=resolver;this.tree=tree;this.includeSaves=includeSaves;scan(DocumentsContract.getTreeDocumentId(tree),"",0);}
    private void scan(String id,String prefix,int depth)throws IOException {
        if(depth>12||files.size()>100000)throw new IOException("Selected tree exceeds index limits");
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(Cursor c=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(c==null)throw new IOException("Document provider returned no cursor");
            while(c.moveToNext()){
                String child=c.getString(0),name=c.getString(1),mime=c.getString(2);Binary.safePath(name);
                if(name.indexOf('/')>=0||name.indexOf('\\')>=0)throw new IOException("Provider returned path-like filename");
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
                    if((includeSaves||!name.equalsIgnoreCase("SaveData"))&&!name.equalsIgnoreCase("backups"))scan(child,prefix+name+"/",depth+1);
                } else {String key=(prefix+name).toLowerCase(Locale.ROOT);if(files.put(key,DocumentsContract.buildDocumentUriUsingTree(tree,child))!=null)throw new IOException("Case-colliding resources");displayPaths.add(prefix+name);}
            }
        }catch(SecurityException e){throw new IOException("Directory permission revoked",e);}
    }
    public Set<String> paths(){return Collections.unmodifiableSet(displayPaths);}
    public byte[] read(String name,int limit)throws IOException{
        Uri uri=files.get(Binary.safePath(name).toLowerCase(Locale.ROOT));if(uri==null)throw new FileNotFoundException(name);
        try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new IOException("Provider cannot open "+name);return Binary.read(in,limit);}
    }
    public Archive.Source source(String name)throws IOException {
        final Uri uri=files.get(Binary.safePath(name).toLowerCase(Locale.ROOT));if(uri==null)throw new FileNotFoundException(name);
        return new Archive.Source(){
            public long size()throws IOException{try(ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"r")){if(fd==null||fd.getStatSize()<0)throw new IOException("Archive provider must support file size/seeking");return fd.getStatSize();}}
            public byte[] read(long offset,int length)throws IOException{
                ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"r");
                if(fd==null)throw new IOException("Cannot open archive");
                try(FileInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
                    Binary.range(offset,length,fd.getStatSize());FileChannel channel=in.getChannel();channel.position(offset);byte[] b=new byte[length];ByteBuffer buffer=ByteBuffer.wrap(b);
                    while(buffer.hasRemaining()){int n=channel.read(buffer);if(n<0)throw new EOFException("Short archive slice");if(n==0)throw new IOException("Provider did not supply requested data");}return b;
                }
            }
        };
    }
    /** Static admission report. Parsing a file is not permission to execute its script. */
    public String inspect()throws IOException {
        int parsed=0,failed=0,unknown=0,plainText=0,blockedText=0;Map<String,Integer> profiles=new TreeMap<>(),ops=new TreeMap<>();String first="",textIssue="";
        GameResources resources=new GameResources(this);GameVersion version=GameVersion.detect(resources);Profile profile=version.profile();
        TextCodec codec=TextConfiguration.load(resources);
        for(String path:files.keySet())if(path.startsWith("advdata/mes/")&&path.endsWith(".mes")){
            byte[] data=read(path,64*1024*1024);MesScript script=null;
            try{script=new MesScript(path,data,profile);}catch(IOException ignored){}
            if(script==null){failed++;continue;}parsed++;profiles.put(script.profile.id,profiles.getOrDefault(script.profile.id,0)+1);
            if(script.profile.id.equals("dc3dd-a5a8"))for(MesScript.Record r:script.records())if(r.opcode==0x64||r.opcode==0x65){
                try{NativeText.plain(script.nativeText(r),codec,null,null,r.opcode==0x64);plainText++;}
                catch(IOException e){blockedText++;if(textIssue.isEmpty())textIssue=path+" @0x"+Integer.toHexString(r.offset)+": "+e.getMessage();}
            }
            for(MesScript.Record r:script.records())if(script.profile.operation(r.opcode).equals("unknown")){unknown++;String key=String.format(Locale.ROOT,"%s / %02X",script.profile.id,r.opcode);ops.put(key,ops.getOrDefault(key,0)+1);if(first.isEmpty())first=path+" @0x"+Integer.toHexString(r.offset)+" opcode "+Integer.toHexString(r.opcode);}
        }
        return "已索引文件："+files.size()+"\nMES 已解析："+parsed+"，解析失败："+failed+"\n整套脚本版本："+profiles+"\n未知物理指令记录："+unknown+"\n首个待适配位置："+first+"\nDD 文本可解码："+plainText+"，含未适配控制或缺失姓名状态："+blockedText+"\n首个文本问题："+textIssue+"\n\n这里只进行静态检查，解析成功不代表游戏可运行。DC2DM 已接入变量和控制流核心，其原版系统宿主仍待实现；DC3DD 的设置、存读档与原版正文框仍在适配。";
    }
}
