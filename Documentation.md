
# Introduction #

This page is the documentation page of QuiXProc

# Installation #
## Simple Way ##
Go to the [QuiXProc website](http://www.quixproc.com/quix/home) and [register](http://www.quixproc.com/quix/info?type=register) to have access to Innovimax's compilation Services. Note that the QuiXProc compilation services (QuiXProc Pro and QuiXProc Free) are improved version of QuiXProc Open.


## Detailled Way ##
![http://quixproc.googlecode.com/svn/wiki/QuiXProc.png](http://quixproc.googlecode.com/svn/wiki/QuiXProc.png)

To have access to **QuiXProc Open**:
  * First check out this source code which is **Java 1.6+** compliant :http://code.google.com/p/quixproc/source/checkout
  * check out the source code of **QuiXPath 1.1**:  http://code.google.com/p/quixpath/source/browse/#svn%2Ftags%2F1.1.0
  * check out the source code of **QuiXDM 1.1**:  http://code.google.com/p/quixdm/source/browse/#svn%2Ftags%2F1.1.0
  * check out the source code of **FXP 1.1**: http://gforge.inria.fr/scm/viewvc.php/branches/VERSION_1_1_0/?root=evoxs
  * check out **Saxon 9.4**: http://saxon.sourceforge.net/
  * check out all the dependencies:
    * Apache commons:
      * httpclient (3.1) http://archive.apache.org/dist/httpcomponents/commons-httpclient/binary/
      * logging : http://commons.apache.org/logging/download_logging.cgi
      * io (1.3.1) : http://archive.apache.org/dist/commons/io/binaries/
    * Apache FOP : http://mirrors.ircam.fr/pub/apache//xmlgraphics/fop/binaries/
    * Apache Avalon Framework (4.2.0) : http://archive.apache.org/dist/avalon/avalon-framework/v4.2.0/
    * Apache XMLGraphics commons : http://mirror.speednetwork.de/apache//xmlgraphics/commons/binaries/
    * ISO-Relax: http://sourceforge.net/projects/iso-relax/
    * Jing: http://code.google.com/p/jing-trang/
    * Metadata Extractor (2.3.1) : http://code.google.com/p/metadata-extractor/
    * MSV (Multi Schema Validator): http://java.net/downloads/msv/releases/
    * TagSoup: http://home.ccil.org/~cowan/XML/tagsoup/
    * XMLUnit: http://xmlunit.sourceforge.net/

You can also use the build.xml that has been contributed so far : [issue 3](https://code.google.com/p/quixproc/issues/detail?id=3)

Once everything compiled then you can launch any Class of the `innovimax.quixproc.codex.drivers.open.*` package
`QuiXProcB` and `RunTestB` (respectively `QuiXProcC` and `RunTestC`) permits to launch QuiXProc and the Testsuite with different parameters.

# Details #

## Command line Options ##
  * `-rI` / `--run-it`: to execute the pipeline in Streaming if possible and in DOM if not
  * `-rS` / `--stream-all`: to execute the pipeline in Streaming only. If not possible it will fail
  * `-rD` / `--dom-all`: to execute the pipeline in DOM only (ignore the streaming)
  * `-tN` / `--trace-no`: to desactivate the trace
  * `-tW` / `--trace-wait`: to active only the trace when the processor is waiting
  * `-tA` / `--trace-all`: to active full trace
  * `-D` / `--debug`: activate the debug mode
  * `-a` /  `--schema-aware`: Turn on schema-aware processing
  * `-b` / `--binding prefix=uri`: Specify namespace binding
  * `-c` / `--config configfile`:              Specify a particular configuration file
  * `-d` / `--data-input port=uri`:            Bind the specified input port to data
  * `-D` / `--debug`:               	       Turn on debugging
  * `-E` / `--entity-resolver className`:      Specify a resolver class for URI resolution
  * `-G` / `--log-style logstyle`:             Specify the default style for p:log output
  * `-i` / `--input port=uri`:      	       Bind the specified input port
  * `-l` / `--library library.xpl`:            Load the specified library
  * `-o` / `--output port=uri`:                Bind the specified output port
  * `-p` / `--with-param [port@]param=value`:  Specify a parameter
  * `-S` / `--safe-mode`:                      Request "safe" execution
  * `-s` / `--step-name stepname`:             Run the step named 'stepname'
  * `-U` / `--uri-resolver className`:         Specify a resolver class for URI resolution

## Examples ##
GettingStarted

## Testsuite ##
http://tests.xproc.org