
// // VL startup

// // Manually set soundcard to ensure routing works
// var o = Server.local.options;
// //var i = ServerOptions.devices.indexOfEqual("MOTU UltraLite");
// //var i = ServerOptions.devices.indexOfEqual("US-366 (Core Audio)");
// var i = ServerOptions.devices.indexOfEqual("US-366 (Bit Accurate)");


// o.inDevice = ServerOptions.devices[i];
// o.outDevice = ServerOptions.devices[i];
// o.numOutputBusChannels = 4;
// o.numInputBusChannels = 2;

 s.boot;

// once server is ready, load synths and gui 
s.doWhenBooted{
		
		var scp = PathName.new(Platform.resourceDir).parentPath;
		var path = PathName.new(scp +/+"gyroOscUI8.scd");
		var x = File.new(path.asAbsolutePath,"r");
		var c = x.readAllString;
		interpret(c);
		x.close;


		"Welcome to mball".postln;
};
