package org.tangowiz.app

import com.google.android.gms.wearable.*
import org.tangowiz.link.WearMessages

class PhoneWearService:WearableListenerService(){
 override fun onMessageReceived(event:MessageEvent){when(event.path){WearMessages.COMMAND_PATH->PhoneWatch.command(this,event.sourceNodeId,event.data);WearMessages.WATCH_STATUS_PATH->PhoneWatch.watchStatus(this,event.sourceNodeId,event.data)}}
 override fun onChannelOpened(channel:ChannelClient.Channel){PhoneWatch.init(this);WatchAudioInput.open(this,channel)}
 override fun onPeerConnected(peer:Node){PhoneWatch.peerConnected(this)}
 override fun onPeerDisconnected(peer:Node){PhoneWatch.changed()}
}
