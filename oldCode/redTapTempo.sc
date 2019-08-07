

a= RedTapTempoGUI();

a.tempo;
a.tempo= 2;
a.monitor= true;
a.monitorBus= 0;
a.monitorAmp= 0.2;
a.monitor= false;
a.close;

//--
(
w= Window("hgf", Rect(100, 200, 500, 500));
w.front;
v= CompositeView(w, Rect(10, 10, 400, 400)).background_(Color.blue).decorator= FlowLayout(Rect(0, 0, 300, 300));
c= TempoClock(1.4);
a= RedTapTempoGUI(c, parent:v);
)
c.tempo= 2.2