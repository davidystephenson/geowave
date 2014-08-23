---
layout: docs
title: Ingest Plugins
---

# Ingest plugin
A plugin framework (using SPI based injection) is provided, and several methods are supported out of the box.  
The plugin framework is created in geowave-ingest, and the supported types are in geowave-types.

First we will show how to build and use the built in types, and after that describe how to create a new plugin.


# Building ingest framework

This assumes you have already built the main project, if not first do that
*note - you should either edit the geowave/pom.xml file for the correct accumulo, hadoop, geotools, and geoserver versions, or supply command line parameters to override them.  

    $ git clone git@github.com:ngageoint/geowave.git
    $ cd geowave
	$ mvn clean install

now we can build the ingest framework with included types

    $ cd geowave-types
    $ mvn package -Pingest-singlejar

# Running ingest 
the ingest file is now packaged in target - 

    $ java -jar geowave-types-0.8.0-SNAPSHOT-ingest-tool.jar
	usage: <operation> <options>
	
	Operations:
	 -clear         clear ALL data from a GeoWave namespace, this actually
	                deletes Accumulo tables prefixed by the given namespace
	 -hdfsingest    copy supported files from local file system to HDFS and
	                ingest from HDFS
	 -hdfsstage     stage supported files in local file system to HDFS
	 -localingest   ingest supported files in local file system directly,
	                without using HDFS
	 -poststage     ingest supported files that already exist in HDFS
	
	Options are specific to operation choice. Use <operation> -h for help.


Okay, so what do these options mean? We will go into each in more detail, but basically the ingest plugin supports two types of ingest.  Local, and HDFS.  Which you use probably depends on the type and amount of data you need to load.  The framework handles abstracting across the two methods, so you only need to write the code once.

The hdfsingest option is actually just the hdfsstage and poststage methods chained together.   Using them separately is useful if you have a use case to ingest the same data multiple times from hdfs, or if you have a process that has already loaded the data into hdfs.

Each plugin in the ingest framework is required to identify the type of files it can ingest.  The coarse filter is based on file extension, and then a secondary filter can be created based on reading the contents of the file.  

The intent is to allow the user to point to a directory of files and for the framework to discover and ingest all possible types.  The user can explicitly select only certain plugins if they need to narrow this down.

Now for more details:

## Option: -clear


    $ java -jar geowave-types-0.8.0-SNAPSHOT-ingest-tool.jar -clear -h
	usage: -clear <options>
	
	Options:
	 -c,--clear               Clear ALL data stored with the same prefix as
	                          this namespace (optional; default is to append
	                          data to the namespace if it exists)
	 -h,--help                Display help
	 -i,--instance-id <arg>   The Accumulo instance ID
	 -index,--index <arg>     The type of index, either 'spatial' or
	                          'spatial-temporal' (optional; default is
	                          'spatial')
	 -l,--list                List the available ingest types
	 -n,--namespace <arg>     The table namespace (optional; default is no
	                          namespace)
	 -p,--password <arg>      The password for the user
	 -t,--types <arg>         Explicitly set the ingest type by name (or
	                          multiple comma-delimited types), if not set all
	                          available ingest types will be used
	 -u,--user <arg>          A valid Accumulo user ID
	 -v,--visibility <arg>    The visiblity of the data ingested (optional;
	                          default is 'public')
	 -z,--zookeepers <arg>    A comma-separated list of zookeeper servers that
	                          an Accumulo instance is using


This option will delete all geowave data stored in accumulo for the provided dataset.  Required parameters for this command are
 
 * zookeepers
 * accumulo instance id
 * accumulo user id
 * accumulo password
 * geowave namespace


## Option: -localingest 

This runs the ingest code  (parse to features, load features to accumulo) all locally.  


	$ java -jar geowave-types-0.8.0-SNAPSHOT-ingest-tool.jar  -localingest -h
	usage: -localingest <options>
	
	Options:
	 -b,--base <arg>          Base input file or directory to crawl with one
	                          of the supported ingest types
	 -c,--clear               Clear ALL data stored with the same prefix as
	                          this namespace (optional; default is to append
	                          data to the namespace if it exists)
	 -h,--help                Display help
	 -i,--instance-id <arg>   The Accumulo instance ID
	 -index,--index <arg>     The type of index, either 'spatial' or
	                          'spatial-temporal' (optional; default is
	                          'spatial')
	 -l,--list                List the available ingest types
	 -n,--namespace <arg>     The table namespace (optional; default is no
	                          namespace)
	 -p,--password <arg>      The password for the user
	 -t,--types <arg>         Explicitly set the ingest type by name (or
	                          multiple comma-delimited types), if not set all
	                          available ingest types will be used
	 -u,--user <arg>          A valid Accumulo user ID
	 -v,--visibility <arg>    The visiblity of the data ingested (optional;
	                          default is 'public')
	 -x,--extension <arg>     individual or comma-delimited set of file
	                          extensions to accept (optional)
	 -z,--zookeepers <arg>    A comma-separated list of zookeeper servers that
	                          an Accumulo instance is using


