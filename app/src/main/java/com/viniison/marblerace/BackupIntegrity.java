package com.viniison.marblerace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** ZIP verification independent of Android. Rejects truncation, corrupt
 * entries, bad CRC and oversized archives before modifying user data.
 */
final class BackupIntegrity {
    static final int MAX_ENTRIES=301; // manifest + up to 300 imported songs
    static final long MAX_META_BYTES=18L*1024*1024;
    static final long MAX_AUDIO_BYTES=64L*1024*1024;
    static final long MAX_ARCHIVE_BYTES=1600L*1024*1024+MAX_META_BYTES+1024*1024;
    private BackupIntegrity(){}
    static void verify(File archive)throws IOException {
        if(archive==null||!archive.isFile()||archive.length()<22)
            throw new IOException("O ZIP está vazio ou incompleto. Crie outro backup pelo menu SALVAR.");
        if(archive.length()>MAX_ARCHIVE_BYTES)
            throw new IOException("Esse ZIP excede o limite seguro do MarbleLab.");
        try(ZipFile zip=new ZipFile(archive)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            int count=0;
            long total=0;
            boolean manifestFound=false;
            byte[] buffer=new byte[32768];
            while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();
                if(++count>MAX_ENTRIES)throw new IOException("O ZIP contém arquivos demais.");
                String name=entry.getName();
                boolean manifest="manifest.json".equals(name);
                if(count==1&&!manifest)
                    throw new IOException("ZIP inválido: falta o manifesto na primeira posição.");
                if(entry.isDirectory()||(!manifest&&!name.matches("audio/[0-9]+\\.bin")))
                    throw new IOException("Arquivo inesperado dentro do ZIP: "+name);
                if(manifest) {
                    if(manifestFound)throw new IOException("Manifesto duplicado.");
                    manifestFound=true;
                }
                long max=manifest?MAX_META_BYTES:MAX_AUDIO_BYTES;
                if(entry.getSize()>max || entry.getSize()==0)
                    throw new IOException("Arquivo interno ausente ou muito grande: "+name);
                CRC32 checksum=new CRC32();
                long read=0;
                try(InputStream in=zip.getInputStream(entry)){
                    int n;
                    while((n=in.read(buffer))!=-1){
                        read+=n;
                        if(read>max)throw new IOException("Limite excedido dentro de "+name);
                        checksum.update(buffer,0,n);
                    }
                }
                if(read==0)
                    throw new IOException("Arquivo vazio dentro do backup: "+name);
                if(entry.getSize()>=0 && entry.getSize()!=read)
                    throw new IOException("Tamanho incorreto: "+name);
                if(entry.getCrc()<0||checksum.getValue()!=entry.getCrc())
                    throw new IOException("Erro de integridade da música ou dos personagens: "+name);
                total+=read;
                if(total>MAX_ARCHIVE_BYTES)
                    throw new IOException("O backup é grande demais.");
            }
            if(!manifestFound)
                throw new IOException("O ZIP não contém os dados dos personagens.");
        }catch(ZipException damaged){
            throw new IOException("O ZIP está incompleto ou corrompido. Não restaure esse arquivo; crie outro pelo menu SALVAR.",damaged);
        }
    }
}
