package org.circus.core;

import java.io.IOException;

/** Explicit DD binding retained for the existing DD audit tools. */
public final class Dc3Resources extends CircusResources {
    public Dc3Resources(GameResources files)throws IOException{super(files,Profiles.get("dc3dd-a5a8"));}
}
