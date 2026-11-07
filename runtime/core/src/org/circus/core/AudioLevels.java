package org.circus.core;

/** Conversion of native DirectSound balance units for the portable output adapters. */
public final class AudioLevels {
    private AudioLevels(){}
    public static float directSoundBalance(int pan){return pan==0?0:Math.copySign((float)(1-Math.pow(10,-Math.abs((long)pan)/2000.0)),pan);}
}
