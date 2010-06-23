package org.apache.hadoop.fs.gfarmfs;

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.util.Progressable;

class GfarmFSOutputStream extends OutputStream {
    GfarmFSNativeOutputChannel channel = null;

    public GfarmFSOutputStream(String path) throws IOException {
        channel = new GfarmFSNativeOutputChannel(path);
    }

    public long getPos() throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        return channel.tell();
    }

    public void write(int v) throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        byte[] b = new byte[1];
        b[0] = (byte) v;
        write(b, 0, 1);
    }

    public void write(byte b[], int off, int len) throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        channel.write(ByteBuffer.wrap(b, off, len));
    }

    public void flush() throws IOException {
        if (channel == null)
            throw new IOException("File closed");
        channel.flush();
    }
    
    public void sync() throws IOException {
	if (channel == null)
	    throw new IOException("File closed");
	channel.sync();
    }

    public synchronized void close() throws IOException {
        if (channel == null)
            return;

        channel.close();
        channel = null;
    }
}
