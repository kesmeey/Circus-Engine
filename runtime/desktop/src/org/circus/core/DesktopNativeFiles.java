package org.circus.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.*;

/** CRT-style file adapter confined to the runtime's own save directory.
 * GameResources remains read-only. Ordinary OS failures return-1, while an
 * unsupported request or a path outside this owner raises IOException. */
public final class DesktopNativeFiles implements Pc1NativeSettings.NativeIo,Closeable {
    private static final class Descriptor {
        final SeekableByteChannel channel;final boolean writing;
        Descriptor(SeekableByteChannel channel,boolean writing){this.channel=channel;this.writing=writing;}
    }
    private final Path root;
    private final Map<Integer,Descriptor> descriptors=new HashMap<>();
    private int nextDescriptor=3;
    private IOException lastFailure;
    public DesktopNativeFiles(Path directory)throws IOException{
        if(directory==null)throw new IOException("Native file save directory missing");
        Path normalized=directory.toAbsolutePath().normalize();Files.createDirectories(normalized);root=normalized.toRealPath();
    }
    public Path directory(){return root;}
    public IOException lastFailure(){return lastFailure;}
    private Path resolve(String source,boolean writing)throws IOException{
        if(source==null||source.indexOf('\0')>=0)throw new IOException("Native file path missing or invalid");
        final Path input;try{input=Paths.get(source.replace('\\','/'));}catch(InvalidPathException failure){throw new IOException("Native file path invalid",failure);}
        Path target=(input.isAbsolute()?input:root.resolve(input)).normalize();
        if(!target.startsWith(root)||target.equals(root))throw new IOException("Native file path leaves runtime save directory");
        Path cursor=root;
        for(Path part:root.relativize(target)){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor))throw new IOException("Native file path crosses a symbolic link");}
        return target;
    }
    public int open(String path,int flags,int mode)throws IOException{
        boolean writing=flags==0x8301;
        if(flags!=0x8000&&!writing||writing&&mode!=0x180||!writing&&mode!=0)throw new IOException("Unsupported native CRT open flags/mode");
        Path target=resolve(path,writing);
        if(descriptors.size()>=128||nextDescriptor==Integer.MAX_VALUE)return -1;
        try{
            if(writing){Files.createDirectories(target.getParent());if(!target.getParent().toRealPath().startsWith(root))throw new IOException("Native file parent leaves runtime save directory");}
            SeekableByteChannel channel=writing?Files.newByteChannel(target,StandardOpenOption.WRITE,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING):Files.newByteChannel(target,StandardOpenOption.READ);
            int descriptor=nextDescriptor++;descriptors.put(descriptor,new Descriptor(channel,writing));lastFailure=null;return descriptor;
        }catch(IOException failure){lastFailure=failure;return -1;}
    }
    private static void requested(byte[] bytes,int requested)throws IOException{if(bytes==null||requested<0||requested>bytes.length)throw new IOException("Native CRT request exceeds supplied buffer");}
    public int read(int descriptor,byte[] destination,int requested)throws IOException{
        requested(destination,requested);Descriptor value=descriptors.get(descriptor);if(value==null||value.writing)return -1;
        try{int count=value.channel.read(ByteBuffer.wrap(destination,0,requested));lastFailure=null;return count<0?0:count;}catch(IOException failure){lastFailure=failure;return -1;}
    }
    public int write(int descriptor,byte[] source,int requested)throws IOException{
        requested(source,requested);Descriptor value=descriptors.get(descriptor);if(value==null||!value.writing)return -1;
        try{int count=value.channel.write(ByteBuffer.wrap(source,0,requested));lastFailure=null;return count;}catch(IOException failure){lastFailure=failure;return -1;}
    }
    public int close(int descriptor){Descriptor value=descriptors.remove(descriptor);if(value==null)return -1;try{value.channel.close();lastFailure=null;return 0;}catch(IOException failure){lastFailure=failure;return -1;}}
    public void close()throws IOException{IOException failure=null;for(Descriptor value:descriptors.values())try{value.channel.close();}catch(IOException error){if(failure==null)failure=error;else failure.addSuppressed(error);}descriptors.clear();if(failure!=null)throw failure;}
}
