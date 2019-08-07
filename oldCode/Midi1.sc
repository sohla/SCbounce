MIDIClient.init;

m = MIDIOut(0).latency_(Server.default.latency);
m.noteOn(0, 60, 60);
m.noteOff(16, 60, 60);
m.allNotesOff(16);




MIDIIn.connectAll;    // init for one port midi interface

~control = { arg src, chan, num, val;    [src,chan,num,val].postln; };


MIDIIn.addFuncTo(\control, ~control);

MIDIIn.removeFuncFrom(\control, ~control);

MIDIIn.disconnectAll