Most of the options should be pretty self explanitory.   The index type uses one of the two predefined index implementations.  You can perform temporal lookup/filtering with either, but the spatial-temporal includes indexing in the primary index - so will be more performant if spatial extents are commonly used when querying data.

Visibility is passed to accumulo as a string, so you should put whatever you want in here.

The namespace option is the geowave namespace;  this will be the prefix of the geowave tables in accumulo.  There are a few rules for this that derive from geotools/geoserver as well as accumulo.  To keep it simple if you only use alphabet characters and "_" (underscore) you will be fine.

The extensions argument allows you to override the plugin types, narrowing the scope of what is passed to the plugins

The types argument allows you to explicitly only use certain plugins.

Finally, the base directory is the root directory that will be scanned on the local system for files to ingest.  The process will scan all subdirectories under the provided directory.


## Option -hdfsingest

This option first copies the local files to an Avro record in hdfs, then executes the ingest process as a map-reduce job.  Data is ingested into geowave using the GeowaveInputFormat.   This is likely to be the fastest ingest method overall for data sets of any notable size (or if they have a large ingest/transform cost).  


	$ java -jar geowave-types-0.8.0-SNAPSHOT-ingest-tool.jar  -hdfsingest -h
	usage: -hdfsingest <options>
	
	Options:
	 -b,--base <arg>          Base input file or directory to crawl with one
	                          of the supported ingest types
	 -c,--clear               Clear ALL data stored with the same prefix as
	                          this namespace (optional; default is to append
	                          data to the namespace if it exists)
	 -h,--help                Display help
	 -hdfs <arg>              HDFS hostname and port in the format
	                          hostname:port
	 -hdfsbase <arg>          fully qualified path to the base directory in
	                          hdfs
	 -i,--instance-id <arg>   The Accumulo instance ID
	 -index,--index <arg>     The type of index, either 'spatial' or
	                          'spatial-temporal' (optional; default is
	                          'spatial')
	 -jobtracker <arg>        Hadoop job tracker hostname and port in the
	                          format hostname:port
	 -l,--list                List the available ingest types
	 -n,--namespace <arg>     The table namespace (optional; default is no
	                          namespace)
	 -p,--password <arg>      The password for the user
	 -t,--types <arg>         Explicitly set the ingest type by name (or
	                          multiple comma-delimited types), if not set all
	                          available ingest types will be used
	 -u,--user <arg>          A valid Accumulo user ID
	 -v,--visibility <arg>    The visiblity of the data ingested (optional;
	                          default is 'public')
	 -x,--extension <arg>     individual or comma-delimited set of file
	                          extensions to accept (optional)
	 -z,--zookeepers <arg>    A comma-separated list of zookeeper servers that
	                          an Accumulo instance is using



The options here are, for the most part, same as for localingest, with a few additions.

The hdfs argument should be the hostname and port, so something like  "hdfs-namenode.cluster1.com:8020".   

The hdfsbase argument is the root path in hdfs that will serve as the base for the stage location.  If the directory doesn't exist it will be created. The actual ingest file will be created in a "type" (plugin type - seen with the --list option) subdirectory under this base directory.

The jobtracker argument is the hostname and port for the jobtracker, so something like  mapreduce-namenode.cluster1.com:8021


The hdfsstage and poststage options will just be subsets of this comment; the first creating an avro file in hdfs, the second reading this avro file and ingesting into geowave

# Ingesting all the things

What can we ingest?

	$ java -jar geowave-types-0.8.0-SNAPSHOT-ingest-tool.jar  -localingest --list
	Available ingest types currently registered as plugins:
	
	tdrive:
	     files from Microsoft Research T-Drive trajectory data set
	
	geotools:
	     all file-based datastores supported within geotools
	
	geolife:
	     files from Microsoft Research GeoLife trajectory data set

	gpx:
	     xml files adhering to the schema of gps exchange format

Let's start out with the geotools datastore;  this wraps a bunch of geotools supported formats.   We will use the shapefile capability for our example here.

## Something recognizable

The naturalearthdata side has a few shapefile we can use use.  On the page
[http://www.naturalearthdata.com/downloads/50m-cultural-vectors/](http://www.naturalearthdata.com/downloads/50m-cultural-vectors/ "1:50m Cultural Vectors")

Let's download the Admin 0 - Countries shapefile: [http://www.naturalearthdata.com/http//www.naturalearthdata.com/download/50m/cultural/ne_50m_admin_0_countries.zip](http://www.naturalearthdata.com/http//www.naturalearthdata.com/download/50m/cultural/ne_50m_admin_0_countries.zip "Admin 0 - Countries")

Okay, let's ingest this.  I'll take some liberty with the file locations, but the process should be obvious

	$ mkdir ingest
    $ mv ne_50m_admin_0_countries.zip ingest/
	$ cd ingest
    $ unzip ne_50m_admin_0_countries.zip
	$ rm ne_50m_admin_0_countries.zip
    $ cd ..
    $ java -jar geowave-types-0.8.0-SNAPSHOT-ingest-tool.jar -localingest -b ./ingest -i instance -n adminborders -p pass -t geotools -u user -z zooo-1:2181
	

