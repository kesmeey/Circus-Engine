package org.circus.core;
import java.io.*;
/** One independent CVS2 continuation and synchronized platform metadata. */
final class Dc2BorrowedLeaf {
 final byte[] leaf,metadata;final Dc2BorrowedState provenance=new Dc2BorrowedState();
 Dc2BorrowedLeaf(byte[] bytes,byte[] platform,Dc2BorrowedState state)throws IOException{
  if(bytes==null||bytes.length<66||bytes.length>Pc1PortableCandidate.MAX_LEAF||platform==null||platform.length>Pc1PortableCandidate.MAX_METADATA||state.kind==0)throw new IOException("DC2 borrowed leaf input");
  leaf=bytes.clone();metadata=platform.clone();ByteArrayOutputStream out=new ByteArrayOutputStream();state.save(new DataOutputStream(out));provenance.load(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
 }
 private Dc2BorrowedLeaf(DataInput in)throws IOException{provenance.load(in);leaf=Pc1PortableCandidate.bytes(in,Pc1PortableCandidate.MAX_LEAF);metadata=Pc1PortableCandidate.bytes(in,Pc1PortableCandidate.MAX_METADATA);if(leaf.length<66||provenance.kind==0)throw new IOException("DC2 borrowed leaf missing");}
 boolean matches(PcHost host){return host.resources.profileId.equals("dc2-3363")&&provenance.matches(host.dc2Borrowed);}
 void validate(MesVm vm,PcHost host)throws IOException{
  if(!matches(host)||host.saveRequest>=0||!vm.scriptName().equals(provenance.script)||!host.script.sha256.equals(provenance.sha)||vm.offset()!=provenance.pc)throw new IOException("DC2 borrowed continuation/provenance mismatch");
  if(provenance.kind<=2){MesScript.Record record=host.script.fetchRecord(provenance.sourceOffset);String op=host.script.profile.operation(record.opcode);if(record.end!=provenance.pc||provenance.kind==1&&(!op.equals("dm.system.command")||record.args[0]!=50)||provenance.kind==2&&(!op.equals("pc.persist")||record.args[0]!=4||record.args[1]!=1))throw new IOException("DC2 borrowed source is not its producer");}
 }
 void save(DataOutput out)throws IOException{provenance.save(out);Pc1PortableCandidate.bytes(out,leaf);Pc1PortableCandidate.bytes(out,metadata);}
 static Dc2BorrowedLeaf load(DataInput in)throws IOException{return new Dc2BorrowedLeaf(in);}
}
