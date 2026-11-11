package org.circus.core;

import java.io.*;
import java.util.*;

/** PH 40A2E0 services. The reviewed default cases and platform redraw requests
 * are distinct from unimplemented staff-roll and network services. */
public final class Dc4SystemCommands {
    // 178ee9... PE, VA4B4458:43 pairs(track ID,CP932 title pointer), then -1.
    // The text itself remains in the original game; this is not DD's88-track table.
    public static final int[] MUSIC_TRACKS={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43};
    // Native default block4AE4B8 and40E7D0's six category branches. State owns
    // the direct assignment: its ordinary setter has additional side effects.
    public static final int[] DEFAULT_KEYS={26,27,29,13,28,11,16,30,10,18,12,17,14,7,15,8,5,9,237,214,207,208,213,212,211,210,209,206,190,203,225,205,227,202,201,200,194,191,188,185,184,182,181,180,199,198,197,196};
    public static final int[] DEFAULT_VALUES={3,3,3,0,1,49,7,1,1,1,6,0,1,60,1,70,1,100,1,0,80,0,1,1,1,0,0,80,0,1,100,1,1,0,1,1,5,0,1,0,3,1,1,1,0,5,2,4};
    private static final int[][] DEFAULT_CATEGORIES={
        {237,10,12,213,181,202,205,212,211,180},
        {26,11,29,28,209,30,191,210},
        {225,203,7,14,8,15,9,5,207,200,206,190,208},
        {},{}, {201,194,199,198,197,196}
    };
    /** group-1 is the complete default projection; group4 also clears bank93.
     * Complete reset clears favorites; text group1 also writes100:49=49. */
    public static int[][] defaultRegisters(int group)throws IOException {
        if(group< -1||group>=DEFAULT_CATEGORIES.length)throw new IOException("PH default category "+group);
        int[] keys=group<0?DEFAULT_KEYS:DEFAULT_CATEGORIES[group];int[][] result=new int[keys.length][2];
        for(int n=0;n<keys.length;n++){result[n][0]=keys[n];for(int k=0;k<DEFAULT_KEYS.length;k++)if(DEFAULT_KEYS[k]==keys[n]){result[n][1]=DEFAULT_VALUES[k];break;}}
        return result;
    }
    private static final int FORMAT=0x50485331,VOICE_BYTES=408;
    private byte[] settingsBackup;
    private boolean settingsEditing;
    private final int[] musicOrder=MUSIC_TRACKS.clone();
    private int musicSelected,musicSeed=1;
    public int musicCatalogueIndex(){return musicOrder[musicSelected]-1;}
    public int[] musicOrder(){return musicOrder.clone();}

    public boolean command(Dc4Host host,int[] args)throws IOException {
        if(args.length<2)throw new IOException("PH system service requires two operands");
        int service=args[0],mode=args[1];
        switch(service){
        case 1:host.variable(3,0,Calendar.getInstance().get(Calendar.HOUR_OF_DAY));return true;
        case 2:{
            int selected=0,best=host.variable(5,0);
            for(int n=1;n<4;n++){int value=host.variable(5,n);if(value>best){best=value;selected=n;}}
            host.variable(3,0,selected);return true;
        }
        case 6:
            // Native selects controller mode3(save) or4(load), then requests
            // its ten-slot surface. The portable renderer owns that surface.
            host.ui.saveGrid=mode==0;return true;
        case 7:
            // LOG's0/1 select the controller's frame request. No MES variable
            // is modified; the platform already redraws the current UI frame.
            if(mode==0||mode==1)return true;
            break;
        case 9:host.startCredits(mode);return true;
        case 18:settings(host,mode);return true;
        case 19:
            if(mode==0){host.variable(9,87,host.availableSaves.contains(host.state.read(99,254))?0:1);return true;}
            break;
        case 20:
            // Native0 allocates the ten current-page PNG thumbnails, nonzero
            // frees them. Desktop/Canvas read their owned portable thumbnails;
            // no native image handles exist to allocate or release here.
            return true;
        case 51:
            // WIN_SIZE always opens the native Win32 name form on first run.
            // The portable binding accepts its original default names. Editable
            // name controls are outside the current PH presentation scope.
            // Mode1 only refreshes a private native changed-name flag; it has
            // no MES-variable result. Never substitute D.D.'s protagonist.
            if(mode==0||mode==1)return true;
            break;
        case 105:
            // Native40A070 returns without sending anything when clear-name/
            // e-mail are absent or its registration prompt is declined. This
            // binding stays on that offline path and writes no success flag.
            return true;
        case 100:case 215:
            // Both installed callers reach40A2E0's default branch. Preserve
            // their native lack of state mutation, rather than invent a reset.
            if(mode==0)return true;
            break;
        case 200:case 201:case 202:music(host,service,mode);return true;
        }
        throw new IOException("Unimplemented PH native system service ["+service+", "+mode+"]");
    }

