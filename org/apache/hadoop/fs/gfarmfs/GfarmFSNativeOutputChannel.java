package org.apache.hadoop.fs.gfarmfs;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class GfarmFSNativeOutputChannel implements WritableByteChannel {
    private static final int DEFAULT_BUF_SIZE = 1 << 20;
    private ByteBuffer writeBuffer;
    private long cPtr = 0; // for storing struct gfs_file

    private final static native long open(String path);
    private final static native int close(long cPtr);
    private final static native int write(long cPtr, ByteBuffer buf, int begin, int end);
    private final static native int flush(long cPtr);
    private final static native int sync(long cPtr);
    private final static native int seek(long cPtr, long offset);
    private final static native long tell(long cPtr);

    public GfarmFSNativeOutputChannel(String path) throws IOException {
        writeBuffer = ByteBuffer.allocateDirect(DEFAULT_BUF_SIZE);
        writeBuffer.clear();
        cPtr = open(path);
    }

    public boolean isOpen()
    {
        return (cPtr != 0);
    }

    public int write(ByteBuffer src) throws IOException {
        if (cPtr == 0)
            throw new IOException("File closed");

        int r0 = src.remaining();
        // While the src buffer has data, copy it in and flush
        while (src.hasRemaining()) {
            if (writeBuffer.remaining() == 0) {
                writeBuffer.flip();
                writeDirect(writeBuffer);
            }
            // Save end of input buffer
            int lim = src.limit();
            // Copy in as much data we have space
            if (writeBuffer.remaining() < src.remaining())
                src.limit(src.position() + writeBuffer.remaining());
            writeBuffer.put(src);
            // restore the limit to what it was
            src.limit(lim);
        }
        int r1 = src.remaining();
        return r0 - r1;
    }

    private void writeDirect(ByteBuffer buf) throws IOException
    {
        if(!buf.isDirect())
            throw new IllegalArgumentException("need direct buffer");

        int pos = buf.position();
        int last = buf.limit();
        if (last - pos == 0)
            return;

        int sz = write(cPtr, buf, pos, last);
        if(sz < 0)
            throw new IOException("writeDirect failed");

        // System.out.println("Wrote via JNI: kfsFd: " + kfsFd + " amt: " + sz);
        if (sz == last) {
            buf.clear();
            return;
        }
        if (sz == 0) {
            return;
        }
        // System.out.println("Compacting on kfsfd: " + kfsFd);

        // we wrote less than what is available.  so, shift things
        // over to reflect what was written out.
        ByteBuffer temp = ByteBuffer.allocateDirect(DEFAULT_BUF_SIZE);
        temp.put(buf);
        temp.flip();
        buf.clear();
        buf.put(temp);
    }

    public int flush() throws IOException
    {
        if (cPtr == 0)
            throw new IOException("File closed");

        // flush everything
        writeBuffer.flip();
        writeDirect(writeBuffer);

        return flush(cPtr);
    }

    public int sync() throws IOException
    {
	if (cPtr == 0)
	    throw new IOException("File closed");
	
	// flush everything
	writeBuffer.flip();
	writeDirect(writeBuffer);
	
	return sync(cPtr);
    }

    public int seek(long offset) throws IOException
    {
        if (cPtr == 0)
            throw new IOException("File closed");
        flush();
        return seek(cPtr, offset);
    }

    public long tell() throws IOException
    {
        if (cPtr == 0)
            throw new IOException("File closed");
        return tell(cPtr) + writeBuffer.remaining();
    }

    public void close() throws IOException
    {
        if (cPtr != 0) {
	    flush();
            close(cPtr);
            cPtr = 0;
        }

        return;
    }

    protected void finalize() throws Throwable
    {
        if (isOpen()) close();
        super.finalize();
    }
}
