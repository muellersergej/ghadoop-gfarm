import java.io.*;

import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.gfarmfs.*;

public class Test {
    public static void main(String[] args){
        try {
            Configuration conf = new Configuration();
            conf.set("fs.gfarmfs.impl", "org.apache.hadoop.fs.gfarmfs.GfarmFileSystem");

            FileSystem fs = FileSystem.getNamed("gfarmfs:///", conf);
            //fs.mkdirs(new Path("hogehoge"));
            //fs.rename(new Path("hogehoge"), new Path("fufufu"));
            //fs.mkdirs(new Path("fufufu/fofofo"));
            //fs.delete(new Path("fufufu"), true);
            //((GfarmFileSystem)fs).getFileSize(new Path("fofoffofoofdsafa"));
            //fs.getFileStatus(new Path("fufufu"));
            //fs.getFileStatus(new Path("fufufu"));
            //fs.mkdirs(new Path("hoge/fuga/fuga"));
            fs.copyFromLocalFile(new Path("./test.c"), new Path("gfarmfs:///test.c"));
            fs.copyToLocalFile(new Path("gfarmfs:///test.c"), new Path("./test2.c"));
        } catch (IOException e) {
                e.printStackTrace();
        }
    }
}
