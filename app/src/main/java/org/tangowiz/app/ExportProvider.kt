package org.tangowiz.app
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
class ExportProvider:ContentProvider(){
 override fun onCreate()=true
 private fun file(uri:Uri):File{if(uri.scheme!="content"||uri.authority!="org.tangowiz.app.exports"||uri.pathSegments.size!=1)throw FileNotFoundException("Invalid export URI");val name=uri.lastPathSegment?:throw FileNotFoundException("Missing export");if(!Regex("tangowiz-debug-[0-9]+\\.zip").matches(name))throw FileNotFoundException("Invalid export name");val f=File(context!!.cacheDir,name);if(!f.isFile||f.canonicalFile.parentFile!=context!!.cacheDir.canonicalFile)throw FileNotFoundException("ZIP is no longer available; create another export");return f}
 override fun getType(uri:Uri):String{file(uri);return "application/zip"}
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{if(mode!="r")throw FileNotFoundException("Exports are read-only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)}
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor{val f=file(uri);val columns=projection?.filter{it==OpenableColumns.DISPLAY_NAME||it==OpenableColumns.SIZE}?.toTypedArray()?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE);return MatrixCursor(columns).apply{addRow(columns.map{if(it==OpenableColumns.DISPLAY_NAME)f.name else f.length()}.toTypedArray())}}
 override fun insert(uri:Uri,values:ContentValues?):Uri?=null
 override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
 override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
}
