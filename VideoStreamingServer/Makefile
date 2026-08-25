
OPENCV_ROOT := C:/Userspace/OpenCV/opencv
OPENCV_JAR := $(OPENCV_ROOT)/build/java/opencv-4120.jar
OPENCV_LIB := $(OPENCV_ROOT)/build/java/x64

ifeq ($(USER),mh96160)
ifeq ($(CYGWIN_ENV),1)
else
# regular debian install (in my case: MX linux based on Debian 13 'trixie')	
OPENCV_JAR := /usr/share/java/opencv4/opencv-4100.jar
OPENCV_LIB := /usr/lib/jni
PARM := --enable-native-access=ALL-UNNAMED
endif
endif

ifeq ($(OS),Windows_NT)
CP := .;$(OPENCV_JAR)
else
CP := .:$(OPENCV_JAR)
endif


%.class : %.java
	javac -cp "$(CP)" $<

all : VideoStreamingServer.class VideoStreamingClient.class

clean :
	rm -f -v *.class

runserver : VideoStreamingServer.class
	java --enable-native-access=ALL-UNNAMED -Djava.library.path="$(OPENCV_LIB)" -cp "$(CP)" VideoStreamingServer
	#java -Djava.library.path="$(OPENCV_LIB)" -cp "$(CP)" VideoStreamingServer sample.mp4 8080
	#java $(PARM) -Djava.library.path="$(OPENCV_LIB)" -cp "$(CP)" VideoStreamingServer

runclient : VideoStreamingClient.class
	java -cp "$(CP)" VideoStreamingClient localhost 8080