    private int favoriteCount(Dc4Host h){int count=0;for(int value:h.state.musicFavorites)if(value!=0)count++;return count;}
    private boolean filter(Dc4Host h)throws IOException{return h.state.read(100,21)!=0&&favoriteCount(h)!=0;}
    private int random(int bound){musicSeed=musicSeed*214013+2531011;return ((musicSeed>>>16)&32767)%bound;}
    private void rebuild(Dc4Host h)throws IOException {
        //40_8EB0 uses100:26 in this edition. The CRT RNG is represented by an
        // isolated deterministic stream; global native RNG/frame identity is
        // outside the portable scheduler contract.
        int hold=h.variable(7,98);if(hold<0||hold>=musicOrder.length)hold=0;
        int current=musicOrder[hold];
        if(h.state.read(100,26)<2){
            System.arraycopy(MUSIC_TRACKS,0,musicOrder,0,musicOrder.length);musicSelected=current-1;h.variable(7,98,musicSelected);return;
        }
        Arrays.fill(musicOrder,0);musicOrder[hold]=current;int n=0;
        for(int tries=0;tries<10000&&n<musicOrder.length;tries++){
            if(n==hold){n++;continue;}
            int track=MUSIC_TRACKS[random(MUSIC_TRACKS.length)];boolean used=false;for(int t:musicOrder)if(t==track){used=true;break;}
            if(!used)musicOrder[n++]=track;
        }
        for(int track:MUSIC_TRACKS){boolean used=false;for(int t:musicOrder)if(t==track){used=true;break;}if(!used){while(n==hold)n++;if(n<musicOrder.length)musicOrder[n++]=track;}}
        musicSelected=hold;
    }
    private void move(Dc4Host h,int direction)throws IOException {
        boolean filtered=filter(h);
        for(int n=0;n<musicOrder.length*2;n++){
            musicSelected+=direction;
            if(musicSelected>=musicOrder.length){rebuild(h);musicSelected=0;}
            else if(musicSelected<0){rebuild(h);musicSelected=musicOrder.length-1;}
            if(!filtered||h.state.musicFavorites[musicOrder[musicSelected]]!=0){h.variable(7,98,musicSelected);return;}
        }
        throw new IOException("PH favorite filter has no favorite in its43-track catalogue");
    }
    private void music(Dc4Host h,int service,int mode)throws IOException {
        if(service==201){
            if(mode==255||mode==254)Arrays.fill(h.state.musicFavorites,mode==255?1:0);
            else {int n=h.state.read(99,246)+mode;if(n<0||n>=MUSIC_TRACKS.length)throw new IOException("PH music favorite index "+n);int track=MUSIC_TRACKS[n];h.state.musicFavorites[track]=h.state.musicFavorites[track]==0?1:0;}
            return;
        }
        if(service==202){int n=h.state.read(99,246)+mode;h.variable(3,0,n>=0&&n<MUSIC_TRACKS.length?h.state.musicFavorites[MUSIC_TRACKS[n]]:0);return;}
        if(mode==6){h.variable(3,0,favoriteCount(h));return;}
        if(favoriteCount(h)==0)h.state.write(100,21,0);
        switch(mode){
        case 0:
            rebuild(h);
            if(filter(h)){if(h.state.musicFavorites[musicOrder[musicSelected]]==0)move(h,1);h.variable(7,98,musicSelected);h.state.write(99,254,musicCatalogueIndex());}
            return;
        case 1:case 99:
            musicSelected=h.variable(7,98);
            if(musicSelected<0||musicSelected>=musicOrder.length){musicSelected=0;h.variable(7,98,0);}
            h.variable(3,11,0);h.state.write(99,254,musicCatalogueIndex());
            if(mode==99){h.musicRoom.seek=h.state.read(99,187);h.musicRoom.seekGeneration++;}
            else h.musicTrack(musicOrder[musicSelected],2);
            return;
        case 2:move(h,1);return;
        case 3:move(h,-1);return;
        case 4:return; // This edition's native branch has no title-baking work.
        case 5:
            int index=h.variable(7,98);if(index<0||index>=MUSIC_TRACKS.length){index=0;h.variable(7,98,0);}
            musicSelected=index;h.variable(3,11,0);int track=MUSIC_TRACKS[index];
            if(h.state.read(100,21)!=0)h.state.musicFavorites[track]=1;
            h.musicTrack(track,2);
            if(h.state.read(100,26)==2)for(int n=0;n<musicOrder.length;n++)if(musicOrder[n]==track){h.state.write(99,254,index);h.variable(7,98,n);musicSelected=n;break;}
            return;
        default:throw new IOException("Unimplemented PH music service200 mode "+mode);
        }
    }

