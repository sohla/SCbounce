(
var visualEvents = List.new(20);
var defaultEnv = Env.asr(0.01,1,1);//Env([0, 1, 1, 0], [0.1, 0.8, 0.1], \sin);
var view;
var p;
var controlBus = Bus.control(s,4);

var updateView= {
    var now = thisThread.seconds;
    var count = 0;

    // Draw each visual event with careful type handling
    visualEvents.do { |event|
        // Initialize variables with default values to establish types
        var elapsed = 0.0;
        var normTime = 0.0;
        var envVal = 1.0;
        var shape = \circle;
        var pos = 10@10;
        var size = 50.0;
        var col = Color.white;
		var cbv = controlBus.getSynchronous();

        // Get elapsed time
        if(event[\startTime].notNil) {
            elapsed = now - event[\startTime];
        };

        // Get normalized time safely
        if(event[\duration].notNil && (event[\duration] != 0)) {
            normTime = elapsed / event[\duration];
        } {
            normTime = 0;
        };


        // Only draw if not expired
        if(normTime < 1.0) {

			shape = event[\shape];
			pos = event[\px]@event[\py];
			envVal = event[\envelope].at(normTime);
            size = event[\size] * envVal * cbv;
			col = event[\color];
			col = col.alpha_(event[\alphaEnv].at(normTime));

            count = count + 1;

            // Safe drawing based on shape
            Pen.width = 1; // Reset pen width

			Pen.rotate(event[\rotation], pos.x, pos.y);

			switch(shape,
                \circle, {
                    Pen.fillColor = col;
                    Pen.fillOval(Rect.aboutPoint(pos, size, size));
                },
                \square, {
                    Pen.fillColor = col;
                    Pen.fillRect(Rect.aboutPoint(pos, size, size));
                },
                \line, {
                    Pen.strokeColor = col;
					Pen.width = max(1, size.squared.lincurve(1,100000,1,10,3));
					Pen.line(pos - (size@0), pos + (size@0));
					// Pen.moveTo(pos - (500@0));
					// Pen.splineCurve(pos - (500@0), pos + (500@0), pos - (250@1500), pos + (250@1500), 100);
                    Pen.stroke;
                }
            );

			Pen.rotate(event[\rotation].neg, pos.x, pos.y);

		};
    };


    // Remove expired events safely
    visualEvents = visualEvents.select { |event|
        var now = thisThread.seconds;
        var elapsed = now - event[\startTime];
        var duration = event[\duration] ? 1;
        elapsed < duration;
    };


    // Draw debug count
	// Pen.fillColor = Color.white;
	// Pen.stringCenteredIn(
	// 	"Active visuals: " ++ count,
	// 	Rect(10, 10, 200, 20),
	// 	Font("Helvetica", 12),
	// 	Color.white
	// );
};



var makeView = {
	view = UserView()
    .background_(Color.black)
    .animate_(true)
	.frameRate_(60)
	.drawFunc_(updateView)
};

var window = Window("Visual Synthesizer", Rect(100, 100, 1200, 800))//.fullScreen
    .front
    .alwaysOnTop_(true)
.background_(Color.white().alpha_(0.01))
.layout_(GridLayout.rows(
	[makeView.(),makeView.()],
	[makeView.(),makeView.()],
).margins_(0).hSpacing_(1).vSpacing_(1));


var addVisual = { |shape, px, py, size, color, rotation, duration, envelope, alphaEnv|
    var event;

    // Type safety - make sure all values are valid
    shape = shape ? \circle;
    px = px ? 10;
    py = py ? 10;
    size = size ? 50;  // Don't convert yet
	rotation = rotation ? 0;
    color = color ? Color.white;
    duration = duration ? 5;  // Don't convert yet
    envelope = envelope ? defaultEnv;
	alphaEnv = alphaEnv ? Env([0,1,0], [0.0,1], \sin);

    // Create the event with safe parameters
    event = (
        \shape: shape,
        \px: px,
		\py: py,
        \size: size,
        \color: color,
		\rotation: rotation,
        \duration: duration,
        \envelope: envelope,
		\alphaEnv: alphaEnv,
        \startTime: thisThread.seconds
    );

    // Add to the list
    visualEvents = visualEvents.add(event);
	// "Event added. List now has % items".format(visualEvents.size).postln;
};

Event.addEventType(\customEvent2, {
currentEnvironment.postln;
});
Event.addEventType(\customEvent, {
	addVisual.(
	    shape: ~shape ? \circle,
	    px: ~px ? 10,
	    py: ~py ? 10,
		size: ~size ? 50,
		color: ~color ? Color.white,
		rotation: ~rotation ? 0,
	    duration: ~duration ? 5,
	    envelope: ~envelope ? defaultEnv,
		alphaEnv: ~alphaEnv ? Env([0,1,0.4,0], [0.01,0.09,0.9], \sin)
	);

    ~type = \note;
    currentEnvironment.play;
});

CmdPeriod.doOnce({
	window.close;
	controlBus.free;
});


SynthDef(\versatilePerc, {
    |out=0, freq=50, tension=0.1, decay=0.5, clickLevel=0.5, amp=0.5, dist = 5, pan = 0|
    var pitch_contour, drum_osc, click_osc, drum_env, click_env, sig, pch;

    pitch_contour = Line.kr(1, 0, 0.02);
	pch = freq * (1 + (pitch_contour * tension));
	drum_osc = SinOsc.ar([pch,pch*1.004], LFNoise2.ar([7,8],4,-4),0.5);
    click_osc = LPF.ar(WhiteNoise.ar(1), 1500);
    drum_env = EnvGen.ar(
        Env.perc(attackTime: 0.005, releaseTime: decay, curve: -4),
        doneAction: 2
    );
    click_env = EnvGen.ar(
        Env.perc(attackTime: 0.001, releaseTime: 0.01),
        levelScale: clickLevel
    );
	sig = (drum_osc * drum_env) + (click_osc * click_env);
	sig = (sig * dist).tanh.distort;
	Out.ar(out, Pan2.ar(sig[0],pan,amp))
}).add;

SynthDef(\controlSynth, {
	|out=0, cb=0|
	var in = In.ar(out,2);
	var sig = LFCub.kr(13,0,0.05,0.3);
	Out.kr(cb, sig);
	ReplaceOut.ar(out, in);
}).add;


// SynthDef(\adcverb, {
// 	|out = 0|
// 	var in = In.ar(out, 2);
// 	Out.ar(out, AdCVerb.ar(in * 0.1));
// }).add;

	p = Pbind(
		\type, \customEvent,
		\instrument, \versatilePerc,
		\cb, controlBus,
		\shape, Prand([\circle], inf),
		\hue, Pseg(Pseq([0.0,0.999], inf), 60, \linear, inf),
		\color, Pfunc({|e|Color.hsv(e.hue,0.7,0.7)}),
		\rotation, pi/2,//Pseg(Pseq([-pi/12,pi/12], inf), 4, \linear, inf),
		\dur, Pxrand([0.125*2], inf),
		\root, Pseq([0,3,-2,7,-4,2].stutter(4*6), inf),
		\tension, Pwhite(0.01,0.99),
		\dist,Pkey(\tension) * 10,
		\pan, Pseg(Pseq([-1,1], inf), 4, \sine, inf),
		\decay,Pwhite(0.1,5),
		\duration, Pkey(\decay) * 2,
		\size, (Pkey(\tension) * 100) + 20,
		\octave, Pseq([3,4,5,6,7,8].stutter(4).reverse, inf),
		\note, Pseq([0,4,7,11].reverse, inf),
		\px, 250 + (Pkey(\pan) * 200),
		\py, 500 - (Pkey(\octave) * 50) - (Pkey(\note) * 5),
		\envelope, Pfunc{Env([1,0.001], [1], \exp)}

		);

p = Pfx(p,\controlSynth, \cb, controlBus);
p.play;

// controlBus.postln;

)
// s.queryAllNodes
// s.plotTree
s.scope(2,24,rate:\control)


// position can also be an envelope
// Env.new(levels: [0, 1, -1, 0], times: [0.1, 0.5, 1], curve: [-5, 0, -5]).plot;
// do we describe all paramters as envelopes
// or use a control bus





// Env(Signal.sineFill(100, 1.0/[1, 2, 3, 4, 5, 6])).plot
// Env(levels:Signal.sineFill(100, 1.0/[2, 0.1,0.1]),times:[0.01]).plot