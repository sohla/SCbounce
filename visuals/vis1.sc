(
SynthDef(\synth16, { |out=0, freq=100, gate=1, att=0.1, dec=0.1, sus=0.3, rel=0.3, amp=1.0, dt=0.1|
	var env = EnvGen.ar(Env.adsr(att, dec,sus, rel), gate, doneAction:2);
	var in = LocalIn.ar(2);
	var my = MouseY.kr(1,90);
	var sig = SinOsc.ar([freq, freq + (freq * 0.02)], SinOsc.ar(freq * MouseX.kr(1,10), 0, my, my.neg) * in, 0.1);
	var sub = SinOsc.ar([freq, freq + (freq * 0.02)] * 0.5, 0, 0.5);
	LocalOut.ar(sig * sub);
	sig = (sig + sub) * env * amp;
	// sig = DelayC.ar(sig,2,dt/2, 1, sig);

	Out.ar(out, sig);
}).add;

)


(

var y = 0, f = 0;
var h = 800, w = 800;
var window = Window("", Rect(200,200,w,h)).front;
var view = UserView(window, window.view.bounds.insetBy(5,5));
var updater = false;

Pdef(\a,
	Pbind(
		\instrument, \synth16,
		\octave, Prand([3,4,5], inf),
		\degree, Pxrand([0, 1, 2, 4, 5], inf),
		\dur, Pxrand([0.4, 0.2, 0.1, 0.8,3], inf),
		\dt, Pkey(\dur),
		\att, Pkey(\dur) * 0.1,
		\dec,  Pwhite(0.1,0.6),
		\sus, 0,
		\amp, 1,
		\func, Pfunc({ |e|
			y = e.degree;
			f = e.dur;
			updater = true;
		})
	)
);

Pdef(\b,
	Pbind(
		\instrument, \synth16,
		\octave, Prand([6,7,8], inf),
		\degree, Pxrand([0, 1, 2, 4, 5], inf),
		\dur, Pxrand([0.2], inf),
		\dt, Pkey(\dur),
		\amp, 0.1,
		\att, Pwhite(0.04, 0.01),
		\dec, Pwhite(0.1,0.6),
		\rel, 0.1,//Pwhite(0.1, 3) * 0.03,
		\sus, 0.0,
	)
);

Pdef(\a).play(quant:[0.1]);
Pdef(\b).play(quant:[0.1]);

window.onClose = {
	Pdef(\a).stop ;
	Pdef(\b).stop ;

};
window.background = Color.black;
view.clearOnRefresh = false;
view.background = Color.gray(1,0.7);
view.animate = true;
view.drawFunc = {|v|
	v.frame.postln;
	if(updater == true){
		updater = false;
	Pen.fillColor = Color.green;
	Pen.addRect(Rect(0, window.bounds.height - 50 - (y * 50), view.bounds.width, 50) );
	Pen.fill;
	}{
	Pen.fillColor = Color.grey(0.0,  0.1 / f);
	Pen.addRect(view.bounds.moveBy(-5,-5));
	Pen.fill;
	};

};

)



a=(\a:2, \b:3, \func:({|v|v.postln}))
b = Event.new(proto:a)
