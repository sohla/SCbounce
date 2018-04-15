(
var window, slider;
var cs = ControlSpec(100, 1000, \linear, 0.01); // min, max, mapping, step, default
var bc = Bus.control(s).value_(cs.map(cs.default));
var lagSynth;
var synth;



SynthDef(\lag, { |out, inValue, lagTime = 0.0001| 
        Out.kr(out, Lag.kr(inValue, lagTime).linlin(0,1000,200,450)); 
}).send; 


SynthDef(\help_Bus, { |ffreq = 100 ,ff = 0.2|
    Out.ar(0,
        RLPF.ar(
            LFPulse.ar(SinOsc.kr(ff, 0, 50, 61), [0,0.1], 0.1),
            ffreq, 0.9
        ).clip2(0.4)
    );
}).send;
SynthDef(\simpleSynth, { |ffreq = 100 ,ff = 0.2|
    Out.ar(0,
		SinOsc.ar(ffreq,0,0.2);
    );
}).send;

window = Window("")
	.bounds_(Rect(
		0,0,
		Window.screenBounds.width/2,
		Window.screenBounds.height/2)
		.center_(Window.availableBounds.center)
	)
	.front;


window.layout = VLayout(

	Slider()//• should try EZSlider too
		.maxWidth_(30)
		.valueAction_(cs.default)
	    .action_({|o|
			lagSynth.set(\inValue, cs.map(o.value));    
	    });
);

synth = Synth.head(s,\simpleSynth);
lagSynth = Synth(\lag, [\lagTime, 2, \out, bc, \inValue, cs.map(cs.default)]); 
// here is the magic! map each arg
synth.map(\ffreq,bc);

bc.plot;

window.onClose = ({
	Buffer.freeAll;
	s.freeAll;
});
CmdPeriod.doOnce({window.close});

)



/*
	Taking Nick Collins example and expanding

*/

(
SynthDef(\mapexample,{arg freq=440;
	Out.ar(0,SinOsc.ar(freq,0,0.1))
}).add;

SynthDef(\moda,{|bus|
	Out.kr(bus,SinOsc.ar(550,0,100,1000))
}).add;


SynthDef(\modb,{|bus|
	Out.kr(bus,SinOsc.ar(5,0,50,100))
}).add;


SynthDef(\thru,{|bus|
	Out.kr(bus,In.kr(bus).linlin(0,1,100,1200))
}).add;

)


(
g= Synth(\mapexample);
c = Bus.control(s);
g.map(\freq, c.index);
c.set(660);
)


c.plotAudio();
c.set(1440);

h= {Out.kr(c.index, SinOsc.ar(550,0,100,1000))}.play;
h.free;

j = Synth(\moda,[\bus,c]);
j.free;

k = Synth(\modb,[\bus,c]);
k.free;

l = Synth(\thru,[\bus,c]);
l.free;




Quarks.gui