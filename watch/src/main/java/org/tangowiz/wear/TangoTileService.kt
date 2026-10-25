package org.tangowiz.wear
import androidx.wear.tiles.*
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.DimensionBuilders.*
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ActionBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class TangoTileService:TileService(){
 override fun onCreate(){super.onCreate();WatchCache.init(this)}
 override fun onTileRequest(requestParams:RequestBuilders.TileRequest):ListenableFuture<TileBuilders.Tile>{val state=WatchCache.state
  fun click(id:String)=ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(if(id=="display")AmbientDisplayActivity::class.java.name else WatchActivity::class.java.name).addKeyToExtraMapping("control",ActionBuilders.AndroidStringExtra.Builder().setValue(id).build()).build()).build()).build()
  fun text(value:String,size:Float,id:String?=null):LayoutElementBuilders.Text{val t=LayoutElementBuilders.Text.Builder().setText(if(value.contains(" ?"))"? "+value.removeSuffix(" ?") else value).setMaxLines(2).setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(ColorBuilders.argb(0xffeef8f1.toInt())).build());if(id!=null)t.setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(click(id)).setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(8f)).build()).setBackground(ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xff215343.toInt())).setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(18f)).build()).build()).build());return t.build()}
  val buttons=LayoutElementBuilders.Row.Builder().setWidth(wrap()).addContent(text(if(state.optBoolean("listening"))"Ⅱ" else "▶",22f,"toggle")).addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build()).addContent(text(if(state.optString("microphone")=="watch")"⌚" else "📱",18f,"microphone")).addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build()).addContent(text("☷",22f,"history")).addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build()).addContent(text("⛶",22f,"display")).build()
  val status=if(WatchCache.pending.isNotBlank())WatchCache.pending else if(WatchCache.error.isNotBlank())WatchCache.error else if(!WatchCache.connected)"Disconnected · cached" else if(state.optBoolean("listening")&&!state.optBoolean("ready"))state.optString("phase","Starting…") else if(WatchCache.stale())"Cached" else state.optString("phase","")
  val root=LayoutElementBuilders.Column.Builder().setWidth(expand()).setHeight(expand()).setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER).setModifiers(ModifiersBuilders.Modifiers.Builder().setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(24f)).setEnd(dp(24f)).setTop(dp(25f)).setBottom(dp(20f)).build()).build()).addContent(text(WatchCache.orchestra(),22f)).addContent(text(WatchCache.song(),15f)).addContent(text(status,11f)).addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build()).addContent(buttons).build()
  return Futures.immediateFuture(TileBuilders.Tile.Builder().setResourcesVersion("1").setFreshnessIntervalMillis(30000).setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build()).build()).build())
 }
 override fun onTileResourcesRequest(requestParams:RequestBuilders.ResourcesRequest)=Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())
}
