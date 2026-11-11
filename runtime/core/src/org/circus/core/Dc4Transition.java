package org.circus.core;

import java.io.IOException;

/** Original0F fallback for actual PH modes8/40. These modes have no native
 * fade clock:4284B0 calls422680 (stop cast motion),407C70 (bind retained
 * screen texture), then bypasses both the0/1 fade and2 transition branches.
 * Committing pending data here is a portable phase-boundary approximation;
 * all changed state is serialized by the shared scene/host owners. */
public final class Dc4Transition {
    private Dc4Transition(){}
    public static boolean command(Dc4Host h,String op,int[] a,byte[] bytes)throws IOException{
        if(!op.equals("scene.transition")||a[0]!=8&&a[0]!=40)return false;
        h.phScene.stopCastMotion(h);
        h.baseCommand("scene.transition",new int[]{5,0},new byte[0]);
        h.scene.transition=a[0];h.scene.transitionParameter=a[1];return true;
    }
}
