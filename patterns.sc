
TempoClock.default.tempo = 120/60;

Pdef(\pat1,
	Pbind(
		\instrument, \trig_demo
		\root,1,
		\scale, #[0,4,7],
        \degree, Prand([[0,1,2],[2,3,4]], inf),
		\dur, Prand([0.2,0.4,0.4,0.2,0.2]*0.1, inf)
)).play;


Pdef(\pat1).set(\sustain,0.01);
Pdef(\pat1).set(\attack,0.5);



Pdef(\pat1).stop;
Pdef(\pat1).play;


Pdef(\pat2,
	Pbind(
        \note, Prand([0,7,11], inf),
		\dur, Prand([0.8,0.4,0.2], inf),
		\octave, 3
)).play;

Pdef(\pat3,
	Pbind(
        \note, Prand([0,7,11], inf),
		\dur, Prand([0.1,0.2,0.1], inf),
		\octave, 6
)).play;
Pdef(\pat3).set(\sustain,0.001);
Pdef(\pat3).set(\transpose,2);
