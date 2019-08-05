(
var window;
var na = NetAddr("127.0.0.1", 6667);
var modality = MKtl('midi_3_lpd8', "akai-lpd8");
var names = [
	"near", 
	"far", 
	"blobCount", 
	"divide"
	];
var specs = [
	[0,127,\lin,1].asSpec, 
	[0,127,\lin,1].asSpec, 
	[1,4,\lin,1,1].asSpec, 
	[3,16,\lin,1,3].asSpec, 
	];

window = Window("")
	.bounds_(Rect(
		0,0,
		80,
		80)
		.center_(Window.availableBounds.center)
	)
	.front;

window.layout = HLayout(
);





window.onClose = ({

	Buffer.freeAll;
	s.freeAll;

	modality.elAt(\kn).resetAction;
	modality.free;
	na.free;
});

CmdPeriod.doOnce({window.close});

modality.device.midiOut.latency = 0.0;


modality.elAt(\kn).action_({ |el,i|
	var index = el.parent.indexOf(el);
	na.sendMsg("/inputSettings/sliders/"++names[index], specs[index].map(el.value).asInteger);    
});


)


