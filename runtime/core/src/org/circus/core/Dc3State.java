package org.circus.core;

import java.io.*;

/** Special-bank storage, separate from platform effects and ordinary MES variables. */
public final class Dc3State implements Variables.Special {
    public final String profileId;
    private final byte[] b100=new byte[150],b96=new byte[400],b97=new byte[1200],b98=new byte[650];
    // PP_CN 4256A0 / 4216B0: 741160 globals, then LSB-first music/CG/scene bitmaps.
    // The old portable save layout below remains a logical projection, not a native memory dump.
    private static final int PP_MUSIC=100,PP_CG=127,PP_SCENES=277,PP_BYTES=359;
    private static final int PP_STATE=0x50505331,PP_SETTINGS=0x50505431;
    private final byte[] packedProgress;
    private final int[] b99=new int[256],b93=new int[20],b94=new int[51],b95=new int[51];
    public final int[] musicFavorites=new int[400];
    public int voiceCount;
    /** Runtime-only gallery/route-clear dirty counter; never serialized. */
    public long progressRevision;
    public Dc3State(){this(false);}
    public Dc3State(boolean originalSystem){this("dc3dd-a5a8",originalSystem);}
    public Dc3State(String profileId,boolean originalSystem){
        this.profileId=profileId;packedProgress=profileId.equals("dc3pp-9872")?new byte[PP_BYTES]:null;
        // The original-system host uses 40ED10's initial preference table.
        // Keep the earlier chapter-only defaults for the separate Android demo.
        b100[10]=(byte)(originalSystem||packedProgress!=null?0:1);if(packedProgress==null){b96[1]=1;b96[255]=1;}else packedProgress[10]=b100[10];b99[12]=5;b99[14]=b99[15]=b99[5]=1;b99[29]=3;
        b99[7]=b99[8]=b99[9]=100;b99[243]=b99[245]=b99[246]=b99[253]=b99[254]=-1;
        b99[203]=1;b99[225]=100;resetVoicePreferences();
        if(originalSystem||packedProgress!=null)resetPreferences();
    }
    /** 40ED10(0): native default table 4A5100; progression bytes are retained. */
    public void resetPreferences(){
        if(profileId.equals("dc4ph-abb6")){
            try{resetPhPreferences(-1);}catch(IOException invalid){throw new IllegalStateException(invalid);}return;
        }
        int[] keys={5,7,8,9,10,11,12,13,14,15,16,17,18,26,27,28,29,30,184,185,188,190,191,194,200,201,202,203,205,206,207,208,209,210,211,212,213,214,225,227,237},values={1,65,80,100,1,49,6,0,1,1,7,0,1,3,3,1,3,1,3,0,1,0,0,5,1,1,0,1,1,85,80,0,0,0,0,1,1,0,100,1,1};
        for(int n=0;n<keys.length;n++)b99[keys[n]]=values[n];
        b99[199]=0;b99[198]=5;b99[197]=2;b99[196]=4;
        java.util.Arrays.fill(b93,0);java.util.Arrays.fill(musicFavorites,0);
        if(packedProgress!=null){
            // PP_CN 406C94 copies 478110 -> 7410D8; these are the effective getter values.
            int[] ppKeys={5,6,7,8,9,10,11,12,13,14,15,16,17,18,23,24,26,27,28,29,30};
            int[] ppValues={1,1,20,23,27,1,11,2,0,1,1,7,0,1,0,0,8,8,0,17,1};
            for(int n=0;n<ppKeys.length;n++)b99[ppKeys[n]]=ppValues[n];
        }
    }
    public void resetPhPreferences(int group)throws IOException{
        if(!profileId.equals("dc4ph-abb6"))throw new IOException("PH defaults require PH state");
        for(int[] pair:Dc4SystemCommands.defaultRegisters(group))b99[pair[0]]=pair[1];
        if(group<0||group==4)java.util.Arrays.fill(b93,0);
        if(group<0)java.util.Arrays.fill(musicFavorites,0);
        if(group==1)b100[49]=49;
    }
    /** PH4332B0 writes the native button-bar preference directly. MES227's
     * setter remains absent, as in40D240; ordinary scripts cannot toggle it. */
    public void togglePhMessageControls()throws IOException{
        if(!profileId.equals("dc4ph-abb6"))throw new IOException("PH message controls require the PH state");
        b99[227]=b99[227]==0?1:0;
    }
    public void resetVoicePreferences(){java.util.Arrays.fill(b94,100);java.util.Arrays.fill(b95,1);}
    public void migrateLegacyAudio(){b99[203]=1;b99[225]=100;resetVoicePreferences();}
    private byte[] bank(int b){switch(b){case 100:return b100;case 96:return b96;case 97:return b97;case 98:return b98;default:return null;}}
    private static void index(int i,int n)throws IOException{if(i<0||i>=n)throw new IOException("Special variable index outside verified range: "+i);}
    private static int ppBase(int bank){return bank==96?PP_MUSIC:bank==97?PP_CG:bank==98?PP_SCENES:-1;}
    private static int ppCount(int bank){return bank==96?210:bank==97?1200:650;}
    private static boolean ppReadableRegister(int i){return i>=0&&i<=18||i==20||i==21||i>=23&&i<=30||i>=223&&i<=255;}
    private static boolean ppWritableRegister(int i){return i==0||i==1||i>=5&&i<=19||i>=23&&i<=30||i>=224&&i<=226||i>=233&&i<=237||i>=241&&i<=246||i>=253&&i<=255;}
    private int packedRead(int bank,int i)throws IOException {
        if(i<0)throw new IOException("Negative PP persistent index");
        int base=ppBase(bank),offset=bank==100?i:base+i/8;index(offset,packedProgress.length);
        return bank==100?packedProgress[offset]&255:(packedProgress[offset]>>>(i&7))&1;
    }
    private void packedWrite(int bank,int i,int value)throws IOException {
        if(i<0)throw new IOException("Negative PP persistent index");
        if(bank==100){if(i>=100)return;if(packedProgress[i]!=(byte)value){packedProgress[i]=(byte)value;progressRevision++;}return;}
        if(i>=ppCount(bank))return;int p=ppBase(bank)+i/8,mask=1<<(i&7);byte old=packedProgress[p];
        packedProgress[p]=(byte)(value==0?old&~mask:old|mask);if(old!=packedProgress[p])progressRevision++;
    }
    /** Project PP's canonical packed bytes into the unchanged portable serialization fields. */
    private void projectPacked(){
        if(packedProgress==null)return;System.arraycopy(packedProgress,0,b100,0,100);
        for(int bank:new int[]{96,97,98}){byte[] logical=bank(bank);int base=ppBase(bank);for(int i=0;i<ppCount(bank);i++)logical[i]=(byte)((packedProgress[base+i/8]>>>(i&7))&1);}
    }
    /** Old PP saves used DD-shaped logical arrays. Valid flag projections migrate exactly. */
    private byte[] decodePacked(byte[][] projection)throws IOException {
        for(int i=100;i<projection[0].length;i++)if(projection[0][i]!=0)throw new IOException("Legacy P.P. save contains bank 100:"+i+" outside the native writable region; cannot migrate faithfully; keep the original save");
        byte[] restored=new byte[PP_BYTES];System.arraycopy(projection[0],0,restored,0,100);
        for(int bank:new int[]{96,97,98}){byte[] logical=projection[bank-95];int base=ppBase(bank);for(int i=0;i<ppCount(bank);i++){
            int value=logical[i]&255;if(value>1)throw new IOException("Legacy P.P. save contains non-boolean flag "+bank+":"+i+" = "+value+"; cannot migrate faithfully; keep the original save");
            if(value!=0)restored[base+i/8]|=1<<(i&7);
        }}
        return restored;
    }
    private void readProgress(DataInput in,byte[] ownedRaw)throws IOException {
        byte[][] target={b100,b96,b97,b98};
        if(packedProgress==null){for(byte[] bytes:target)in.readFully(bytes);return;}
        byte[][] projection={new byte[b100.length],new byte[b96.length],new byte[b97.length],new byte[b98.length]};
        for(byte[] bytes:projection)in.readFully(bytes);byte[] raw=decodePacked(projection);
        if(ownedRaw!=null){
            for(int i=0;i<raw.length;i++){int mask=i==PP_CG-1||i==PP_BYTES-1?3:255;if((raw[i]&mask)!=(ownedRaw[i]&mask))throw new IOException("PP packed bytes disagree with their logical save projection");}
            raw=ownedRaw;
        }
        for(int n=0;n<target.length;n++)System.arraycopy(projection[n],0,target[n],0,target[n].length);
        System.arraycopy(raw,0,packedProgress,0,raw.length);
    }
    public int read(int b,int i)throws IOException {
        if(profileId.equals("dc4ph-abb6")&&b==99&&(i==19||i>=179&&i<=182))return b99[i]; // PH40CC50: additional stored registers.
        if(packedProgress!=null&&(b==100||ppBase(b)>=0))return packedRead(b,i);
        if(packedProgress!=null&&(b==93||b==94||b==95))return 0;
        if(packedProgress!=null&&b==99&&!ppReadableRegister(i))return 0;
        byte[] bytes=bank(b);if(bytes!=null){index(i,bytes.length);return bytes[i]&255;}
        if(b==93)return i>=0&&i<20?b93[i]:0;
        if(b==94||b==95){if(i==50||(i>=0&&i<voiceCount))return (b==94?b94:b95)[i];return -1;}
        if(b!=99)return 0;index(i,256);
        if(i==217){index(b99[218],20);return b93[b99[218]];}
        // Live platform queries belong to Dc3Host (WY/PP99:3 is capability, 4 is mode).
        // Remaining READ kind2 entries here are stored projections with unresolved
        // business effects, as recorded in the fidelity audit; storage is not proof
        // that all native query callbacks have been reconstructed.
        return Dc3StateRules.READ[i]==0?0:b99[i];
    }
    public void write(int b,int i,int value)throws IOException {
        if(profileId.equals("dc4ph-abb6")&&b==99&&(i==19||i>=179&&i<=182)){b99[i]=value;return;} // PH40D240.
        if(packedProgress!=null&&(b==100||ppBase(b)>=0)){packedWrite(b,i,value);return;}
        if(packedProgress!=null&&(b==93||b==94||b==95))return;
        if(packedProgress!=null&&b==99&&!ppWritableRegister(i))return;
        if(packedProgress!=null&&b==99&&(i==5||i==7||i==8||i==9)){b99[i]=value;return;}
        byte[] bytes=bank(b);if(bytes!=null){if(i<0||i>=bytes.length)return;if(b==100&&i==49)write(99,11,value);if(bytes[i]!=(byte)value&&(b==97||b==98||b==100&&i>=100&&(i<=110||profileId.equals("dc4ph-abb6")&&i==111)))progressRevision++;bytes[i]=(byte)value;return;}
        if(b==93){if(i>=0&&i<20)b93[i]=value;return;}
        if(b==94||b==95){if(i==50||(i>=0&&i<voiceCount))(b==94?b94:b95)[i]=b==94?Math.min(value,100):value;return;}
        if(b!=99)return;index(i,256);
        if(i==217){index(b99[218],20);b93[b99[218]]=value;return;}
        int kind=Dc3StateRules.WRITE[i];if(kind==0)return;
        if(kind>=2){
            // These native setters update preferences and notify the audio device.
            // The platform reads the same values when updating channel gain.
            if(i==5||i==14||i==15)value=value==0?0:1;
            else if(i==7||i==8)value=Math.max(0,Math.min(100,value));
            else if(i==225)value=Math.min(100,value);
            else if(i==203||i==237){} // Master enable and window preference; consumed by the desktop platform.
            else throw new IOException("Unimplemented native state effect 99:"+i);
        }
        if(i==224)value=value>0?1:0;
        if(i==243||i==245||i==246||i==253||i==254)if(value==255)value=-1;
        b99[i]=value;if(i==10)b99[214]=value==2?1:0;
    }
    public void save(DataOutput out)throws IOException {
        projectPacked();
        if(packedProgress!=null){out.writeInt(PP_STATE);out.write(packedProgress);}
        out.writeInt(voiceCount);for(byte[] b:new byte[][]{b100,b96,b97,b98})out.write(b);
        for(int[] b:new int[][]{b99,b93,b94,b95})for(int v:b)out.writeInt(v);
    }
    /** Loading an older story must not roll back gallery progress or current preferences. */
    public void mergeGallery(Dc3State other)throws IOException{
        if(!profileId.equals(other.profileId))throw new IOException("Gallery state profile mismatch");
        for(int bank:new int[]{97,98}){int count=packedProgress==null?bank(bank).length:ppCount(bank);for(int i=0;i<count;i++)if(read(bank,i)==0&&other.read(bank,i)!=0)write(bank,i,other.read(bank,i));}
    }
    /** Portable preferences/progress projection; PP prefixes its complete owned packed bytes. */
    public byte[] settingsSnapshot()throws IOException {
        projectPacked();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        if(packedProgress!=null){out.writeInt(PP_SETTINGS);out.write(packedProgress);}
        for(byte[] b:new byte[][]{b100,b96,b97,b98})out.write(b);
        for(int v:b93)out.writeInt(v);for(int i:settingsRegisters())out.writeInt(b99[i]);for(int f:musicFavorites)out.writeInt(f);for(int[] b:new int[][]{b94,b95})for(int v:b)out.writeInt(v);return bytes.toByteArray();
    }
    private int[] settingsRegisters(){if(!profileId.equals("dc4ph-abb6"))return Dc3StateRules.SETTINGS;int[] keys=java.util.Arrays.copyOf(Dc3StateRules.SETTINGS,Dc3StateRules.SETTINGS.length+3);keys[keys.length-3]=180;keys[keys.length-2]=181;keys[keys.length-1]=182;return keys;}
    private int portableSettingsLength(){return b100.length+b96.length+b97.length+b98.length+4*(b93.length+settingsRegisters().length+musicFavorites.length+b94.length+b95.length);}
    public boolean acceptsSettingsLength(int n)throws IOException{int legacy=portableSettingsLength();return n==legacy||n==legacy-408||n==legacy-2008||packedProgress!=null&&n==legacy+4+PP_BYTES;}
    /** Shared by normal play and explicit native-progress import. */
    public byte[] savePersistent()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();new DataOutputStream(bytes).writeInt(0x44445032);bytes.write(settingsSnapshot());return bytes.toByteArray();}
    public void loadPersistent(byte[] bytes)throws IOException{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(bytes.length>4&&in.readInt()==0x44445032){byte[] data=new byte[bytes.length-4];in.readFully(data);restoreSettings(data);}
        else{restoreSettings(bytes);migrateLegacyAudio();}
    }
    public void restoreSettings(byte[] bytes)throws IOException {
        if(!acceptsSettingsLength(bytes.length))throw new IOException("Invalid settings snapshot");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        byte[] raw=null;if(packedProgress!=null&&bytes.length==portableSettingsLength()+4+PP_BYTES){if(in.readInt()!=PP_SETTINGS)throw new IOException("Unsupported PP settings version");raw=new byte[PP_BYTES];in.readFully(raw);}
        readProgress(in,raw);
        for(int i=0;i<b93.length;i++)b93[i]=in.readInt();for(int i:settingsRegisters())b99[i]=in.readInt();if(in.available()>=1600)for(int n=0;n<musicFavorites.length;n++)musicFavorites[n]=in.readInt();if(in.available()==408)for(int[] b:new int[][]{b94,b95})for(int n=0;n<b.length;n++)b[n]=in.readInt();else resetVoicePreferences();
    }
    public void load(DataInput in)throws IOException {
        int voices=in.readInt();byte[] raw=null;if(voices==PP_STATE){if(packedProgress==null)throw new IOException("PP packed save profile mismatch");raw=new byte[PP_BYTES];in.readFully(raw);voices=in.readInt();}if(voices<0||voices>50)throw new IOException("Invalid voice count");
        readProgress(in,raw);voiceCount=voices;
        for(int[] b:new int[][]{b99,b93,b94,b95})for(int i=0;i<b.length;i++)b[i]=in.readInt();
    }
}
