package org.circus.core;

import java.io.IOException;
import java.util.*;

/** Addressed byte views over explicitly owned PC1 regions. Native negative
 *ordinary references may reach another known owner; unknown gaps still fail.
 *This map owns no duplicate bytes and is not an emulated Windows process. */
public final class Pc1NativeMemory implements Variables.NativeByteMemory {
    public static final int CODE_BASE=0x9a077c,CODE_BYTES=100000,SETTINGS_BASE=0x454cd8;
    private static final int LIVE_BASE=Pc1FrameLayout.LIVE_BASE,BANK90_BASE=LIVE_BASE+Pc1FrameLayout.BANK90_OFFSET;
    public interface SettingsBytes {
        int read(int offset)throws IOException;
        /** Raw memory write, without pretending this is a bank100 setter and
         *without invoking its immediate file-write side effect. */
        void write(int offset,int value)throws IOException;
    }
    private final Pc1NativeFrame frame;
    private final Pc1CodeBuffer code;
    private final SettingsBytes settings;
    public interface Region {
        int read(int offset)throws IOException;void write(int offset,int value)throws IOException;
        /** Byte-only owners retain an explicit ordered fallback. Shared native
         *DWORD storage overrides these with one physical word operation. */
        default int readInt32(int offset)throws IOException{int value=0;for(int n=0;n<4;n++)value|=(read(offset+n)&255)<<(n*8);return value;}
        default void writeInt32(int offset,int value)throws IOException{for(int n=0;n<4;n++)write(offset+n,value>>>(n*8)&255);}
    }
    private static final class Binding {final long base;final int length;final Region region;Binding(long base,int length,Region region){this.base=base;this.length=length;this.region=region;}}
    private final List<Binding> additional=new ArrayList<>();
    private Pc1GuestAddressSpace guest;
    private Pc1StaticImage staticImage;
    public Pc1NativeMemory(Pc1NativeFrame frame,Pc1CodeBuffer code,SettingsBytes settings){if(frame==null||code==null||settings==null)throw new IllegalArgumentException("PC1 native memory owners missing");this.frame=frame;this.code=code;this.settings=settings;}
    /** Additional reviewed BSS/global owners may publish their existing bytes.
     * Overlap is rejected; binding never creates a second copy of memory. */
    public void bind(int address,int length,Region region)throws IOException{
        if(staticImage!=null)throw new IOException("PC1 fixed-owner layout is finalized; construct all native views before static image holes");
        long base=address&0xffffffffL,end=base+length;
        if(length<=0||region==null||end>0x100000000L||overlap(base,end,LIVE_BASE,Pc1FrameLayout.FRAME_BYTES)||overlap(base,end,CODE_BASE,CODE_BYTES)||overlap(base,end,SETTINGS_BASE,Pc1NativeSettings.SIZE))throw new IOException("PC1 native region overlaps an existing owner or leaves address space");
        for(Binding value:additional)if(overlap(base,end,value.base,value.length))throw new IOException("PC1 native region overlaps a previously bound owner");if(guest!=null)guest.requireStaticDisjoint(address,length);additional.add(new Binding(base,length,region));
        if(guest!=null)guest.rememberStatic(address,length);
    }
    /** One guest process namespace per canonical native bus. Existing/future
     *fixed owners cannot silently shadow its arena or any explicit mapping. */
    public void bindGuestAddressSpace(Pc1GuestAddressSpace space)throws IOException{
        if(space==null)throw new IOException("PC1 Guest32 resolver missing");if(guest==space)return;if(guest!=null)throw new IOException("PC1 native bus already has another Guest32 address space");
        space.requireStaticDisjoint(LIVE_BASE,Pc1FrameLayout.FRAME_BYTES);space.requireStaticDisjoint(CODE_BASE,CODE_BYTES);space.requireStaticDisjoint(SETTINGS_BASE,Pc1NativeSettings.SIZE);for(Binding binding:additional)space.requireStaticDisjoint((int)binding.base,binding.length);
        if(staticImage!=null)for(FixedRegion range:staticImage.ownedRanges())space.requireStaticDisjoint(range.address,range.length);
        space.attachBus(this);space.rememberStatic(LIVE_BASE,Pc1FrameLayout.FRAME_BYTES);space.rememberStatic(CODE_BASE,CODE_BYTES);space.rememberStatic(SETTINGS_BASE,Pc1NativeSettings.SIZE);for(Binding binding:additional)space.rememberStatic((int)binding.base,binding.length);if(staticImage!=null)for(FixedRegion range:staticImage.ownedRanges())space.rememberStatic(range.address,range.length);guest=space;
    }
    public static final class FixedRegion {public final int address,length;FixedRegion(int address,int length){this.address=address;this.length=length;}}
    public List<FixedRegion> fixedRegions(){List<FixedRegion> result=new ArrayList<>();result.add(new FixedRegion(LIVE_BASE,Pc1FrameLayout.FRAME_BYTES));result.add(new FixedRegion(CODE_BASE,CODE_BYTES));result.add(new FixedRegion(SETTINGS_BASE,Pc1NativeSettings.SIZE));for(Binding binding:additional)result.add(new FixedRegion((int)binding.base,binding.length));result.sort(Comparator.comparingLong(value->value.address&0xffffffffL));return Collections.unmodifiableList(result);}
    /** Optional and last: claim preferred-section holes after every fixed view.
     * This never constructs an initialized Windows process or duplicates views. */
    public void finalizeStaticImage(Pc1StaticImage image)throws IOException{if(image==null)throw new IOException("PC1 static image required");if(staticImage==image)return;if(staticImage!=null)throw new IOException("PC1 native bus already finalized with another static image");if(guest!=null)for(FixedRegion range:image.sectionRanges())guest.requireStaticDisjoint(range.address,range.length);image.attach(this,fixedRegions());if(guest!=null)for(FixedRegion range:image.ownedRanges())guest.rememberStatic(range.address,range.length);staticImage=image;}
    public Pc1StaticImage staticImage(){return staticImage;}
    public Pc1GuestAddressSpace guestAddressSpace(){return guest;}
    private static boolean overlap(long start,long end,long base,int length){return start<base+length&&base<end;}
    public int readAddress(int address)throws IOException{
        long at=address&0xffffffffL;
        if(at>=LIVE_BASE&&at<LIVE_BASE+Pc1FrameLayout.FRAME_BYTES)return frame.readU8((int)at-LIVE_BASE);
        if(at>=CODE_BASE&&at<CODE_BASE+CODE_BYTES)return code.readU8((int)at-CODE_BASE);
        if(at>=SETTINGS_BASE&&at<SETTINGS_BASE+Pc1NativeSettings.SIZE)return settings.read((int)at-SETTINGS_BASE);
        for(Binding value:additional)if(at>=value.base&&at<value.base+value.length)return value.region.read((int)(at-value.base));
        if(staticImage!=null&&staticImage.inSection(address))return staticImage.read(address);
        if(guest!=null&&guest.contains(address))return guest.readMapped(address);
        throw unknown(address);
    }
    public void writeAddress(int address,int value)throws IOException{
        long at=address&0xffffffffL;
        if(at>=LIVE_BASE&&at<LIVE_BASE+Pc1FrameLayout.FRAME_BYTES){frame.writeU8((int)at-LIVE_BASE,value);return;}
        if(at>=CODE_BASE&&at<CODE_BASE+CODE_BYTES){code.writeU8((int)at-CODE_BASE,value);return;}
        if(at>=SETTINGS_BASE&&at<SETTINGS_BASE+Pc1NativeSettings.SIZE){settings.write((int)at-SETTINGS_BASE,value&255);return;}
        for(Binding binding:additional)if(at>=binding.base&&at<binding.base+binding.length){binding.region.write((int)(at-binding.base),value&255);return;}
        if(staticImage!=null&&staticImage.inSection(address)){staticImage.write(address,value);return;}
        if(guest!=null&&guest.contains(address)){guest.writeMapped(address,value);return;}
        throw unknown(address);
    }
    public int readInt32(int address)throws IOException{
        long at=address&0xffffffffL;if(at>=LIVE_BASE&&at+4<=LIVE_BASE+Pc1FrameLayout.FRAME_BYTES)return frame.readI32((int)at-LIVE_BASE);if(at>=CODE_BASE&&at+4<=CODE_BASE+CODE_BYTES){int offset=(int)at-CODE_BASE;return code.readU8(offset)|code.readU8(offset+1)<<8|code.readU8(offset+2)<<16|code.readU8(offset+3)<<24;}if(at>=SETTINGS_BASE&&at+4<=SETTINGS_BASE+Pc1NativeSettings.SIZE){int offset=(int)at-SETTINGS_BASE,value=0;for(int n=0;n<4;n++)value|=settings.read(offset+n)<<(n*8);return value;}
        for(Binding binding:additional)if(at>=binding.base&&at+4<=binding.base+binding.length)return binding.region.readInt32((int)(at-binding.base));int value=0;for(int n=0;n<4;n++)value|=readAddress(address+n)<<(n*8);return value;
    }
    public void writeInt32(int address,int value)throws IOException{
        long at=address&0xffffffffL;if(at>=LIVE_BASE&&at+4<=LIVE_BASE+Pc1FrameLayout.FRAME_BYTES){frame.writeI32((int)at-LIVE_BASE,value);return;}if(at>=CODE_BASE&&at+4<=CODE_BASE+CODE_BYTES){code.writeBytes((int)at-CODE_BASE,new byte[]{(byte)value,(byte)(value>>>8),(byte)(value>>>16),(byte)(value>>>24)});return;}if(at>=SETTINGS_BASE&&at+4<=SETTINGS_BASE+Pc1NativeSettings.SIZE){int offset=(int)at-SETTINGS_BASE;for(int n=0;n<4;n++)settings.write(offset+n,value>>>(n*8)&255);return;}
        for(Binding binding:additional)if(at>=binding.base&&at+4<=binding.base+binding.length){binding.region.writeInt32((int)(at-binding.base),value);return;}for(int n=0;n<4;n++)writeAddress(address+n,value>>>(n*8));
    }
    public int readBank90(int index)throws IOException{return readAddress(BANK90_BASE+index);}
    public void writeBank90(int index,int value)throws IOException{writeAddress(BANK90_BASE+index,value);}
    public int readNativeOrdinary(int bank,int index)throws IOException{if(bank>=10||index>=100)return 0;return readAddress(LIVE_BASE+Pc1FrameLayout.ORDINARY_OFFSET+bank*100+index);}
    public void writeNativeOrdinary(int bank,int index,int value)throws IOException{if(bank>=10||index>=100)return;writeAddress(LIVE_BASE+Pc1FrameLayout.ORDINARY_OFFSET+bank*100+index,Math.max(0,Math.min(255,value)));}
    public byte ordinaryByte(int flatIndex){return frame.ordinaryByte(flatIndex);}
    public void clearOrdinaryBytes(){frame.clearOrdinaryBytes();}
    private static IOException unknown(int address){return new IOException("PC1 native byte alias reaches unowned neighboring memory at0x"+Integer.toHexString(address));}
}
