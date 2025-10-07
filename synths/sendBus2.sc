(
SynthDef(\feed, { |cb=0|
	var mx = MouseX.kr(0.0,1.0);
	var my = MouseY.kr(0.0,1.0);

	var sl = Integrator.kr(mx,0.99).poll;
	var ib = In.kr(cb, 8);
	Out.kr(cb, [mx, ib[0], ib[1], ib[2], ib[3], ib[4], ib[5], ib[6]]);
}).send(s);
)



b = Bus.control(s,8);

a= Synth(\feed, [\cb, b]);

b.scope

b.getn(8,{|v|v.postln});

Canvas3D