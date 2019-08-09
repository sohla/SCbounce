b = NetAddr("127.0.0.1",2345);
b.sendMsg("/acc", 200,0,0);    

// sending to Protopixel
( 

	var i=0;
	var oscOut = NetAddr("127.0.0.1",2345);

	Routine { 
	    loop {

			var r = (1.5 + sin(i * 2pi)) * 64;
			var g = (1 + cos(i * 2pi * 0.5)) * 128;
			var b = (1.5 - sin(i * 2pi)) * 64;
			var t = (i * 820).mod(250);

			i = i - 0.002;

			oscOut.sendMsg("/acc", r,g,b);
			oscOut.sendMsg("/rot", t);
	        0.01.yield;

	    }
	}.play;
)
 

Mouse