package com.narmanb.bmhero;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.Locale;
import java.util.zip.*;

/** Validates before replacing a previous import; accepts raw or ZIP N64 dumps. */
public final class RomImporter {
    private static final int SIZE = 12 * 1024 * 1024;
    private static final String SHA1 = "a36364b7e59351f7551ab351cb3b41ebc4be285b";
    public static void importRom(InputStream source, File destination) throws IOException {
        if (source == null) throw new IOException("Cannot read the selected document");
        byte[] data;
        try (BufferedInputStream in = new BufferedInputStream(source)) {
            in.mark(4);
            boolean zipped = in.read() == 'P' && in.read() == 'K';
            in.reset();
            if (zipped) {
                ZipInputStream zip = new ZipInputStream(in);
                ZipEntry entry;
                data = null;
                int count = 0;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > 128) throw new IOException("Too many ZIP entries");
                    String name = entry.getName().toLowerCase(Locale.ROOT);
                    if (!entry.isDirectory() && (name.endsWith(".z64") || name.endsWith(".n64") || name.endsWith(".v64"))) {
                        data = readBounded(zip);
                        break;
                    }
                    // Bound skipped entries too, including archives with oversized notes.
                    byte[] buffer = new byte[8192]; int n, total = 0;
                    while ((n = zip.read(buffer)) != -1) {
                        total += n;
                        if (total > SIZE) throw new IOException("ZIP entry is too large");
                    }
                }
                if (data == null) throw new IOException("No N64 ROM found in ZIP");
            } else data = readBounded(in);
        }
        int header = ((data[0]&255)<<24)|((data[1]&255)<<16)|((data[2]&255)<<8)|(data[3]&255);
        if (header == 0x37804012) {
            for (int i=0;i<SIZE;i+=2) { byte t=data[i];data[i]=data[i+1];data[i+1]=t; }
        } else if (header == 0x40123780) {
            for (int i=0;i<SIZE;i+=4) { byte t=data[i];data[i]=data[i+3];data[i+3]=t;t=data[i+1];data[i+1]=data[i+2];data[i+2]=t; }
        } else if (header != 0x80371240) throw new IOException("Not an N64 ROM");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder digest = new StringBuilder();
            for (byte b:hash) digest.append(String.format(Locale.ROOT,"%02x",b&255));
            if (!SHA1.equals(digest.toString())) throw new IOException("Select Bomberman Hero USA 1.0");
        } catch (NoSuchAlgorithmException e) { throw new IOException(e); }
        File temporary = new File(destination.getPath()+".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(temporary)) { out.write(data); out.getFD().sync(); }
            Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } finally { temporary.delete(); }
    }
    private static byte[] readBounded(InputStream in) throws IOException {
        byte[] data=new byte[SIZE]; int pos=0,n;
        while(pos<SIZE && (n=in.read(data,pos,SIZE-pos))!=-1) { if(n==0) continue; pos+=n; }
        if(pos!=SIZE || in.read()!=-1) throw new IOException("Expected a 12 MiB USA ROM");
        return data;
    }
}
