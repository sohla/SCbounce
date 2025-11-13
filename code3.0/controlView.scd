var renderRate = 30;
var spacing = 4;
var contentView;
var addTitleView = Require("titleView.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");

var controls = Dictionary.newFrom([
  // "bright", 0.1,
  // "texture", 0.2,
  // "thick", 0.3,
  // "motion", 0.4,
  "sensitivity", 0.5,
  "volume", 0.6,
]);

var sliderView = {|lt="default", rt=""|
  var st;
  View().layout_(HLayout(
    UserView()
      .background_(Color.grey)
      .maxHeight_(80)
      .frameRate_(30)
      .animate_(true)
      .drawFunc_({|v|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        var tw = 80;
        Pen.fillColor = Color.white.alpha_(0.5);
        lt.drawLeftJustIn(Rect(10,0,vw/2,vh), Font(size:48), Color.white.alpha_(0.1));
        rt.drawRightJustIn(Rect(vw/2,0,(vw/2)-10,vh), Font(size:48), Color.white.alpha_(0.1));
        Pen.addRect(Rect((vw * controls.at(lt)) - (tw/2), 2, tw, vh - 4));
        Pen.fill;
      })
      .mouseMoveAction_({|v,x,y|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        if(x<0,{x=0});
        controls.put(lt, x/vw);
      })
      .mouseUpAction_({|v,x,y|
      })
      .mouseDownAction_({|v,x,y|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        if(x<0,{x=0});
        controls.put(lt, x/vw);
      });
  ));
  
};


var controlsView = {|d|
	// var osc = OSCFunc({ |msg, time, addr, recvPort|
	// 	var d = ~devices.at(msg[1].asInteger);
	// 	if(d != nil,{
	// 		{addDetailView.(d)}.defer;
	// 	});
	// },'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));
	
  var sliders = controls.collect({|v,k| 
      [sliderView.(k, "")]
  });

	View()
		// .onClose_({osc.free})
		.layout_(GridLayout.columns(sliders).hSpacing_(spacing).vSpacing_(spacing))
};


var controlView = {
	var osc = OSCFunc({ |msg, time, addr, recvPort|
		var d = ~devices.at(msg[1].asInteger);
		if(d != nil,{
			{addDeviceView.(d)}.defer;
		});
	},'/airkit/addDevice', NetAddr("127.0.0.1", NetAddr.langPort));
	contentView = View()
		.onClose_({osc.free})
		.layout_(GridLayout.new().hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]));
		// .mouseDownAction_({false});
	contentView
};

var addDeviceView = {|d|

	var gl;
	var numDevices = contentView.children.size;

	var view = View()
		.background_(Color.black)
		.layout_(gl = GridLayout.rows(
			[				
				[controlsView.(d), columns:2]
			],[
				[addTitleView.(d), columns:2],
			],[
				addPrevButton.(d),
				addNextButton.(d),
			]
		).hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]))

		// .mouseDownAction_({false})
		// .mouseUpAction_({false});

		.mouseUpAction_({|but|
			view.background = Color.black;
		})
		.mouseDownAction_({|but|
			view.background = Color.white.alpha_(0.1);
			d.personality = d.personality + 1;
			NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
		});

	// add device view
	// contentView.layout.add(view, (numDevices/2).floor, numDevices%2);	
	contentView.layout.add(view, 0, numDevices);	

};

OSCFunc({ |msg, time, addr, recvPort|
    controls.put("volume",msg[1]/127.0);
},'/airkit/cc/72', NetAddr("127.0.0.1", NetAddr.langPort));

OSCFunc({ |msg, time, addr, recvPort|
    controls.put("sensitivity",msg[1]/127.0);
},'/airkit/cc/75', NetAddr("127.0.0.1", NetAddr.langPort));

controlView;


