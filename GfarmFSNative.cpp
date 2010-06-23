#include "org_apache_hadoop_fs_gfarmfs_GfarmFSNative.h"
#include "org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel.h"
#include "org_apache_hadoop_fs_gfarmfs_GfarmFSNativeInputChannel.h"

#include <gfarm/gfarm.h>

#include <cassert>
#include <iostream>
#include <string>
#include <vector>
using namespace std;

//-----------------------------------------------------------------------------
// Utilities
//
namespace
{
  inline string jstr2cppstr(JNIEnv *env, jstring src)
  {
    string cpps;
    char const * s = env->GetStringUTFChars(src, 0);
    cpps.assign(s);
    env->ReleaseStringUTFChars(src, s);
    return cpps;
  }

  inline void throw_io_exception(JNIEnv *env, const string& s){
    jclass clazz = env->FindClass("java/io/IOException");
    assert(clazz != 0);
    env->ThrowNew(clazz, s.c_str());
  }
  inline void throw_file_not_found_exception(JNIEnv *env, const string& s, const string& path){
    jclass clazz = env->FindClass("java/io/FileNotFoundException");
    assert(clazz != 0);
    string msg = s + ": " + path;
    env->ThrowNew(clazz, msg.c_str());    
  }
}

//-----------------------------------------------------------------------------
// class GfarmFSNative
//
JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_init
  (JNIEnv *env, jclass cls)
{
  return gfarm_initialize(0, NULL);
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_terminate
  (JNIEnv *env, jclass cls)
{
  return gfarm_terminate();
}


JNIEXPORT jstring JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_getErrorString
  (JNIEnv *env, jclass cls, jint e)
{
  return env->NewStringUTF(gfarm_error_string(e));
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_mkdir
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  string path = jstr2cppstr(env, jstrpath);
  gfarm_error_t e = gfs_mkdir(path.c_str(), 0755);
  if(e != GFARM_ERR_NO_ERROR && e != GFARM_ERR_ALREADY_EXISTS)
    goto err;
  return 0;

err:
  if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
    throw_file_not_found_exception(env, gfarm_error_string(e), path);
  else
    throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_rmdir
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  string path = jstr2cppstr(env, jstrpath);
  return gfs_rmdir(path.c_str());
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_rename
  (JNIEnv *env, jclass cls, jstring jstrsrc, jstring jstrdst)
{
  string src = jstr2cppstr(env, jstrsrc);
  string dst = jstr2cppstr(env, jstrdst);
  return gfs_rename(src.c_str(), dst.c_str());
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_remove
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  string path = jstr2cppstr(env, jstrpath);
  return gfs_remove(path.c_str());
}

JNIEXPORT jboolean JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_isDirectory
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  jboolean r;
  string path = jstr2cppstr(env, jstrpath);

  struct gfs_stat s;
  gfarm_error_t e = gfs_stat(path.c_str(), &s);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  r = (GFARM_S_ISDIR(s.st_mode)) ? JNI_TRUE : JNI_FALSE;
  gfs_stat_free(&s);
  return r;

err:
  return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_isFile
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  jboolean r;
  string path = jstr2cppstr(env, jstrpath);

  struct gfs_stat s;
  gfarm_error_t e = gfs_stat(path.c_str(), &s);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  r = (GFARM_S_ISREG(s.st_mode)) ? JNI_TRUE : JNI_FALSE;
  gfs_stat_free(&s);
  return r;  

err:
  return JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_getFileSize
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  jlong r;
  string path = jstr2cppstr(env, jstrpath);

  struct gfs_stat s;
  gfarm_error_t e = gfs_stat(path.c_str(), &s);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  r = s.st_size;
  gfs_stat_free(&s);
  return r;

err:
  if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
    throw_file_not_found_exception(env, gfarm_error_string(e), path);
  else
    throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_getModificationTime
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  jlong r;
  string path = jstr2cppstr(env, jstrpath);

  struct gfs_stat s;
  gfarm_error_t e = gfs_stat(path.c_str(), &s);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  r = s.st_mtimespec.tv_sec * 1000; // TODO: Is this ok?
  gfs_stat_free(&s);
  return r;

err:
  if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
    throw_file_not_found_exception(env, gfarm_error_string(e), path);
  else
    throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_getReplication
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  jlong r;
  string path = jstr2cppstr(env, jstrpath);

  struct gfs_stat s;
  gfarm_error_t e = gfs_stat(path.c_str(), &s);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  r = s.st_ncopy;
  gfs_stat_free(&s);
  return r;

err:
  if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
    throw_file_not_found_exception(env, gfarm_error_string(e), path);
  else
    throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jobjectArray JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_readdir
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  jobjectArray jentries;
  string path = jstr2cppstr(env, jstrpath);
  
  vector<string> v;
  gfarm_error_t e;
  GFS_Dir dir;
  struct gfs_dirent *entry;
  e = gfs_opendir(path.c_str(), &dir);
  if(e != GFARM_ERR_NO_ERROR){
    if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
      goto err_not_found;
    else
      goto err;
  }
  while ((e = gfs_readdir(dir, &entry)) == GFARM_ERR_NO_ERROR && entry != NULL)
    v.push_back(entry->d_name);
  gfs_closedir(dir);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;

  // Construct String[] from vector<string>
  jentries = env->NewObjectArray(v.size(), env->FindClass("Ljava/lang/String;"), NULL);
  for(unsigned int i=0; i<v.size(); i++){
    jstring s = env->NewStringUTF(v[i].c_str());
    env->SetObjectArrayElement(jentries, i, s);
    env->DeleteLocalRef(s);
  }
  return jentries;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return NULL;

err_not_found:
  throw_file_not_found_exception(env, gfarm_error_string(e), path);
  return NULL;
}

JNIEXPORT jobjectArray JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNative_getDataLocation
(JNIEnv *env, jclass cls, jstring jstrpath, jlong start, jlong len)
{
  string path = jstr2cppstr(env, jstrpath);

  gfarm_error_t e;
  int n;
  char **hosts;
  jclass jstrClass = env->FindClass("Ljava/lang/String;");
  jstring s;

  e = gfs_replica_list_by_name(path.c_str(), &n, &hosts);

  jobjectArray jentries = env->NewObjectArray(n, jstrClass, NULL);

  if (e == GFARM_ERR_NO_ERROR) {
    for (int i = 0; i < n; i++) {
      s = env->NewStringUTF(hosts[i]);
      env->SetObjectArrayElement(jentries, i, s);
      env->DeleteLocalRef(s);
    }
  }

  for (int i = 0; i < n; ++i)
    free(hosts[i]);
  free(hosts);

  return jentries;
}

//-----------------------------------------------------------------------------
// class GfarmFSNativeOutputChannel
//
JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_open
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  string path = jstr2cppstr(env, jstrpath);
  GFS_File f = NULL;
  gfarm_error_t e = gfs_pio_create(path.c_str(), GFARM_FILE_WRONLY | GFARM_FILE_TRUNC, 0644, &f);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  return (jlong)f;

err:
  if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
    throw_file_not_found_exception(env, gfarm_error_string(e), path);
  else
    throw_io_exception(env, gfarm_error_string(e));
  if(f != NULL) gfs_pio_close(f);
  return 0;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_close
  (JNIEnv *env, jclass cls, jlong jptr)
{
  GFS_File f = (GFS_File)jptr;
  if(f == NULL) return 0;
  gfarm_error_t e = gfs_pio_close(f);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  return 0;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_write
  (JNIEnv *env, jclass cls, jlong jptr, jobject jbuf, jint begin, jint end)
{
  GFS_File f = (GFS_File)jptr;
  if(jbuf == NULL)
    return 0;

  void * addr = env->GetDirectBufferAddress(jbuf);
  jlong cap = env->GetDirectBufferCapacity(jbuf);
  if(addr == NULL || cap < 0)
    return 0;
  if(begin < 0 || end > cap || begin >= end)
    return 0;

  addr = (void *)(uintptr_t(addr) + begin);
  int ret = 0;
  gfarm_error_t e = gfs_pio_write(f, addr, (end - begin), &ret);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return ret;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_flush
  (JNIEnv *env, jclass cls, jlong jptr)
{
  gfarm_error_t e;
  GFS_File f = (GFS_File)jptr;
  if(f == NULL) return 0;
  e = gfs_pio_flush(f);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return 0;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_sync
  (JNIEnv *env, jclass cls, jlong jptr)
{
  gfarm_error_t e;
  GFS_File f = (GFS_File)jptr;
  if(f == NULL) return 0;
  e = gfs_pio_flush(f);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  e = gfs_pio_sync(f);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return 0;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_seek
  (JNIEnv *env, jclass cls, jlong jptr, jlong joffset)
{
  GFS_File f = (GFS_File)jptr;
  gfarm_error_t e = gfs_pio_seek(f, joffset, GFARM_SEEK_SET /* from beginning */, NULL);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return 0;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeOutputChannel_tell
  (JNIEnv *env, jclass cls, jlong jptr)
{
  GFS_File f = (GFS_File)jptr;
  gfarm_off_t cur;
  gfarm_error_t e = gfs_pio_seek(f, 0, GFARM_SEEK_CUR /* from current */, &cur);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return cur;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

//-----------------------------------------------------------------------------
// class GfarmFSNativeInputChannel
//
JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeInputChannel_open
  (JNIEnv *env, jclass cls, jstring jstrpath)
{
  string path = jstr2cppstr(env, jstrpath);
  GFS_File f = NULL;
  gfarm_error_t e = gfs_pio_open(path.c_str(), GFARM_FILE_RDONLY, &f);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  return (jlong)f;

err:
  if(e == GFARM_ERR_NO_SUCH_FILE_OR_DIRECTORY)
    throw_file_not_found_exception(env, gfarm_error_string(e), path);
  else
    throw_io_exception(env, gfarm_error_string(e));
  if(f != NULL) gfs_pio_close(f);
  return 0;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeInputChannel_close
  (JNIEnv *env, jclass cls, jlong jptr)
{
  GFS_File f = (GFS_File)jptr;
  if(f == NULL) return 0;
  gfarm_error_t e = gfs_pio_close(f);
  if(e != GFARM_ERR_NO_ERROR)
    goto err;
  return 0;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeInputChannel_read
  (JNIEnv *env, jclass cls, jlong jptr, jobject jbuf, jint begin, jint end)
{
  GFS_File f = (GFS_File)jptr;
  if(jbuf == NULL)
    return 0;

  void * addr = env->GetDirectBufferAddress(jbuf);
  jlong cap = env->GetDirectBufferCapacity(jbuf);
  if(addr == NULL || cap < 0)
    return 0;
  if(begin < 0 || end > cap || begin >= end)
    return 0;

  addr = (void *)(uintptr_t(addr) + begin);
  int ret = 0;
  gfarm_error_t e = gfs_pio_read(f, addr, (end - begin), &ret);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return ret;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}


JNIEXPORT jint JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeInputChannel_seek
  (JNIEnv *env, jclass cls, jlong jptr, jlong joffset)
{
  GFS_File f = (GFS_File)jptr;
  gfarm_error_t e = gfs_pio_seek(f, joffset, GFARM_SEEK_SET /* from beginning */, NULL);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return 0;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}

JNIEXPORT jlong JNICALL Java_org_apache_hadoop_fs_gfarmfs_GfarmFSNativeInputChannel_tell
  (JNIEnv *env, jclass cls, jlong jptr)
{
  GFS_File f = (GFS_File)jptr;
  gfarm_off_t cur;
  gfarm_error_t e = gfs_pio_seek(f, 0, GFARM_SEEK_CUR /* from current */, &cur);
  if(e != GFARM_ERR_NO_ERROR) goto err;
  return cur;

err:
  throw_io_exception(env, gfarm_error_string(e));
  return -1;
}
