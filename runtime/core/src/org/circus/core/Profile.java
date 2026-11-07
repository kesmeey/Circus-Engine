package org.circus.core;

import java.util.*;

/** Version binding. Numeric opcodes are never inferred from another game's offsets. */
public final class Profile {
    public final String id;
    public final int version, headerStride, versionBytes, string8Start, stringStart, encodedStart, wordsStart;
    public final int extension, label, block, companion;
    public final boolean signedBlockScan, dollarToSpace, demo;
    public final boolean textVerified;
    public final Variables.Model variableModel;
    public final boolean signedFetchOpcode;
    public final boolean allowTerminalFragment;
    public final boolean runtimeExactVersion;
    public final boolean extensionAtFetch;
    private final Set<Integer> extensionOwners;
    private final Set<Integer> stringPayloadOwners;
    private final Map<Integer,String> operations;
    private final int[] accepted;
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean demo,Map<Integer,String> ops){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,postmap,demo,Variables.Model.BYTE_WRAP,ops);
    }
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,textVerified,demo,variableModel,ops,null);
    }
    /** Alternative stamps establish framing only; native loader admission is a separate rule. */
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops,int[] altVersions){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,textVerified,
             demo,variableModel,ops,altVersions,false,false,false);
    }
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops,int[] altVersions,
                   boolean signedFetchOpcode,boolean allowTerminalFragment,boolean runtimeExactVersion){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,textVerified,
             demo,variableModel,ops,altVersions,signedFetchOpcode,allowTerminalFragment,runtimeExactVersion,true,new int[0]);
    }
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops,int[] altVersions,
                   boolean signedFetchOpcode,boolean allowTerminalFragment,boolean runtimeExactVersion,
                   boolean extensionAtFetch,int[] extensionOwners){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,textVerified,
             demo,variableModel,ops,altVersions,signedFetchOpcode,allowTerminalFragment,runtimeExactVersion,
             extensionAtFetch,extensionOwners,new int[0]);
    }
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops,int[] altVersions,
                   boolean signedFetchOpcode,boolean allowTerminalFragment,boolean runtimeExactVersion,
                   boolean extensionAtFetch,int[] extensionOwners,int[] stringPayloadOwners){
        this.id=id;this.version=version;this.headerStride=stride;this.versionBytes=vb;this.string8Start=s8;
        this.stringStart=s;this.encodedStart=enc;this.wordsStart=words;this.extension=ext;
        this.label=label;this.block=block;this.companion=companion;this.signedBlockScan=signed;
        this.dollarToSpace=postmap;this.textVerified=textVerified;this.variableModel=variableModel;
        this.demo=demo;this.operations=Collections.unmodifiableMap(new HashMap<>(ops));
        this.signedFetchOpcode=signedFetchOpcode;
        this.allowTerminalFragment=allowTerminalFragment;
        this.runtimeExactVersion=runtimeExactVersion;
        this.extensionAtFetch=extensionAtFetch;
        Set<Integer> owners=new HashSet<>();for(int opcode:extensionOwners)owners.add(opcode);
        this.extensionOwners=Collections.unmodifiableSet(owners);
        Set<Integer> strings=new HashSet<>();for(int opcode:stringPayloadOwners)strings.add(opcode);
        this.stringPayloadOwners=Collections.unmodifiableSet(strings);
        if(altVersions==null||altVersions.length==0){accepted=new int[]{version};}
        else{accepted=new int[altVersions.length+1];accepted[0]=version;System.arraycopy(altVersions,0,accepted,1,altVersions.length);}
    }
    public boolean acceptsVersion(int v){for(int x:accepted)if(x==v)return true;return false;}
    /** Structural aliases do not override a native loader's exact signature check. */
    public boolean canExecuteVersion(int v){return runtimeExactVersion?v==version:acceptsVersion(v);}
    public boolean ownsExtension(int opcode){return extensionOwners.contains(opcode);}
    public boolean ownsStringPayload(int opcode){return stringPayloadOwners.contains(opcode);}
    /** Snapshot identity includes the declared rules and a reviewed host/core revision.
     * A wire-compatible array layout does not make old game state semantically portable. */
    public String executionIdentity(){
        StringBuilder contract=new StringBuilder("native-mes-contract-8/2026-11-04\n");
        contract.append(id).append('\n').append(version).append(',').append(headerStride).append(',').append(versionBytes)
            .append(',').append(string8Start).append(',').append(stringStart).append(',').append(encodedStart).append(',').append(wordsStart)
            .append(',').append(extension).append(',').append(label).append(',').append(block).append(',').append(companion)
            .append(',').append(signedBlockScan).append(',').append(dollarToSpace).append(',').append(textVerified)
            .append(',').append(demo).append(',').append(variableModel).append(',').append(signedFetchOpcode)
            .append(',').append(allowTerminalFragment).append(',').append(runtimeExactVersion).append(',').append(extensionAtFetch)
            .append('\n').append(new TreeSet<>(extensionOwners)).append('\n').append(new TreeSet<>(stringPayloadOwners))
            .append('\n').append(new TreeMap<>(operations));
        int[] versions=accepted.clone();Arrays.sort(versions);contract.append('\n').append(Arrays.toString(versions));
        // Previous DC2 snapshots may already have skipped unread story because
        // 99:251 returned a catalog index. Host-tail migration cannot repair
        // that execution history. Keep other families' reviewed identities.
        if(id.equals("dc2-3363"))contract.append("\ndc2-first-play-host-1/2026-11-07");
        if(id.equals("dc2pc-5769"))contract.append("\npc2-first-play-host-1/2026-11-08");
        return Binary.sha256(contract.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public String operation(int opcode){
        // These decoders sign-extend the byte for framing, then use an unsigned
        // table bound. Negative opcodes therefore take the native default.
        if(signedFetchOpcode&&opcode>=0x80&&opcode<=0xff)return "native.no_effect";
        String s=operations.get(opcode);return s==null?"unknown":s;
    }
    public int opcode(String operation){for(Map.Entry<Integer,String> e:operations.entrySet())if(e.getValue().equals(operation))return e.getKey();throw new IllegalArgumentException(operation);}
}
