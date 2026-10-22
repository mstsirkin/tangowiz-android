package org.tangowiz.app
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
class ExportProvider:ContentProvider(){
 override fun onCreate()=true
 private fun file(uri:Uri):File { val name=uri.lastPathSegment?:error("Missing export");require(Regex("tangowiz-debug-[0-9]+\\.zip").matches(name));return File(context!!.cacheDir,name) }
 override fun getType(uri:Uri)="application/zip"
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {require(mode=="r");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)}
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor { val f=file(uri);return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).apply{addRow(arrayOf(f.name,f.length()))} }
 override fun insert(uri:Uri,values:ContentValues?):Uri?=null
 override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
 override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
}
