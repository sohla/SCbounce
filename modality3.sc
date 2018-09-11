MIDIClient.init;
m = MIDIOut(0).latency_(0);

g = MKtl('bcf', "behringer-bcf2000",multiIndex:0);

g.enable(true)
g.outputElements
g.gui
g.getKeysValues;
g.postElements

g.desc.elementsDesc;; 

g.getKeysValues([\bt_1_1])
g.elAt(\bt,0,0).action_({|e| ["->",e.elemDesc.midiNum, e.deviceValue].postln;});


g.elAt(\bt,0,0).action_({|e| ["2->",e.elemDesc.midiNum, e.deviceValue].postln;});


g.elAt(\sl,0).action_({|e| ["s->",e.elemDesc.midiNum, e.deviceValue].postln;});

g.elAt(\sl,0).resetAction; // clear action

g.elAt(\sl).action_({});


// attach an action to send out midi
g.elAt(\bt).action_({|e| m.control(0, e.elemDesc.midiNum, e.deviceValue); ["->",e.elemDesc.midiNum, e.value].postln;});


g.elAt(\bt).action_({});

// use action 
g.elAt(\bt,0,0).valueAction = 0.0


(
d = 0;
{
    loop {
        0.01.wait;
        d = d + 0.01;
		// don't forget to call action
		g.elAt(\bt,0,0).valueAction = g.elAt(\bt,0,0).value.asBoolean.not.asInt;
        8.do { |i|
            g.elAt(\sl, i).value_(
                (d + (i/8)).mod(1.0);
            );
        };
    }
}.fork;
)



// save it as achive
g.getKeysValues.writeArchive("/Users/soh_la/Develop/SuperCollider/Projects/mycelium/s1.txt")

// load it
~loaded_presets = Object.readArchive("/Users/soh_la/Develop/SuperCollider/Projects/mycelium/s1.txt")
~loaded_presets.keys.do{|k| "%: % (%)\n".postf(k, v = ~loaded_presets[k], v.class)}

// set value and action
g.setKVAction(~loaded_presets);





g.free;

