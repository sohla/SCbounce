

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
	var splayCount = 8;

	mx = LFSaw.kr( (1.0/(30.0)) * MouseX.kr(0.01, 1.0) ,1,0.5,0.5);

	my = MouseY.kr(0.001,1.0,1.0);//splay

	sp = Splay.arFill(splayCount,
		{ |i| DelayN.ar(Warp1.ar(1, buffer, mx, pch,my, envbuf, 8, 0.3, 2), 0.3, (i*0.4))  },
		0.5,
		1,
		splayCount.rand.linlin(0,splayCount,-1.0,1.0),
	) * 0.7 ;

	//sp = HPF.ar(sp,888);

	Out.ar(0,sp);

	}).send(s);

)


FreqScope.new(1300, 200, 0);



p = ("~/Music/SCSamples/light_wind.wav").standardizePath;
p = ("~/Music/SCSamples/lassem_ria2.wav").standardizePath;
p = ("~/Music/SCSamples/kidsSong.wav").standardizePath;
c = Buffer.read(s, p);

z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(70).midicps / 435.0]); 
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(58).midicps / 435.0]);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(82).midicps / 435.0]);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(46).midicps / 435.0]);




p = ("~/Music/sounds/aNewDay.wav").standardizePath;
c = Buffer.read(s, p);
z = Synth(\warp,[\buffer,c,\ws,0.3,\pch,(69).midicps / 440.0]);


x.free;
c.free;



{LFSaw.kr(1.0,1,0.5,0.5)}.plot(1.0,minval:-1.0,maxval:1.0)

Server.local.makeWindow



//inMemory kids bg

(
{
var root, scale;
			// bowed string
		var trig, p, s, exc, x, freq;
		root = 26+36+12+4;
		scale = #[0];
			freq = (scale.choose + root).midicps;
			exc = BrownNoise.ar([0.007,0.007]) * max(0, LFNoise1.kr(exprand(0.125,0.5), 0.6, 0.4));
			s = (Klank.ar(`[
					Array.series(12, freq, freq),
					Array.geom(12,1,rrand(0.7,0.9)),
					Array.fill(12, {rrand(1.0,3.0)})
				], exc) * 0.1).softclip * 0.4;
}.play;
)
