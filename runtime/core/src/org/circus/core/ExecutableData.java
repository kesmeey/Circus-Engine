package org.circus.core;
import java.io.*;

/** Admission for an explicitly selected PE data source. Never executes code. */
public final class ExecutableData {
    private ExecutableData(){}
    public static void validate(CircusResources data,TextCodec codec)throws IOException {
        if(!data.files.explicitExecutable())return;
        String id=data.profile.id;
        if(id.equals("dc3dd-a5a8"))new Dc3NativeData(data,codec);
        else if(id.equals("dc2dm-9d72"))new Dc2NativeData(data,codec);
        else if(id.equals("dcpc-2063"))new PcNameInput().prepare(data,codec);
        else {byte[] head=data.files.slice(data.files.executable,0,64);if(Binary.u16(head,0)!=0x5a4d)throw new IOException("所选文件不是 PE 程序数据");long offset=Binary.u32(head,60);byte[] pe=data.files.slice(data.files.executable,offset,26);if(Binary.u32(pe,0)!=0x4550||Binary.u16(pe,4)!=0x14c||Binary.u16(pe,24)!=0x10b)throw new IOException("所选文件不是受支持的 32 位 x86 PE 数据");}
    }
}
