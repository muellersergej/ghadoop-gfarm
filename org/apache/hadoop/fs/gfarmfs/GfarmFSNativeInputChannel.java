package org.apache.hadoop.fs.gfarmfs;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class GfarmFSNativeInputChannel implements ReadableByteChannel {
    private static final int DEFAULT_BUF_SIZE = 1 << 20;
    private ByteBuffer readBuffer;
    private long cPtr = 0; // for storing struct gfs_file

    private final static native long open(String path);
    private final static native int close(long cPtr);
    private final static native int read(long cPtr, ByteBuffer buf, int begin, int end);
    private final static native int seek(long cPtr, long offset);
    private final static native long tell(long cPtr);

    public GfarmFSNativeInputChannel(String path) {
        readBuffer = ByteBuffer.allocateDirect(DEFAULT_BUF_SIZE);
        readBuffer.flip();
        cPtr = open(path);
    }

    public boolean isOpen() {
        return (cPtr != 0);
    }

    public int read(ByteBuffer dst) throws IOException {
        if (cPtr == 0)
            throw new IOException("File closed");

        int r0 = dst.remaining();

        // While the dst buffer has space for more data, fill
        while(dst.hasRemaining()) {
            // Fill input buffer if it's empty
            if(!readBuffer.hasRemaining()) {
                readBuffer.clear();
                readDirect(readBuffer);
                readBuffer.flip();
                // If we failed to get anything, call that EOF
                if(!readBuffer.hasRemaining())
                    break;
            }

            // Save end of input buffer
            int lim = readBuffer.limit();
            
            // If dst buffer can't contain all of input buffer, limit
            // our copy size.
            if(dst.remaining() < readBuffer.remaining())
                readBuffer.limit(readBuffer.position() + dst.remaining());
            
            // Copy into dst buffer
            dst.put(readBuffer);

            // Restore end of input buffer marker (maybe changed
            // earlier)
            readBuffer.limit(lim);
        }

        // If we copied anything into the dst buffer (or if there was
        // no space available to do so), return the number of bytes
        // copied.  Otherwise return -1 to indicate EOF.
        int r1 = dst.remaining();
        if(r1 < r0 || r0 == 0)
            return r0 - r1;
        else
            return -1;
    }

    private void readDirect(ByteBuffer buf) throws IOException {
        if(!buf.isDirect())
            throw new IllegalArgumentException("need direct buffer");

        int pos = buf.position();
        int sz = read(cPtr, buf, pos, buf.limit());
        if(sz < 0)
            throw new IOException("readDirect failed");

        // System.out.println("Read via JNI: kfsFd: " + kfsFd + " amt: " + sz);
        buf.position(pos + sz);
    }

    public int seek(long offset) throws IOException {
        if (cPtr == 0)
            throw new IOException("File closed");
	
	readBuffer.clear();
	readBuffer.flip();

        return seek(cPtr, offset);
    }

    public long tell() throws IOException {
        if (cPtr == 0)
            throw new IOException("File closed");
        return tell(cPtr) - readBuffer.remaining();
    }

    public void close() throws IOException {
        if (cPtr != 0) {
            close(cPtr);
            cPtr = 0;
        }
        return;
    }

    protected void finalize() throws Throwable {
        if (isOpen()) close();
        super.finalize();
    }
}
