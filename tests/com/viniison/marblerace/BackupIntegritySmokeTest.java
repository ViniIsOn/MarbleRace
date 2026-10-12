package com.viniison.marblerace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs without Android and rejects truncated/corrupt backup files. */
public final class BackupIntegritySmokeTest {
    private static void checkValid(File file)throws Exception {
        BackupIntegrity.verify(file);
    }
    private static void expectRejected(File file,String description)throws Exception {
        boolean rejected=false;
        try{BackupIntegrity.verify(file);}
        catch(IOException failure){rejected=true;}
        if(!rejected)throw new AssertionError("Accepted "+description);
    }
    public static void main(String[] args)throws Exception {
        File temp=Files.createTempDirectory("marble-backup-test-").toFile();
        File complete=new File(temp,"valid.zip");
        File truncated=new File(temp,"truncated.zip");
        File broken=new File(temp,"damaged.zip");
        try{
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(complete))){
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write("{\"format\":\"MarbleLab-studio-zip\",\"version\":2}".getBytes("UTF-8"));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("audio/0.bin"));
                byte[] music=new byte[24*1024];
                for(int i=0;i<music.length;i++)music[i]=(byte)(i*19+3);
                zip.write(music);zip.closeEntry();
            }
            checkValid(complete);
            byte[] bytes=Files.readAllBytes(complete.toPath());
            byte[] shortBytes=new byte[bytes.length-12];
            System.arraycopy(bytes,0,shortBytes,0,shortBytes.length);
            Files.write(truncated.toPath(),shortBytes);
            expectRejected(truncated,"truncated ZIP central directory");
            bytes[bytes.length/2]^=0x5a;
            Files.write(broken.toPath(),bytes);
            expectRejected(broken,"corrupted ZIP CRC or compressed content");
            System.out.println("PASS: complete backup accepted; truncated and corrupt ZIPs rejected");
        }finally{
            complete.delete();truncated.delete();broken.delete();temp.delete();
        }
    }
}
