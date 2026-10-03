package com.narmanb.bmhero;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import org.libsdl.app.SDLActivity;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends SDLActivity {
    private static final int PICK = 71;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean multiple;
    private File data;
    private String startupFailure;
    private DiagnosticFiles diagnostics;
    private native void nativeInit(String path);
    private native void nativeDocumentResult(boolean success, String[] paths);
    @Override protected String[] getLibraries() { return new String[]{"c++_shared", "SDL2", "BMHero"}; }
    @Override public void loadLibraries() {
        if (startupFailure != null) throw new IllegalStateException(startupFailure);
        for (String library : getLibraries()) {
            if (diagnostics != null) diagnostics.stage("Loading " + library);
            System.loadLibrary(library);
        }
    }
    @Override protected void onCreate(Bundle state) {
        data = new File(getFilesDir(), "bmhero");
        diagnostics = new DiagnosticFiles(data);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            diagnostics.failure(error);
            if (previous != null) previous.uncaughtException(thread, error);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
        try {
            diagnostics.stage("Extracting assets");
            if (!data.isDirectory() && !data.mkdirs()) throw new IOException("Cannot create app storage");
            installAssets("assets", new File(data,"assets"));
            installAssets("recompcontrollerdb.txt", new File(data,"recompcontrollerdb.txt"));
            loadLibraries();
            diagnostics.stage("Calling nativeInit");
            nativeInit(data.getAbsolutePath());
        } catch (Exception | UnsatisfiedLinkError e) {
            // SDLActivity must receive onCreate, but its broken-library flow
            // must prevent surface/native-thread creation after an asset failure.
            startupFailure = "Startup failed: " + e.getMessage();
            diagnostics.failure(e);
            super.onCreate(state);
            return;
        }
        diagnostics.stage("Creating SDL surface");
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void installAssets(String source, File dest) throws IOException {
        String[] children = getAssets().list(source);
        if (children != null && children.length > 0) {
            if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("Cannot create assets directory");
            for (String child:children) installAssets(source+"/"+child,new File(dest,child));
        } else try(InputStream in=getAssets().open(source);OutputStream out=new FileOutputStream(dest)) {
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
    }
    // Called from a native runtime thread; framework operations stay on UI thread.
    public void requestOpenDocument(boolean allowMultiple) {
        runOnUiThread(()-> {
            multiple=allowMultiple;
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,allowMultiple);
            try { startActivityForResult(intent,PICK); }
            catch (RuntimeException e) { nativeDocumentResult(false,new String[0]); }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        if(request!=PICK) {super.onActivityResult(request,result,intent);return;}
        if(result!=RESULT_OK || intent==null) {nativeDocumentResult(false,new String[0]);return;}
        final boolean importMany=multiple;
        io.execute(()-> {
            try {
                ArrayList<String> paths=new ArrayList<>();
                if(importMany) {
                    ArrayList<Uri> uris=new ArrayList<>();
                    if(intent.getClipData()!=null) {
                        if(intent.getClipData().getItemCount()>64)throw new IOException("Too many files");
                        for(int i=0;i<intent.getClipData().getItemCount();i++)uris.add(intent.getClipData().getItemAt(i).getUri());
                    } else if(intent.getData()!=null)uris.add(intent.getData());
                    for(Uri uri:uris)paths.add(copyMod(uri).getAbsolutePath());
                } else {
                    File rom=new File(data,"imported.z64");
                    RomImporter.importRom(getContentResolver().openInputStream(intent.getData()),rom);
                    paths.add(rom.getAbsolutePath());
                }
                nativeDocumentResult(!paths.isEmpty(),paths.toArray(new String[0]));
            } catch(Exception e) {
                nativeDocumentResult(false,new String[0]);
                runOnUiThread(()->new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show());
            }
        });
    }
    private File copyMod(Uri uri) throws IOException {
        String name="mod.nrm";
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst())name=new File(c.getString(0)).getName();
        }
        File dir=new File(getCacheDir(),"pick-"+UUID.randomUUID());
        if(!dir.mkdirs())throw new IOException("Cannot copy selected file");
        File dest=new File(dir,name);
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)) {
            if(in==null)throw new IOException("Cannot open document");
            byte[] b=new byte[65536];int n;long total=0;
            while((n=in.read(b))!=-1) {total+=n;if(total>128L*1024*1024)throw new IOException("File too large");out.write(b,0,n);}
        } catch(IOException e) {dest.delete();dir.delete();throw e;}
        return dest;
    }
    @Override protected void onDestroy() {
        io.shutdownNow(); super.onDestroy();
        // The runtime is initialized once per process; the launcher survives.
        if (isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
