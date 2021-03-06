package jvmdbbroker.core

import java.io._
import sun.misc.Signal;
import sun.misc.SignalHandler;
import scala.xml._

object Main extends Logging with SignalHandler {

    var router : Router = _
    var mainThread : Thread = _
    var shutdown = false
    var selfCheckServer : SelfCheckServer = _

    def handle(signal:Signal) {
        if(shutdown) return
        println("# signalHandler: " + signal.getName())
        mainThread.interrupt()
    }

    def main(args:Array[String]) {

        System.setProperty("org.terracotta.quartz.skipUpdateCheck","true");

        mainThread = Thread.currentThread

        Signal.handle(new Signal("TERM"), this);
        Signal.handle(new Signal("INT"), this);

        val rootDir = "."

        val stopFile = rootDir+File.separator+"cmd_stop"

        val t1 = System.currentTimeMillis

        val in = new InputStreamReader(new FileInputStream(rootDir+File.separator+"config.xml"),"UTF-8")
        val cfgXml = XML.load(in)
        in.close()

        val t = System.getenv("runninginide")
        if( t != null && t == "yes" ) {
            log.info("running in ide, skip compiling")
        } else {
            val compiler = new FlowCompiler(rootDir)
            val ok = compiler.compile()
            if(!ok) {
                log.error("compile failed")
                return;
            }
        }

        router = new Router(rootDir)

        val selfCheckPort = (cfgXml \ "CohPort").text.toInt

        if( selfCheckPort > 0 ) {
            selfCheckServer = new SelfCheckServer(selfCheckPort,router)
        }

        val t2 = System.currentTimeMillis
        log.info("scalabpe started, ts=%s[ms]".format(t2-t1))

        new File(stopFile).delete()

        while( !shutdown ) {
            try {
                Thread.sleep(1000)

                val f = new File(stopFile)
                if( f.exists ) {
                    f.delete()
                    shutdown = true
                }

            } catch {
                case e:Exception =>
                    shutdown = true
            }
        }

        if( selfCheckServer != null )
            selfCheckServer.close()

        router.close()

        log.asInstanceOf[ch.qos.logback.classic.Logger].getLoggerContext().stop
    }

}

