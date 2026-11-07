package org.circus.core;

import java.io.*;
import java.util.*;

/** Addressed401CC0/40E5A0 reads and4020B0 uninitialized allocation. */
public final class Pc1GraphicsAddressTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private interface Work {void run()throws IOException;}
    private static void reject(Work work,String text)throws IOException{boolean bad=false;try{work.run();}catch(IOException expected){bad=String.valueOf(expected.getMessage()).contains(text);}check(bad,"precise "+text);}
    private static final class State implements Pc1Compositor.State,Pc1InlineText.Memory {
        final Map<Integer,Byte> raw=new HashMap<>();final List<Integer> reads=new ArrayList<>();
        State()throws IOException{string(Pc1NativePaths.PRIMARY_ROOT,"C:\\game");string(Pc1NativePaths.FALLBACK_ROOT,"E:\\disc");string(Pc1NativePaths.DATA_PREFIX,"\\AdvData\\");string(Pc1NativePaths.SEPARATOR,"\\");string(Pc1ResourceLiterals.GRAPHICS_CATEGORY,"GRP");bytes(Pc1ResourceLiterals.GRAPHICS_EXTENSION,new byte[]{'.','c','r','x',0,0,0,0});string(0x42a058,"WORK GBUF");dword(0x94c61c,0);dword(0x94c62c,0);}
        void bytes(int address,byte[] bytes){for(int n=0;n<bytes.length;n++)raw.put(address+n,bytes[n]);}
        void string(int address,String text)throws IOException{byte[] bytes=text.getBytes("US-ASCII");this.bytes(address,Arrays.copyOf(bytes,bytes.length+1));}
        void dword(int address,int value){for(int n=0;n<4;n++)raw.put(address+n,(byte)(value>>>(n*8)));}
        void forget(int start,int end){for(int at=start;at<end;at++)raw.remove(at);}
        public int read(int address)throws IOException{reads.add(address);Byte value=raw.get(address);if(value==null)throw new IOException("unowned byte0x"+Integer.toHexString(address));return value&255;}
        public void write(int address,int value)throws IOException{if(!raw.containsKey(address))throw new IOException("unowned write0x"+Integer.toHexString(address));raw.put(address,(byte)value);}
        public int readInt(int address)throws IOException{int value=0;for(int n=0;n<4;n++)value|=read(address+n)<<(n*8);return value;}
        public void writeInt(int address,int value)throws IOException{for(int n=0;n<4;n++)write(address+n,value>>>(n*8));}
        public void writeByte(int address,int value)throws IOException{write(address,value);}
    }
    private static byte[] cstring(Pc1NativePaths.Memory memory,int address)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n=0;n<1024*1024;n++){int value=memory.read(address+n)&255;if(value==0)return out.toByteArray();out.write(value);}throw new IOException("scan bound");}
    private static final class Backend implements Pc1GraphicsResources.Backend {
        final State state;final List<String> events=new ArrayList<>();int attributes,orphans;boolean mutate,failAllocation,checkLocal,noImage;byte[] workAfterAllocation;
        Backend(State state){this.state=state;}
        public void visitCg(byte[] candidate)throws IOException{events.add("cg:"+new String(candidate,"US-ASCII"));}
        public Pc1GraphicsResources.Source readImage(byte[] category,byte[] name,byte[] extension){throw new AssertionError("production must use addressed call");}
        public Pc1GraphicsResources.Source readImage(Pc1GraphicsResources.AddressedImage request)throws IOException{
            if(checkLocal){int length=new String(cstring(request,request.name),"US-ASCII").length();check(request.read(request.name+length)==0&&request.read(request.name+length+1)==0,"modifier move retains original known terminator as stale second NUL");reject(()->request.read(request.name+length+2),"unknown local260");}
            final int target=0xe0000000;byte[] path=new byte[260];boolean[] known=new boolean[260];Pc1NativePaths.Memory memory=new Pc1NativePaths.Memory(){public int read(int address)throws IOException{long at=(address&0xffffffffL)-(target&0xffffffffL);if(at>=0&&at<260){if(!known[(int)at])throw new IOException("unknown path stack tail");return path[(int)at]&255;}return request.read(address);}public void write(int address,int value)throws IOException{long at=(address&0xffffffffL)-(target&0xffffffffL);if(at>=0&&at<260){path[(int)at]=(byte)value;known[(int)at]=true;}else request.write(address,value);}};
            Pc1NativePaths.buildAsset(memory,target,request.category,request.name,request.extension,address->{events.add("query:"+new String(cstring(memory,address),"US-ASCII"));if(mutate){state.string(Pc1NativePaths.FALLBACK_ROOT,"F:\\live");state.string(request.category,"NEW");state.string(request.extension,".bmp");byte[] replacement="other.old".getBytes("US-ASCII");for(int n=0;n<replacement.length;n++)request.write(request.name+n,replacement[n]);request.write(request.name+replacement.length,0);}return attributes;});events.add("open:"+new String(cstring(memory,target),"US-ASCII"));return noImage?null:Pc1GraphicsResources.Source.rgb(NativeRgbImage.zeroed(1,1));
        }
        public Pc1GraphicsResources.Source allocateImage(int width,int height,int format)throws IOException{events.add("allocate:"+width+","+height+","+format);if(failAllocation)return null;if(workAfterAllocation!=null){state.bytes(0x42a058,Arrays.copyOf(workAfterAllocation,workAfterAllocation.length+1));}int stride=(width*(format==0?3:4)+3)&~3;byte[] raw=new byte[stride*height];for(int n=0;n<raw.length;n++)raw[n]=(byte)(0xa5+n);return format==0?Pc1GraphicsResources.Source.rgb(NativeRgbImage.fromBgr24(width,height,stride,raw)):Pc1GraphicsResources.Source.alpha(NativeInverseAlphaImage.fromAbgr32(width,height,stride,raw));}
        public void allocationResult(Pc1GraphicsResources.Source source,int handle){events.add("result:"+handle);if(handle==-1)orphans++;}
    }
    private static final class Fixture {final State state=new State();final Backend backend=new Backend(state);final Pc1Compositor pixels;final Pc1GraphicsResources resources;Fixture()throws IOException{NativeRgbImage front=NativeRgbImage.zeroed(3,2);for(int y=0;y<2;y++)for(int x=0;x<3;x++)front.setRgb(x,y,0x112233);pixels=new Pc1Compositor(front);resources=new Pc1GraphicsResources(pixels,state,backend);}int load(String name)throws IOException{return resources.load(name.getBytes("US-ASCII"),0,pixels);}}
    public static int run()throws Exception{
        count=0;Fixture absolute=new Fixture();absolute.state.forget(Pc1ResourceLiterals.GRAPHICS_CATEGORY,Pc1ResourceLiterals.GRAPHICS_EXTENSION+8);check(absolute.load("D:\\exact.old")>=20,"drive name bypasses unknown category/extension bytes");check(absolute.backend.events.equals(Arrays.asList("cg:D:\\exact","open:D:\\exact.old")),"drive path skips attribute query and forced extension");
        Fixture empty=new Fixture();empty.state.forget(Pc1ResourceLiterals.GRAPHICS_CATEGORY,Pc1ResourceLiterals.GRAPHICS_EXTENSION+8);empty.state.bytes(Pc1ResourceLiterals.GRAPHICS_CATEGORY,new byte[]{0});empty.state.bytes(Pc1ResourceLiterals.GRAPHICS_EXTENSION,new byte[]{0});check(empty.load("asset")>=20&&empty.backend.events.contains("open:C:\\game\\AdvData\\asset"),"one known NUL byte is sufficient; no DWORD/neighbor pre-read");
        Fixture early=new Fixture();early.state.forget(Pc1ResourceLiterals.GRAPHICS_EXTENSION,Pc1ResourceLiterals.GRAPHICS_EXTENSION+8);early.state.bytes(Pc1ResourceLiterals.GRAPHICS_EXTENSION,new byte[]{'.','c','r','x',' '});check(early.load("asset.old")>=20&&early.backend.events.contains("open:C:\\game\\AdvData\\GRP\\asset.crx"),"extension early whitespace stops before unowned suffix");Fixture signedSpace=new Fixture();signedSpace.state.forget(Pc1ResourceLiterals.GRAPHICS_EXTENSION,Pc1ResourceLiterals.GRAPHICS_EXTENSION+8);signedSpace.state.bytes(Pc1ResourceLiterals.GRAPHICS_EXTENSION,new byte[]{'.',(byte)0x97});check(signedSpace.load("asset")>=20&&signedSpace.backend.events.contains("open:C:\\game\\AdvData\\GRP\\asset."),"linked signed-char CRT whitespace behavior remains NativePaths authority");
        Fixture fallback=new Fixture();fallback.backend.attributes=-1;fallback.backend.mutate=true;fallback.backend.checkLocal=true;check(fallback.load("first@.old")>=20,"mutable addressed fallback loads");check(fallback.backend.events.contains("query:C:\\game\\AdvData\\GRP\\first.crx")&&fallback.backend.events.contains("open:F:\\live\\AdvData\\NEW\\other.bmp"),"fallback rereads same roots/category/name/extension owners after query");check(new String(fallback.resources.name(20),"US-ASCII").equals("first@.old"),"resident key remains original caller name after working local mutation");Fixture failure=new Fixture();failure.state.forget(Pc1ResourceLiterals.GRAPHICS_EXTENSION,Pc1ResourceLiterals.GRAPHICS_EXTENSION+8);failure.state.bytes(Pc1ResourceLiterals.GRAPHICS_EXTENSION,new byte[]{'.','x'});reject(()->failure.load("asset"),"unowned byte");check(failure.backend.events.equals(Arrays.asList("cg:asset"))&&!failure.pixels.loaded(20),"actual required suffix fault preserves prior CG visit and no fabricated file/pixel success");
        Fixture like=new Fixture();byte[] source=like.pixels.front().bgr24();like.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));int created=like.resources.createLike(0,like.pixels);check(created==20&&like.pixels.width(created)==3&&like.pixels.height(created)==2&&like.pixels.format(created)==0,"4020B0 allocates matching source metadata in20..99");byte[] initial=like.pixels.rgb(created).bgr24();for(int n=0;n<initial.length;n++)check(initial[n]==(byte)(0xa5+n),"all initial allocation bytes including padding retained");check(!Arrays.equals(source,initial)&&Arrays.equals(source,like.pixels.front().bgr24()),"no source pixel copy and no front mutation");check(new String(like.resources.name(created),"US-ASCII").equals("WORK GBUF")&&like.resources.rawNameBytes(created)[10]=="old-long-resident-key".getBytes("US-ASCII")[10],"live WORK GBUF copy retains inactive resident suffix");check(like.backend.events.equals(Arrays.asList("allocate:3,2,0","result:20")),"allocation/pool result occurs before live-name consumer");check(like.pixels.activeTarget()==0,"allocation does not select/reset active target");like.state.string(0x42a058,"CHANGED");check(like.resources.createLike(0)==21&&new String(like.resources.name(21),"US-ASCII").equals("CHANGED"),"new work name is read from live byte owner for every call");like.backend.events.clear();check(like.resources.createLike(-1)==-1&&like.resources.createLike(19)==-1&&like.backend.events.isEmpty(),"native−1 and unallocated source skip heap/name calls");
        Fixture full=new Fixture();for(int n=20;n<100;n++)full.pixels.installRgb(n,NativeRgbImage.zeroed(1,1));full.state.forget(0x42a058,0x42a070);check(full.resources.createLike(0)==-1&&full.backend.orphans==1&&full.backend.events.equals(Arrays.asList("allocate:3,2,0","result:-1")),"full pool allocates first, retains orphan, and never reads work name");Fixture malloc=new Fixture();malloc.backend.failAllocation=true;malloc.state.forget(0x42a058,0x42a070);check(malloc.resources.createLike(0)==-1&&malloc.backend.events.equals(Arrays.asList("allocate:3,2,0"))&&!malloc.pixels.loaded(20),"malloc failure skips pool/name/result publication");Fixture partial=new Fixture();partial.state.forget(0x42a058,0x42a070);reject(()->partial.resources.createLike(0),"unowned byte");check(partial.pixels.loaded(20)&&partial.backend.events.contains("result:20"),"work-name unknown fault retains already installed allocation");
        Fixture alpha=new Fixture();alpha.pixels.installAlpha(21,NativeInverseAlphaImage.fromAbgr32(2,1,8,new byte[8]));int alphaNew=alpha.resources.createLike(21);check(alphaNew==20&&alpha.pixels.format(alphaNew)==1&&alpha.pixels.stride(alphaNew)==8,"format1 createLike remains raw ABGR allocation");
        Fixture alias=new Fixture();int record=Pc1GraphicsResources.IMAGE_BASE-256;alias.state.dword(record,0);alias.state.forget(0x42a058,0x42a070);check(alias.resources.createLike(-2)==-1&&alias.backend.events.isEmpty(),"known negative metadata pointer0 returns before shape/name reads");alias.state.dword(record,1);alias.state.dword(record+12,2);alias.state.dword(record+16,1);alias.state.dword(record+20,0);alias.state.string(0x42a058,"WORK GBUF");check(alias.resources.createLike(-2)==20&&alias.pixels.width(20)==2&&alias.backend.events.contains("allocate:2,1,0"),"negative handle uses known adjacent raw metadata instead of a fabricated array bound");check(alias.resources.createLike(Integer.MIN_VALUE)==21&&alias.pixels.width(21)==3,"native handle shift wraps to the existing front record");
        machineVectors();return count;
    }

    // BEGIN current-PE 18 programs / 10650 interpreted instructions.
    private static byte[] unhex(String raw){byte[] out=new byte[raw.length()/2];for(int n=0;n<out.length;n++)out[n]=(byte)Integer.parseInt(raw.substring(n*2,n*2+2),16);return out;}
    private static void machineVectors()throws Exception{
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.backend.attributes=0;f.backend.mutate=false;
        check(f.resources.load(unhex("443a5c65786163742e6f6c64"),0,f.pixels)==-1,"actual401CC0 absolute_ignores_unknown return");
        check(f.backend.events.equals(Arrays.asList("cg:D:\\exact","open:D:\\exact.old")),"actual401CC0 absolute_ignores_unknown original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList()),"actual401CC0 absolute_ignores_unknown conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.state.bytes(0x42a04c,unhex("00"));
        f.state.bytes(0x42a050,unhex("00"));
        f.backend.attributes=0;f.backend.mutate=false;
        check(f.resources.load(unhex("6173736574"),0,f.pixels)==-1,"actual401CC0 empty_category_and_extension return");
        check(f.backend.events.equals(Arrays.asList("cg:asset","query:C:\\game\\AdvData\\asset","open:C:\\game\\AdvData\\asset")),"actual401CC0 empty_category_and_extension original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList(4366412,4366412,4366416)),"actual401CC0 empty_category_and_extension conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.state.bytes(0x42a04c,unhex("47525000"));
        f.state.bytes(0x42a050,unhex("2e63727820"));
        f.backend.attributes=0;f.backend.mutate=false;
        check(f.resources.load(unhex("61737365742e6f6c64"),0,f.pixels)==-1,"actual401CC0 early_space return");
        check(f.backend.events.equals(Arrays.asList("cg:asset","query:C:\\game\\AdvData\\GRP\\asset.crx","open:C:\\game\\AdvData\\GRP\\asset.crx")),"actual401CC0 early_space original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList(4366412,4366413,4366412,4366412,4366413,4366414,4366415,4366412,4366413,4366414,4366415,4366416,4366416,4366417,4366417,4366418,4366418,4366419,4366419,4366420)),"actual401CC0 early_space conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.state.bytes(0x42a04c,unhex("47525000"));
        f.state.bytes(0x42a050,unhex("2e97"));
        f.backend.attributes=0;f.backend.mutate=false;
        check(f.resources.load(unhex("6173736574"),0,f.pixels)==-1,"actual401CC0 signed_space return");
        check(f.backend.events.equals(Arrays.asList("cg:asset","query:C:\\game\\AdvData\\GRP\\asset.","open:C:\\game\\AdvData\\GRP\\asset.")),"actual401CC0 signed_space original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList(4366412,4366413,4366412,4366412,4366413,4366414,4366415,4366412,4366413,4366414,4366415,4366416,4366416,4366417)),"actual401CC0 signed_space conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.state.bytes(0x42a04c,unhex("47525000"));
        f.state.bytes(0x42a050,unhex("2e63727800000000"));
        f.backend.attributes=-1;f.backend.mutate=true;
        check(f.resources.load(unhex("6669727374402e6f6c64"),0,f.pixels)==-1,"actual401CC0 fallback_live_arguments return");
        check(f.backend.events.equals(Arrays.asList("cg:first@","query:C:\\game\\AdvData\\GRP\\first.crx","open:F:\\live\\AdvData\\NEW\\other.bmp")),"actual401CC0 fallback_live_arguments original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList(4366412,4366413,4366412,4366412,4366413,4366414,4366415,4366412,4366413,4366414,4366415,4366416,4366416,4366417,4366417,4366418,4366418,4366419,4366419,4366420,4366412,4366412,4366413,4366414,4366415,4366412,4366413,4366414,4366415,4366416,4366416,4366417,4366417,4366418,4366418,4366419,4366419,4366420)),"actual401CC0 fallback_live_arguments conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.state.bytes(0x42a04c,unhex("47525000"));
        f.state.bytes(0x42a050,unhex("2e78"));
        f.backend.attributes=0;f.backend.mutate=false;
        reject(()->f.resources.load(unhex("6173736574"),0,f.pixels),"unowned byte");
        check(f.backend.events.equals(Arrays.asList("cg:asset")),"actual401CC0 required_suffix_unknown original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList(4366412,4366413,4366412,4366412,4366413,4366414,4366415,4366412,4366413,4366414,4366415,4366416,4366416,4366417,4366417,4366418)),"actual401CC0 required_suffix_unknown conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.backend.attributes=0;f.backend.mutate=false;
        check(f.resources.load(unhex("443a5c6578616374402e6f6c64"),0,f.pixels)==-1,"actual401CC0 absolute_at_modifier return");
        check(f.backend.events.equals(Arrays.asList("cg:D:\\exact@","open:D:\\exact.old")),"actual401CC0 absolute_at_modifier original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList()),"actual401CC0 absolute_at_modifier conditional literal byte read order");}
        {Fixture f=new Fixture();f.backend.noImage=true;f.state.forget(0x42a04c,0x42a058);
        f.backend.attributes=0;f.backend.mutate=false;
        check(f.resources.load(unhex("443a5c65786163742a2e6f6c64"),0,f.pixels)==-1,"actual401CC0 absolute_star_modifier return");
        check(f.backend.events.equals(Arrays.asList("cg:D:\\exact*","open:D:\\exact.old")),"actual401CC0 absolute_star_modifier original native call journal");
        List<Integer> reads=new ArrayList<>();for(int address:f.state.reads)if(address>=0x42a04c&&address<0x42a058)reads.add(address);check(reads.equals(Arrays.asList()),"actual401CC0 absolute_star_modifier conditional literal byte read order");}
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        int got=f.resources.createLike(0);check(got==20,"actual4020B0 rgb return");
        check(f.backend.events.equals(Arrays.asList("allocate:3,2,0","result:20")),"actual4020B0 rgb heap/pool ordering");
        check(Arrays.equals(f.resources.rawNameBytes(got),unhex("574f524b20474255460065736964656e742d6b65790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),"actual4020B0 rgb all104 stale resident-name bytes");
        check(f.pixels.stride(got)==12&&f.pixels.width(got)==3&&f.pixels.height(got)==2&&f.pixels.format(got)==0,"actual4020B0 rgb exact metadata");
        byte[] expected=unhex("a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"),actual=f.pixels.format(got)==0?f.pixels.rgb(got).bgr24():f.pixels.alpha(got).abgr32();check(Arrays.equals(actual,Arrays.copyOf(expected,expected.length-1)),"actual4020B0 rgb every observed uninitialized allocation byte and padding");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        f.pixels.installAlpha(21,NativeInverseAlphaImage.fromAbgr32(2,1,8,new byte[8]));
        int got=f.resources.createLike(21);check(got==20,"actual4020B0 alpha return");
        check(f.backend.events.equals(Arrays.asList("allocate:2,1,1","result:20")),"actual4020B0 alpha heap/pool ordering");
        check(Arrays.equals(f.resources.rawNameBytes(got),unhex("574f524b20474255460065736964656e742d6b65790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),"actual4020B0 alpha all104 stale resident-name bytes");
        check(f.pixels.stride(got)==8&&f.pixels.width(got)==2&&f.pixels.height(got)==1&&f.pixels.format(got)==1,"actual4020B0 alpha exact metadata");
        byte[] expected=unhex("a5a6a7a8a9aaabacad"),actual=f.pixels.format(got)==0?f.pixels.rgb(got).bgr24():f.pixels.alpha(got).abgr32();check(Arrays.equals(actual,Arrays.copyOf(expected,expected.length-1)),"actual4020B0 alpha every observed uninitialized allocation byte and padding");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        int got=f.resources.createLike(-1);check(got==-1,"actual4020B0 minus1 return");
        check(f.backend.events.equals(Arrays.asList()),"actual4020B0 minus1 heap/pool ordering");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        int got=f.resources.createLike(19);check(got==-1,"actual4020B0 empty_source return");
        check(f.backend.events.equals(Arrays.asList()),"actual4020B0 empty_source heap/pool ordering");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        f.backend.failAllocation=true;
        int got=f.resources.createLike(0);check(got==-1,"actual4020B0 malloc_fail return");
        check(f.backend.events.equals(Arrays.asList("allocate:3,2,0")),"actual4020B0 malloc_fail heap/pool ordering");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        for(int h=20;h<100;h++)f.pixels.installRgb(h,NativeRgbImage.zeroed(1,1));
        int got=f.resources.createLike(0);check(got==-1,"actual4020B0 full_pool return");
        check(f.backend.events.equals(Arrays.asList("allocate:3,2,0","result:-1")),"actual4020B0 full_pool heap/pool ordering");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        f.backend.workAfterAllocation=unhex("4348414e474544");
        int got=f.resources.createLike(0);check(got==20,"actual4020B0 live_work_name return");
        check(f.backend.events.equals(Arrays.asList("allocate:3,2,0","result:20")),"actual4020B0 live_work_name heap/pool ordering");
        check(Arrays.equals(f.resources.rawNameBytes(got),unhex("4348414e474544002d7265736964656e742d6b65790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),"actual4020B0 live_work_name all104 stale resident-name bytes");
        check(f.pixels.stride(got)==12&&f.pixels.width(got)==3&&f.pixels.height(got)==2&&f.pixels.format(got)==0,"actual4020B0 live_work_name exact metadata");
        byte[] expected=unhex("a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"),actual=f.pixels.format(got)==0?f.pixels.rgb(got).bgr24():f.pixels.alpha(got).abgr32();check(Arrays.equals(actual,Arrays.copyOf(expected,expected.length-1)),"actual4020B0 live_work_name every observed uninitialized allocation byte and padding");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        int at=Pc1GraphicsResources.IMAGE_BASE-256;f.state.dword(at,1);f.state.dword(at+12,2);f.state.dword(at+16,1);f.state.dword(at+20,0);
        int got=f.resources.createLike(-2);check(got==20,"actual4020B0 negative_alias return");
        check(f.backend.events.equals(Arrays.asList("allocate:2,1,0","result:20")),"actual4020B0 negative_alias heap/pool ordering");
        check(Arrays.equals(f.resources.rawNameBytes(got),unhex("574f524b20474255460065736964656e742d6b65790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),"actual4020B0 negative_alias all104 stale resident-name bytes");
        check(f.pixels.stride(got)==8&&f.pixels.width(got)==2&&f.pixels.height(got)==1&&f.pixels.format(got)==0,"actual4020B0 negative_alias exact metadata");
        byte[] expected=unhex("a5a6a7a8a9aaabacad"),actual=f.pixels.format(got)==0?f.pixels.rgb(got).bgr24():f.pixels.alpha(got).abgr32();check(Arrays.equals(actual,Arrays.copyOf(expected,expected.length-1)),"actual4020B0 negative_alias every observed uninitialized allocation byte and padding");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        int at=Pc1GraphicsResources.IMAGE_BASE-256;f.state.dword(at,0);f.state.dword(at+12,2);f.state.dword(at+16,1);f.state.dword(at+20,0);
        int got=f.resources.createLike(-2);check(got==-1,"actual4020B0 negative_null return");
        check(f.backend.events.equals(Arrays.asList()),"actual4020B0 negative_null heap/pool ordering");
        }
        {Fixture f=new Fixture();f.resources.name(20,"old-long-resident-key".getBytes("US-ASCII"));
        int got=f.resources.createLike(-2147483648);check(got==20,"actual4020B0 shift_wrap return");
        check(f.backend.events.equals(Arrays.asList("allocate:3,2,0","result:20")),"actual4020B0 shift_wrap heap/pool ordering");
        check(Arrays.equals(f.resources.rawNameBytes(got),unhex("574f524b20474255460065736964656e742d6b65790000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")),"actual4020B0 shift_wrap all104 stale resident-name bytes");
        check(f.pixels.stride(got)==12&&f.pixels.width(got)==3&&f.pixels.height(got)==2&&f.pixels.format(got)==0,"actual4020B0 shift_wrap exact metadata");
        byte[] expected=unhex("a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbd"),actual=f.pixels.format(got)==0?f.pixels.rgb(got).bgr24():f.pixels.alpha(got).abgr32();check(Arrays.equals(actual,Arrays.copyOf(expected,expected.length-1)),"actual4020B0 shift_wrap every observed uninitialized allocation byte and padding");
        }
    }
    // END current-PE vectors.
    public static void main(String[] args)throws Exception{System.out.println("Pc1GraphicsAddressTests assertions: "+run());}
}
