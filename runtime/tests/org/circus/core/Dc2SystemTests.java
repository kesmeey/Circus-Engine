package org.circus.core;

import java.io.*;
import java.util.*;

/** D.C.II 00404F50 service admission and reviewed state changes, using synthetic data. */
public final class Dc2SystemTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static Dc2Host host(String id)throws IOException {
        final byte[] names=new byte[20];
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/GRP/NAMES.DAT"));}
            public byte[] read(String path,int limit)throws IOException{if(limit<names.length)throw new IOException("Fixture limit");return names.clone();}
            public Archive.Source source(String path){return new Archive.Source(){
                public long size(){return names.length;}
                public byte[] read(long start,int length)throws IOException{Binary.range(start,length,names.length);return Arrays.copyOfRange(names,(int)start,(int)start+length);}
            };}
        });
        Dc2Host h=Dc2Host.create(new CircusResources(files,Profiles.get(id)),TextCodec.identity());
        h.attach(new Variables(h,h.resources.profile.variableModel));return h;
    }
    private static void command(Dc2Host h,int service,int argument)throws IOException{h.command("dm.system.command",new int[]{service,argument},new byte[0]);}
    private static byte[] snapshot(Dc2Host h)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();h.save(new DataOutputStream(out));return out.toByteArray();}
    private static void script(Dc2Host h,String name)throws IOException{
        int version=h.resources.profile.version;
        byte[] data={0,0,0,0,(byte)version,(byte)(version>>>8),0,0,0};
        h.position(new MesScript(name,data,h.resources.profile),6);
    }
    private static void unresolved(Dc2Host h,int service,int argument)throws Exception{
        byte[] before=snapshot(h);boolean rejected=false;
        try{command(h,service,argument);}catch(IOException e){rejected=e.getMessage().contains("D.C.II system service "+service+"("+argument+")")&&e.getMessage().contains("00404F50")&&e.getMessage().contains("@0x6");}
        check(rejected,"effectful service "+service+" must identify its native unresolved effect");
        check(Arrays.equals(before,snapshot(h)),"unresolved service "+service+" cannot silently mutate state");
        check(h.unmodelled.isEmpty(),"unresolved service "+service+" cannot be accepted as a logged no-op");
    }
    private static void unsupportedEffect(Dc2Host h,String semantic,int[] args)throws Exception{
        byte[] before=snapshot(h);boolean rejected=false;
        try{h.command(semantic,args,new byte[0]);}catch(IOException e){rejected=e.getMessage().contains("Unsupported native semantic dc2-3363/")&&e.getMessage().contains(h.script.name)&&e.getMessage().contains("@0x6")&&e.getMessage().contains(Arrays.toString(args));}
        check(rejected,"unreviewed effect must fail with native semantic, arguments and exact script site");
        check(Arrays.equals(before,snapshot(h))&&h.unmodelled.isEmpty(),"unreviewed effect is not accepted as a logged no-op");
    }
    public static int run()throws Exception {
        count=0;Dc2Host h=host("dc2-3363");script(h,"folder/0228b_test");
        h.variables.write(9,2,77);command(h,1,0);
        check(h.variables.read(9,0)==2&&h.variables.read(9,1)==28&&h.variables.read(9,3)==1,"DC2 calendar extracts native filename date and b period");
        check(h.variables.read(9,2)==77,"calendar mode zero does not also compute the weekday");
        command(h,1,1);check(h.variables.read(9,2)==4,"DC2 weekday uses non-leap day-of-year plus one");
        script(h,"folder\\1231D_test");command(h,1,0);command(h,1,1);
        check(h.variables.read(9,0)==12&&h.variables.read(9,1)==31&&h.variables.read(9,3)==2&&h.variables.read(9,2)==2,"DC2 calendar handles native backslash basename and D period");
        byte[] before=snapshot(h);command(h,1,2);check(Arrays.equals(before,snapshot(h)),"calendar unsupported mode is a verified native return");
        command(h,2,0);check(h.variables.read(9,10)==0,"DC2 six-entry native priority default");
        h.variables.write(0,5,1);h.variables.write(0,6,255);command(h,2,0);
        check(h.variables.read(9,10)==5,"DC2 rank considers exactly six entries, not the PC1 or PC2 table");
        h.variables.write(0,0,1);command(h,2,0);check(h.variables.read(9,10)==0,"DC2 six-entry priority resolves equal affection");
        before=snapshot(h);command(h,2,1);check(Arrays.equals(before,snapshot(h)),"rank mode one is a native return, not PC affinity alternate mode");
        Set<Integer> effectful=new HashSet<>(Arrays.asList(1,2,6,8,10,11,12,20,30,31,40,50));
        h.command("pc.wait",new int[]{3,0},new byte[0]);before=snapshot(h);
        for(int service=0;service<256;service++)if(!effectful.contains(service)){
            command(h,service,0);check(Arrays.equals(before,snapshot(h)),"DC2 verified native default "+service+" must not inherit same-number DM service");
        }
        check(h.unmodelled.isEmpty(),"native default services are proven returns, not unmodelled side effects");
        check(((PcHost)h).hasTimedInput(),"native default service cannot clear pending PC input state");
        check(!h.history.active&&!h.ui.waiting,"service 7 does not open DM history for DC2");
        for(int service:new int[]{8,30,31})unresolved(h,service,0);
        unsupportedEffect(h,"native.unmodelled",new int[]{1,2});
        for(int kind:new int[]{5,6}){h.command("pc.effect",new int[]{kind,kind-5},new byte[0]);check(h.scene.nativeParticles.kind()==kind&&h.scene.nativeParticles.atlas().endsWith(kind==5?"SAKU_YUKI01":"SAKU_YUKI02"),"reviewed DC2 sprite effect binds its own native atlas");check(h.scene.particleKind==0&&h.unmodelled.isEmpty(),"reviewed native effect does not also enable the approximate DM renderer");}
        byte[] noEffect=snapshot(h);h.command("native.no_effect",new int[]{5,6},new byte[0]);check(Arrays.equals(noEffect,snapshot(h)),"proven native fallback remains a no-op");
        for(int n=0;n<5;n++)h.grid.add(new Dc2Ui.Button());
        h.write(99,0,0);command(h,6,0);
        check(h.saveGrid&&h.grid.get(0).action.isEmpty()&&h.grid.get(1).action.isEmpty(),"DC2 save configuration disables reserved slots zero and one");
        check(h.grid.get(2).binding==2&&h.grid.get(2).action.equals("selsave"),"DC2 first save page binds native slot and label");
        h.gridWaiting=h.ui.waiting=true;
        check(h.menuActionForGrid(0)==null&&new String(h.menuActionForGrid(2),Binary.SJIS).equals("selsave")&&h.read(99,254)==2,"DC2 save activation consumes configured entry");
        h.write(99,0,1);command(h,6,0);
        check(((PcHost)h).gridSlot(4)==12&&h.grid.get(4).binding==12,"DC2 page stride is eight even though save shows five rows");
        h.grid.clear();for(int n=0;n<10;n++)h.grid.add(new Dc2Ui.Button());h.availableSaves.add(12);command(h,6,1);
        check(!h.saveGrid&&h.grid.get(3).action.isEmpty()&&h.grid.get(4).action.equals("selsave2")&&h.grid.get(9).binding==17,"DC2 load prepares ten entries and enables existing slots only");
        Dc2Ui.Button projected=h.gridControl(4);check(projected!=h.grid.get(4)&&projected.binding==12&&projected.action.equals("@slot:4")&&h.grid.get(4).action.equals("selsave2"),"grid projection preserves native action and slot binding");
        projected.action="changed";projected.binding=0;check(h.grid.get(4).action.equals("selsave2")&&h.grid.get(4).binding==12,"platform input-view mutations cannot replace native metadata");
        check(h.gridControl(3).action.isEmpty(),"disabled native entry stays disabled in its platform view");
        // The native helper binds entries at configuration, rather than recomputing the page on click.
        h.write(99,0,2);h.gridWaiting=h.ui.waiting=true;check(h.read(99,246)==0,"native getter's default zero is independent of internal grid waiting");
        check(new String(h.menuActionForGrid(4),Binary.SJIS).equals("selsave2")&&h.read(99,254)==12,"DC2 activation preserves configured slot when page state changes");
        Dc2Host dm=host("dc2dm-9d72");command(dm,6,0);check(dm.saveGrid,"DM save-grid service is retained");
        command(dm,6,1);check(!dm.saveGrid,"DM load-grid service is retained");
        command(dm,18,0);dm.write(99,7,42);command(dm,18,1);check(dm.variables.read(9,35)==1,"DM option-difference service remains independently implemented");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("PASS "+run()+" DC2 native system assertions");}
}
