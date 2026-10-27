package org.circus.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** A serializable playback recipe for the native simultaneous-voice command. */
public final class MixedVoice {
    private MixedVoice(){}
    public static String reference(List<String> names)throws IOException{if(names.isEmpty()||names.size()>10)throw new IOException("Voice group size");for(String n:names)if(!n.toUpperCase(Locale.ROOT).startsWith("VOICE\\")&&!n.toUpperCase(Locale.ROOT).startsWith("VOICE/"))throw new IOException("Voice group resource");return "mix:"+Base64.getEncoder().encodeToString(String.join("\n",names).getBytes(StandardCharsets.UTF_8));}
    public static Xpcm decode(CircusResources resources,String reference)throws IOException{
        if(reference.length()>4096)throw new IOException("Voice recipe too large");String[] names;
        try{names=new String(Base64.getDecoder().decode(reference.substring(4)),StandardCharsets.UTF_8).split("\n",-1);}catch(IllegalArgumentException e){throw new IOException("Voice recipe encoding",e);}
        if(names.length<1||names.length>10)throw new IOException("Voice group size");List<Xpcm> inputs=new ArrayList<>();for(String name:names){if(!name.toUpperCase(Locale.ROOT).startsWith("VOICE/")&&!name.toUpperCase(Locale.ROOT).startsWith("VOICE\\"))throw new IOException("Voice recipe resource");inputs.add(resources.audio(name));}return mix(inputs);
    }
    public static Xpcm mix(List<Xpcm> inputs)throws IOException{
        if(inputs.isEmpty()||inputs.size()>10)throw new IOException("Voice mix size");int rate=0,channels=1;long length=0;
        for(Xpcm a:inputs){if(!a.codec.equals("pcm"))throw new IOException("Grouped Ogg voice is not implemented");rate=Math.max(rate,a.sampleRate);channels=Math.max(channels,a.channels);}
        for(Xpcm a:inputs)length=Math.max(length,((long)a.payload.length/(a.channels*(a.bits/8))*rate+a.sampleRate-1)/a.sampleRate);
        if(length*channels*2>128*1024*1024)throw new IOException("Voice mix allocation limit");byte[] output=new byte[(int)length*channels*2];
        for(int f=0;f<length;f++)for(int ch=0;ch<channels;ch++){int value=0;for(Xpcm a:inputs){long source=(long)f*a.sampleRate;int at=(int)(source/rate),count=a.payload.length/(a.channels*(a.bits/8));if(at<count){int c=Math.min(ch,a.channels-1),first=sample(a,at,c),next=sample(a,Math.min(count-1,at+1),c);value+=first+(int)((long)(next-first)*(source%rate)/rate);}}value=Math.max(-32768,Math.min(32767,value));int off=(f*channels+ch)*2;output[off]=(byte)value;output[off+1]=(byte)(value>>8);}
        return Xpcm.pcm(output,channels,rate,16);
    }
    private static int sample(Xpcm a,int frame,int ch){int at=(frame*a.channels+ch)*(a.bits/8);return a.bits==8?((a.payload[at]&255)-128)<<8:(short)((a.payload[at]&255)|(a.payload[at+1]<<8));}
}
