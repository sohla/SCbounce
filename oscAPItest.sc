NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, 1);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/mute", 8891, 0);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/unmute", 8891, 0);




(

NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8891, 36.rand);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8890, 36.rand);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8889, 36.rand);
NetAddr.new("192.168.1.149", 57120).sendMsg("/airkit/loadPersonality", 8888, 36.rand);

)



(

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


