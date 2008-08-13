package org.apache.hadoop.fs.gfarmfs;

import java.io.*;
import java.nio.ByteBuffer;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FSInputStream;

class GfarmFSInputStream extends FSInputStream {
    private GfarmFSNativeInputChannel channel = null;
    private long fileSize = 0;
    private FileSystem.Statistics statistics = null;

    public GfarmFSInputStream(GfarmFSNative gfsImpl, String path, FileSystem.Statistics stat) throws IOException {
        channel = new GfarmFSNativeInputChannel(path);
        if(channel != null)
            fileSize = gfsImpl.getFileSize(path);
        statistics = stat;
    }


    public long getPos() throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        return channel.tell();
    }

    public synchronized int available() throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        return (int) (this.fileSize - getPos());
    }

    public synchronized void seek(long pos) throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        channel.seek(pos);
    }

    public synchronized boolean seekToNewSource(long targetPos) throws IOException {
        return false;
    }

    public synchronized int read() throws IOException {
        if (channel == null)
            throw new IOException("File closed");

        byte b[] = new byte[1];
        int res = read(b, 0, 1);
        if (res == 1) {
            if (statistics != null)
                statistics.incrementBytesRead(1);
            return ((int) (b[0] & 0xff));
        }
        return -1;
    }
    
    public synchronized int read(byte b[], int off, int len) throws IOException {
        if (channel == null)
            throw new IOException("File closed");

        int res = channel.read(ByteBuffer.wrap(b, off, len));
        // Use -1 to signify EOF
        if (res == 0)
            return -1;
        if (statistics != null)
            statistics.incrementBytesRead(res);
        return res;
    }

    public synchronized void close() throws IOException {
        if (channel == null)
            return;
        channel.close();
        channel = null;
    }

    // TODO: Unsupported operations
    public boolean markSupported() {
        return false;
    }
    public void mark(int readLimit) {
        // Do nothing
    }
    public void reset() throws IOException {
        throw new IOException("Mark not supported");
    }
}
