package org.circus.runtime;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.util.*;

/** Registered Android DocumentsProvider for synthetic SAF regression tests.
 * Uses real ContentResolver/PFD access and contains no original game assets. */
public final class DocumentTreeFixtureProvider extends DocumentsProvider {
    public static final String AUTHORITY="org.circus.runtime.documenttree.audit";
    static volatile boolean hideNames;
    private static final String ID=DocumentsContract.Document.COLUMN_DOCUMENT_ID,NAME=DocumentsContract.Document.COLUMN_DISPLAY_NAME,MIME=DocumentsContract.Document.COLUMN_MIME_TYPE,SIZE=DocumentsContract.Document.COLUMN_SIZE,TIME=DocumentsContract.Document.COLUMN_LAST_MODIFIED;
    private static final class Node {
        final String id,parent,name;final byte[] data;
        Node(String id,String parent,String name,byte[] data){this.id=id;this.parent=parent;this.name=name;this.data=data;}
        boolean directory(){return data==null;}
    }
    @Override public boolean onCreate(){return true;}
    static String root(String scenario){return scenario+":root";}
    private static Map<String,Node> nodes(String id)throws FileNotFoundException{
        int colon=id.indexOf(':');if(colon<0)throw new FileNotFoundException("Unknown fixture document");String scenario=id.substring(0,colon);
        if(!Arrays.asList("flat","nested","reordered","missing","unknown","badcolumns").contains(scenario))throw new FileNotFoundException("Unknown fixture tree");
        String root=root(scenario),game=scenario+":game",adv=scenario+":adv",dat=scenario+":dat",mes=scenario+":mes",saves=scenario+":saves";Map<String,Node> all=new LinkedHashMap<String,Node>();
        all.put(root,new Node(root,"","Fixture root",null));String parent=root;
        if(scenario.equals("nested")){all.put(game,new Node(game,root,"D.C.II S.C",null));parent=game;}
        all.put(adv,new Node(adv,parent,"aDvDaTa",null));all.put(dat,new Node(dat,adv,"DaT",null));all.put(mes,new Node(mes,adv,"mEs",null));
        byte[] script=new byte[32];script[0]=1;script[8]=0x69;script[9]=0x3b;all.put(scenario+":start",new Node(scenario+":start",mes,"START.MES",script));
        if(!scenario.equals("missing")&&!hideNames)all.put(scenario+":names",new Node(scenario+":names",dat,"names.dat",new byte[20]));
        all.put(saves,new Node(saves,adv,"SaveData",null));all.put(scenario+":system",new Node(scenario+":system",saves,"system.dat",new byte[]{7,8,9}));return all;
    }
    private static Object value(Node n,String column,boolean unknown){
        if(ID.equals(column))return n.id;if(NAME.equals(column))return n.name;if(MIME.equals(column))return n.directory()?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream";
        if(SIZE.equals(column))return unknown?null:n.directory()?0:n.data.length;if(TIME.equals(column))return unknown?null:1000L;
        if(DocumentsContract.Document.COLUMN_FLAGS.equals(column))return 0;return null;
    }
    private static void row(MatrixCursor cursor,Node node,boolean unknown){MatrixCursor.RowBuilder row=cursor.newRow();for(String column:cursor.getColumnNames())row.add(value(node,column,unknown));}
    @Override public Cursor queryRoots(String[] projection){String[] columns=projection==null?new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS}:projection;MatrixCursor out=new MatrixCursor(columns);MatrixCursor.RowBuilder row=out.newRow();for(String column:columns)row.add(column.equals(DocumentsContract.Root.COLUMN_ROOT_ID)?"audit":column.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID)?root("flat"):column.equals(DocumentsContract.Root.COLUMN_TITLE)?"Synthetic SAF audit":0);return out;}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{Node node=nodes(id).get(id);if(node==null)throw new FileNotFoundException(id);MatrixCursor out=new MatrixCursor(projection==null?new String[]{ID,NAME,MIME,SIZE,TIME}:projection);row(out,node,id.startsWith("unknown:"));return out;}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort)throws FileNotFoundException{
        String[] columns=projection==null?new String[]{ID,NAME,MIME,SIZE,TIME}:projection;
        // The cursor still names every requested field; do not assume positions.
        if(parent.equals("reordered:dat"))columns=new String[]{NAME,SIZE,ID,MIME,TIME};
        if(parent.startsWith("badcolumns:"))columns=new String[]{NAME,MIME,SIZE,TIME};
        MatrixCursor out=new MatrixCursor(columns);for(Node node:nodes(parent).values())if(node.parent.equals(parent))row(out,node,parent.startsWith("unknown:"));return out;
    }
    @Override public boolean isChildDocument(String parent,String child){int colon=parent.indexOf(':');return colon>0&&child.startsWith(parent.substring(0,colon+1));}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        if(!mode.equals("r"))throw new FileNotFoundException("Synthetic fixture is read-only");Node node=nodes(id).get(id);if(node==null||node.directory())throw new FileNotFoundException(id);
        File directory=new File(getContext().getCacheDir(),"saf-document-fixture");if(!directory.isDirectory()&&!directory.mkdirs())throw new FileNotFoundException("Cannot prepare synthetic fixture");File file=new File(directory,id.replace(':','_')+".bin");
        try{try(FileOutputStream out=new FileOutputStream(file)){out.write(node.data);}return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException failure){throw new FileNotFoundException(failure.toString());}
    }
}
