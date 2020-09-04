#!/bin/sh

# In order to generate the Jooq classes either copy and paste the code below or run this through "sh" like:
# sh ./src/main/resources/README.txt

# Load the DDL schema in MySQL
DDL_DIR="`dirname \"$0\"`"
mysql -u root -proot killbill < "${DDL_DIR}"/ddl.sql

# Download the required jars
MVN_DOWNLOAD="mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.2:get -DremoteRepositories=https://repo.maven.apache.org/maven2"
JOOQ_VERSION=3.13.4
$MVN_DOWNLOAD -Dartifact=org.jooq:jooq:$JOOQ_VERSION:jar
$MVN_DOWNLOAD -Dartifact=org.jooq:jooq-meta:$JOOQ_VERSION:jar
$MVN_DOWNLOAD -Dartifact=org.jooq:jooq-codegen:$JOOQ_VERSION:jar
REACTIVE_STREAM_VERSION=1.0.2
$MVN_DOWNLOAD -Dartifact=org.reactivestreams:reactive-streams:$REACTIVE_STREAM_VERSION:jar
MYSQL_VERSION=8.0.21
$MVN_DOWNLOAD -Dartifact=mysql:mysql-connector-java:$MYSQL_VERSION:jar

M2_REPOS=~/.m2/repository
JOOQ="$M2_REPOS/org/jooq"
MYSQL="$M2_REPOS/mysql/mysql-connector-java/$MYSQL_VERSION/mysql-connector-java-$MYSQL_VERSION.jar"
REACTIVE_STREAMS="$M2_REPOS/org/reactivestreams/reactive-streams/$REACTIVE_STREAM_VERSION/reactive-streams-$REACTIVE_STREAM_VERSION.jar"
JARS="$JOOQ/jooq/$JOOQ_VERSION/jooq-$JOOQ_VERSION.jar:$JOOQ/jooq-meta/$JOOQ_VERSION/jooq-meta-$JOOQ_VERSION.jar:$JOOQ/jooq-codegen/$JOOQ_VERSION/jooq-codegen-$JOOQ_VERSION.jar:$REACTIVE_STREAMS:$MYSQL:.";

# Run jOOQ's generation tool
java -cp $JARS org.jooq.codegen.GenerationTool ./src/main/resources/gen.xml
