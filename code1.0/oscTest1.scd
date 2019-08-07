(

n = s.nextNodeID;
s.sendMsg("/s_new", "default", n, 0, 0, "freq", 200);

o = OSCFunc({ arg msg, time, addr, recvPort;

	//[msg, time, addr, recvPort].postln;

	((msg[2].asFloat + 1pi) / 1 ).postln;

	// s.sendMsg("/n_set", n, "freq", 100 + (80 * msg[3].asFloat) );
	// s.sendMsg("/n_set", n, "amp", 0.1 +  (msg[2].asFloat + 1pi) / 2pi );

}, '/gyro');

)
o.free;
s.sendMsg("/n_free", n);

s.queryAllNodes;
{SinOsc.ar(120,0,0.1)}.play

// talking to QLAb

b = NetAddr.new("172.20.10.8", 53000);    // create the NetAddr
b.sendMsg("/qlab", "go");    // send the application the messa

OSCFunc.trace(true); // Turn posting on
OSCFunc.trace(false); // Turn posting off


b.sendMsg("/select/2", "");    // send the application the messa
b.sendMsg("/cue/5/start", "");    // send the application the messa
b.sendMsg("/cue/1/stop", "");    // send the application the messa

b.sendMsg("/cue/2/translationX -100", "");    // send the application the messa


o = OSCMonitor.new;     // make one
o.enable;            // turn it on
o.show;                // show them on gui, no po
o.watchStatus = true;
s.boot;
o.verbose = true;

o.disable;
o.watchStatus = false;

NetAddr.langPort;
n = NetAddr("127.0.0.1",5403)
n.ip.class
n.port


a = Synth.head(s,\help_Bus);
a.set(\ffreq,423);
a.set(\ff,0.1);


 
b = OSCBus(nil,"/test",3,s);
b.bus.scope
a.map(\ffreq,b.bus);


OSCBus(nil,"/test",3,s).bus.scope

o = OSCFunc({ arg msg, time, addr, recvPort;
	[msg, time, addr, recvPort].postln;
}, '/test');

o.free;



o = OSCFunc({ arg msg, time, addr, recvPort;
	[msg, time, addr, recvPort].postln;
}, '/espData');

o.free;
	




















a = (\e:"hello", \f:(\a:1,\b:2))
a.class

a.f

a


b = Event.new(proto:a)

b.f.a = 2
b.e = 99
b.f = 


a
b
b.class
b.e = "world"

a.e
b.e

a[\e] = "world"

b = (\a:a)


x = true
x.not



b[\a][\e]

b[\a][\e] = "hello world"

c = (\pnt: (\x:0,\y:1,\z:2))

c[\pnt][\y]

c.pnt.y

d= Dictionary.newFrom(["1":4])
d.trueAt("1") == 4



["a","b","c"].find(["b"])

~a = {"oh yes".postln}
~a.()


Server
s.addr

b = NetAddr.new("127.0.0.1", 9001);    // create the NetAddr
b.sendMsg("/hello", 0, "world", 1.2345);    // send the application the messa


b.sendMsg("/playNote", 76, 100);    // send the application the messa




b = NetAddr.new("127.0.0.1", 9001);    // create the NetAddr
b.sendMsg("/gyrosc/gyro", 


180/pi



b = NetAddr.new("10.1.1.10", 8888);    // create the NetAddr
b.sendMsg("/led",1);    // send the application the message "hello" with the parameter "ther
b.sendMsg("/onIP", 1,2,3);    // send the application the message "hello" with the parameter "ther


// thanks to Eli Fieldsteel for this!
z = "ifconfig | grep \"status: active\" -B6 | grep \"inet \" | awk 'BEGIN {FS=\"inet \"}{print $2}' | awk 'BEGIN {FS=\" \"}{print $1}'".unixCmdGetStdOut.replace("\n",""); 
a = z.split($.) // returns array
a[0].asInteger
b.sendMsg("/onIP", a[0].asInteger,a[1].asInteger,a[2].asInteger,a[3].asInteger);    // send the application the message "hello" with the parameter "ther



// laptop 172.20.10.2
// ipod 172.20.10.3

b = NetAddr.new("10.1.1.12", 51706);    // create the NetAddr
b.sendMsg("/bounce", "motionReset");    // send the application the messa

b.port.asString
b.ip.class
// OSCReceiver
// MIDIReceiver

(

var w = Window().front; 
var ax=[];
var cols = 5;
var rows = 6;

var uberBtn = { |i|
	var button, stack; 
	stack = StackLayout(
		button = Button() 
			.states_([["off ", Color.rand, Color.rand],["on" , Color.rand, Color.rand]]) 
			.action_({|b| ("btn["++i++"]").postln;if(b.value == 1,stack.index = 1)}),
		Button().layout_(
			HLayout(
				Slider().action_({|sld|("sld["++i++":A]:"++sld.value).postln}),
				Slider().action_({|sld|("sld["++i++":B]:"++sld.value).postln}),
				Slider().action_({|sld|("sld["++i++":C]:"++sld.value).postln})
				))
		.states_([button.states.last]) 
		.action_({|b| button.valueAction_(0); stack.index = 0; })
	).mode_(1)
};


rows.do{ |ix|
	var ay=[];
	cols.do{|iy|
		ay=ay.add(uberBtn.((ix*cols)+iy));
	};
	ax=ax.add(ay);
};

w.layout = VLayout(*ax.collect { |o| HLayout(*o) }); 
) 




b = NetAddr.new("10.0.0.58", 51700);    // create the NetAddr
b.sendMsg("/bounce", "motionReset");    // send the application the messa



b = NetAddr.new("10.0.0.57", 51700);    // create the NetAddr
b.sendMsg("/bounce", "motionReset");    // send the application the messa
b
b.port.asString
b.ip.class



b = NetAddr.new("10.1.1.14", 56120);    // create the NetAddr
b.sendMsg("/hmr/default", 1);    // send the application the messa

b.sendMsg("/hmr/lx", 6,5,0,0);    // send the application the messa

7.do{|i| b.sendMsg("/hmr/lx", i,0,0,0)};    // send the application the messa


b = NetAddr.new("192.168.3.40", 57120);    // create the NetAddr
b.sendMsg("/hmr/default", 1);    // send the application the messa




NetAddr.langPort;
NetAddr.localAddr;

o = OSCFunc({ arg msg, time, addr, recvPort;
	([msg, time, addr, recvPort]).postln;
}, '/text');

o.free;

NetAddr.localAddr.sendMsg("/text","Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse turpis est, aliquet sed dolor sit amet, porta feugiat libero. Quisque tincidunt lorem aliqu")


SerialPort





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
	        0.005.yield;

	    }
	}.play;
)
 



