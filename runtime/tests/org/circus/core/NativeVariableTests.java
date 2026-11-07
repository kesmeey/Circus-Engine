package org.circus.core;

import java.io.*;
import java.util.*;

/** Counterexamples to conflating DC2, DC2PC, DD and DM variable memory. */
public final class NativeVariableTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static final class Special implements Variables.Special {
        private final Map<Integer,Integer> bytes=new HashMap<>();
        public int read(int bank,int index){return bank==100?bytes.getOrDefault(index,0):0;}
        public void write(int bank,int index,int value){if(bank==100&&index<100)bytes.put(index,value&255);}
    }
    public static int run()throws Exception {
        count=0;
        Profile original=Profiles.get("dc2-3363");
        check(original.variableModel==Variables.Model.PC_BYTE,"DC2 413A70/413C70 has no wide bank aliases");
        Variables dc2=new Variables(new Special(),original.variableModel);
        dc2.write(0,0,0x12);dc2.write(0,1,0x34);dc2.write(0,2,0x56);dc2.write(0,3,0x78);
        check(dc2.read(200,0)==0,"DC2 unknown bank 200 is not a DC2PC BE32 alias");
        dc2.write(200,0,0x11223344);check(dc2.read(0,0)==0x12&&dc2.read(0,3)==0x78,"Unknown DC2 bank writes cannot alter ordinary memory");
        dc2.write(10,0,77);check(dc2.read(10,0)==0,"DC2 has ten ordinary banks, not eleven");
        for(Variables.Model model:Variables.Model.values()){
            Variables memory=new Variables(new Special(),model);memory.write(0,99,42);
            memory.write(0,100,99);memory.write(0,Integer.MAX_VALUE,99);
            check(memory.read(0,100)==0&&memory.read(0,Integer.MAX_VALUE)==0,"Native ordinary upper bounds return zero: "+model);
            check(memory.read(0,99)==42,"Out-of-bank writes preserve the final valid byte: "+model);
        }
        Variables wide=new Variables(new Special(),Variables.Model.PC_WIDE);
        wide.write(200,96,0x89abcdef);
        check(wide.read(200,96)==0x89abcdef&&wide.read(0,99)==0xef,"PC2 final valid BE32 window reaches index 99");
        check(wide.read(200,97)==0&&wide.read(210,0)==0,"PC2 wide views cannot cross a bank or expose bank 10");
        Variables wrapped=new Variables(new Special(),Variables.Model.BYTE_WRAP);
        wrapped.write(0,0,-1);dc2.write(0,0,-1);
        check(wrapped.read(0,0)==255&&dc2.read(0,0)==0,"DD byte wrapping differs from DC2 saturation");
        Variables dm=new Variables(new Special(),Variables.Model.DC2_PACKED);
        dm.write(310,19,-1);
        check(dm.read(100,19)==255&&dm.read(100,20)==255&&dm.read(310,19)==-1,"DM wide alias writes use persistent byte setters");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();dm.save(new DataOutputStream(bytes));
        check(bytes.size()==2204,"Variable snapshot layout is unchanged when history is empty");
        Dc2State persistent=new Dc2State();persistent.write(97,0,1);persistent.write(98,0,1);
        check(persistent.read(96,400)==1&&persistent.read(97,1200)==1,"DM native bit reads alias the following packed region");
        persistent.write(96,400,0);persistent.write(97,1200,0);
        check(persistent.read(97,0)==1&&persistent.read(98,0)==1,"DM native setters retain their logical flag-count limits");
        check(persistent.read(98,650)==0&&persistent.read(98,655)==0,"DM raw scene padding bits remain readable inside the owned final byte");
        return count;
    }
}
