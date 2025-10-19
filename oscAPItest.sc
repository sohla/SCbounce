// OSC Commands
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, 1);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/mute", 8891, 0);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/unmute", 8891, 0);


NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, 0);

// randomly pick a pers. and send to all
(
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, 36.rand);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8890, 36.rand);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8889, 36.rand);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8888, 36.rand);

)



(

// randomly pick a pers. and send for all every n.wait
fork{
	loop{
		NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, 36.rand);
		NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8890, 36.rand);
		NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8889, 36.rand);
		NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8888, 36.rand);
		3.0.wait;
	};
};
)


// go through 0..n
(
n = 20;
a = Pbind(\dur, 1.0, \i, Pseq((0..n), inf),\pf, Pfunc({|e|
	NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, e.i);
	})
).play;

)

a.stop