b = NetAddr("127.0.0.1",2345);
b.sendMsg("/Content/a/params/play");    
b.sendMsg("/Content/a/params/alpha",0);    
b.sendMsg("/Content/a/params/stop");    
b.sendMsg("/Content/color/params/color",0,255,255,0);    



b = NetAddr("192.168.1.3",56121);
b.disconnect

b.sendMsg("/dfplayer/restartDevice",1);    
 
b.sendMsg("/dfplayer/volume",1.asInteger);    
b.sendMsg("/dfplayer/volume",5.asInteger);    
b.sendMsg("/dfplayer/volume",30.asInteger);    

b.sendMsg("/dfplayer/play",6.asInteger);    
b.sendMsg("/dfplayer/play",3.asInteger);    
b.sendMsg("/dfplayer/play",4. asInteger);    
b.sendMsg("/dfplayer/play",8.asInteger);    
b.sendMsg("/dfplayer/play",9.asInteger);    
b.sendMsg("/dfplayer/play",7.asInteger);    
b.sendMsg("/dfplayer/pause");    	

b.sendMsg("/dfplayer/fadeIn",500.asInteger);    
b.sendMsg("/dfplayer/fadeOut",500.asInteger);    

	
b.sendMsg("/dfplayer/restartDevice",1);    


b.sendMsg("/dfplayer/powerLevel");    

o = OSCFunc({ arg msg, time, addr, recvPort;
	([msg, time, addr, recvPort]).postln;
}, '/dfplayer/powerLevel');

o.free;

0
Pdef(\pat1,
	Pbind(
		\func, Pfunc({|e| 
			b.sendMsg("/dfplayer/play",(5.rand+1).asInteger); 
			b.sendMsg("/dfplayer/powerLevel");
			Date.localtime.postln;
    
		}),
        \degree, 1,
		\amp, 0,
		\dur, Pseq([2], inf)
)).play;


Pdef(\pat1).set(\sustain,0.01);
Pdef(\pat1).set(\attack,0.5);

Pdef(\pat1).set(\root,2);
Pdef(\pat1).map(\dur,Pseq([0.1], inf));



Pdef(\pat1).stop;
Pdef(\pat1).clear;
Pdef(\pat1).play;

o = OSCFunc({ arg msg, time, addr, recvPort;
	([msg, time, addr, recvPort]).postln;
}, '/thermos');

o.free;




b = NetAddr("10.0.2.55",8888);
b.disconnect

b.sendMsg("/fff/default",666);    



b = NetAddr("127.0.0.1",9999);
b.sendMsg("/thecabin/play","growl5.mp3", 1.0, true, 0, 0);    
b.sendMsg("/thecabin/play","laughing10.wav");    
b.sendMsg("/thecabin/play","beware_chant.wav");    
b.sendMsg("/thecabin/play","drone3_1.mp3");    
b.sendMsg("/thecabin/stop");    

b.sendMsg("/thecabin/systemsound",4095.asInteger);    

b = NetAddr("127.0.0.1", 6667);
b.sendMsg("/inputSettings/group/threshold1", 12);    

	o = OSCFunc({ arg msg, time, addr, recvPort;
		([msg, time, addr, recvPort]).postln;
	}, '/inputSettings/group/threshold1', recvPort:6666);

	o.free;

