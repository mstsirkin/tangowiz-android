package org.tangowiz.wear

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

/** Local watch presentation; changing these options never changes phone recognition settings. */
object DisplayPreferences {
 private fun prefs()=WatchCache.context.getSharedPreferences("display-names",Context.MODE_PRIVATE)
 var shortOrchestra:Boolean get()=prefs().getBoolean("short_orchestra",false);set(value){prefs().edit().putBoolean("short_orchestra",value).apply()}
 var showSinger:Boolean get()=prefs().getBoolean("show_singer",false);set(value){prefs().edit().putBoolean("show_singer",value).apply()}
 var singerFirstName:Boolean get()=prefs().getBoolean("singer_first_name",false);set(value){prefs().edit().putBoolean("singer_first_name",value).apply()}
 fun show(context:Context):AlertDialog{
  val checked=booleanArrayOf(shortOrchestra,showSinger,singerFirstName)
  var dependentAdapter:BaseAdapter?=null
  val dialog=AlertDialog.Builder(context).setTitle("Display names")
   .setMultiChoiceItems(arrayOf("Short orchestra name","Show singer","Show singer’s first name"),checked){_,index,value->
    when(index){0->shortOrchestra=value;1->showSinger=value;2->singerFirstName=value}
    if(index==1)dependentAdapter?.notifyDataSetChanged()
    WatchCache.changed()
   }.setPositiveButton("Done",null).show()
  val original=dialog.listView.adapter
  dependentAdapter=object:BaseAdapter(){
   override fun getCount()=original.count
   override fun getItem(position:Int)=original.getItem(position)
   override fun getItemId(position:Int)=original.getItemId(position)
   override fun areAllItemsEnabled()=false
   override fun isEnabled(position:Int)=position!=2||showSinger
   override fun getView(position:Int,convertView:View?,parent:ViewGroup):View =
    original.getView(position,convertView,parent).apply{
     isEnabled=isEnabled(position)
     alpha=if(isEnabled)1f else 0.38f
    }
  }
  dialog.listView.adapter=dependentAdapter
  checked.forEachIndexed{index,value->dialog.listView.setItemChecked(index,value)}
  return dialog
 }
}
