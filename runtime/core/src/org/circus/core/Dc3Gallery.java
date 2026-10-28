package org.circus.core;

import java.io.*;
import java.util.*;

/** DD 40EDC0/40EDE0: NAMES catalogue indices are the persistent gallery flags. */
public final class Dc3Gallery {
    private final CircusResources resources;
    private Map<String,Integer> images,scripts;
    public Dc3Gallery(CircusResources resources){this.resources=resources;}
    private static String key(String name){
        String result=name.replace('/','\\').toUpperCase(Locale.ROOT);
        int dot=result.indexOf('.');return dot<0?result:result.substring(0,dot);
    }
    private Map<String,Integer> index(String category)throws IOException{
        Map<String,Integer> result=new HashMap<>();
        if(resources!=null)for(NamesCatalog.Entry entry:resources.names.table(category))result.putIfAbsent(key(entry.name),entry.index);
        return result;
    }
    public void image(Dc3State state,String name)throws IOException{
        if(images==null)images=index("images");Integer id=images.get(key(name));if(id!=null)state.write(97,id,1);
    }
    public void completedScript(Dc3State state,String name)throws IOException{
        if(scripts==null)scripts=index("scenario_scripts");Integer id=scripts.get(key(name));if(id!=null)state.write(98,id,1);
    }
    private void frame(Dc3State state,Dc3Scene.Frame frame)throws IOException{if(frame!=null)image(state,frame.layers[1].resource);}
    /** Recover only retained display evidence. Preloads, clear flags and unvisited branches
     * cannot establish which CG variations were actually seen in an older release. */
    public void recover(Dc3Host host)throws IOException{
        frame(host.state,host.scene.published);frame(host.state,host.systemScene);
        String previous="";
        for(Dc2History.Entry entry:host.history.entries){
            if(!previous.isEmpty()&&!previous.equalsIgnoreCase(entry.script))completedScript(host.state,previous);
            previous=entry.script;
            if(entry.scene.length!=0){Dc3Scene.Frame frame=new Dc3Scene.Frame();frame.load(new DataInputStream(new ByteArrayInputStream(entry.scene)));frame(host.state,frame);}
        }
    }
}
