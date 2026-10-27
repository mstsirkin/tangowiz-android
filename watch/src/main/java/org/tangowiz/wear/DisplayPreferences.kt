package org.tangowiz.wear

import android.app.AlertDialog
import android.content.Context

/** Local watch presentation; changing these options never changes phone recognition settings. */
object DisplayPreferences {
 private fun prefs()=WatchCache.context.getSharedPreferences("display-names",Context.MODE_PRIVATE)
 var shortOrchestra:Boolean get()=prefs().getBoolean("short_orchestra",false);set(value){prefs().edit().putBoolean("short_orchestra",value).apply()}
 var showSinger:Boolean get()=prefs().getBoolean("show_singer",false);set(value){prefs().edit().putBoolean("show_singer",value).apply()}
 var singerFirstName:Boolean get()=prefs().getBoolean("singer_first_name",false);set(value){prefs().edit().putBoolean("singer_first_name",value).apply()}
 fun show(context:Context):AlertDialog{
  val checked=booleanArrayOf(shortOrchestra,showSinger,singerFirstName)
  return AlertDialog.Builder(context).setTitle("Display names")
   .setMultiChoiceItems(arrayOf("Short orchestra name","Show singer","Show singer’s first name"),checked){_,index,value->
    when(index){0->shortOrchestra=value;1->showSinger=value;2->singerFirstName=value}
    WatchCache.changed()
   }.setPositiveButton("Done",null).show()
 }
}
