(
var w = Window.new.front.bounds_(400@600).alwaysOnTop_(true);//.fullScreen;
var scenes = [
	(
		performer: "caz1",
		patches: ["tree","mute","wind","mute","ocean","mute","bee","mute","blobblob","mute"]
	),(
		performer: "caz2",
		patches: ["insect","mute","chicken","mute","rain","mute","cello","mute","tram","mute"]
	),(
		performer: "tim1",
		patches: ["tree","mute","insect","mute","frog","mute","bee","mute","ocean","mute"]
	),(
		performer: "tim2",
		patches: ["tree","mute","insect","mute","frog","mute","bee","mute","ocean","mute"]
	)
];

var buttonsView = {|j|
	View().layout_(VLayout(
		StaticText()
			.maxHeight_(35)
			.align_(\center)
			.font_(Font.default.size_(22))
			.background_(Color.grey)
			.string_(scenes[j].performer),
	View().layout_(VLayout(*{|i|
		Button()
			.states_([
				[scenes[j].patches[i], Color.white, Color.grey],
				[scenes[j].patches[i], Color.white, Color.new255(0, 139, 69)]
			])
			.font_(Font.default.size_((i%2).asBoolean.if({16},{32})))
			.minHeight_((i%2).asBoolean.if({30},{80}))
			.action_({|but|
				if(but.value == 1,{
					but.parent.children.do({|ab|
						if(but != ab, {ab.valueAction_(0)});
					});
				});
			})
		}!scenes[j].patches.size).spacing_(0).margins_([0,0,0,0]))
	));
};


var deviceView = {|i|
	VLayout(buttonsView.(i))
}!scenes.size;
w.layout = HLayout(*deviceView.());

)

​
