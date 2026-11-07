package org.circus.core;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

/** Actual backend C403B40 leaf against full original instruction images.
 *Deferred owner only: no HWND/device/audio/target process is created. */
public final class Pc1DirectSoundDspAudit {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static int n(Object value){return((Number)value).intValue();}
    private static byte[] data(ByteBuffer buffer){byte[] bytes=new byte[buffer.capacity()];for(int k=0;k<bytes.length;k++)bytes[k]=buffer.get(k);return bytes;}
    private static Pc1Audio.Catalog empty(){return new Pc1Audio.Catalog(){public int musicCount(){return 0;}public int soundCount(){return 0;}public Pc1Audio.Entry music(int index)throws IOException{throw new IOException("unused");}public Pc1Audio.Entry sound(int index)throws IOException{throw new IOException("unused");}};}
    private static Pc1NativeMemory memory()throws IOException{final byte[] settings=new byte[Pc1NativeSettings.SIZE];return new Pc1NativeMemory(Pc1NativeFrame.zeroBss(),Pc1CodeBuffer.zeroBss(),new Pc1NativeMemory.SettingsBytes(){public int read(int at){return settings[at]&255;}public void write(int at,int value){settings[at]=(byte)value;}});}
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];for(int c;(c=in.read(bytes))!=-1;)out.write(bytes,0,c);return out.toByteArray();}
    private static byte[] encode(Pc1DirectSound.Snapshot snapshot)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();snapshot.save(new DataOutputStream(out));return out.toByteArray();}
    private static final String VECTORS="eJztlm1rHEcMx98H8h2Oe22D5lFSv0opx2hGcg3mLtxt2pSQ715d7PRmHWxw6lJKvW92Nf9ZrTTzk3Y+v3+32WyXdrzRZXf6tcVStz9ttshEjMEskVUBk2gxUEQJ0hL11NG09E4x5wgSkLXx4EaJCCMU3F599av75fjH2R98ypAkw8N4byc9+fjPZ2uz+Xx/c+FDWxY97l2ydnfSq7+Eo7Zx9gSXodOiH85OYuRE+TJ+c3eQdjclUyim0RkHtTxYMOgwYUu5gLWhJaSYchPOnlLjWkoGYxSKMfaSa9lefC9N7nRybRRSp6apxGRgvQQUtUbBBASC9tEr8OgqxYOUKMTdjUDYAdXG9lGGu2aev3sOF8Fuj6dlF+rudHuz17H7/XAc0+Kdr8uqbKY3N5vr2YizMBtpFmYjz8JslFmYjToLs4GzMBv07fGXS8Z3zROmZ/K9ThHrHNDZXgX1eOBs18cT6qMJ+HjCdwOXaO8fvlw9TfBy/PhaAEu3IMiiWix6IQlL6mDEUEWoSBWHOKvXIXNMBJJDyyXYoMGc+xvAmzeAXwvg8D3Az7FLlQrlMgo5YjgStDQki5kGGq2Onh3nyiOWItkJZO/WiK0L5JYE5Rl2cw0WsaPEwl2BwWuBiklNUoulweZeAQs6qCnHkDSpf7HV0nOPob8SuyEgrXYKCFZEhoJhNSGt9BhhvfX+Uy0rByWneUJIvpOrnfdf2eqDOeCqDGilx1RhBYY3oLCqukQ8vx8yVV7poWb+Ie6phNlTgJhXa8GxrusfY5ljDbjS/awRV0vlKPBqLSoj/m3mr18IvVRShCiooD37gSJ7F66g2Xtz4qSG3vCFkJKN3gOUytkhqirZ38z0b0B//Ub9/5b6Jw7b+DLq1cSPHOotXLWTQZNW1NEukM1JTRxKAkGAiiUqtREgWVAxSaBN9bljSsu+3yxdFYWxtxoNAHuvlAlL9b8Ka2DD7CUwFH3rhiRjGub1hOFJ6vFF0M/HlP/M848Q+88G8gDk+XYf1HY5LI7Svi23v/m2f2OteIe897DdH3aH4+3N7d6n6SftH78S8tCw37/78id6ivZH";
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IOException("Expected immutable backend DLL");String json;try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(VECTORS)))){json=new String(readAll(in),StandardCharsets.UTF_8);}List<?> rows=(List<?>)((Map<?,?>)DataJson.parse(json)).get("cases");check(rows.size()==5,"All actual403B40 original programs embedded");
        for(Object value:rows){Map<?,?> row=(Map<?,?>)value;Pc1DirectSound.Snapshot captured;String expected=row.get("global_sha256").toString();
            try(Pc1DirectSound backend=Pc1DirectSound.deferred(Paths.get(args[0]),null)){
                Pc1Audio audio=new Pc1Audio(backend,empty());Pc1NativeMemory bus=memory();audio.bindNativeMemory(bus);backend.bindNativeMemory(bus);ByteBuffer globals=backend.borrowDecoderRegion(0x455574);check(globals.isDirect()&&globals.order()==ByteOrder.LITTLE_ENDIAN&&globals.capacity()==0x38008,"Exact sole native decoder global backing");check(backend.borrowDecoderRegion(0x42fca8).getInt(0)==0xbd5fa0&&backend.borrowDecoderRegion(0x43de78).getInt(0)==1,"Verified PE cursor/extra seeds, not invented BSS0");
                if(Boolean.TRUE.equals(row.get("pattern")))for(int k=0;k<globals.capacity();k++)globals.put(k,(byte)(k*37+9));globals.putInt(0x48d578-0x455574,n(row.get("ready")));backend.initializeDecoderTables();byte[] all=data(globals);check(Binary.sha256(all).equals(expected),"Complete actual C403B40/stale/gap byte image ready="+row.get("ready"));check(Binary.sha256(Arrays.copyOfRange(all,0x10000,0x30000)).equals(row.get("table_sha256")),"All65536 LUT words agree with original instructions");check(globals.getInt(0x48d578-0x455574)==n(row.get("ready_after")),"Ready remains arbitrary rawDWORD when skipped");
                globals.put(0,(byte)0x67);check(bus.readAddress(0x455574)==0x67,"Native direct producer and bus share one byte owner");bus.writeAddress(0x455574,all[0]);check(globals.get(0)==all[0],"Bus writes mutate same native backing");check(!backend.pcmInitialized()&&audio.pcmReadyRaw()==0,"LUT leaf never implies DS/device initialization");
                captured=backend.snapshotBoundary(()->backend.capture(audio));byte[] raw=encode(captured);Pc1DirectSound.Snapshot detached=Pc1DirectSound.Snapshot.load(new DataInputStream(new ByteArrayInputStream(raw)));check(Arrays.equals(raw,encode(detached)),"PDB5/PDS6 include decoder bytes losslessly without copying PE tables");
            }
            try(Pc1DirectSound staged=Pc1DirectSound.stage(Paths.get(args[0]),0,null,captured)){
                check(Binary.sha256(staged.decoderBytesForAudit(0x455574)).equals(expected),"Stopped stage preserves full table and stale decoder globals");Pc1Audio restored=Pc1Audio.restore(staged,empty(),captured.control());Pc1NativeMemory bus=memory();restored.bindNativeMemory(bus);staged.bindNativeMemory(bus);check(bus.readAddress(0x465574)==(staged.borrowDecoderRegion(0x455574).get(0x10000)&255),"Restored bus borrows staged owner without reseeding");check(!staged.pcmInitialized(),"BSS decoder adoption never creates a PCM device");
            }
        }
        System.out.println("Pc1DirectSoundDspAudit PASS "+checks+"; actual C leaf and exact decoder snapshot views; no device/window/EXE");
    }
}
