(

var staker;

var stack = {
	var visuals = Require("visuals");
	var devices = Require("devices.scd");
	var grid = Require("grid.scd");
	View().layout_(staker =StackLayout(
		grid.(),
		devices.(),
		visuals.(),
	));
};

var tabButton = {|title|
	Button()
	.minHeight_(100)
	.font_(Font(size:30))
	.states_([
		[title, Color.white, Color.grey],
		[title, Color.white, Color.new255(0, 139, 69)]
	])
	.mouseDownAction_({|but|
		if(but.value == 0,{
			but.parent.children.do({|ab,i|
				if(but != ab, {
					ab.valueAction_(0);
				},{
					staker.index = i;
				});
			});
		},{but.valueAction_(0)});
	})
};

var tab1;
var tabs = {|t|
	View().layout_(HLayout(
		tab1 = tabButton.("🁪"),
		tabButton.("⚃"),
		tabButton.("※")
	).spacing_(0).margins_(0)).maxHeight_(100);

};

var mainView = VLayout(
		tabs.(),
		stack.()
);

w = Window().bounds_(Rect(100,100,1000,700)).layout_(mainView).front;
tab1.valueAction = 1;

~loader = {|n|	(PathName(thisProcess.nowExecutingPath).pathOnly++n).load};

)
