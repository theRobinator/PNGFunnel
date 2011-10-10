all : classes
	echo 'Main-class: PNGFunnel.PNGFunnel' > bin/manifest.tmp
	cd bin; jar cvfm PNGFunnel.jar manifest.tmp PNGFunnel
	-rm bin/manifest.tmp
	mv bin/PNGFunnel.jar .

classes : ConverterThread.java DecoderThread.java FileChooser.java Keyreader.java PNGFunnel.java
	mkdir -p bin
	javac *.java -d bin

clean :
	-rm -r bin/PNGFunnel

spotless : clean
	-rm PNGFunnel.jar
