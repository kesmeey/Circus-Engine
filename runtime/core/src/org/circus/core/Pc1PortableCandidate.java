package org.circus.core;

import java.io.*;
import java.util.*;

/** Independent portable continuation paired with a real native candidate.
 * Its CVS2 leaf never contains another session envelope. This is deliberately
 * separate from the original Windows8004-byte story-slot representation. */
final class Pc1PortableCandidate {
    static final int MAX_LEAF=32*1024*1024,MAX_METADATA=2*1024*1024;
    final byte[] leaf,metadata;
    final String fingerprint,script,sha256;
    final int rawPc,portablePc,kind;
    Pc1PortableCandidate(byte[] leaf,byte[] metadata,Pc1Frame nativeCandidate,int kind,int portablePc)throws IOException{
        if(leaf==null||leaf.length<66||leaf.length>MAX_LEAF||metadata==null||metadata.length>MAX_METADATA||kind<1||kind>3||nativeCandidate==null)throw new IOException("PC1 portable candidate input");
        this.leaf=leaf.clone();this.metadata=metadata.clone();this.kind=kind;this.portablePc=portablePc;
        Pc1SystemContext.ReturnPoint p=nativeCandidate.returnPoint();script=p.script;sha256=p.sha256;rawPc=p.pc;fingerprint=fingerprint(nativeCandidate);
    }
    private Pc1PortableCandidate(DataInput in)throws IOException{
        kind=in.readUnsignedByte();script=in.readUTF();Binary.safePath(script);sha256=in.readUTF();rawPc=in.readInt();portablePc=in.readInt();fingerprint=in.readUTF();
        if(kind<1||kind>3||!sha256.matches("[0-9a-f]{64}")||!fingerprint.matches("[0-9a-f]{64}")||rawPc<0||portablePc<0)throw new IOException("PC1 portable candidate provenance");
        leaf=bytes(in,MAX_LEAF);if(leaf.length<66)throw new IOException("PC1 portable candidate leaf missing");metadata=bytes(in,MAX_METADATA);
    }
    static byte[] bytes(DataInput in,int max)throws IOException{int length=in.readInt();if(length<0||length>max)throw new IOException("PC1 session payload length");byte[] value=new byte[length];in.readFully(value);return value;}
    static void bytes(DataOutput out,byte[] value)throws IOException{out.writeInt(value.length);out.write(value);}
    void save(DataOutput out)throws IOException{out.writeByte(kind);out.writeUTF(script);out.writeUTF(sha256);out.writeInt(rawPc);out.writeInt(portablePc);out.writeUTF(fingerprint);bytes(out,leaf);bytes(out,metadata);}
    static Pc1PortableCandidate load(DataInput in)throws IOException{return new Pc1PortableCandidate(in);}
    boolean matches(Pc1Frame frame)throws IOException{return frame!=null&&fingerprint.equals(fingerprint(frame));}
    void validate(MesVm vm,PcHost host)throws IOException{
        Pc1Frame frame=host.pc1.saves().candidate();
        if(host.pc1.inSystem()||host.saveRequest>=0||host.pc1.requestedSave()!=null||!matches(frame)||!script.equals(vm.scriptName())||!sha256.equals(host.script.sha256)||vm.offset()!=portablePc)throw new IOException("PC1 portable candidate leaf/provenance mismatch");
        Pc1SystemContext.ReturnPoint point=frame.returnPoint();int expected=point.pc+(kind==1?frame.rawOwnedString().length+2:0);
        if(point.pc!=rawPc||expected!=portablePc||kind==1&&!frame.preparedBy1B()||kind==2&&!frame.capturedForPersistCurrent()||kind==3&&!frame.capturedFromEvent())throw new IOException("PC1 portable candidate producer cursor mismatch");
    }
    private static String fingerprint(Pc1Frame frame)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();frame.saveBound(new DataOutputStream(b));byte[] nativeBytes=frame.nativeFrame().bytes();
        //404E70 patches these bytes before I/O; a patch cannot create a producer.
        int ordinary=Pc1FrameLayout.ORDINARY_OFFSET;nativeBytes[ordinary+902]=0;Arrays.fill(nativeBytes,ordinary+920,ordinary+926,(byte)0);b.write(nativeBytes);return Binary.sha256(b.toByteArray());
    }
}
