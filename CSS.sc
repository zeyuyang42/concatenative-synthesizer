s.boot；
s.options.sampleRate = 44100;  // in case of sample rate mismatchi, mostly caused by Zoom's default 48000Hz setting
s.options.memSize_(65536 * 4); // according to the Note in JPverb
s.reboot；
(
	/*
     * VARIABLES: all variables used in this synthesizer (with GUI)
	 */
var buf_ctrl, buf_src, buf_rcd;
var path_ctrl, path_src;
var scope_ctrl, scope_src, scope_out, scope_text_ctrl, scope_text_src;
var in_text_ttl,
// in_text_source, in_text_control,
    in_button_s_reset, in_button_c_reset,
    in_button_load, in_button_load_mode,
    in_textv_source, in_textv_control,in_button_stretch_source,in_button_stretch_control,
    in_func_stretch_source, in_func_stretch_control,
    in_button_eval, in_button_eval_mode, in_textv_eval,
	in_button_live, in_button_live_mode,
    in_func_load, in_func_eval, in_func_live;
var cct_text_ttl,
    cct_text_zcr, cct_text_lms, cct_text_sc, cct_text_st,
    cct_knob_zcr, cct_knob_lms, cct_knob_sc, cct_knob_st,
	cct_text_time, cct_text_dur, cct_text_lens, cct_text_rand,
    cct_knob_time, cct_knob_dur, cct_knob_lens, cct_knob_rand,
    cct_button_freeze, cct_func_freeze,
    cct_button_reset, cct_func_reset;
var eq_slider_fb, eq_slider_rq, eq_text_fb, eq_text_rq, eq_button_reset, eq_func_reset;
var rvb_text_ttl,
	rvb_text_t60, rvb_text_damp, rvb_text_size, rvb_text_diff,
	rvb_text_depth, rvb_text_freq, rvb_text_low, rvb_text_high,
    rvb_knob_t60, rvb_knob_damp, rvb_knob_size, rvb_knob_diff,
	rvb_knob_depth, rvb_knob_freq, rvb_knob_low, rvb_knob_high,
    rvb_slider_mix,
    rvb_button_reset, rvb_func_reset;
var util_text_ttl,
    util_button_record, util_func_record,
    util_button_play, util_func_play,
    util_button_stream, util_func_stream,
    util_text_gain, util_text_gaino, util_text_gainc, util_text_gains,
    util_knob_gaino, util_knob_gainc, util_knob_gains;
var synth_control, synth_source, synth_concate, synth_eq, synth_rvrb, synth_output;
var bufnum_ctrl, bufnum_src, bufnum_scope_ctrl, bufnum_scope_src, bufnum_scope_out;
var bus_control, bus_source, bus_concat, bus_eq, bus_rvrb, bus_output, bus_silence;
var layout_in_load, layout_in_eval, layout_in_live;
var	layout_cct_zcr, layout_cct_lms, layout_cct_sc, layout_cct_st,
    layout_cct_time, layout_cct_dur, layout_cct_lens, layout_cct_rand;
var layout_rvb_t60, layout_rvb_damp, layout_rvb_size, layout_rvb_diff,
	layout_rvb_depth, layout_rvb_freq, layout_rvb_low, layout_rvb_high;
var layout_util_out, layout_util_gain;
var layout_scope, layout_in, layout_cct, layout_eq, layout_rvb, layout_util;
var font=Font("Silom", 14), ndef_fadetime=0.1;
var buf_readrate_ctrl = 1, buf_readrate_src = 1;
var button_marvin;
var img_marvin = Image.open(thisProcess.nowExecutingPath.dirname+/+"marvin6.png");

s.waitForBoot {
	/*
	 * DATASTORE: define buffers
	 */

	//load default audio
	path_ctrl = thisProcess.nowExecutingPath.dirname+/+"demo_control.wav";
	path_src = thisProcess.nowExecutingPath.dirname+/+"demo_source.wav";

	// use only channel 0 as input
	buf_ctrl = Buffer.readChannel(s, path_ctrl, channels: 0);
	buf_src  = Buffer.readChannel(s, path_src, channels: 0);
	buf_rcd  = Buffer.alloc(s, 44100 * 10.0, 1);

	bufnum_ctrl = buf_ctrl.bufnum;
	bufnum_src = buf_src.bufnum;
	// buffer of scope components
	bufnum_scope_ctrl = Buffer.alloc(s, 1024, 1).bufnum;
	bufnum_scope_src  = Buffer.alloc(s, 1024, 1).bufnum;
	bufnum_scope_out  = Buffer.alloc(s, 1024, 1).bufnum;

	// assign bus number
	bus_control = 84;
	bus_source  = 73;
	bus_concat  = 42;
	bus_eq      = 51;
	bus_rvrb    = 30;
	bus_output  =  0;
	bus_silence = 99;



	// instantiate the Recorder
	r = Recorder.new(s);
	r.filePrefix = "SuperCollider_";
	r.recHeaderFormat = "wav";
	thisProcess.platform.recordingsDir = thisProcess.nowExecutingPath.dirname+/+"recordings";

	/*
	 * SYNTHESIZER: main audio processing pipeline of concatenative sound synthesizer
	 */

	// SynthDef of main concatenative algorithm
	SynthDef(\zy_concate,{
		arg bufnum_scope_ctrl, bufnum_scope_src,
		    bus_control, bus_source, bus_concat,
		    storesize=5.0, seektime=1.0, seekdur=1.0, matchlen=0.05, freeze=0,
		    zcr=0.0, lms=1.0, sc=0.0, st=0.0,
		    rand=0.0, thres=0.01,
		    gainc=1.0, gains=1.0;
		var concat, control, source;
		//
		control = In.ar(bus_control, 1);
		source  = In.ar(bus_source, 1);
		control = control*gainc;
		source  = source*gains;
		// concatenative synthesis algorithm
		concat= Concat2.ar(control, source, storesize, seektime, seekdur, matchlen, freeze, zcr, lms, sc, st, rand, thres);
		// writing to scope buffer
		ScopeOut2.ar(Normalizer.ar(control), bufnum_scope_ctrl);
		ScopeOut2.ar(Normalizer.ar(source),  bufnum_scope_src);
		// writing to output bus
		Out.ar(bus_concat, concat)
		}).add;

	// 10-band EQ
	SynthDef(\zy_10eq, {
		arg bus_in, bus_out,
		    amp1 = 0, amp2 = 0, amp3 = 0, amp4 = 0, amp5 = 0,
		    amp6 = 0, amp7 = 0, amp8 = 0, amp9 = 0, amp10 = 0,
		    rq = 0.8;
		var freq1=36,   freq2=75,   freq3=157,  freq4=329,   freq5=688,
		    freq6=1440, freq7=3013, freq8=6303, freq9=13184, freq10=18000;
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

	// reverb
	SynthDef(\zy_reverb, {
		arg bus_in, bus_out, mix_rate = 0.5,
		    t60 = 1.0, damp = 0.0, size = 1, earlydiff = 0.707,
		    mdepth = 0.1, mfreq = 2, lowband = 500, highband = 2000;
		var sig_dry, sig_wet, sig_out;
		sig_dry = Pan2.ar(In.ar(bus_in, 1));
		sig_wet = JPverb.ar(sig_dry, t60, damp, size, earlydiff, mdepth, mfreq, 1, 1, lowband, highband);
		sig_out = Mix([sig_dry * mix_rate, sig_wet * (1-mix_rate)]);
		Out.ar(bus_out, sig_out)
	}).add;

	// output control
	SynthDef(\zy_output, {
		arg bus_in, bus_out, bufnum_scope,
		    gaino=1;
		var sig;
		sig = In.ar(bus_in, 2);
		sig = sig*gaino;
		ScopeOut2.ar(Normalizer.ar(sig),  bufnum_scope);
		Out.ar(bus_out, sig)
	}).add;

	// run input streams each with default samples
	{Out.ar(bus_control, Ndef(\zy_control).ar)}.play;。
	{Out.ar(bus_source,  Ndef(\zy_source).ar)}.play;
	Ndef(\zy_control).fadeTime = ndef_fadetime;
	Ndef(\zy_source).fadeTime = ndef_fadetime;
	Ndef(\zy_control, {PlayBuf.ar(1, bufnum_ctrl, 1, loop:1)});
	Ndef(\zy_source,  {PlayBuf.ar(1, bufnum_src,  1 , loop:1)});

	// run synths
	synth_output  = Synth(\zy_output,  [\bus_in, bus_rvrb, \bus_out, bus_output , \bufnum_scope, bufnum_scope_out]);
	synth_rvrb    = Synth(\zy_reverb,  [\bus_in, bus_eq, \bus_out, bus_rvrb]);
	synth_eq      = Synth(\zy_10eq,    [\bus_in, bus_concat, \bus_out, bus_eq]);
	synth_concate = Synth(\zy_concate, [\bufnum_scope_ctrl, bufnum_scope_ctrl, \bufnum_scope_src, bufnum_scope_src,
		                  \bus_control, bus_control, \bus_source, bus_source, \bus_concat, bus_concat]);


	/*
	 * GUI: define the graphic user interface
	 */

	// main window object
	w = Window.new("concatenative sound synthesizer", Rect(200,200, 800, 500));
	w.view.background = Color.new255(205.0, 140.0, 149.0);
	w.front;
	CmdPeriod.doOnce {w.close};
	w.onClose = {
		synth_concate.free;
		synth_eq.free;
		synth_output.free;
		synth_rvrb.free;
		Ndef.clear;
		Buffer.freeAll; // free all the buffer
	};


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


	// input components
	in_text_ttl = StaticText().string_("Inputs").align_(\top).font_(font);

	in_button_s_reset = Button().states_([["reset"]]).action_({}).font_(font);
	in_button_c_reset= Button().states_([["reset"]]).action_({}).font_(font);

	in_button_load = Button().states_([["load"]]).action_({in_func_load.value}).font_(font);
	in_button_load_mode = Button().states_([["control", Color.white, Color.new255(205.0, 140.0,149.0)],
		                              ["source", Color.white, Color.new255(205.0, 140.0,149.0)]]).font_(font);

	in_textv_source  = TextView();
	in_textv_control = TextView();
	in_button_stretch_source   = Button().states_([["setS"]]).action_({in_func_stretch_source.value}).font_(font);
	in_button_stretch_control  = Button().states_([["setC"]]).action_({in_func_stretch_control.value}).font_(font);


	in_func_stretch_source = {
		in_textv_source.string.postln;
		Ndef(\zy_source, {var pointer; var replaytime;
			replaytime = in_textv_source.string.interpret;
			replaytime.postln;
			pointer = LFSaw.ar(1/replaytime).range(0, 1);
			Warp1.ar(
				numChannels:1,
				bufnum:bufnum_src,
				pointer:pointer,
				freqScale:1,
				windowSize:0.1,
				envbufnum:-1,
				overlaps:8,
				windowRandRatio:0.1,
				interp:2
		)});
	};
	in_func_stretch_control = {
		in_textv_control.string.postln;
		Ndef(\zy_control, {var pointer; var replaytime;
			replaytime = in_textv_control.string.interpret;
			replaytime.postln;
			pointer = LFSaw.ar(1/replaytime).range(0, 1);
			Warp1.ar(
				numChannels:1,
				bufnum:bufnum_ctrl,
				pointer:pointer,
				freqScale:1,
				windowSize:0.1,
				envbufnum:-1,
				overlaps:8,
				windowRandRatio:0.1,
				interp:2
		)});
	};

	in_func_load = {
		in_button_load_mode.value.postln;
		Dialog.openPanel({ |file| m.(file, in_button_load_mode.value) }, nil, false);
	};
	m = {
		|path, mode|
		[\loaded, path].postln;
		["mode: ", mode].postln;
		if ((in_button_load_mode.value == 0), {
			buf_ctrl.free;
			buf_ctrl = Buffer.readChannel(s, path, channels: 0);
			Ndef(\zy_control, {PlayBuf.ar(1,bufnum_ctrl, 1, loop:1)});
		});
		if ((in_button_load_mode.value == 1), {
			buf_src.free;
			buf_src = Buffer.readChannel(s, path, channels: 0);
			Ndef(\zy_source,  {PlayBuf.ar(1,bufnum_src, 1, loop:1)});
		});
	};

	// evaluation components
	in_button_eval = Button().states_([["eval"]]).action_({in_func_eval.value}).font_(font);
	in_button_eval_mode = Button().states_([["control", Color.white, Color.new255(205.0, 140.0,149.0)],
		                              ["source", Color.white, Color.new255(205.0, 140.0,149.0)]]).font_(font);
	in_textv_eval = TextView().string_("Saw.ar(SinOsc.kr(LFNoise0.kr(0.1,3,4.5),0,10,30))");
	in_func_eval = {
		in_button_eval_mode.value.postln;
		if ((in_button_eval_mode.value == 0), {
			Ndef(\zy_control, {in_textv_eval.string.interpret.asArray});
		});
		if ((in_button_eval_mode.value == 1), {
			Ndef(\zy_source, {in_textv_eval.string.interpret.asArray});
		});
	};

	// live sampling components
	in_button_live = Button().states_([["live"]]).action_({in_func_live.value}).font_(font);
	in_button_live_mode = Button().states_([["control", Color.white, Color.new255(205.0, 140.0,149.0)],
		                              ["source", Color.white, Color.new255(205.0, 140.0,149.0)]]).font_(font);
	in_func_live = {
		in_button_live_mode.value.postln;
		if ((in_button_live_mode.value == 0), {
			Ndef(\zy_control, { SoundIn.ar });
		});
		if ((in_button_live_mode.value == 1), {
			Ndef(\zy_source, { SoundIn.ar });
		});
	};


	// concatenative synthesizer components
	cct_text_ttl = StaticText().string_("Concatenator").align_(\top).font_(font);
	cct_text_zcr = StaticText().string_("zcr").align_(\center).font_(font);
	cct_text_lms = StaticText().string_("lms").align_(\center).font_(font);
	cct_text_sc =  StaticText().string_("s_c").align_(\center).font_(font);
	cct_text_st =  StaticText().string_("s_t").align_(\center).font_(font);
	cct_knob_zcr = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\zcr, sl.value.postln)});
	cct_knob_lms = Knob(w, Rect(0, 00, 100, 5)).value_(1.0).action_({ |sl| synth_concate.set(\lms, sl.value.postln)});
	cct_knob_sc  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\sc,  sl.value.postln)});
	cct_knob_st  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\st,  sl.value.postln)});

	cct_text_time = StaticText().string_("time").align_(\center).font_(font);
	cct_text_dur  = StaticText().string_("dur ").align_(\center).font_(font);
	cct_text_lens = StaticText().string_("lens").align_(\center).font_(font);
	cct_text_rand = StaticText().string_("rand").align_(\center).font_(font);

	cct_knob_time = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\seektime, sl.value.linlin(0, 1, 1, 5).postln)});
	cct_knob_dur  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\seekdur, sl.value.linlin(0, 1, 1, 5).postln)});
	cct_knob_lens = Knob(w, Rect(0, 00, 100, 5)).value_(0.5).action_({ |sl| synth_concate.set(\matchlen, sl.value.linlin(0, 1, 0.0, 0.1).postln)});
	cct_knob_rand = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_concate.set(\rand,  sl.value.linlin(0, 1, 0.0, 0.8).postln)});

	cct_button_freeze = Button().states_([["freeze", Color.black, Color.white],
		                                  ["sampling", Color.white, Color.new255(205.0, 140.0, 149.0)]]).action_({cct_func_freeze.value}).font_(font);
	cct_func_freeze = {synth_concate.set(\freeze, cct_button_freeze.value.postln)};

	cct_button_reset = Button().states_([["reset"]]).action_({cct_func_reset.value}).font_(font);
	cct_func_reset = {
		cct_knob_zcr.value_(0.0);
		cct_knob_lms.value_(1.0);
		cct_knob_sc.value_(0.0);
		cct_knob_st.value_(0.0);
		cct_knob_time.value_(0.0);
		cct_knob_dur.value_(0.0);
		cct_knob_lens.value_(0.5);
		cct_knob_rand.value_(0.0);
		synth_concate.set(\zcr, 0.0.postln);
		synth_concate.set(\lms, 1.0.postln);
		synth_concate.set(\sc,  0.0.postln);
		synth_concate.set(\st,  0.0.postln);
		synth_concate.set(\seektime, 0.0.postln);
		synth_concate.set(\seekdur, 0.0.postln);
		synth_concate.set(\matchlen,  0.5.postln);
		synth_concate.set(\rand,  0.0.postln);
	};


	// equalizer components
	eq_text_fb = StaticText().string_("Equalizer").align_(\center).font_(font);
	// filter bank
	eq_slider_fb = {Slider(w, Rect(0, 0, 5, 100)).value_(0.5)}!10;
	eq_slider_rq = Slider(w, Rect(0, 00, 100, 10)).value_(0.31).action_({ |sl| synth_eq.set(\rq, sl.value.linlin(0, 1, 0.1, 3).postln)});
	// reset button
	eq_button_reset = Button().states_([["reset"]]).action_({eq_func_reset.value}).font_(font);
	// related functions
	eq_func_reset = {
		eq_slider_rq.value_(0.31);
		eq_slider_fb.do{ |sl| sl.value_(0.5)};
        // todo: find a better way to restore default setting
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


	// internal reverb components
	rvb_text_ttl = StaticText().string_("Reverb").align_(\top).font_(font);

	rvb_text_t60   = StaticText().string_("T_60").align_(\center).font_(font);
	rvb_text_damp  = StaticText().string_("damp").align_(\center).font_(font);
	rvb_text_size  = StaticText().string_("size").align_(\center).font_(font);
	rvb_text_diff  = StaticText().string_("diff").align_(\center).font_(font);
	rvb_text_depth = StaticText().string_("dpth").align_(\center).font_(font);
	rvb_text_freq  = StaticText().string_("freq").align_(\center).font_(font);
	rvb_text_low   = StaticText().string_("low").align_(\center).font_(font);
	rvb_text_high  = StaticText().string_("high").align_(\center).font_(font);

	rvb_knob_t60   = Knob(w, Rect(0, 00, 100, 5)).value_(0.091).action_({ |sl| synth_rvrb.set(\t60, sl.value.linlin(0, 1, 0.1, 10).postln)});
	rvb_knob_damp  = Knob(w, Rect(0, 00, 100, 5)).value_(0.0).action_({ |sl| synth_rvrb.set(\damp, sl.value.linlin(0, 1, 0, 1).postln)});
	rvb_knob_size  = Knob(w, Rect(0, 00, 100, 5)).value_(0.3).action_({ |sl| synth_rvrb.set(\size, sl.value.linexp(0, 1, 0.5, 5).postln)});
	rvb_knob_diff  = Knob(w, Rect(0, 00, 100, 5)).value_(0.707).action_({ |sl| synth_rvrb.set(\earlydiff, sl.value.linlin(0, 1, 0, 1).postln)});
	rvb_knob_depth = Knob(w, Rect(0, 00, 100, 5)).value_(0.1).action_({ |sl| synth_rvrb.set(\mdepth, sl.value.linlin(0, 1, 0, 1).postln)});
	rvb_knob_freq  = Knob(w, Rect(0, 00, 100, 5)).value_(0.2).action_({ |sl| synth_rvrb.set(\mfreq, sl.value.linlin(0, 1, 0, 10).postln)});
	rvb_knob_low   = Knob(w, Rect(0, 00, 100, 5)).value_(0.39).action_({ |sl| synth_rvrb.set(\lowband, sl.value.linexp(0, 1, 100, 6000).postln)});
	rvb_knob_high  = Knob(w, Rect(0, 00, 100, 5)).value_(0.3).action_({ |sl| synth_rvrb.set(\highband, sl.value.linexp(0, 1, 1000, 10000).postln)});

	rvb_slider_mix = Slider(w, Rect(0, 00, 100, 10)).value_(0.5).action_({ |sl| synth_rvrb.set(\mix_rate, sl.value.linlin(0, 1, 0, 1).postln)});

	rvb_button_reset = Button().states_([["reset"]]).action_({rvb_func_reset.value}).font_(font);
	rvb_func_reset = {
		rvb_knob_t60.value_(0.091);
		rvb_knob_damp.value_(0.0);
		rvb_knob_size.value_(0.3);
		rvb_knob_diff.value_(0.707);
		rvb_knob_depth.value_(0.1);
		rvb_knob_freq.value_(0.2);
		rvb_knob_low.value_(0.39);
		rvb_knob_high.value_(0.3);
		synth_rvrb.set(\t60, 0.091.linlin(0, 1, 0.1, 10).postln);
		synth_rvrb.set(\damp, 0.0.linlin(0, 1, 0, 1).postln);
		synth_rvrb.set(\size,  0.3.linexp(0, 1, 0.5, 5).postln);
		synth_rvrb.set(\earlydiff,  0.707.linlin(0, 1, 0, 1).postln);
		synth_rvrb.set(\mdepth, 0.1.linlin(0, 1, 0, 1).postln);
		synth_rvrb.set(\mfreq, 0.2.linlin(0, 1, 0, 10).postln);
		synth_rvrb.set(\lowband,  0.39.linexp(0, 1, 100, 6000).postln);
		synth_rvrb.set(\highband,  0.3.linexp(0, 1, 1000, 10000).postln);

		rvb_slider_mix.value_(0.5);
		synth_rvrb.set(\mix_rate,  0.5.postln);
	};

	// utility components
	util_text_ttl = StaticText().string_("Outputs").align_(\top).font_(font);
	util_button_record = Button(w, Rect(0, 00, 10, 5)).states_([["Record", Color.black, Color.white],
		                                  ["ing...", Color.white, Color.new255(205.0, 140.0, 149.0)]]).action_({util_func_record.value}).font_(font);
	util_func_record = {
		if ((util_button_record.value == 0), {
			"stop recording...".postln;
		    // start recording:
	        r.stopRecording;
		});
		if ((util_button_record.value == 1), {
			"start recording...".postln;
	        r.record;
		});
	};

	util_button_play = Button(w, Rect(0, 00, 10, 5)).states_([["Stop", Color.black, Color.white],
		                                  ["Start", Color.white, Color.new255(205.0, 140.0, 149.0)]]).action_({util_func_play.value}).font_(font);
	util_func_play = {
		if ((util_button_play.value == 0), {
			"Start playing...".postln;
			synth_output.set(\bus_out, bus_output);
		});
		if ((util_button_play.value == 1), {
			"Stop playing...".postln;
			synth_output.set(\bus_out, bus_silence);
		});
	};

	util_button_stream = Button(w, Rect(0, 00, 10, 5)).states_([
		                                  ["Output",  Color.black, Color.white],
		                                  ["Source",  Color.white, Color.new255(205.0, 140.0, 149.0)],
		                                  ["Control", Color.white, Color.new255(205.0, 140.0, 149.0)]
	                                      ]).font_(font).action_({util_func_stream.value});
	util_func_stream = {
		if ((util_button_stream.value == 0), {
			"Output".postln;
			synth_output.set(\bus_in, bus_rvrb);
		});
		if ((util_button_stream.value == 1), {
			"Source".postln;
			synth_output.set(\bus_in, bus_source);
		});
		if ((util_button_stream.value == 2), {
			"Control".postln;
			synth_output.set(\bus_in, bus_control);
		});
	};
	util_text_gain  = StaticText().string_("Gain").align_(\top).font_(font);
	util_text_gainc  = StaticText().string_("C").align_(\top).font_(font);
	util_text_gaino  = StaticText().string_("O").align_(\top).font_(font);
	util_text_gains  = StaticText().string_("S").align_(\top).font_(font);
	util_knob_gainc = Slider(w, Rect(0, 00, 5, 10)).value_(0).action_({ |sl| synth_concate.set(\gainc, sl.value.linexp(0, 1, 1, 16).postln)});
	util_knob_gaino = Slider(w, Rect(0, 00, 5, 10)).value_(0).action_({ |sl| synth_output.set(\gaino, sl.value.linlin(0, 1, 1, 16).postln)});
    util_knob_gains = Slider(w, Rect(0, 00, 5, 10)).value_(0).action_({ |sl| synth_concate.set(\gains, sl.value.linlin(0, 1, 1, 16).postln)});
	button_marvin = Button(w, Rect(0, 00, 10, 5)).states_([
		                                  ["Life?",  Color.black, Color.white],
		                                  ["Don't",  Color.white, Color.new255(205.0, 140.0, 149.0)],
		                                  ["talk", Color.white, Color.new255(205.0, 140.0, 149.0)],
				                          ["to me",  Color.white, Color.new255(205.0, 140.0, 149.0)],
		                                  ["About", Color.white, Color.new255(205.0, 140.0, 149.0)],
				                          ["Life!",  Color.white, Color.new255(205.0, 140.0, 149.0)]
	                                      ]).font_(font);


	// layout of GUI
	// audio scopeviewer layout
	layout_scope = GridLayout.columns([GridLayout.rows([GridLayout.columns([scope_text_ctrl, scope_ctrl]),
		           GridLayout.columns([scope_text_src, scope_src])]), scope_out]);

	layout_in_load = GridLayout.columns([GridLayout.rows([in_button_load, in_button_load_mode]),
	                 GridLayout.rows([in_textv_control, in_button_stretch_control]),
		             GridLayout.rows([in_textv_source, in_button_stretch_source])]);

	layout_in_eval = GridLayout.columns([GridLayout.rows([in_button_eval, in_button_eval_mode]), in_textv_eval]);
	layout_in_live = GridLayout.rows([in_button_live, in_button_live_mode]);

	layout_in = GridLayout.columns([in_text_ttl, layout_in_load, layout_in_eval, layout_in_live]);

	// concate synthesizer controll panel layout
	layout_cct_zcr  = GridLayout.rows([cct_text_zcr, cct_knob_zcr]);
	layout_cct_lms  = GridLayout.rows([cct_text_lms, cct_knob_lms]);
	layout_cct_sc   = GridLayout.rows([cct_text_sc, cct_knob_sc]);
	layout_cct_st   = GridLayout.rows([cct_text_st, cct_knob_st]);
	layout_cct_time = GridLayout.rows([cct_text_time, cct_knob_time]);
	layout_cct_dur  = GridLayout.rows([cct_text_dur, cct_knob_dur]);
	layout_cct_lens = GridLayout.rows([cct_text_lens, cct_knob_lens]);
	layout_cct_rand = GridLayout.rows([cct_text_rand, cct_knob_rand]);

	layout_cct   = GridLayout.columns([cct_text_ttl, GridLayout.rows([layout_cct_zcr, layout_cct_time]), GridLayout.rows([layout_cct_lms, layout_cct_dur]),
		GridLayout.rows([layout_cct_sc, layout_cct_lens]), GridLayout.rows([layout_cct_st, layout_cct_rand]), GridLayout.rows([cct_button_freeze, cct_button_reset])]);


	// eq control panel layout
	layout_eq    = GridLayout.columns([GridLayout.columns([eq_text_fb, GridLayout.rows(eq_slider_fb)]), eq_slider_rq, eq_button_reset]);


	// concate synthesizer controll panel layout
	layout_rvb_t60   = GridLayout.rows([rvb_text_t60, rvb_knob_t60]);
	layout_rvb_damp  = GridLayout.rows([rvb_text_damp, rvb_knob_damp]);
	layout_rvb_size  = GridLayout.rows([rvb_text_size, rvb_knob_size]);
	layout_rvb_diff  = GridLayout.rows([rvb_text_diff, rvb_knob_diff]);
	layout_rvb_depth = GridLayout.rows([rvb_text_depth, rvb_knob_depth]);
	layout_rvb_freq  = GridLayout.rows([rvb_text_freq, rvb_knob_freq]);
	layout_rvb_low   = GridLayout.rows([rvb_text_low, rvb_knob_low]);
	layout_rvb_high  = GridLayout.rows([rvb_text_high, rvb_knob_high]);

	layout_rvb = GridLayout.columns([rvb_text_ttl,
		         GridLayout.rows([layout_rvb_t60, layout_rvb_damp]),
		         GridLayout.rows([layout_rvb_size, layout_rvb_diff]),
		         GridLayout.rows([layout_rvb_depth, layout_rvb_freq]),
		         GridLayout.rows([layout_rvb_low, layout_rvb_high]),
		         GridLayout.rows([rvb_slider_mix, rvb_button_reset])]);


	// utilities function panel layout
	layout_util_out  = GridLayout.columns([util_button_record, util_button_play, util_button_stream, button_marvin, img_marvin.plot(showInfo:false)]);
	layout_util_gain = GridLayout.rows([GridLayout.columns([util_knob_gainc, util_text_gainc]),
		                                GridLayout.columns([util_knob_gaino, util_text_gaino]),
		                                GridLayout.columns([util_knob_gains, util_text_gains])]);

	// layout_util = GridLayout.columns([util_text_ttl, layout_util_out, layout_util_gain]);
	layout_util = GridLayout.columns([util_text_ttl,  GridLayout.rows([layout_util_gain, layout_util_out])    ]);

	w.layout = GridLayout.columns([layout_scope, GridLayout.rows([layout_in, layout_cct, layout_eq, layout_rvb, layout_util])]);
}；
)

// have fun~
/*DarkSlateGrey  [ 66.0, 66.0, 66.0 ]
LightPink3  [ 205.0, 140.0, 149.0 ]*/