    private void settings(Dc4Host host,int mode)throws IOException {
        if(mode==0){settingsBackup=host.state.settingsSnapshot();settingsEditing=true;return;}
        if(mode==1){
            if(settingsBackup==null)throw new IOException("PH settings comparison without backup");
            byte[] current=host.state.settingsSnapshot();
            if(current.length!=settingsBackup.length||current.length<VOICE_BYTES+55)throw new IOException("PH settings projection length changed");
            // Native substitutes the saved100:54 while comparing0x4B80 bytes.
            current[54]=settingsBackup[54];
            // Voice records are physically at12231C, outside that native
            // backup. They occur as a408-byte suffix in the portable projection.
            System.arraycopy(settingsBackup,current.length-VOICE_BYTES,current,current.length-VOICE_BYTES,VOICE_BYTES);
            host.variable(3,0,Arrays.equals(current,settingsBackup)?0:1);settingsEditing=false;return;
        }
        if(settingsEditing){
            if(settingsBackup==null)throw new IOException("PH settings rollback without backup");
            // Restore only the native backup region; preserve current voice
            // preferences, which native40A2E0 never copied into its backup.
            byte[] current=host.state.settingsSnapshot(),restored=settingsBackup.clone();
            if(current.length!=restored.length||current.length<VOICE_BYTES)throw new IOException("PH settings projection length changed");
            System.arraycopy(current,current.length-VOICE_BYTES,restored,restored.length-VOICE_BYTES,VOICE_BYTES);
            host.state.restoreSettings(restored);
        }
        settingsEditing=false;
    }

    public void save(DataOutput out)throws IOException {
        out.writeInt(FORMAT);out.writeBoolean(settingsEditing);out.writeBoolean(settingsBackup!=null);
        if(settingsBackup!=null){out.writeInt(settingsBackup.length);out.write(settingsBackup);}
        out.writeInt(musicSelected);out.writeInt(musicSeed);for(int track:musicOrder)out.writeInt(track);
    }
    public void load(DataInput in,Dc3State state)throws IOException {
        if(in.readInt()!=FORMAT)throw new IOException("PH system service state format");
        settingsEditing=in.readBoolean();settingsBackup=null;
        if(in.readBoolean()){
            int length=in.readInt();
            if(length!=state.settingsSnapshot().length)throw new IOException("PH settings backup size");
            settingsBackup=new byte[length];in.readFully(settingsBackup);
        }
        if(settingsEditing&&settingsBackup==null)throw new IOException("PH settings backup is missing");
        musicSelected=in.readInt();musicSeed=in.readInt();
        if(musicSelected<0||musicSelected>=musicOrder.length)throw new IOException("PH music selection");
        boolean[] seen=new boolean[MUSIC_TRACKS.length+1];
        for(int n=0;n<musicOrder.length;n++){int track=in.readInt();if(track<1||track>MUSIC_TRACKS.length||seen[track])throw new IOException("PH music order");seen[track]=true;musicOrder[n]=track;}
    }
}
