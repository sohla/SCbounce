

( 

var winenv;
winenv = Env([0, 1, 0], [0.01, 0.01], [-2, 2]);
z = Buffer.sendCollection(s, winenv.discretize, 1);


SynthDef("help-Diskout", {arg bufnum;
	DiskOut.ar(bufnum, In.ar(0,2));
}).send(s);

SynthDef(\warp, {arg buffer = 0, envbuf = -1, ws = 0.5, pch = 1.0;
	var pos;
	var mx=0,my=0;
	var sp,verb;
	var splayCount = 2;

	mx = LFSaw.kr( (1.0/(30.0)) * MouseX.kr(0.01, 1.0) ,1,0.5,0.5);

	my = MouseY.kr(0.001,1.0,1.0);//splay

	sp = Splay.arFill(splayCount,
		{ |i| DelayN.ar(Warp1.ar(1, buffer, mx, pch,my + (i*0.01), envbuf, 8, 0.3, 2), 0.3, (i*0.4))  },
		0.5,
		1,
		splayCount.rand.linlin(0,splayCount,-1.0,1.0),
	) * 0.7 ;

	Out.ar(0,sp);

	}).send(s);

)


FreqScope.new(1300, 200, 0);



p = ("~/Music/SCSamples/lassem_anton.wav").standardizePath;
p = ("~/Music/SCSamples/lassem_ria2.wav").standardizePath;
p = ("~/Music/SCSamples/kidsSong2.wav").standardizePath;
c = Buffer.read(s, p);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(75).midicps / 435.0]); 
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(64).midicps / 435.0]);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(73).midicps / 435.0]);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(57).midicps / 435.0]);




p = ("~/Music/sounds/aNewDay.wav").standardizePath;
c = Buffer.read(s, p);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(69).midicps / 440.0]);


x.free;
c.free;



{LFSaw.kr(1.0,1,0.5,0.5)}.plot(1.0,minval:-1.0,maxval:1.0)

Server.local.makeWindow