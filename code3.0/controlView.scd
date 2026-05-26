var renderRate = 30;
var spacing = 4;
var contentView;
var addTitleView = Require("titleView.scd");
var addPrevButton = Require("prevButton.scd");
var addNextButton = Require("nextButton.scd");

var sliderView = {|lt="default", rt="", d|
  var st;
  View().layout_(HLayout(
    UserView()
      .background_(Color.black.lighten(0.1))
      // .maxHeight_(80)
      .frameRate_(30)
      .animate_(true)
      .drawFunc_({|v|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        var tw = 140;
				var pos = (vw * d.params.at(lt.asSymbol)) - (tw/2);
        // lt.drawLeftJustIn(Rect(10,0,vw/2,vh), Font(size:48), Color.black());
        // rt.drawRightJustIn(Rect(vw/2,0,(vw/2)-10,vh), Font(size:48), Color.black());
        Pen.fillColor = d.color.alpha_(0.5);
        Pen.addRect(Rect(pos, 2, tw, vh - 4));
        Pen.fill;
        lt.drawCenteredIn(Rect(pos,0,tw,40), Font(size:24), Color.black());
      })
      .mouseMoveAction_({|v,x,y|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        if(x<0,{x=0});
				d.params.put(lt.asSymbol, x/vw);
      })
      .mouseUpAction_({|v,x,y|
      })
      .mouseDownAction_({|v,x,y|
        var vw = v.bounds.width;
        var vh = v.bounds.height;
        if(x<0,{x=0});
				d.params.put(lt.asSymbol, x/vw);
      });
  ));
  
};

var controlsView = {|d|
  // var sliders = d.params.collect({|v,k| 
  //     [sliderView.(k.asString, "", d)]
  // });
	// View()
	// 	// .onClose_({osc.free})
	// 	.layout_(GridLayout.columns(sliders).hSpacing_(spacing).vSpacing_(spacing))
  var view;
    d.env.use{
      view = ~controlView.(d);
    };
    view

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

	var numDevices = contentView.children.size;
	var view = View()
		.background_(Color.black)
		.layout_(GridLayout.rows(
			[				
				[controlsView.(d), columns:2]
			],[
				[addTitleView.(d), columns:2],
			],[
				addPrevButton.(d),
				addNextButton.(d),
			]
		).hSpacing_(spacing).vSpacing_(spacing).margins_([0,0,0,0]));

	// add device view
	// contentView.layout.add(view, (numDevices/2).floor, numDevices%2);	
	contentView.layout.add(view, 0, numDevices);	

};

// OSCFunc({ |msg, time, addr, recvPort|
//     controls.put("volume",msg[1]/127.0);
// },'/airkit/cc/72', NetAddr("127.0.0.1", NetAddr.langPort));

// OSCFunc({ |msg, time, addr, recvPort|
//     controls.put("sensitivity",msg[1]/127.0);
// },'/airkit/cc/75', NetAddr("127.0.0.1", NetAddr.langPort));

controlView;


