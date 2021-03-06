package org.apache.hadoop.fs.gfarmfs;

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.fs.BlockLocation;

/**
 * A FileSystem backed by Gfarm
 *
 */
public class GfarmFileSystem extends FileSystem {

    private GfarmFSNative gfsImpl = null;
    private FileSystem localFs;
    private URI uri;
    private Path workingDir;
   
    public GfarmFileSystem() {
    }

    public void initialize(URI uri, Configuration conf) throws IOException {
        try {
            if (gfsImpl == null)
                gfsImpl = new GfarmFSNative();
            this.localFs = FileSystem.getLocal(conf);
            this.uri = URI.create(uri.getScheme() + "://" + "null");
	    String[] workingDirStr = getConf().getStrings("fs.gfarm.workingDir","/home/" + System.getProperty("user.name"));

	    this.workingDir = new Path(workingDirStr[0]).makeQualified(this);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Unable to initialize Gfarm file system");
            System.exit(-1);
        }
    }

    public void checkPath(Path path) {
	URI thisUri = this.getUri();
	URI thatUri = path.toUri();
	String thatAuthority = thatUri.getAuthority();
	if (thatUri.getScheme() != null
	    && thatUri.getScheme().equalsIgnoreCase(thisUri.getScheme()))
	    return;
	super.checkPath(path);
    }

    public Path makeQualified(Path path) {
	URI thisUri = this.getUri();
	URI thatUri = path.toUri();
        if (thatUri.getScheme() != null
            && thatUri.getScheme().equalsIgnoreCase(thisUri.getScheme()))
	    path = new Path(thisUri.getScheme(), null,
			    thatUri.getPath());
	return super.makeQualified(path);
    }

    public URI getUri() {
        return uri;
    }

    private Path makeAbsolute(Path path) {
        if (path.isAbsolute())
            return path;
        return new Path(workingDir, path);
    }

    public void setWorkingDirectory(Path new_dir) {
        workingDir = makeAbsolute(new_dir);
    }
    
    public Path getWorkingDirectory() {
        return workingDir;
    }
    
    private void replicateAndWait(Path path) {
    	System.out.println("Checking " + path.toString() + " for replication.");
    	String[] hostArray = getConf().getStrings("fs.gfarm.replication.destinations");
    	
    	if (hostArray != null && hostArray.length > 0) {
			// check if replication is necessary
			List<String> destinationsHosts = new ArrayList(Arrays.asList(hostArray));
			Path absolute = makeAbsolute(path);
        	String srep = absolute.toUri().getPath();
			
			Set<String> replicaHosts = new HashSet<String>(Arrays.asList(gfsImpl.getDataLocation(srep, 0, 0))); // start, len do not matter in gfarm
			replicaHosts.retainAll(destinationsHosts);
			if (replicaHosts.size() > 0) {
				// no replication needed. proceed.
				System.out.println("Replication not required for " +srep);
				return;
			} else {
				// replication to a local site is required. replicate to a random local node.
				
				int destIndex;
				try {
					SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
					destIndex = random.nextInt(destinationsHosts.size());
				} catch ( NoSuchAlgorithmException e ) {
	        		destIndex = 0;
	    		}
	    		long filesize = gfsImpl.getFileSize(srep);
				System.out.println("Replicating " +srep + " to "+ destinationsHosts.get(destIndex));
				long stime = System.nanoTime();
				gfsImpl.replicateTo(srep, destinationsHosts.get(destIndex));
				long deltatime = System.nanoTime() - stime;
				System.out.println(String.format("Replication took %f ms for %f Kbytes", (double) deltatime / 1000000.0f,  (double)filesize / 1024.0f));
						
				// wait until replication finishes
				//boolean replicationFinished = false;
				//while (!replicationFinished) {
				//	replicaHosts = new HashSet<String>(Arrays.asList(gfsImpl.getDataLocation(srep, 0, 0))); // start, len do not matter in gfarm
				//	replicaHosts.retainAll(destinationsHosts);
				//	replicationFinished = replicaHosts.size() > 0;
				//	try {
				//		System.out.println("Waiting for replication of " +srep);
				//		java.lang.Thread.sleep(50);
				//	} 
				//	catch ( InterruptedException e) {
				//		// ignore	
				//	}
				//}
				//System.out.println("Local replica for " + srep + " found on " + replicaHosts.toArray()[0].toString());
			}
		}
	}

    public FSDataInputStream open(Path path, int bufferSize)
        throws IOException {
        if (!exists(path))
            throw new IOException("File does not exist: " + path);
        
        // replicate to local site if desired
        boolean replicationDesired = getConf().getBoolean("fs.gfarm.replication.enabled", false);
        System.out.println("Replication is " + (replicationDesired ? "enabled." : "disabled."));
		if (replicationDesired) {
			replicateAndWait(path);
		}
		Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
        // TODO: bufferSize
        return new FSDataInputStream(new GfarmFSInputStream(gfsImpl, srep, statistics));
    }

