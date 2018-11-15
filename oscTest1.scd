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

7.do{|i| b.sendMsg("/hmr/lx", i,55,55,55)};    // send the application the messa


b = NetAddr.new("192.168.3.40", 57120);    // create the NetAddr
b.sendMsg("/hmr/default", 1);    // send the application the messa

RduinoDMX
Array

a = 23
a.asAscii
// Int8Array[ 47, 0, 0, 0, 44, 105, 105, 0, 0, 0, 0, 1, 0, 0, 0, 42 ]
// Int8Array[ 47, 0, 0, 0, 44, 105, 105, 0, 0, 0, 0, 0, 0, 0, 0, 42 ]
//k.maxPacketSize

k = SLIPDecoder.new("/dev/cu.usbmodem3666050", 115200, 8);

f = [806,772,739,754,915,983,809,772];
k.actions = Array.fill(8,{|index| {|i,input| if(index==1,{[index,(input-f[index]).clip(0,1024)].postln;},{[index,input-f[index]].post; " \t".post;})} });
k.start;
k.stop;
k.close;

y = 4;
z = 127;
a = [ 47, 0, 0, 0, 44, 105, 105, 0, 0, 0, 0, 0, 0, 0, y, z ]
b = (a[a.size-2].asBinaryDigits.at([4,5,6,7]) ++ a[a.size-1].asBinaryDigits)
c = b.convertDigits(2);


3.asBinaryDigits.at([4,5,6,7])
[1,0].convertDigits(4)
1.asBinaryDigits.

31.asBinaryDigits.at(6,7)