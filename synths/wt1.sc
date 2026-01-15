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


(
var h = Harmonics(32);
~bufs.do({ arg buf, i;
	buf.sine2( h.ramp(1.0,1.0),h.shelf(1*(i+0.5),1), true, true, true);
});
)

~isPowerOf2 = { |n|
    (n > 0) and: { (n & (n - 1)) == 0 }
};


w = Wavesets.from(String.scDir +/+ "sounds/a11wlk01.wav");
w.dump;        // contains mainly analysis data
w.lengths.sort.select{|v,i| ~isPowerOf2.(v) && (v>64)}.sort.reverse




(
//redo
var w = Wavesets.from(String.scDir +/+ "sounds/a11wlk01.wav");

var indices = w.lengths;
var minValue = 65;

var result = indices.collect { |val, i|
    (original: val, position: i)
}.select { |item|
    var n = item[\original];
    (n >= minValue) and: { (n > 0) and: { (n & (n - 1)) == 0 } }
}.sort { |a, b|
    a[\original] < b[\original]
};


result.do { |item|
    "value: %, original position: %".format(item[\original], item[\position]).postln;
	// w.plot(item[\position], 1);

};

result.slide(2,1).reshape(result.size,2).do {|item|
	"item : %".format(item).postln;


};





)




a = [1,2,3,4,5,6]
a.reshape(2,2)