    public FSDataOutputStream create(Path file,
                                     FsPermission permission,
                                     boolean overwrite,
                                     int bufferSize,
                                     short replication,
                                     long blockSize,
                                     Progressable progress)
        throws IOException {
        if (exists(file)) {
            if (overwrite) {
                delete(file);
            } else {
                throw new IOException("File already exists: " + file);
            }
        }

        Path parent = file.getParent();
        if (parent != null && !mkdirs(parent)) {
            throw new IOException("Mkdirs failed to create " + parent);
        }

        Path absolute = makeAbsolute(file);
        String srep = absolute.toUri().getPath();
        return new FSDataOutputStream(new GfarmFSOutputStream(srep), statistics);
    }

    public FSDataOutputStream append(Path f, int bufferSize,
				     Progressable progress) throws IOException {
	throw new IOException("Not supported");
    }

    public boolean rename(Path src, Path dst) throws IOException {
        Path absoluteS = makeAbsolute(src);
        String srepS = absoluteS.toUri().getPath();
        Path absoluteD = makeAbsolute(dst);
        String srepD = absoluteD.toUri().getPath();
        int e = gfsImpl.rename(srepS, srepD);
        if (e != 0)
            throw new IOException(gfsImpl.getErrorString(e));
        return true;
    }

    @Deprecated
    public boolean delete(Path path) throws IOException {
        return delete(path, true);
    }

    public long getFileSize(Path path) {
        Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
        return gfsImpl.getFileSize(srep);
    }

    public boolean delete(Path path, boolean recursive) throws IOException {
        int e;
        Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
        if (!exists(path))
            return false;
        if (gfsImpl.isFile(srep)){
            e = gfsImpl.remove(srep);
            if (e != 0)
                throw new IOException(gfsImpl.getErrorString(e));
            return true;
        }

        FileStatus[] dirEntries = listStatus(absolute);
        if ((!recursive) && (dirEntries != null) && (dirEntries.length != 0))
            throw new IOException("Directory " + path.toString() + " is not empty.");
        if (dirEntries != null) {
            for (int i = 0; i < dirEntries.length; i++)
                delete(new Path(absolute, dirEntries[i].getPath()), recursive);
        }
        e = gfsImpl.rmdir(srep);
        if (e != 0)
            throw new IOException(gfsImpl.getErrorString(e));
        return true;
    }

    public FileStatus[] listStatus(Path path) throws IOException {
        Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
        if (gfsImpl.isFile(srep))
            return new FileStatus[] { getFileStatus(path) } ;
	String[] entries = null;
	try {
	    entries = gfsImpl.readdir(srep);
	} catch ( Exception e) {
	    return null;
	}

        if (entries == null)
            return null;

        // gfsreaddir() returns "." and ".."; strip them before
        // passing back to hadoop fs.
        int numEntries = 0;
        for (int i = 0; i < entries.length; i++) {
            if ((entries[i].compareTo(".") == 0) || (entries[i].compareTo("..") == 0))
                continue;
            numEntries++;
        }

        FileStatus[] pathEntries = new FileStatus[numEntries];
        int j = 0;
        for (int i = 0; i < entries.length; i++) {
            if ((entries[i].compareTo(".") == 0) || (entries[i].compareTo("..") == 0))
                continue;

            pathEntries[j] = getFileStatus(new Path(path, entries[i]));
            j++;
        }
        return pathEntries;
    }

    public boolean mkdirs(Path path, FsPermission permission)
        throws IOException {
        Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
        String[] dirs = srep.split("/");
        if(dirs.length > 0){
            String dir = "";
            for (int i = 0; i < dirs.length; i++) {
                if (dirs[i].equals("")) continue;
                dir += dirs[i];
                System.out.println("mkdir = " + dir);
                //System.out.println("dir = " + dirs[i]);
                int e = gfsImpl.mkdir(dir, permission.toShort());
                if (e != 0)
                    throw new IOException(gfsImpl.getErrorString(e));
                dir += "/";
            }
        }
        return true;
    }

    public FileStatus getFileStatus(Path path) throws IOException {
        Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
        if (gfsImpl.isDirectory(srep)) {
            return new FileStatus(0, true, 1, 0, gfsImpl.getModificationTime(srep), 0,
								  new FsPermission(gfsImpl.getPermission(srep)),
								  gfsImpl.getOwner(srep),
								  gfsImpl.getGroup(srep),
                                  path.makeQualified(this));
        } else {
            return new FileStatus(gfsImpl.getFileSize(srep),
                                  false,
                                  (int)gfsImpl.getReplication(srep),
                                  getDefaultBlockSize(),
                                  gfsImpl.getModificationTime(srep),
								  0,
								  new FsPermission(gfsImpl.getPermission(srep)),
								  gfsImpl.getOwner(srep),
								  gfsImpl.getGroup(srep),								  
                                  path.makeQualified(this));
        }
    }
	
	public void setPermission(Path path, FsPermission permission) throws IOException {
        Path absolute = makeAbsolute(path);
        String srep = absolute.toUri().getPath();
		gfsImpl.setPermission(srep, permission.toShort());
	}	

    public BlockLocation[] getFileBlockLocations(FileStatus file, long start,
                                                 long len) throws IOException {

	if(file == null) {
	    return null;
	}
      
	String srep = makeAbsolute(file.getPath()).toUri().getPath();
	long blockSize = getDefaultBlockSize();
	String[] hints = gfsImpl.getDataLocation(srep, start, len);

	return new BlockLocation[] { new BlockLocation(null, hints, 0, len) };
    }

}
