package com.example.paddleocrandroidapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            processPdf(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start file picker on launch
        pickPdfLauncher.launch("application/pdf")
    }

    private fun processPdf(uri: Uri) {
        val fileName = getFileName(uri) ?: "temp.pdf"
        val file = File(cacheDir, fileName)
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    copyStream(inputStream, outputStream)
                }
            }
        } catch (e: Exception) {
            showError("Failed to read file: ${e.message}")
            return
        }

        val python = if (Python.isStarted()) Python.getInstance() else Python.start(AndroidPlatform(this))
        val ocrScript: PyObject = python.getModule("ocr_script")

        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Processing")
            .setMessage("Please wait while the OCR is running...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            try {
                val result = ocrScript.callAttr("main", file.absolutePath).toString()
                handler.post {
                    progressDialog.dismiss()
                    showResult(result)
                }
            } catch (e: Exception) {
                handler.post {
                    progressDialog.dismiss()
                    showError("OCR failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun showResult(text: String) {
        AlertDialog.Builder(this)
            .setTitle("OCR Result")
            .setMessage(text)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun copyStream(input: InputStream, output: FileOutputStream) {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }
}
