(
	QtGUI.palette = QPalette.dark; 
a = Window("text-boxes", Rect(200 , 450, 950, 550));
a.view.decorator = FlowLayout(a.view.bounds);
a.onClose = {
	r.stop;
};
CmdPeriod.doOnce({a.close});

//c = (97..121).collect{|o|o.asAscii.asString};
c = (0..127).collect{|o|o.asAscii.asString};

b = EnvelopeView(a, Rect(0, 0, 940, 540))
    .thumbWidth_(10.0)
    .thumbHeight_(10.0)
    .drawLines_(true)
    .drawRects_(true)
	.strokeColor_(Color.grey(0.1))

	.style_(\dots)
    .selectionColor_(Color.green(0.5))
    .value_([Array.rand(c.size,0.0,1.0),Array.rand(c.size,0.0,1.0)]);

c.do({|o,i|
    b.setString(i, o);
    b.setFillColor(i,Color.green(0.5));
});

a.front;
a.view.keyUpAction = {|view, char, modifiers, unicode, keycode, key| 
	if( keycode == 15,{ //r
		b.valueAction_([Array.linrand(c.size,0.0,1.0),Array.linrand(c.size,0.0,1.0)]);
	});
};

c.do{|o,i| 

	var col = Array.new(8);

	c.copyRange(i,c.size).do{
		|u,j| 
		if(j>0,{
			//[i,j+i].postln;
			col.add(j+i);
		});
	};
	//[i,col].postln;
	b.connect(i,col);
};


r = Routine { 
	loop{ 

		{
			var index = (c.size-1).rand;
			var selectIndex = b.selection.first;
			b.setFillColor(index,Color.hsv(1.0.rand,1.0,1-0.4.rand,0.5));
			b.selectIndex(index);
			if(selectIndex != nil, {
				b.deselectIndex(selectIndex);
				//selectIndex.postln;
			});

		}.defer;

		0.01.yield;
	}
}.play(AppClock);

)


// (
// a = [1,2,3,4];

// a.do{|o,i| 

// 	var col = Array.new(8);

// 	a.copyRange(i,a.size).do{
// 		|u,j| 
// 		if(j>0,{
// 			[i,j+i].postln;
// 			col.add(j+i);
// 		});
// 	};
// 	[i,col].postln;
// }


// )


