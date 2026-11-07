package org.circus.core;

import java.io.IOException;

/** PP_CN412A10 service206 case4131B7. Caller owns the shared system-entry
 *storeB23FA4=0; this case itself has only five getter calls and one setter. */
public final class PpSystem206 {
    private PpSystem206(){}
    public interface Access {
        int read(int bank,int index)throws IOException;
        void write(int bank,int index,int value)throws IOException;
    }
    public static void execute(Access access)throws IOException{
        if(access==null)throw new IOException("PP206 variable owner missing");
        int[] values=new int[5],indices=new int[5];
        for(int n=0;n<5;n++){values[n]=access.read(5,95+n);indices[n]=n;}
        for(int n=0;n<5;n++)for(int at=n;at<5;at++)if(values[n]<values[at]&&n!=at){int value=values[n];values[n]=values[at];values[at]=value;int index=indices[n];indices[n]=indices[at];indices[at]=index;}
        access.write(3,0,indices[0]);
    }
}
