package org.tangowiz.wear
import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.*
class TangoComplicationService:ComplicationDataSourceService(){
 override fun onCreate(){super.onCreate();WatchCache.init(this)}
 private fun data(preview:Boolean=false):ComplicationData{val orchestra=if(preview)"Miguel Caló" else WatchCache.orchestra();val song=if(preview)"Jamás retornarás" else listOf(WatchCache.singer(),WatchCache.song()).filter{it.isNotBlank()}.joinToString(" · ");val stale=!preview&&WatchCache.stale();val text=if(stale&&orchestra!="—")"? $orchestra" else if(orchestra.contains(" ?"))"? "+orchestra.removeSuffix(" ?") else orchestra;val title=if(stale&&song.isBlank())"Cached · open TangoWiz" else if(song.contains(" ?"))"? "+song.removeSuffix(" ?") else song
  val builder=LongTextComplicationData.Builder(PlainComplicationText.Builder(text).build(),PlainComplicationText.Builder(listOf(text,title).filter{it.isNotBlank()}.joinToString(". ")).build()).setTitle(PlainComplicationText.Builder(title).build()).setTapAction(PendingIntent.getActivity(this,1,Intent(this,WatchActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE));if(!preview)builder.setValidTimeRange(TimeRange.before(java.time.Instant.ofEpochMilli(WatchCache.state.optLong("updatedAt",0)+600000)));return builder.build()
 }
 override fun onComplicationRequest(request:ComplicationRequest,listener:ComplicationRequestListener){listener.onComplicationData(data())}
 override fun getPreviewData(type:ComplicationType)=if(type==ComplicationType.LONG_TEXT)data(true) else null
}
