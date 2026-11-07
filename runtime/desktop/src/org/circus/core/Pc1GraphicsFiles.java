package org.circus.core;

import java.io.*;
import java.util.*;

/** Production current CHS image backend. Configured canonical path roots,
 * CG catalog/CRT locale and settings bytes are explicit borrowed owners. */
public final class Pc1GraphicsFiles implements Pc1GraphicsResources.Backend {
    /** No parsed/normalized second catalog. Name access must retain native
     * C-string reads across row boundaries when the caller owns those bytes. */
    public interface CgCatalog extends Pc1CgVisit.Catalog {}
    public interface Settings extends Pc1CgVisit.Settings {}
    private final Pc1ResourcePaths paths;
    private final Pc1CrxDecoder decoder;
    private final Pc1WindowsImageIo io;
    private final Pc1CgVisit.Catalog catalog;
    private final Settings settings;
    public Pc1GraphicsFiles(Pc1ResourcePaths paths,Pc1CrxMemory crx,Pc1WindowsImageIo io,Pc1CgVisit.Catalog catalog,Settings settings)throws IOException{
        if(paths==null||crx==null||io==null||catalog==null||settings==null)throw new IOException("PC1 image backend requires canonical paths/CRX/native IO/CG/settings owners");this.paths=paths;decoder=new Pc1CrxDecoder(crx);this.io=io;this.catalog=catalog;this.settings=settings;
    }
    @Override public void visitCg(byte[] beforeFirstDot)throws IOException{
        Pc1CgVisit.visit(beforeFirstDot,catalog,settings);
    }
    @Override public void visitCgSource(Pc1ByteSource source)throws IOException{Pc1CgVisit.visitSource(source,catalog,settings);}
    @Override public Pc1GraphicsResources.Source readImage(byte[] category,byte[] name,byte[] extension)throws IOException{
        throw new IOException("PC1 production image backend requires an addressed native call");
    }
    @Override public Pc1GraphicsResources.Source readImage(Pc1GraphicsResources.AddressedImage request)throws IOException{
        throw new IOException("PC1 production image backend requires a canonical pool load");
    }
    @Override public boolean usesCanonicalPool(){return true;}
    @Override public int readImageInto(Pc1GraphicsResources.AddressedImage request,Pc1ImagePool pool,int mode)throws IOException{
        if(request==null||pool==null)throw new IOException("PC1 addressed image/pool request missing");Pc1ResourcePaths.Resolved path=paths.assetTyped(request.categorySource(),request.name,request.extensionSource(),request.pathCapacity);Pc1CrxDecoder.Input file=io.open(path.rawPath());if(file==null)return -1;try{return decoder.decodeInto(file,io,pool,mode);}finally{io.close(file);}
    }
    @Override public Pc1GraphicsResources.Source allocateImage(int width,int height,int format)throws IOException{
        throw new IOException("PC1 production allocation must retain its canonical pool lease");
    }
    public int orphanAllocationCount(Pc1ImagePool pool)throws IOException{if(pool==null)throw new IOException("PC1 orphan query requires canonical pool owner");return pool.orphanHeapAllocations();}
    public boolean active(){return decoder.active();}
}
