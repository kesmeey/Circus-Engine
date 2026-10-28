package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Parses the original script bundle in memory; prints counts, never exports original script text. */
public final class CircusScrCorpusAudit {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: CircusScrCorpusAudit GAME_DIRECTORY");
        UnityResources resources=new UnityResources(new GameResources(new LocalGameStorage(Paths.get(args[0]))));
        long instructions=0,labels=0,branches=0,strings=0;int scripts=0,other=0,callCount=0;TreeMap<Integer,Integer> ops=new TreeMap<Integer,Integer>(),functions=new TreeMap<Integer,Integer>();
        HashSet<String> names=new HashSet<String>();HashSet<String> dependencies=new HashSet<String>();Map<String,CircusScrScript> parsed=new LinkedHashMap<String,CircusScrScript>();
        for(String name:resources.names("script0",49)){
            byte[] data=resources.text("script0",name);if(data.length<12||!CircusScrScript.SIGNATURE.equals(new String(data,0,12,"US-ASCII"))){other++;continue;}
            CircusScrScript script=new CircusScrScript(name,data);scripts++;names.add(script.name);parsed.put(script.name,script);instructions+=script.instructions.length;labels+=script.labels.size();branches+=script.jumps.length;strings+=script.strings.length;for(String dependency:script.preloadScripts)dependencies.add(CircusScrScript.normalizeName(dependency));
            for(CircusScrScript.Instruction op:script.instructions){ops.put(op.opcode,ops.containsKey(op.opcode)?ops.get(op.opcode)+1:1);if(op.opcode==4){callCount++;functions.put(op.command(),functions.containsKey(op.command())?functions.get(op.command())+1:1);}}
        }
        dependencies.removeAll(names);if(!dependencies.isEmpty())throw new IOException("Missing preloaded scripts: "+dependencies);
        if(scripts==0)throw new IOException("No CircusScr scripts");
        int transfers=0,namedJumps=0,unresolved=0;for(CircusScrScript script:parsed.values()){
            Integer reg=null;ArrayList<Object> parameters=new ArrayList<Object>();
            for(CircusScrScript.Instruction ins:script.instructions){
                if(ins.opcode==0)reg=ins.value;
                else if(ins.opcode==3)parameters.add(ins.value==0?reg:reg!=null&&reg>=0&&reg<script.strings.length?script.strings[reg]:null);
                else if(ins.opcode==4){int count=ins.argumentCount();if(parameters.size()<count)throw new IOException("Linear parameter underflow in "+script.name+":"+ins.index);Object[] a=new Object[count];for(int i=count-1;i>=0;i--)a[i]=parameters.remove(parameters.size()-1);int command=ins.command();
                    if(command==37||command==89||command==134||command==135||command==148||command==149){
                        if(a.length==0||!(a[0] instanceof String)){unresolved++;continue;}String target=(String)a[0];
                        if(command==89){if(!target.isEmpty())script.label(target);namedJumps++;}
                        else{CircusScrScript destination=parsed.get(CircusScrScript.normalizeName(target));if(destination==null)throw new IOException("Missing transfer "+script.name+" -> "+target);if(command==135||command==149){if(a.length<2||!(a[1] instanceof String)){unresolved++;continue;}destination.label((String)a[1]);}transfers++;}
                    }
                }else if(ins.opcode==5||ins.opcode>=7&&ins.opcode<=20)reg=null;
            }
        }
        System.out.println("PASS CircusScr corpus scripts="+scripts+" auxiliary_text_assets="+other+" instructions="+instructions+" labels="+labels+" branches="+branches+" strings="+strings+" calls="+callCount);
        System.out.println("operators="+ops);System.out.println("host_commands="+functions);System.out.println("preload_targets=all_present literal_transfers="+transfers+" named_jumps="+namedJumps+" unresolved_dynamic_targets="+unresolved);
    }
}
