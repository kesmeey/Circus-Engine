package org.circus.core;

import java.io.IOException;
import java.util.*;

/** PC1 41A970/41AAD0 story-file services with explicit native owners and
 * ordered I/O. Paths are supplied after native filename formatting. This does
 * not substitute a partial frame apply for41A620 reconstruction. */
public final class Pc1StorySlotIo {
    private Pc1StorySlotIo(){}
    public enum Kind { CLEAR_WORKING_SIGNATURE,OPEN_READ,READ,CLOSE,COPY_TO_SUSPENDED,RECONSTRUCT,
        PREPARE_CANDIDATE,WRITE_SETTINGS,SET_CANDIDATE_SIGNATURE,OPEN_WRITE,WRITE }
    public static final class Event {
        public final Kind kind;public final int result,requested;
        private Event(Kind kind,int result,int requested){this.kind=kind;this.result=result;this.requested=requested;}
    }
    /** Executes the complete41A620 owner, supplied by its runtime. The reader
     *must not silently replace this with a byte copy or a projected PC alone. */
    public interface Reconstruction {int reconstruct()throws IOException;}
    public interface Preparation {void prepare(Pc1NativeFrame candidate)throws IOException;}
    public interface SettingsWrite {int write()throws IOException;}
    public static final class ReadResult {
        public final int nativeResult,openResult,readCount,closeResult;
        public final boolean admitted;
        public final List<Event> events;
        private ReadResult(int result,int opened,int read,int closed,boolean admitted,List<Event> events){nativeResult=result;openResult=opened;readCount=read;closeResult=closed;this.admitted=admitted;this.events=freeze(events);}
    }
    public static final class WriteResult {
        public final int nativeResult,settingsResult,openResult,writeCount,closeResult;
        public final boolean fullCountAndCloseSucceeded;
        public final List<Event> events;
        private WriteResult(int settings,int opened,int written,int closed,List<Event> events){nativeResult=opened==-1?-1:0;settingsResult=settings;openResult=opened;writeCount=written;closeResult=closed;fullCountAndCloseSucceeded=opened!=-1&&written==Pc1FrameLayout.FRAME_BYTES&&closed==0;this.events=freeze(events);}
    }
    /** Clears only working.signature before open. Short/error reads retain
     *their actual prefix and the prior unread working bytes. The suspended
     *frame is replaced only after count8004 and signature1 both pass; trailing
     *file bytes are never read. Close failure does not prevent native admission. */
    public static ReadResult read(Pc1NativeSettings.NativeIo io,String path,Pc1NativeFrame working,
            Pc1NativeFrame suspended,Reconstruction reconstruction)throws IOException{
        if(io==null||path==null||working==null||suspended==null||reconstruction==null||working==suspended)throw new IOException("PC1 story read requires distinct working/suspended owners and complete reconstruction");
        List<Event> events=new ArrayList<>();working.dword(Pc1NativeSlot.Dword.SIGNATURE,0);event(events,Kind.CLEAR_WORKING_SIGNATURE,0,4);
        int fd=io.open(path,0x8000,0);event(events,Kind.OPEN_READ,fd,0);
        if(fd==-1)return new ReadResult(-1,fd,-1,-1,false,events);
        byte[] buffer=working.bytes();int count;
        try{count=io.read(fd,buffer,Pc1FrameLayout.FRAME_BYTES);}
        catch(IOException failure){working.writeBytes(0,buffer);closeAfterFailure(io,fd,failure);throw failure;}
        working.writeBytes(0,buffer);event(events,Kind.READ,count,Pc1FrameLayout.FRAME_BYTES);
        int closed=io.close(fd);event(events,Kind.CLOSE,closed,0);
        if(count!=Pc1FrameLayout.FRAME_BYTES||working.dword(Pc1NativeSlot.Dword.SIGNATURE)!=1)return new ReadResult(-1,fd,count,closed,false,events);
        suspended.copyFrom(working);event(events,Kind.COPY_TO_SUSPENDED,0,Pc1FrameLayout.FRAME_BYTES);
        int result=reconstruction.reconstruct();event(events,Kind.RECONSTRUCT,result,0);
        return new ReadResult(result,fd,count,closed,true,events);
    }
    /** Metadata preparation happens first, then4027E0 (its native return is
     *ignored), then candidate.signature=1 before the story-file open. Candidate
     *mutations survive failed opens. Like41AAD0, a successful open yields0 even
     *for a short write or failed close; the reliable result is reported apart. */
    public static WriteResult write(Pc1NativeSettings.NativeIo io,String path,Pc1NativeFrame candidate,
            Preparation preparation,SettingsWrite settings)throws IOException{
        if(io==null||path==null||candidate==null||preparation==null||settings==null)throw new IOException("PC1 story write requires candidate, metadata preparation and native settings service");
        List<Event> events=new ArrayList<>();preparation.prepare(candidate);event(events,Kind.PREPARE_CANDIDATE,0,0);
        int settingsResult=settings.write();event(events,Kind.WRITE_SETTINGS,settingsResult,0);
        candidate.dword(Pc1NativeSlot.Dword.SIGNATURE,1);event(events,Kind.SET_CANDIDATE_SIGNATURE,1,4);
        int fd=io.open(path,0x8301,0x180);event(events,Kind.OPEN_WRITE,fd,0);
        if(fd==-1)return new WriteResult(settingsResult,fd,-1,-1,events);
        int written;try{written=io.write(fd,candidate.bytes(),Pc1FrameLayout.FRAME_BYTES);}
        catch(IOException failure){closeAfterFailure(io,fd,failure);throw failure;}
        event(events,Kind.WRITE,written,Pc1FrameLayout.FRAME_BYTES);int closed=io.close(fd);event(events,Kind.CLOSE,closed,0);
        return new WriteResult(settingsResult,fd,written,closed,events);
    }
    private static void event(List<Event> events,Kind kind,int result,int requested){events.add(new Event(kind,result,requested));}
    private static List<Event> freeze(List<Event> events){return Collections.unmodifiableList(new ArrayList<>(events));}
    private static void closeAfterFailure(Pc1NativeSettings.NativeIo io,int descriptor,IOException failure){try{io.close(descriptor);}catch(IOException close){failure.addSuppressed(close);}}
}
