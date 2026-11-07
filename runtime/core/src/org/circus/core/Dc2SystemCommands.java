package org.circus.core;

import java.io.*;

/** D.C.II (2006) 00404F50; deliberately separate from DM's system service numbers. */
public final class Dc2SystemCommands {
    private Dc2SystemCommands(){}
    private static final int[] PRIORITY={6,5,4,3,2,1}; // Original PE 00450A74, six i32 values.
    public static boolean command(Dc2Host host,int[] args)throws IOException {
        if(!host.resources.profile.id.equals("dc2-3363"))throw new IOException("D.C.II system service profile mismatch");
        if(args.length!=2)throw new IOException("D.C.II system service requires two byte arguments");
        int service=args[0],mode=args[1];
        switch(service){
        case 1:
            if(mode==0){
                if(host.script==null)throw new IOException("D.C.II calendar service without script context");
                String path=host.script.name.replace('\\','/');byte[] name=path.substring(path.lastIndexOf('/')+1).getBytes(Binary.SJIS);
                if(name.length<5)throw new IOException("D.C.II calendar filename is shorter than the native five-byte prefix");
                int period=name[4]=='b'||name[4]=='B'?1:name[4]=='c'||name[4]=='C'||name[4]=='d'||name[4]=='D'?2:0;
                host.variables.write(9,0,name[1]-0x210+name[0]*10);
                host.variables.write(9,1,name[3]-0x210+name[2]*10);
                host.variables.write(9,3,period);
            }else if(mode==1){
                int days=host.variables.read(9,1),month=host.variables.read(9,0);
                int[] lengths={31,28,31,30,31,30,31,31,30,31,30};
                for(int n=0;n<lengths.length&&month>n+1;n++)days+=lengths[n];
                host.variables.write(9,2,(days+1)%7);
            }
            return true;
        case 2:
            if(mode==0){int best=0,index=0;for(int n=0;n<PRIORITY.length;n++){int score=host.variables.read(0,n)*100+PRIORITY[n];if(score>best){best=score;index=n;}}host.variables.write(9,10,index);}
            return true;
        case 6:
            // 404CD0 prepares the existing region list. Save configures five entries;
            // load initializes ten backing entries, while the script owns visible rectangles.
            host.saveGrid=mode==0;
            int page=host.read(99,0),count=mode==0?5:10;
            for(int n=0;n<host.grid.size()&&n<count;n++){
                Dc2Ui.Button entry=host.grid.get(n);int slot=page*8+n;entry.binding=slot;
                entry.action=mode==0?(page==0&&n<2?"":"selsave"):
                    (host.availableSaves.contains(slot)?"selsave2":"");
            }
            return true;
        case 8:throw missing(host,service,mode,"0040E410 and native wait 1C/type 0");
        case 10:throw missing(host,service,mode,"00405340");
        case 11:throw missing(host,service,mode,"native wait 1C/type 1");
        case 12:throw missing(host,service,mode,"004057B0 and native wait 1C/type 2");
        case 20:if(!(host instanceof PcHost))throw new IOException("DC2 map requires original PC host");((PcHost)host).openOriginalMap();return true;
        case 30:throw missing(host,service,mode,"0040B700");
        case 31:throw missing(host,service,mode,"native wait 1C/type 5");
        case 40:throw missing(host,service,mode,"write 00BE8BA8 = 0; consumer not yet modelled");
        case 50:throw missing(host,service,mode,"copy native script context 00A08D00 -> 009FE0E0");
        // REA 00404F50's complete switch has no other cases. These are proven native returns,
        // including 7/18/19/21/207/213; applying the same-number DM service would be incorrect.
        default:return true;
        }
    }
    private static IOException missing(Dc2Host host,int service,int mode,String nativeEffect){
        return new IOException("Unimplemented D.C.II system service "+service+"("+mode+") at "+
            (host.script==null?"?":host.script.name)+" @0x"+Integer.toHexString(host.pc)+
            "; native 00404F50 -> "+nativeEffect);
    }
}
