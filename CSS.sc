s.boot；
s.options.memSize_(65536 * 4); //according to the Note in JPverb
s.reboot；
(
	/*
* VARIABLES: all variables used in this synthesizer (with GUI)
	 */
var buf_ctrl, buf_src;
var path_ctrl, path_src;
var scope_ctrl, scope_src, scope_out, scope_text_ctrl, scope_text_src;
var eq_slider_fb, eq_slider_rq, eq_text_fb, eq_text_rq, eq_button_reset, eq_func_reset;
var cct_text_ttl, cct_text_zcr, cct_text_lms, cct_text_sc, cct_text_st, cct_slider_zcr,
    cct_slider_lms, cct_slider_sc, cct_slider_st,
	cct_text_time, cct_text_dur, cct_text_lens, cct_text_rand,
    cct_slider_time, cct_slider_dur, cct_slider_lens, cct_slider_rand,
    cct_button_freeze, cct_func_freeze,
    cct_button_reset, cct_func_reset;
var synth_control, synth_source, synth_concate, synth_eq, synth_rvrb, synth_output;
var bufnum_ctrl, bufnum_src, bufnum_scope_ctrl, bufnum_scope_src, bufnum_scope_out;
var bus_control, bus_source, bus_concat, bus_eq, bus_rvrb, bus_output;
var	layout_cct_zcr, layout_cct_lms, layout_cct_sc, layout_cct_st,
    layout_cct_time, layout_cct_dur, layout_cct_lens, layout_cct_rand;
var layout_cct, layout_scope, layout_eq;
var font = Font("Silom", 14);
/*var load_audio;
var dialog = Dialog.openPanel({ |list| make.(list) }, nil, true);*/

s.waitForBoot {
	/*
	 * DATASTORE: define buffers
	 */

	//input audio path      //todo: use dialog input instead
	path_ctrl = thisProcess.nowExecutingPath.dirname+/+"demo_control.wav";
	path_src = thisProcess.nowExecutingPath.dirname+/+"demo_source.wav";

	// use only only channel 0 as input
	buf_ctrl = Buffer.readChannel(s, path_ctrl, channels: 0);
	buf_src  = Buffer.readChannel(s, path_src, channels: 0);

	bufnum_ctrl = buf_ctrl.bufnum;
	bufnum_src = buf_src.bufnum;
	// buffer of scope components
	bufnum_scope_ctrl = Buffer.alloc(s, 1024, 1).bufnum;
	bufnum_scope_src  = Buffer.alloc(s, 1024, 1).bufnum;
	bufnum_scope_out  = Buffer.alloc(s, 1024, 1).bufnum;

	bufnum_ctrl.postln;
	bufnum_src.postln;
	bufnum_scope_ctrl.postln;
	bufnum_scope_src.postln;
	bufnum_scope_out.postln;

	// assign bus number
	bus_control = 44;
	bus_source  = 43;
	bus_concat  = 42;
	bus_eq      = 41;
	bus_rvrb    = 40;
	bus_output  =  0;

	/*
	 * SYNTHESIZER: main audio processing pipeline of concatenative sound synthesizer
	 */

	// SynthDef of main concatenative algorithm
	SynthDef(\zy_concate,{
		arg bufnum_scope_ctrl, bufnum_scope_src,
		    bus_control, bus_source, bus_concat,
		    storesize=5.0, seektime=1.0, seekdur=1.0, matchlen=0.05, freeze=0,
		    zcr=0.0, lms=1.0, sc=0.0, st=0.0,
		    rand=0.0, thres=0.01;
		var concat, control, source;
		//
		control = In.ar(bus_control, 1);
		source =  In.ar(bus_source, 1);
		// concatenative synthesis algorithm
		concat= Concat2.ar(control, source, storesize, seektime, seekdur, matchlen, freeze, zcr, lms, sc, st, rand, thres);
		// writing to scope buffer
		ScopeOut2.ar(Normalizer.ar(control), bufnum_scope_ctrl);
		ScopeOut2.ar(Normalizer.ar(source),  bufnum_scope_src);
		// writing to output bus
		Out.ar(bus_concat, concat) // remove this after debugging
		}).add;

	// 10-band EQ
	SynthDef(\zy_10eq, {
		arg bus_in, bus_out,
		    amp1 = 0, amp2 = 0, amp3 = 0, amp4 = 0, amp5 = 0,
		    amp6 = 0, amp7 = 0, amp8 = 0, amp9 = 0, amp10 = 0,
		    rq = 0.8;
		var freq1=36,   freq2=75,     freq3=157,    freq4=329,   freq5=688,
		    freq6=1440, freq7 = 3013, freq8 = 6303, freq9=13184, freq10=18000;
		var sig;
		sig = In.ar(bus_in, 1);
		sig = BPeakEQ.ar(sig, freq1, rq, amp1);
		sig = BPeakEQ.ar(sig, freq2, rq, amp2);
		sig = BPeakEQ.ar(sig, freq3, rq, amp3);
		sig = BPeakEQ.ar(sig, freq4, rq, amp4);
		sig = BPeakEQ.ar(sig, freq5, rq, amp5);
		sig = BPeakEQ.ar(sig, freq6, rq, amp6);
		sig = BPeakEQ.ar(sig, freq7, rq, amp7);
		sig = BPeakEQ.ar(sig, freq8, rq, amp8);
		sig = BPeakEQ.ar(sig, freq9, rq, amp9);
		sig = BPeakEQ.ar(sig, freq10, rq, amp10);
		sig = Limiter.ar(sig * 0.1, 1, 0.01);

		Out.ar(bus_out, sig)
	}).add;

	SynthDef(\zy_output, {
		arg bus_in, bus_out, bufnum_scope;
		var sig;
		sig = In.ar(bus_in, 1);
		ScopeOut2.ar(Normalizer.ar(sig),  bufnum_scope);
		Out.ar(bus_out, Pan2.ar(sig, 0.0))
	}).add;

	//active the control and source audio stream
	{Out.ar(bus_control, PlayBuf.ar(1,bufnum_ctrl,BufRateScale.kr(bufnum_ctrl), loop:1))}.play;
	{Out.ar(bus_source, PlayBuf.ar(1,bufnum_src,BufRateScale.kr(bufnum_src), loop:1))}.play;
/*	{Out.ar(bus_control, PinkNoise.ar(1))}.play;
	{Out.ar(bus_source, PinkNoise.ar(1))}.play;*/

	// run synths
	synth_output  = Synth(\zy_output, [\bus_in, bus_eq, \bus_out, bus_output , \bufnum_scope, bufnum_scope_out]);
	synth_eq      = Synth(\zy_10eq, [\bus_in, bus_concat, \bus_out, bus_eq]);
	synth_concate = Synth(\zy_concate, [\bufnum_scope_ctrl, bufnum_scope_ctrl, \bufnum_scope_src, bufnum_scope_src,
		                  \bus_control, bus_control, \bus_source, bus_source, \bus_concat, bus_concat]);


	/*
	 * GUI: define the graphic user interface
	 */

	// main window object
	w = Window.new("concatenative sound synthesizer", Rect(200,200, 1000, 600));
	w.view.background = Color.new255(205.0, 140.0, 149.0);
	w.front;
	CmdPeriod.doOnce {w.close};
	w.onClose = {
		synth_concate.free;
		synth_eq.free;
		synth_output.free;
		Buffer.freeAll; // free all the buffer
	}; //todo: free all synths here

	// scopeviewer components
	scope_text_ctrl = StaticText().string_("Control Signal").align_(\center).font_(font);
	scope_text_src = StaticText().string_("Source Signal").align_(\center).font_(font);
	// the scopeviewer of input controll signal
	scope_ctrl = ScopeView();
	scope_ctrl.background = Color.new255(66.0, 66.0, 66.0);
	scope_ctrl.server = s;
	scope_ctrl.bufnum = bufnum_scope_ctrl;
	scope_ctrl.start;
	scope_ctrl.style = 1;
	// the scopeviewer of input source signal
	scope_src = ScopeView();
	scope_src.background = Color.new255(66.0, 66.0, 66.0);
	scope_src.server = s;
	scope_src.bufnum = bufnum_scope_src;
	scope_src.start;
	scope_src.style = 1;
	// the scope of final output signal
	scope_out = ScopeView();
	scope_out.background = Color.new255(66.0, 66.0, 66.0);
	scope_out.server = s;
	scope_out.bufnum = bufnum_scope_out;
	scope_out.start;
	scope_out.style = 1;

	// equalizer components
	eq_text_fb = StaticText().string_("Equalizer").align_(\center).font_(font);
	// eq_text_rq = StaticText().string_("Q Factor").align_(\center).font_(font);
	// slider
	eq_slider_fb = {Slider(w, Rect(0, 0, 20, 100)).value_(0.5)}!10;
	eq_slider_rq = Slider(w, Rect(0, 00, 100, 10)).value_(0.31).action_({ |sl| synth_eq.set(\rq, sl.value.linlin(0, 1, 0.1, 3).postln)});
	// reset button
	eq_button_reset = Button().states_([["reset"]]).action_({eq_func_reset.value}).font_(font);
	// related functions
	eq_func_reset = {
		eq_slider_rq.value_(0.31);
		eq_slider_fb.do{ |sl| sl.value_(0.5)};
        // todo find a better way to restore default setting
		synth_eq.set(\amp1, 0.postln);
		synth_eq.set(\amp2, 0.postln);
		synth_eq.set(\amp3, 0.postln);
		synth_eq.set(\amp4, 0.postln);
		synth_eq.set(\amp5, 0.postln);
		synth_eq.set(\amp6, 0.postln);
		synth_eq.set(\amp7, 0.postln);
		synth_eq.set(\amp8, 0.postln);
		synth_eq.set(\amp9, 0.postln);
		synth_eq.set(\amp10, 0.postln);
		synth_eq.set(\rq, 0.8.postln)
	};
	eq_slider_fb.do{ |ins, c=0| c = c+1; ins.addAction({ |sl|
	 	~sl = sl.value;
	 	~low = -24;
	 	~high = 24;
	 	switch(c,
	 		1, {synth_eq.set(\amp1, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		2, {synth_eq.set(\amp2, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		3, {synth_eq.set(\amp3, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		4, {synth_eq.set(\amp4, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		5, {synth_eq.set(\amp5, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		6, {synth_eq.set(\amp6, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		7, {synth_eq.set(\amp7, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		8, {synth_eq.set(\amp8, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		9, {synth_eq.set(\amp9, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 		10, {synth_eq.set(\amp10, ~sl.linlin(0, 1, ~low, ~high).postln)},
	 	)});
	 };

	// concatenative synthesizer components
	cct_text_ttl = StaticText().string_("Synthesizer").align_(\center).font_(font);
	cct_text_zcr = StaticText().string_("zcr").align_(\center).font_(font);
	cct_text_lms = StaticText().string_("lms").align_(\center).font_(font);
	cct_text_sc =  StaticText().string_("s_c").align_(\center).font_(font);
	cct_text_st =  StaticText().string_("s_t").align_(\center).font_(font);
	cct_slider_zcr = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\zcr, sl.value.postln)});
	cct_slider_lms = Knob(w, Rect(0, 00, 100, 5)).value_(1.0).action_({ |sl| synth_concate.set(\lms, sl.value.postln)});
	cct_slider_sc  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\sc,  sl.value.postln)});
	cct_slider_st  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\st,  sl.value.postln)});

	cct_text_time = StaticText().string_("time").align_(\center).font_(font);
	cct_text_dur  = StaticText().string_("dur ").align_(\center).font_(font);
	cct_text_lens = StaticText().string_("lens").align_(\center).font_(font);
	cct_text_rand = StaticText().string_("rand").align_(\center).font_(font);

	cct_slider_time = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\seektime, sl.value.linlin(0, 1, 1, 5).postln)});
	cct_slider_dur  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\seekdur, sl.value.linlin(0, 1, 1, 5).postln)});
	cct_slider_lens = Knob(w, Rect(0, 00, 100, 5)).value_(0.5).action_({ |sl| synth_concate.set(\matchlen, sl.value.linlin(0, 1, 0.0, 0.1).postln)});
	cct_slider_rand = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\rand,  sl.value.linlin(0, 1, 0.0, 0.8).postln)});

	cct_button_freeze = Button().states_([["freeze", Color.black, Color.white],
		                                  ["sampling", Color.white, Color.new255(205.0, 140.0, 149.0)]]).action_({cct_func_freeze.value}).font_(font);
	cct_func_freeze = {synth_concate.set(\freeze, cct_button_freeze.value.postln)};

	cct_button_reset = Button().states_([["reset"]]).action_({cct_func_reset.value}).font_(font);
	cct_func_reset = {
		cct_slider_zcr.value_(0.0);
		cct_slider_lms.value_(1.0);
		cct_slider_sc.value_(0.0);
		cct_slider_st.value_(0.0);
		cct_slider_time.value_(0.0);
		cct_slider_dur.value_(0.0);
		cct_slider_lens.value_(0.5);
		cct_slider_rand.value_(0.0);
		synth_concate.set(\zcr, 0.0.postln);
		synth_concate.set(\lms, 1.0.postln);
		synth_concate.set(\sc,  0.0.postln);
		synth_concate.set(\st,  0.0.postln);
		synth_concate.set(\seektime, 0.0.postln);
		synth_concate.set(\seekdur, 0.0.postln);
		synth_concate.set(\matchlen,  0.5.postln);
		synth_concate.set(\rand,  0.0.postln);
	};

	// layout of GUI
	// concate synthesizer controll panel layout
	layout_cct_zcr  = GridLayout.rows([cct_text_zcr, cct_slider_zcr]);
	layout_cct_lms  = GridLayout.rows([cct_text_lms, cct_slider_lms]);
	layout_cct_sc   = GridLayout.rows([cct_text_sc, cct_slider_sc]);
	layout_cct_st   = GridLayout.rows([cct_text_st, cct_slider_st]);
	layout_cct_time = GridLayout.rows([cct_text_time, cct_slider_time]);
	layout_cct_dur  = GridLayout.rows([cct_text_dur, cct_slider_dur]);
	layout_cct_lens = GridLayout.rows([cct_text_lens, cct_slider_lens]);
	layout_cct_rand = GridLayout.rows([cct_text_rand, cct_slider_rand]);

	layout_cct   = GridLayout.columns([cct_text_ttl, GridLayout.rows([layout_cct_zcr, layout_cct_time]), GridLayout.rows([layout_cct_lms, layout_cct_dur]),
		GridLayout.rows([layout_cct_sc, layout_cct_lens]), GridLayout.rows([layout_cct_st, layout_cct_rand]), GridLayout.rows([cct_button_freeze, cct_button_reset])]);

	// audio scopeviewer layout
	layout_scope = GridLayout.columns([GridLayout.rows([GridLayout.columns([scope_text_ctrl, scope_src]),
		           GridLayout.columns([scope_text_src, scope_ctrl])]), scope_out]);

	// eq control panel layout
	layout_eq    = GridLayout.columns([GridLayout.columns([eq_text_fb, GridLayout.rows(eq_slider_fb)]), eq_slider_rq, eq_button_reset]);

	w.layout = GridLayout.columns([layout_scope, GridLayout.rows([layout_cct, layout_eq])]);
}；
)

/*DarkSlateGrey  [ 47.0, 79.0, 79.0 ]
LightPink3  [ 205.0, 140.0, 149.0 ]*/