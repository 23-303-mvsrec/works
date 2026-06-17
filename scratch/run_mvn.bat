@echo off
set "MAVEN_USER_HOME=E:\works\.m2"
set "MAVEN_OPTS=-Djava.io.tmpdir=E:\works\target -Dstyle.color=never"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
"C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe" ^
  -Djava.io.tmpdir=E:\works\target ^
  -classpath "E:\works\.m2\wrapper\dists\apache-maven-3.9.15-bin\4rlcemksed9vjmkvgss0jpc4po\apache-maven-3.9.15\boot\plexus-classworlds-2.9.0.jar" ^
  "-Dclassworlds.conf=E:\works\.m2\wrapper\dists\apache-maven-3.9.15-bin\4rlcemksed9vjmkvgss0jpc4po\apache-maven-3.9.15\bin\m2.conf" ^
  "-Dmaven.home=E:\works\.m2\wrapper\dists\apache-maven-3.9.15-bin\4rlcemksed9vjmkvgss0jpc4po\apache-maven-3.9.15" ^
  "-Dlibrary.jansi.path=E:\works\.m2\wrapper\dists\apache-maven-3.9.15-bin\4rlcemksed9vjmkvgss0jpc4po\apache-maven-3.9.15\lib\jansi-native" ^
  "-Dmaven.multiModuleProjectDirectory=E:\works" ^
  org.codehaus.plexus.classworlds.launcher.Launcher ^
  %*
