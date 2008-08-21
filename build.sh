#!/usr/bin/env bash

# Please set these variables
export JAVA_HOME=/usr/lib/jvm/java-6-sun/
export HADOOP_HOME=/home/kzk/hadoop-gfarm/lib/hadoop-0.17.1/

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
