package org.tangowiz.app

import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Phone capture choices share the recognizer's existing PCM format. */
object PhoneCapture {
 fun source(mode:String):Int=when(mode){"default"->MediaRecorder.AudioSource.DEFAULT;"camcorder"->MediaRecorder.AudioSource.CAMCORDER;else->error("Unknown phone capture mode")}
 fun create(mode:String):AudioRecord {
  val size=maxOf(AudioRecord.getMinBufferSize(8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),16000)
  val recorder=AudioRecord(source(mode),8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size)
  if(recorder.state!=AudioRecord.STATE_INITIALIZED){recorder.release();error("Microphone initialization failed")}
  return recorder
 }
 fun describe(ar:AudioRecord):JSONObject {
  val row=JSONObject().put("audio_source",ar.audioSource).put("sample_rate",ar.sampleRate).put("channels",ar.channelCount).put("encoding",ar.audioFormat)
  runCatching{ar.routedDevice?.let{row.put("routed_device",JSONObject().put("id",it.id).put("type",it.type).put("name",it.productName.toString()))}}
  runCatching{ar.activeRecordingConfiguration?.let{row.put("device_sample_rate",it.format.sampleRate).put("client_source",it.clientAudioSource)}}
  if(Build.VERSION.SDK_INT>=28)runCatching{val mics=JSONArray();ar.activeMicrophones.forEach{mics.put(JSONObject().put("id",it.id).put("description",it.description).put("location",it.location))};row.put("active_microphones",mics)}
  return row
 }
}
