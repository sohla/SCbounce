//------------------------------------------------------------------------
// Wavetable : VOsc
//------------------------------------------------------------------------

// allocate and fill the buffers to be used by VOsc
(
s.waitForBoot({

    var numBufs = 8;


    // allocate table of consecutive buffers
    ~bufs = Buffer.allocConsecutive(numBufs, s, 1024, 1);
    s.sync;

    ~bufs.do({ arg buf, i;
		buf.sine2(1.0/([1,2,3,4,5] / (1+i) ), true, true, true);
        s.sync;
    });
    "Buffers prepared.".postln;
})
)

// play the synth, indexing into the buffers with MouseX
(
x = SynthDef("help-VOsc",{ arg out = 0, bufoffset = 0;
    // mouse x controls the wavetable position
    var bufindex = MouseX.kr(0, ~bufs.size - 1) + bufoffset;

    Out.ar(out,
        VOsc.ar(bufindex, [50, 51], 0, 0.3)
    )
}).play(s,[\out, 0, \bufoffset, ~bufs.first.bufnum]);
)


(
~bufs.do({ arg buf, i;
	buf.sine2( ([1] / (i+1)).reciprocal, true, true, true);
});
)

//------------------------------------------------------------------------
// Harmonics as generator
//------------------------------------------------------------------------

(
var h = Harmonics(8);
~bufs.do({ arg buf, i;
	buf.sine2( h.ramp(1.0,1.0),h.shelf(1*(i+0.5),1), true, true, true);
});
)


//------------------------------------------------------------------------
// Wavesets
//------------------------------------------------------------------------

w = Wavesets.from(String.scDir +/+ "sounds/a11wlk01.wav");
w.dump;

w.plot(0,1)
w.xings
w.lengths
w.lengths.sort.reverse

i = 1240;
w.plot(i, 1);    // plot a single waveset
w.signal.copyRange(w.xings[i], w.xings[i+1]-1).plot;

w.signal

w.eventFor(1000, 6, repeats: 1, playRate: 1).put(\amp, 1).play;
w.xings.size
(
Task({
    w.xings.size.do({ arg i;
        var ev = w.eventFor(w.xings.size - i, 1, 1, 1);

        ev.putPairs([\pan, [-1, 1].choose, \amp, 0.5]);
        ev.play;
        (ev.sustain).wait;
    });
}).play;
)



(
var w, z, p, d, func, ws;
ws = Wavesets.from(String.scDir +/+ "sounds/a11wlk01.wav");

w = Window("plot panel", Rect(20, 30, 520, 450));
z = CompositeView(
    w, Rect(10, 35, 490, 400)
).background_(Color.rand(0.7)).resize_(5);
p = Plotter("plot", parent: z);
d = ws.signal;
func = { |v|
    var index = v.value.linlin(0, 1, 0, ws.xings.size).asInteger;
	var offset = ws.xings[index].neg;
	var len = (ws.xings[index+1] - ws.xings[index]) +1;
	offset.postln;
	p.value = d.rotate(offset).keep(len);
	// w.refresh;
};
Slider.new(w, Rect(10, 10, 490, 20)).resize_(2).action_({ |v| func.(v.value) });
func.value(0);
w.front;
)

(
~isPowerOf2 = { |n|
    (n > 0) and: { (n & (n - 1)) == 0 }
};

)
w.lengths.sort.select{|v,i| ~isPowerOf2.(v) && (v>64)}.sort.reverse

// so xings is the start, and lengths is how much
w.xings.size
w.lengths.size // should be w.xings.size - 1

x = [[1, 2, 3], [3,4]];
y = x.lace(6).reshape(3,2)

a = [w.xings, w.lengths];
// b = a.lace(



(

var w = Wavesets.from(String.scDir +/+ "sounds/a11wlk01.wav");

var indices = w.xings;
var minValue = 1;

var result = indices.collect { |val, i|
	(original: val, position: i)
}.select { |item|
	var n = item[\original];
	(n >= minValue) and: { (n > 0) and: { (n & (n - 1)) == 0 } }
}.sort { |a, b|
	a[\original] < b[\original]
};

indices.postln;
result.do { |item|
	"value: %, original position: %".format(item[\original], item[\position]).postln;
	// w.plot(item[\position], 1);

};
//
// result.slide(2,1).reshape(result.size,2).do {|item|
// 	"item : %".format(item).postln;
//
//
// };





)




a = [1,2,3,4,5,6]
a.reshape(2,2)