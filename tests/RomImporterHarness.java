package com.narmanb.bmhero;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class RomImporterHarness {
 public static void main(String[] args) throws Exception {
  byte[] rom=Files.readAllBytes(Paths.get(args[0]));
  File dir=Files.createTempDirectory("hero-import-test").toFile();
  File dest=new File(dir,"rom.z64");
  for(int order=0;order<3;order++) {
   byte[] b=rom.clone();
   if(order==1) for(int i=0;i<b.length;i+=2){byte t=b[i];b[i]=b[i+1];b[i+1]=t;}
   if(order==2) for(int i=0;i<b.length;i+=4){byte t=b[i];b[i]=b[i+3];b[i+3]=t;t=b[i+1];b[i+1]=b[i+2];b[i+2]=t;}
   RomImporter.importRom(new ByteArrayInputStream(b),dest);
   if(!Arrays.equals(rom,Files.readAllBytes(dest.toPath())))throw new AssertionError("byte order");
  }
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(ZipOutputStream z=new ZipOutputStream(bytes)){z.putNextEntry(new ZipEntry("notes.txt"));z.write(1);z.closeEntry();z.putNextEntry(new ZipEntry("folder/Hero.z64"));z.write(rom);z.closeEntry();}
  RomImporter.importRom(new ByteArrayInputStream(bytes.toByteArray()),dest);
  for(byte[] bad:new byte[][]{new byte[]{1,2,3},Arrays.copyOf(rom,rom.length-4),new byte[rom.length]}) {
   try{RomImporter.importRom(new ByteArrayInputStream(bad),dest);throw new AssertionError("accepted invalid input");}catch(IOException expected){}
   if(!Arrays.equals(rom,Files.readAllBytes(dest.toPath())))throw new AssertionError("lost existing ROM");
  }
  if(new File(dir,"rom.z64.tmp").exists())throw new AssertionError("temporary file left over");
  System.out.println("ROM imports: raw, v64, n64, ZIP and invalid-input preservation passed");
 }
}
