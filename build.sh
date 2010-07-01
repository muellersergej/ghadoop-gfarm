#!/usr/bin/env bash

# Please set these variables
export JAVA_HOME=/usr/local/jdk
export HADOOP_HOME=/home/mikami/hadoop/hadoop-0.20.2
export GFARM_HOME=/data/local/gfarm_v2
export CPLUS_INCLUDE_PATH=${GFARM_HOME}/include

# Include jar files
export CLASSPATH=${CLASSPATH}
for f in $HADOOP_HOME/hadoop-*-core.jar; do
  CLASSPATH=${CLASSPATH}:$f;
done
for f in $HADOOP_HOME/lib/*.jar; do
  CLASSPATH=${CLASSPATH}:$f;
done
for f in $HADOOP_HOME/lib/jetty-ext/*.jar; do
  CLASSPATH=${CLASSPATH}:$f;
done

make

cp hadoop-gfarm.jar ${HADOOP_HOME}/lib/
cp libGfarmFSNative.so ${HADOOP_HOME}/lib/native/Linux-amd64-64/
cp libGfarmFSNative.so ${HADOOP_HOME}/lib/native/Linux-i386-32/